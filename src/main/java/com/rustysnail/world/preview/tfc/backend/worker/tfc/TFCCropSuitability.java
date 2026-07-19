package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.TFCColorPalettes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

public final class TFCCropSuitability
{
    public static final short CROP_IMPOSSIBLE = 0;
    public static final short CROP_POOR = 1;
    public static final short CROP_MARGINAL = 2;
    public static final short CROP_GOOD = 3;
    public static final short CROP_IDEAL = 4;
    public static final float CROP_IDEAL_CLOSENESS = 0.70f;
    public static final CropSuitabilityResult NO_DATA_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_INVALID, 0, 0, 0, 0, 0f, LimitingFactor.NO_DATA, 0, 0, 0, 0, 0f, 0, 0);
    public static final CropSuitabilityResult WATER_RESULT =
        new CropSuitabilityResult(TFCSampleUtils.VALUE_WATER, 0, 0, 0, 0, 0f, LimitingFactor.WATER, 0, 0, 0, 0, 0f, 0, 0);
    private static final String[] NAMES = {"Impossible", "Poor", "Marginal", "Good", "Ideal"};
    // Normal ARGB; PreviewDisplay converts once to NativeImage order when building the palette.
    private static final int[] COLORS = {
        0xFF662D2D, // Impossible
        0xFFB55732, // Poor
        0xFFD4AD3F, // Marginal
        0xFF74A64A, // Good
        0xFF32A852  // Ideal
    };
    private static final ResourceLocation[] KEYS = {
        id("impossible"), id("poor"), id("marginal"), id("good"), id("ideal")
    };
    private static final ResourceLocation NO_DATA = id("no_data");
    private static final int IRRIGATION_WATER_BOOST = 40;
    private static final ThreadLocal<EvaluationScratch> SCRATCH = ThreadLocal.withInitial(EvaluationScratch::new);

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
        if (isSuitabilityValue(value))
        {
            String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.SUITABILITY, KEYS[value]);
            return loaded == null ? NAMES[value] : loaded;
        }
        if (TFCSampleUtils.isWaterValue(value)) return TFCSampleUtils.getWaterTypeName(value);
        String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.SUITABILITY, NO_DATA);
        return loaded == null ? "No Data" : loaded;
    }

    public static int getSuitabilityColor(short value)
    {
        if (isSuitabilityValue(value))
        {
            return WorldPreview.get().biomeColorMap().getCategoricalColor(TFCColorPalettes.SUITABILITY, KEYS[value], COLORS[value]);
        }
        if (TFCSampleUtils.isWaterValue(value)) return TFCSampleUtils.getWaterTypeColor(value);
        return WorldPreview.get().biomeColorMap().getCategoricalColor(TFCColorPalettes.SUITABILITY, NO_DATA, TFCSampleUtils.COLOR_INVALID);
    }

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    public static int hydrationFor(float seasonalRainfall, CropWaterMode mode, boolean flooded)
    {
        if (flooded)
        {
            return 100;
        }
        int rainHydration = FarmlandBlock.getInstantRainHydration(seasonalRainfall); // [0, ~60]
        return mode == CropWaterMode.IRRIGATED
            ? Mth.clamp(rainHydration + IRRIGATION_WATER_BOOST, 0, 100)
            : rainHydration;
    }

    public static short evaluateMapValue(
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
        ClimateRange range = crop.climateRange();
        if (range == null)
        {
            return TFCSampleUtils.VALUE_INVALID;
        }
        EvaluationScratch stats = SCRATCH.get();
        sampleYear(range, crop.flooded(), sampler, chunkData, blockX, blockZ, surfaceY, waterMode, schedule, stats, false);
        return classify(stats.wiggleCount, stats.coreCount, stats.longestCore, stats.averageCloseness,
            schedule.samplesPerYear(), calendar);
    }

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
        ClimateRange range = crop.climateRange();
        if (range == null)
        {
            return NO_DATA_RESULT;
        }
        EvaluationScratch stats = SCRATCH.get();
        sampleYear(range, crop.flooded(), sampler, chunkData, blockX, blockZ, surfaceY, waterMode, schedule, stats, true);
        int n = schedule.samplesPerYear();

        short suitability = classify(stats.wiggleCount, stats.coreCount, stats.longestCore,
            stats.averageCloseness, n, calendar);
        LimitingFactor lf = limitingFactor(suitability, stats.tooCold, stats.tooHot, stats.tooDry, stats.tooWet, n);

        return new CropSuitabilityResult(
            suitability, stats.coreCount, stats.wiggleCount, stats.longestCore, stats.longestWiggle,
            stats.averageCloseness, lf, stats.tooCold, stats.tooHot, stats.tooDry, stats.tooWet,
            calendar.daysPerSample(n), calendar.daysInMonth(), n);
    }

    static short classify(int wiggleCount, int coreCount, int longestCore, float avgCloseness,
                          int samplesPerYear, CropCalendarSettings calendar)
    {
        if (wiggleCount == 0)
        {
            return CROP_IMPOSSIBLE;
        }
        double daysPerSample = calendar.daysPerSample(samplesPerYear);
        double longestCoreDays = longestCore * daysPerSample;
        if (coreCount == 0 || longestCoreDays < calendar.poorCoreDays())
        {
            return CROP_POOR;
        }
        if (longestCoreDays < calendar.goodCoreDays())
        {
            return CROP_MARGINAL;
        }
        if (longestCoreDays >= calendar.idealCoreDays()
            && coreCount * daysPerSample >= calendar.idealCoverageDays()
            && avgCloseness >= CROP_IDEAL_CLOSENESS)
        {
            return CROP_IDEAL;
        }
        return CROP_GOOD;
    }

    static float axisCloseness(float value, float min, float max)
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

    static int finishCircularRun(int count, int prefix, int suffix, int best, int n)
    {
        if (count == 0) return 0;
        if (count == n) return n;
        return Math.min(n, Math.max(best, prefix + suffix));
    }

    private static void sampleYear(
        ClimateRange range,
        boolean flooded,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        AnnualClimateSchedule schedule,
        EvaluationScratch out,
        boolean detailed
    )
    {
        final float avgSeaLevelTemp = chunkData.getAverageSeaLevelTemp(blockX, blockZ);
        final float rainAverage = chunkData.getAverageRainfall(blockX, blockZ);
        final float rainVariance = chunkData.getRainVariance(blockX, blockZ);

        final float tempBase = sampler.temperatureBase(surfaceY, avgSeaLevelTemp);
        final float tempSlope = sampler.temperatureSlope(blockZ, surfaceY, avgSeaLevelTemp);

        final float minT = range.getMinTemperature(false);
        final float maxT = range.getMaxTemperature(false);
        final float minTWiggle = range.getMinTemperature(true);
        final float maxTWiggle = range.getMaxTemperature(true);
        final int minH = range.getMinHydration(false);
        final int maxH = range.getMaxHydration(false);
        final int minHWiggle = range.getMinHydration(true);
        final int maxHWiggle = range.getMaxHydration(true);

        final float[] monthFactors = schedule.monthFactors;
        final float[] rainFactors = schedule.rainTriangleFactors;
        final int n = schedule.samplesPerYear();

        int coreCount = 0, coreCurrent = 0, coreBest = 0, corePrefix = 0;
        int wiggleCount = 0, wiggleCurrent = 0, wiggleBest = 0, wigglePrefix = 0;
        boolean corePrefixOpen = true, wigglePrefixOpen = true;
        int tooCold = 0, tooHot = 0, tooDry = 0, tooWet = 0;
        float closenessSum = 0f;

        for (int i = 0; i < n; i++)
        {
            float temp = tempBase + tempSlope * monthFactors[i];
            float rainfall = rainVariance == 0f ? rainAverage : rainAverage * (1f + rainVariance * rainFactors[i]);
            int hydration = hydrationFor(rainfall, waterMode, flooded);

            boolean core = temp >= minT && temp <= maxT && hydration >= minH && hydration <= maxH;
            if (core)
            {
                coreCount++;
                coreCurrent++;
                if (corePrefixOpen) corePrefix++;
                if (coreCurrent > coreBest) coreBest = coreCurrent;
            }
            else
            {
                coreCurrent = 0;
                corePrefixOpen = false;
            }

            boolean wiggle = temp >= minTWiggle && temp <= maxTWiggle
                && hydration >= minHWiggle && hydration <= maxHWiggle;
            if (wiggle)
            {
                wiggleCount++;
                wiggleCurrent++;
                if (wigglePrefixOpen) wigglePrefix++;
                if (wiggleCurrent > wiggleBest) wiggleBest = wiggleCurrent;
            }
            else
            {
                wiggleCurrent = 0;
                wigglePrefixOpen = false;
            }

            if (detailed)
            {
                if (temp < minT) tooCold++;
                else if (temp > maxT) tooHot++;
                if (hydration < minH) tooDry++;
                else if (hydration > maxH) tooWet++;
            }

            closenessSum += 0.5f * (axisCloseness(temp, minT, maxT) + axisCloseness(hydration, minH, maxH));
        }

        out.coreCount = coreCount;
        out.wiggleCount = wiggleCount;
        out.longestCore = finishCircularRun(coreCount, corePrefix, coreCurrent, coreBest, n);
        out.longestWiggle = finishCircularRun(wiggleCount, wigglePrefix, wiggleCurrent, wiggleBest, n);
        out.tooCold = tooCold;
        out.tooHot = tooHot;
        out.tooDry = tooDry;
        out.tooWet = tooWet;
        out.averageCloseness = closenessSum / n;
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
            return LimitingFactor.SHORT_SEASON;
        }
        if (suitability == CROP_MARGINAL && maxCount < n / 4)
        {
            return LimitingFactor.SHORT_SEASON;
        }
        if (maxCount == tooCold) return LimitingFactor.TOO_COLD;
        if (maxCount == tooHot) return LimitingFactor.TOO_HOT;
        if (maxCount == tooDry) return LimitingFactor.TOO_DRY;
        return LimitingFactor.TOO_WET;
    }

    private TFCCropSuitability() {}

    public enum CropWaterMode
    {
        RAIN_FED,
        IRRIGATED
    }

    public enum LimitingFactor
    {
        NONE, TOO_COLD, TOO_HOT, TOO_DRY, TOO_WET, SHORT_SEASON, NO_DATA, WATER
    }

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
        double daysPerSample,
        int daysInMonth,
        int samplesPerYear
    )
    {
        public long growingWindowDays()
        {
            return Math.round(longestCoreRun * daysPerSample);
        }
    }

    private static final class EvaluationScratch
    {
        int coreCount;
        int wiggleCount;
        int longestCore;
        int longestWiggle;
        int tooCold;
        int tooHot;
        int tooDry;
        int tooWet;
        float averageCloseness;
    }
}
