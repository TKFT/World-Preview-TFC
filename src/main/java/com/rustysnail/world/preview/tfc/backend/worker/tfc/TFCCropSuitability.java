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

    // Classification thresholds (in samples). One sample ~= 4 days on the default 96-day year, so a
    // 6-sample run ~= 24 days (~one crop cycle). Kept as tunable named constants.
    public static final int CROP_POOR_CORE_SAMPLES = 3;
    public static final int CROP_GOOD_CORE_SAMPLES = 6;
    public static final int CROP_IDEAL_CORE_SAMPLES = 10;
    public static final int CROP_IDEAL_CORE_COVERAGE = TFCPreviewClimateSampler.SAMPLES_PER_YEAR / 2; // half the year
    public static final float CROP_IDEAL_CLOSENESS = 0.70f;

    // Days represented by one annual sample, for the "growing window" tooltip.
    public static final int DAYS_PER_SAMPLE = TFCPreviewClimateSampler.DEFAULT_DAYS_PER_YEAR / TFCPreviewClimateSampler.SAMPLES_PER_YEAR;

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
        int tooWetSamples
    )
    {
        public int growingWindowDays()
        {
            return longestCoreRun * DAYS_PER_SAMPLE;
        }
    }

    public static final CropSuitabilityResult NO_DATA_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_INVALID, 0, 0, 0, 0, 0f, LimitingFactor.NO_DATA, 0, 0, 0, 0);

    public static final CropSuitabilityResult WATER_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_WATER, 0, 0, 0, 0, 0f, LimitingFactor.WATER, 0, 0, 0, 0);

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
     * Evaluates a land point. {@code monthFactors} and {@code fractionOfYear} are precomputed once per
     * work unit (length {@link TFCPreviewClimateSampler#SAMPLES_PER_YEAR}); nothing here allocates
     * arrays or strings. Water points are handled by the caller (this is land only).
     */
    public static CropSuitabilityResult evaluate(
        TFCCropRegistry.Entry crop,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        float[] monthFactors,
        float[] fractionOfYear
    )
    {
        if (crop == null || !crop.hasClimateData())
        {
            return NO_DATA_RESULT;
        }

        final ClimateRange range = crop.climateRange();
        final boolean flooded = crop.flooded();
        final float avgSeaLevelTemp = chunkData.getAverageSeaLevelTemp(blockX, blockZ);
        final float rainAverage = chunkData.getAverageRainfall(blockX, blockZ);
        final float rainVariance = chunkData.getRainVariance(blockX, blockZ);

        final int n = monthFactors.length;
        int coreMask = 0;
        int wiggleMask = 0;
        float closenessSum = 0f;
        int tooCold = 0, tooHot = 0, tooDry = 0, tooWet = 0;

        final float minT = range.getMinTemperature(false);
        final float maxT = range.getMaxTemperature(false);
        final int minH = range.getMinHydration(false);
        final int maxH = range.getMaxHydration(false);

        for (int i = 0; i < n; i++)
        {
            float temp = sampler.previewTemperature(blockZ, surfaceY, avgSeaLevelTemp, monthFactors[i]);
            float rainfall = sampler.previewSeasonalRainfall(rainAverage, rainVariance, fractionOfYear[i]);
            int hydration = hydrationFor(rainfall, waterMode, flooded);

            if (range.checkBoth(hydration, temp, false)) coreMask |= (1 << i);
            if (range.checkBoth(hydration, temp, true)) wiggleMask |= (1 << i);

            if (temp < minT) tooCold++;
            else if (temp > maxT) tooHot++;
            if (hydration < minH) tooDry++;
            else if (hydration > maxH) tooWet++;

            closenessSum += 0.5f * (axisCloseness(temp, minT, maxT) + axisCloseness(hydration, minH, maxH));
        }

        int coreCount = Integer.bitCount(coreMask);
        int wiggleCount = Integer.bitCount(wiggleMask);
        int longestCore = longestCircularRun(coreMask, n);
        int longestWiggle = longestCircularRun(wiggleMask, n);
        float avgCloseness = closenessSum / n;

        short suitability = classify(wiggleCount, coreCount, longestCore, avgCloseness);
        LimitingFactor lf = limitingFactor(suitability, tooCold, tooHot, tooDry, tooWet, n);

        return new CropSuitabilityResult(
            suitability, coreCount, wiggleCount, longestCore, longestWiggle, avgCloseness, lf,
            tooCold, tooHot, tooDry, tooWet);
    }

    private static short classify(int wiggleCount, int coreCount, int longestCore, float avgCloseness)
    {
        if (wiggleCount == 0)
        {
            return CROP_IMPOSSIBLE;
        }
        if (coreCount == 0 || longestCore < CROP_POOR_CORE_SAMPLES)
        {
            return CROP_POOR;
        }
        if (longestCore < CROP_GOOD_CORE_SAMPLES)
        {
            return CROP_MARGINAL;
        }
        if (longestCore >= CROP_IDEAL_CORE_SAMPLES
            && coreCount >= CROP_IDEAL_CORE_COVERAGE
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
