package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.util.climate.ClimateRange;

import net.dries007.tfc.world.chunkdata.ChunkData;

import net.minecraft.util.Mth;

/**
 * Evaluates annual crop-growing suitability for the {@link com.rustysnail.world.preview.tfc.RenderSettings.RenderMode#TFC_CROP_SUITABILITY}
 * map. This is a preview classification (categories, not guaranteed yields): for each of
 * {@link TFCPreviewClimateSampler#SAMPLES_PER_YEAR} annual climate samples it derives temperature and
 * farmland hydration, checks them against the crop's {@link ClimateRange}, and buckets the year into
 * Impossible / Poor / Marginal / Good / Ideal based mainly on the longest continuous core-valid run
 * (the growing window), treating the year as circular.
 */
public final class TFCCropSuitability
{
    private TFCCropSuitability() {}

    // Suitability values stored in the map section (0..4). Water / invalid reuse the shared reserved
    // values in TFCSampleUtils (VALUE_WATER_* / VALUE_INVALID).
    public static final short CROP_IMPOSSIBLE = 0;
    public static final short CROP_POOR = 1;
    public static final short CROP_MARGINAL = 2;
    public static final short CROP_GOOD = 3;
    public static final short CROP_IDEAL = 4;

    private static final String[] NAMES = { "Impossible", "Poor", "Marginal", "Good", "Ideal" };

    // Normal ARGB; PreviewDisplay converts once to NativeImage order when building the palette.
    private static final int[] COLORS = {
        0xFF662D2D, // Impossible
        0xFFB55732, // Poor
        0xFFD4AD3F, // Marginal
        0xFF74A64A, // Good
        0xFF32A852  // Ideal
    };

    // Sample-count classification thresholds (poor/good/ideal core-run lengths and ideal coverage) are
    // now derived from the live calendar in CropCalendarSettings, so they scale with month length and
    // the crop-growth modifier. Only the closeness gate (not calendar-dependent) stays a constant.
    public static final float CROP_IDEAL_CLOSENESS = 0.70f;

    // Irrigation assumes a standard nearby freshwater source: +40 farmland hydration (TFC's water
    // boost), neutral soil multiplier. Matches FarmlandBlock#getInstantHydrationFromRainHydration.
    private static final int IRRIGATION_WATER_BOOST = 40;

    public enum CropWaterMode
    {
        RAIN_FED,
        IRRIGATED
    }

    public enum LimitingFactor
    {
        NONE, TOO_COLD, TOO_HOT, TOO_DRY, TOO_WET, SHORT_SEASON, NO_DATA, WATER
    }

    /**
     * Detailed per-point result. Only the {@link #suitability} short is stored in the map section;
     * the rest is recomputed on demand for the hovered quart.
     */
    public record CropSuitabilityResult(
        short suitability,
        int coreValidSamples,
        int wiggleValidSamples,
        int longestCoreRun,
        int longestWiggleRun,
        float averageCloseness,
        LimitingFactor limitingFactor,
        int tooColdSamples,
        int tooHotSamples,
        int tooDrySamples,
        int tooWetSamples,
        float daysPerSample,
        int daysInMonth,
        int samplesPerYear
    )
    {
        public int growingWindowDays()
        {
            return Math.round(longestCoreRun * daysPerSample);
        }
    }

    public static final CropSuitabilityResult NO_DATA_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_INVALID, 0, 0, 0, 0, 0f, LimitingFactor.NO_DATA, 0, 0, 0, 0, 0f, 0, 0);

    public static final CropSuitabilityResult WATER_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_WATER, 0, 0, 0, 0, 0f, LimitingFactor.WATER, 0, 0, 0, 0, 0f, 0, 0);

    public static boolean isSuitabilityValue(short value)
    {
        return value >= CROP_IMPOSSIBLE && value <= CROP_IDEAL;
    }

    public static int suitabilityCount()
    {
        return NAMES.length;
    }

    public static String getSuitabilityName(short value)
    {
        if (isSuitabilityValue(value)) return NAMES[value];
        if (TFCSampleUtils.isWaterValue(value)) return "Water";
        return "No Data";
    }

    public static int getSuitabilityColor(short value)
    {
        if (isSuitabilityValue(value)) return COLORS[value];
        if (TFCSampleUtils.isWaterValue(value)) return TFCSampleUtils.COLOR_WATER;
        return TFCSampleUtils.COLOR_INVALID;
    }

    /** Farmland hydration for a rain-fed / irrigated sample from seasonal rainfall (mm). */
    public static int hydrationFor(float seasonalRainfall, CropWaterMode mode, boolean flooded)
    {
        if (flooded)
        {
            return 100; // flooded farmland is always fully hydrated (both modes)
        }
        int rainHydration = FarmlandBlock.getInstantRainHydration(seasonalRainfall); // [0, ~60]
        return mode == CropWaterMode.IRRIGATED
            ? Mth.clamp(rainHydration + IRRIGATION_WATER_BOOST, 0, 100)
            : rainHydration;
    }

    /**
     * Map-generation entry point: returns only the suitability short and allocates nothing (no record,
     * list, array, Component or string). Reads the cached {@link AnnualClimateSchedule} arrays and the
     * dynamic {@link CropCalendarSettings} thresholds. Water points are handled by the caller (land only).
     */
    public static short evaluateValue(
        TFCCropRegistry.Entry crop,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        AnnualClimateSchedule schedule,
        CropCalendarSettings calendar
    )
    {
        if (crop == null || !crop.hasClimateData())
        {
            return TFCSampleUtils.VALUE_INVALID;
        }
        long packed = sampleYear(crop, sampler, chunkData, blockX, blockZ, surfaceY, waterMode, schedule, null);
        int coreMask = (int) (packed & 0xFFFFFFFFL);
        int wiggleMask = (int) (packed >>> 32);
        int coreCount = Integer.bitCount(coreMask);
        int wiggleCount = Integer.bitCount(wiggleMask);
        int longestCore = longestCircularRun(coreMask, schedule.samplesPerYear());
        return classify(wiggleCount, coreCount, longestCore, CLOSENESS_BOX.get()[0], calendar);
    }

    /**
     * Hover entry point: full breakdown (counts, runs, closeness, limiting factor, calendar). Allocates
     * one result record; only ever called for a single hovered quart, never in the map loop.
     */
    public static CropSuitabilityResult evaluateDetailed(
        TFCCropRegistry.Entry crop,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        AnnualClimateSchedule schedule,
        CropCalendarSettings calendar
    )
    {
        if (crop == null || !crop.hasClimateData())
        {
            return NO_DATA_RESULT;
        }
        int[] tallies = new int[4]; // tooCold, tooHot, tooDry, tooWet
        long packed = sampleYear(crop, sampler, chunkData, blockX, blockZ, surfaceY, waterMode, schedule, tallies);
        int coreMask = (int) (packed & 0xFFFFFFFFL);
        int wiggleMask = (int) (packed >>> 32);
        int n = schedule.samplesPerYear();

        int coreCount = Integer.bitCount(coreMask);
        int wiggleCount = Integer.bitCount(wiggleMask);
        int longestCore = longestCircularRun(coreMask, n);
        int longestWiggle = longestCircularRun(wiggleMask, n);
        float avgCloseness = CLOSENESS_BOX.get()[0];

        short suitability = classify(wiggleCount, coreCount, longestCore, avgCloseness, calendar);
        LimitingFactor lf = limitingFactor(suitability, tallies[0], tallies[1], tallies[2], tallies[3], n);

        return new CropSuitabilityResult(
            suitability, coreCount, wiggleCount, longestCore, longestWiggle, avgCloseness, lf,
            tallies[0], tallies[1], tallies[2], tallies[3],
            calendar.daysPerSample(), calendar.daysInMonth(), n);
    }

    // Per-thread scratch for the average-closeness output of sampleYear (parallel work units each run
    // on their own thread). Kept off the returned packed long, which only carries the two bitmasks.
    private static final ThreadLocal<float[]> CLOSENESS_BOX = ThreadLocal.withInitial(() -> new float[1]);

    /**
     * Runs the annual sample loop, returning core/wiggle bitmasks packed into a long
     * (core in low 32 bits, wiggle in high 32). Optionally fills {@code tallies}
     * [tooCold, tooHot, tooDry, tooWet]. The average closeness is stored in a per-thread box and read
     * back by the caller. Allocates nothing when {@code tallies == null}.
     */
    private static long sampleYear(
        TFCCropRegistry.Entry crop,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        AnnualClimateSchedule schedule,
        int[] tallies
    )
    {
        final ClimateRange range = crop.climateRange();
        final boolean flooded = crop.flooded();
        final float avgSeaLevelTemp = chunkData.getAverageSeaLevelTemp(blockX, blockZ);
        final float rainAverage = chunkData.getAverageRainfall(blockX, blockZ);
        final float rainVariance = chunkData.getRainVariance(blockX, blockZ);

        // Per-point temperature linear terms: temp = base + slope * monthFactor (exact; see sampler).
        final float tempBase = sampler.temperatureBase(surfaceY, avgSeaLevelTemp);
        final float tempSlope = sampler.temperatureSlope(blockZ, surfaceY, avgSeaLevelTemp);

        final float minT = range.getMinTemperature(false);
        final float maxT = range.getMaxTemperature(false);
        final int minH = range.getMinHydration(false);
        final int maxH = range.getMaxHydration(false);

        final float[] monthFactors = schedule.monthFactors;
        final float[] rainFactors = schedule.rainTriangleFactors;
        final int n = schedule.samplesPerYear();

        int coreMask = 0;
        int wiggleMask = 0;
        float closenessSum = 0f;

        for (int i = 0; i < n; i++)
        {
            float temp = tempBase + tempSlope * monthFactors[i];
            float rainfall = rainVariance == 0f ? rainAverage : rainAverage * (1f + rainVariance * rainFactors[i]);
            int hydration = hydrationFor(rainfall, waterMode, flooded);

            if (range.checkBoth(hydration, temp, false)) coreMask |= (1 << i);
            if (range.checkBoth(hydration, temp, true)) wiggleMask |= (1 << i);

            if (tallies != null)
            {
                if (temp < minT) tallies[0]++;
                else if (temp > maxT) tallies[1]++;
                if (hydration < minH) tallies[2]++;
                else if (hydration > maxH) tallies[3]++;
            }

            closenessSum += 0.5f * (axisCloseness(temp, minT, maxT) + axisCloseness(hydration, minH, maxH));
        }

        CLOSENESS_BOX.get()[0] = closenessSum / n;
        return (coreMask & 0xFFFFFFFFL) | ((long) wiggleMask << 32);
    }

    private static short classify(int wiggleCount, int coreCount, int longestCore, float avgCloseness, CropCalendarSettings calendar)
    {
        if (wiggleCount == 0)
        {
            return CROP_IMPOSSIBLE;
        }
        if (coreCount == 0 || longestCore < calendar.poorCoreSamples())
        {
            return CROP_POOR;
        }
        if (longestCore < calendar.goodCoreSamples())
        {
            return CROP_MARGINAL;
        }
        if (longestCore >= calendar.idealCoreSamples()
            && coreCount >= calendar.idealCoverageSamples()
            && avgCloseness >= CROP_IDEAL_CLOSENESS)
        {
            return CROP_IDEAL;
        }
        return CROP_GOOD;
    }

    private static LimitingFactor limitingFactor(short suitability, int tooCold, int tooHot, int tooDry, int tooWet, int n)
    {
        if (suitability == CROP_GOOD || suitability == CROP_IDEAL)
        {
            return LimitingFactor.NONE;
        }
        int maxCount = Math.max(Math.max(tooCold, tooHot), Math.max(tooDry, tooWet));
        if (maxCount == 0)
        {
            // Temperature and hydration are individually fine most of the time; the window is short.
            return LimitingFactor.SHORT_SEASON;
        }
        // Marginal ratings where no axis dominates are best described as a short season.
        if (suitability == CROP_MARGINAL && maxCount < n / 4)
        {
            return LimitingFactor.SHORT_SEASON;
        }
        if (maxCount == tooCold) return LimitingFactor.TOO_COLD;
        if (maxCount == tooHot) return LimitingFactor.TOO_HOT;
        if (maxCount == tooDry) return LimitingFactor.TOO_DRY;
        return LimitingFactor.TOO_WET;
    }

    /** Normalized proximity to the center of a range; open-ended/zero-width ranges avoid div-by-zero. */
    private static float axisCloseness(float value, float min, float max)
    {
        if (Float.isInfinite(min) || Float.isInfinite(max))
        {
            return (value >= min && value <= max) ? 1f : 0f;
        }
        float half = (max - min) * 0.5f;
        if (half <= 0f)
        {
            return value == min ? 1f : 0f;
        }
        float center = (min + max) * 0.5f;
        float d = Math.abs(value - center) / half;
        return Math.max(0f, 1f - d);
    }

    /** Longest run of set bits in the low {@code n} bits of {@code mask}, treating the year as circular. */
    private static int longestCircularRun(int mask, int n)
    {
        if (mask == 0) return 0;
        int full = (n >= 32) ? -1 : ((1 << n) - 1);
        if ((mask & full) == full) return n;
        int best = 0, cur = 0;
        for (int i = 0; i < 2 * n; i++)
        {
            if ((mask & (1 << (i % n))) != 0)
            {
                cur++;
                if (cur > best) best = cur;
            }
            else
            {
                cur = 0;
            }
        }
        return Math.min(best, n);
    }
}
