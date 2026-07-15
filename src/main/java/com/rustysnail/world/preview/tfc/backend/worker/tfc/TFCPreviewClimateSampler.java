package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.OverworldClimateModel;

/**
 * A lightweight, Level-free climate sampler for the preview. Subclasses TFC's
 * {@link OverworldClimateModel} so it can reuse the model's (public) monthly + elevation temperature
 * math ({@link OverworldClimateModel#getAverageMonthlyTemperature}) without a generated world, and
 * reimplements the model's seasonal-rainfall triangle ({@code getInstantRainfall}) as a static
 * function of the ChunkData rainfall/variance so no {@code Level} lookup is needed.
 *
 * <p>Relationship to TFC:
 * <ul>
 *   <li>Temperature per (calendar) sample matches {@code OverworldClimateModel.getInstantTemperature}
 *       minus the per-tick daily random variation (omitted for a stable regional preview): month
 *       factor is interpolated between the current and next {@link Month} temperature modifiers, then
 *       {@code getAverageMonthlyTemperature(z, y, avgSeaLevelTemp, monthFactor, false)} applies the
 *       hemisphere-aware monthly curve and the elevation lapse rate.</li>
 *   <li>Rainfall per sample matches {@code OverworldClimateModel.getInstantRainfall}: a triangle wave
 *       of amplitude {@code rainVariance * rainAverage} about {@code rainAverage}, phase
 *       {@code fractionOfYear + 0.75}. TFC does not hemisphere-flip the rainfall phase (only
 *       temperature is hemisphere-aware), so we don't either.</li>
 * </ul>
 * The daily random temperature term is intentionally dropped so the map is stable and deterministic.
 */
public class TFCPreviewClimateSampler extends OverworldClimateModel
{
    // Map sampling resolution. 48 samples = four per month. This is independent of the configured
    // month length; CropCalendarSettings converts each sample to real calendar days. Detailed hover
    // uses a separate cached schedule with one sample per calendar day (subject to a safety cap).
    public static final int MAP_SAMPLES_PER_YEAR = 48;
    /**
     * Kept as the public default used by tooltip fallbacks and older callers.
     */
    public static final int SAMPLES_PER_YEAR = MAP_SAMPLES_PER_YEAR;
    public static final int MONTHS_PER_YEAR = 12;
    /**
     * Salt TFC uses to derive its climate seed from the world seed (OverworldClimateModel ctor).
     */
    private static final long CLIMATE_SEED_SALT = 719283741234L;
    private static final Month[] MONTHS = Month.values();

    /**
     * The interpolated month temperature factor for a sample at {@code fractionOfYear} in [0,1),
     * matching TFC's {@code Mth.lerp(fractionOfMonth, month.mod, month.next().mod)}.
     */
    public static float monthFactorForFraction(float fractionOfYear)
    {
        float monthPos = fractionOfYear * MONTHS_PER_YEAR;
        int monthIdx = Mth.floor(monthPos) % MONTHS_PER_YEAR;
        if (monthIdx < 0) monthIdx += MONTHS_PER_YEAR;
        float delta = monthPos - Mth.floor(monthPos);
        Month m = MONTHS[monthIdx];
        return Mth.lerp(delta, m.getTemperatureModifier(), m.next().getTemperatureModifier());
    }

    /**
     * The normalized seasonal-rainfall triangle value in [-1,1] for a sample at {@code fractionOfYear},
     * matching {@code OverworldClimateModel.getInstantRainfall}'s phase {@code fractionOfYear + 0.75}.
     * Seasonal rainfall is then {@code rainAverage * (1 + rainVariance * factor)}, which equals TFC's
     * {@code Helpers.triangle(rainVariance*rainAverage, rainAverage, 1, fractionOfYear+0.75)}.
     */
    public static float rainfallTriangleFactor(float fractionOfYear)
    {
        // Helpers.triangle(amplitude=1, midpoint=0, frequency=1, value=phase) gives the [-1,1] wave.
        return Helpers.triangle(1f, 0f, 1f, fractionOfYear + 0.75f);
    }

    public TFCPreviewClimateSampler(long worldSeed, float temperatureScale)
    {
        super(LinearCongruentialGenerator.next(worldSeed, CLIMATE_SEED_SALT), temperatureScale);
    }

    /**
     * Elevation-adjusted, hemisphere-aware monthly temperature (no daily variation) for a sample.
     *
     * @param z               sampled block Z (drives hemisphere/latitude)
     * @param surfaceY        approximate surface height (elevation lapse)
     * @param avgSeaLevelTemp ChunkData#getAverageSeaLevelTemp at the point
     * @param monthFactor     precomputed via {@link #monthFactorForFraction}
     */
    public float previewTemperature(int z, int surfaceY, float avgSeaLevelTemp, float monthFactor)
    {
        return getAverageMonthlyTemperature(z, surfaceY, avgSeaLevelTemp, monthFactor, false);
    }

    /**
     * Seasonal rainfall (mm) for a sample, mirroring {@code OverworldClimateModel.getInstantRainfall}.
     * Positive variance ⇒ wetter mid-year (summer in the northern calendar); negative ⇒ wetter winter.
     */
    public float previewSeasonalRainfall(float rainAverage, float rainVariance, float fractionOfYear)
    {
        return rainVariance == 0f
            ? rainAverage
            : Helpers.triangle(rainVariance * rainAverage, rainAverage, 1f, fractionOfYear + 0.75f);
    }

    // ---------------- Per-point temperature fast path ----------------
    // getInstantTemperature (sans daily variation) is affine in the month factor:
    //   temp(mf) = adjustTemperatureByElevation(y, avg, calculateMonthlyTemperature(z, mf), 0)
    // and calculateMonthlyTemperature(z, mf) = mf * calculateMonthlyTemperature(z, 1) (it is mf times
    // a z-only latitude term), and adjustTemperatureByElevation is affine in its monthTemperature arg.
    // Hence temp(mf) = base + slope * mf, where base and slope depend only on (z, y, avg). Computing
    // them once per point lets the annual loop do a single multiply-add per sample instead of a
    // full monthly + elevation recomputation. This is algebraically exact (not an approximation).

    /**
     * Constant term of {@code temp(monthFactor) = base + slope*monthFactor} for a point.
     */
    public float temperatureBase(int surfaceY, float avgSeaLevelTemp)
    {
        return adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, 0f, 0f);
    }

    /**
     * Slope term of {@code temp(monthFactor) = base + slope*monthFactor} for a point.
     */
    public float temperatureSlope(int z, int surfaceY, float avgSeaLevelTemp)
    {
        float latitude = calculateMonthlyTemperature(z, 1f); // z-only latitude term (mf = 1)
        return adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, latitude, 0f)
            - adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, 0f, 0f);
    }
}
