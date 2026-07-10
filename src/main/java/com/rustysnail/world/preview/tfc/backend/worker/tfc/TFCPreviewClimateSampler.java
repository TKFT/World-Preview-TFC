package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.OverworldClimateModel;

import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

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
    /** Salt TFC uses to derive its climate seed from the world seed (OverworldClimateModel ctor). */
    private static final long CLIMATE_SEED_SALT = 719283741234L;

    // Annual sampling resolution. 24 samples = two per month over the default TFC calendar. Kept as
    // named constants so later work can make the calendar / sample count configurable.
    public static final int SAMPLES_PER_YEAR = 24;
    public static final int MONTHS_PER_YEAR = 12;
    public static final int DEFAULT_DAYS_PER_MONTH = 8;
    public static final int DEFAULT_DAYS_PER_YEAR = DEFAULT_DAYS_PER_MONTH * MONTHS_PER_YEAR; // 96

    private static final Month[] MONTHS = Month.values();

    public TFCPreviewClimateSampler(long worldSeed, float temperatureScale)
    {
        super(LinearCongruentialGenerator.next(worldSeed, CLIMATE_SEED_SALT), temperatureScale);
    }

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

    /** Per-sample fraction-of-year [0,1) for the annual schedule (constant; safe to reuse). */
    public static float[] annualFractionOfYear()
    {
        float[] a = new float[SAMPLES_PER_YEAR];
        for (int i = 0; i < SAMPLES_PER_YEAR; i++)
        {
            a[i] = (float) i / SAMPLES_PER_YEAR;
        }
        return a;
    }

    /** Per-sample month temperature factor for the annual schedule (constant; safe to reuse). */
    public static float[] annualMonthFactors()
    {
        float[] a = new float[SAMPLES_PER_YEAR];
        for (int i = 0; i < SAMPLES_PER_YEAR; i++)
        {
            a[i] = monthFactorForFraction((float) i / SAMPLES_PER_YEAR);
        }
        return a;
    }

    /**
     * Elevation-adjusted, hemisphere-aware monthly temperature (no daily variation) for a sample.
     * @param z            sampled block Z (drives hemisphere/latitude)
     * @param surfaceY     approximate surface height (elevation lapse)
     * @param avgSeaLevelTemp ChunkData#getAverageSeaLevelTemp at the point
     * @param monthFactor  precomputed via {@link #monthFactorForFraction}
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
}
