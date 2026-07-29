package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.TFCColorPalettes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

/**
 * Climate-limited annual production simulation. The class name remains stable for configuration
 * compatibility, but values stored under flag 16 are complete mature-harvest categories.
 */
public final class TFCCropSuitability
{
    public static final short CROP_IMPOSSIBLE = 0;
    public static final short CROP_ONE_HARVEST = 1;
    public static final short CROP_TWO_HARVESTS = 2;
    public static final short CROP_THREE_HARVESTS = 3;
    public static final short CROP_FOUR_PLUS_HARVESTS = 4;
    public static final short CROP_YEAR_ROUND = 5;

    static final byte CORE = 0;
    static final byte HEALTHY_ONLY = 1;
    static final byte LETHAL = 2;

    private static final int IRRIGATION_WATER_BOOST = 40;
    private static final int MAX_HARVEST_LOOPS_PER_INTERVAL = 1024;
    private static final double EPSILON = 1e-12d;
    private static final ThreadLocal<EvaluationScratch> SCRATCH =
        ThreadLocal.withInitial(EvaluationScratch::new);

    private static final String[] NAMES = {
        "Impossible",
        "One Harvest",
        "Two Harvests",
        "Three Harvests",
        "Four or More Harvests",
        "Year-Round Growth"
    };
    private static final int[] COLORS = {
        0xFF5C2626,
        0xFFB54B2D,
        0xFFD39136,
        0xFFA3B441,
        0xFF569E45,
        0xFF27B06F
    };
    private static final ResourceLocation[] KEYS = {
        id("impossible"),
        id("one_harvest"),
        id("two_harvests"),
        id("three_harvests"),
        id("four_plus_harvests"),
        id("year_round")
    };
    private static final ResourceLocation NO_DATA = id("no_data");

    public static final CropHarvestResult NO_DATA_RESULT = emptyResult(
        TFCSampleUtils.VALUE_INVALID, LimitingFactor.NO_DATA);
    public static final CropHarvestResult WATER_RESULT = emptyResult(
        TFCSampleUtils.VALUE_WATER, LimitingFactor.WATER);

    public static boolean isSuitabilityValue(short value)
    {
        return value >= CROP_IMPOSSIBLE && value <= CROP_YEAR_ROUND;
    }

    public static int suitabilityCount()
    {
        return NAMES.length;
    }

    public static String getSuitabilityName(short value)
    {
        if (isSuitabilityValue(value))
        {
            String loaded = WorldPreview.get().biomeColorMap()
                .getCategoricalName(TFCColorPalettes.CROP_HARVEST_POTENTIAL, KEYS[value]);
            return loaded == null ? NAMES[value] : loaded;
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeName(value);
        }
        String loaded = WorldPreview.get().biomeColorMap()
            .getCategoricalName(TFCColorPalettes.CROP_HARVEST_POTENTIAL, NO_DATA);
        return loaded == null ? "No Data" : loaded;
    }

    public static int getSuitabilityColor(short value)
    {
        if (isSuitabilityValue(value))
        {
            return WorldPreview.get().biomeColorMap().getCategoricalColor(
                TFCColorPalettes.CROP_HARVEST_POTENTIAL, KEYS[value], COLORS[value]);
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeColor(value);
        }
        return WorldPreview.get().biomeColorMap().getCategoricalColor(
            TFCColorPalettes.CROP_HARVEST_POTENTIAL, NO_DATA, 0xFF5A5A5A);
    }

    public static int hydrationFor(float seasonalRainfall, CropWaterMode mode, boolean flooded)
    {
        if (flooded)
        {
            return 100;
        }
        int rainHydration = FarmlandBlock.getInstantRainHydration(seasonalRainfall);
        return mode == CropWaterMode.IRRIGATED
            ? Mth.clamp(rainHydration + IRRIGATION_WATER_BOOST, 0, 100)
            : rainHydration;
    }

    /**
     * Allocation-free hot path: 48 climate intervals are evaluated twice and only a primitive
     * category is returned.
     */
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
        EvaluationScratch scratch = SCRATCH.get();
        simulateClimate(
            crop, range, sampler, chunkData, blockX, blockZ, surfaceY, waterMode,
            schedule, calendar, scratch, false);
        return classify(scratch.completedHarvests, scratch.coreCount == schedule.samplesPerYear());
    }

    public static CropHarvestResult evaluateDetailed(
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
        EvaluationScratch scratch = SCRATCH.get();
        simulateClimate(
            crop, range, sampler, chunkData, blockX, blockZ, surfaceY, waterMode,
            schedule, calendar, scratch, true);
        return scratch.toResult(crop.harvestBehavior(), schedule.samplesPerYear(), calendar);
    }

    static CropHarvestResult evaluateConditions(
        byte[] conditions,
        CropHarvestBehavior behavior,
        CropCalendarSettings calendar
    )
    {
        EvaluationScratch scratch = new EvaluationScratch();
        scratch.reset();
        double intervalDays = calendar.daysPerSample(conditions.length);
        for (int year = 0; year < 2; year++)
        {
            boolean countYear = year == 1;
            for (byte condition : conditions)
            {
                scratch.advance(condition, intervalDays, countYear, behavior, calendar);
            }
        }
        scratch.limitingFactor = scratch.coreCount == conditions.length
            ? LimitingFactor.NONE : LimitingFactor.SHORT_SEASON;
        return scratch.toResult(behavior, conditions.length, calendar);
    }

    static short classify(int completedHarvests, boolean yearRoundCoreGrowth)
    {
        if (completedHarvests <= 0)
        {
            return CROP_IMPOSSIBLE;
        }
        if (yearRoundCoreGrowth)
        {
            return CROP_YEAR_ROUND;
        }
        if (completedHarvests == 1) return CROP_ONE_HARVEST;
        if (completedHarvests == 2) return CROP_TWO_HARVESTS;
        if (completedHarvests == 3) return CROP_THREE_HARVESTS;
        return CROP_FOUR_PLUS_HARVESTS;
    }

    private static void simulateClimate(
        TFCCropRegistry.Entry crop,
        ClimateRange range,
        TFCPreviewClimateSampler sampler,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        CropWaterMode waterMode,
        AnnualClimateSchedule schedule,
        CropCalendarSettings calendar,
        EvaluationScratch scratch,
        boolean detailed
    )
    {
        scratch.reset();

        final float averageSeaLevelTemperature = chunkData.getAverageSeaLevelTemp(blockX, blockZ);
        final float averageRainfall = chunkData.getAverageRainfall(blockX, blockZ);
        final float rainfallVariance = chunkData.getRainVariance(blockX, blockZ);
        final float temperatureBase = sampler.temperatureBase(surfaceY, averageSeaLevelTemperature);
        final float temperatureSlope =
            sampler.temperatureSlope(blockZ, surfaceY, averageSeaLevelTemperature);

        final float minTemperature = range.getMinTemperature(false);
        final float maxTemperature = range.getMaxTemperature(false);
        final float minTemperatureWiggle = range.getMinTemperature(true);
        final float maxTemperatureWiggle = range.getMaxTemperature(true);
        final int minHydration = range.getMinHydration(false);
        final int maxHydration = range.getMaxHydration(false);
        final int minHydrationWiggle = range.getMinHydration(true);
        final int maxHydrationWiggle = range.getMaxHydration(true);
        final double intervalDays = calendar.daysPerSample(schedule.samplesPerYear());

        for (int year = 0; year < 2; year++)
        {
            boolean countYear = year == 1;
            for (int i = 0; i < schedule.samplesPerYear(); i++)
            {
                float temperature =
                    temperatureBase + temperatureSlope * schedule.monthFactors[i];
                float rainfall = rainfallVariance == 0f
                    ? averageRainfall
                    : averageRainfall * (1f + rainfallVariance * schedule.rainTriangleFactors[i]);
                int hydration = hydrationFor(rainfall, waterMode, crop.flooded());

                boolean core = temperature >= minTemperature && temperature <= maxTemperature
                    && hydration >= minHydration && hydration <= maxHydration;
                boolean healthy = core
                    || (temperature >= minTemperatureWiggle && temperature <= maxTemperatureWiggle
                        && hydration >= minHydrationWiggle && hydration <= maxHydrationWiggle);
                byte condition = core ? CORE : healthy ? HEALTHY_ONLY : LETHAL;

                if (detailed && countYear)
                {
                    if (temperature < minTemperature) scratch.tooCold++;
                    else if (temperature > maxTemperature) scratch.tooHot++;
                    if (hydration < minHydration) scratch.tooDry++;
                    else if (hydration > maxHydration) scratch.tooWet++;
                }
                scratch.advance(
                    condition, intervalDays, countYear, crop.harvestBehavior(), calendar);
            }
        }
        scratch.limitingFactor = detailed
            ? limitingFactor(
                scratch.coreCount == schedule.samplesPerYear(),
                scratch.tooCold, scratch.tooHot, scratch.tooDry, scratch.tooWet)
            : LimitingFactor.NONE;
    }

    static int finishCircularRun(int count, int prefix, int suffix, int best, int samples)
    {
        if (count == 0) return 0;
        if (count == samples) return samples;
        return Math.min(samples, Math.max(best, prefix + suffix));
    }

    private static LimitingFactor limitingFactor(
        boolean yearRoundCore,
        int tooCold,
        int tooHot,
        int tooDry,
        int tooWet
    )
    {
        if (yearRoundCore)
        {
            return LimitingFactor.NONE;
        }
        int max = Math.max(Math.max(tooCold, tooHot), Math.max(tooDry, tooWet));
        if (max == 0) return LimitingFactor.SHORT_SEASON;
        if (max == tooCold) return LimitingFactor.TOO_COLD;
        if (max == tooHot) return LimitingFactor.TOO_HOT;
        if (max == tooDry) return LimitingFactor.TOO_DRY;
        return LimitingFactor.TOO_WET;
    }

    private static CropHarvestResult emptyResult(short category, LimitingFactor limitingFactor)
    {
        return new CropHarvestResult(
            category, 0, 0d, 0d, 0d, 0d, 0d, 0d, false,
            CropHarvestBehavior.REPLANT, limitingFactor, 0, 0, 0, 0, 0, 0);
    }

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    private TFCCropSuitability()
    {
    }

    public enum CropWaterMode
    {
        RAIN_FED,
        IRRIGATED
    }

    public enum LimitingFactor
    {
        NONE,
        TOO_COLD,
        TOO_HOT,
        TOO_DRY,
        TOO_WET,
        SHORT_SEASON,
        NO_DATA,
        WATER
    }

    public record CropHarvestResult(
        short category,
        int completedHarvests,
        double activeGrowthDays,
        double healthyOnlyDays,
        double lethalDays,
        double longestCoreWindowDays,
        double longestSurvivableWindowDays,
        double requiredGrowthDays,
        boolean yearRoundCoreGrowth,
        CropHarvestBehavior harvestBehavior,
        LimitingFactor limitingFactor,
        int daysInMonth,
        int samplesPerYear,
        int tooColdSamples,
        int tooHotSamples,
        int tooDrySamples,
        int tooWetSamples
    )
    {
    }

    private static final class EvaluationScratch
    {
        boolean planted;
        double growth;
        double expiry;
        int completedHarvests;

        int coreCount;
        int coreCurrent;
        int coreBest;
        int corePrefix;
        boolean corePrefixOpen;

        int survivableCount;
        int survivableCurrent;
        int survivableBest;
        int survivablePrefix;
        boolean survivablePrefixOpen;

        int healthyOnlyCount;
        int lethalCount;
        int tooCold;
        int tooHot;
        int tooDry;
        int tooWet;
        LimitingFactor limitingFactor;

        void reset()
        {
            this.planted = false;
            this.growth = 0d;
            this.expiry = 0d;
            this.completedHarvests = 0;
            this.coreCount = 0;
            this.coreCurrent = 0;
            this.coreBest = 0;
            this.corePrefix = 0;
            this.corePrefixOpen = true;
            this.survivableCount = 0;
            this.survivableCurrent = 0;
            this.survivableBest = 0;
            this.survivablePrefix = 0;
            this.survivablePrefixOpen = true;
            this.healthyOnlyCount = 0;
            this.lethalCount = 0;
            this.tooCold = 0;
            this.tooHot = 0;
            this.tooDry = 0;
            this.tooWet = 0;
            this.limitingFactor = LimitingFactor.NONE;
        }

        void advance(
            byte condition,
            double intervalDays,
            boolean countYear,
            CropHarvestBehavior behavior,
            CropCalendarSettings calendar
        )
        {
            if (countYear)
            {
                this.recordCondition(condition);
            }

            if (condition == LETHAL)
            {
                this.kill();
                return;
            }
            if (condition == HEALTHY_ONLY)
            {
                if (this.planted)
                {
                    this.expiry += intervalDays * calendar.growthPerDay();
                    if (this.expiry + EPSILON >= calendar.localExpiryLimit())
                    {
                        this.kill();
                    }
                }
                return;
            }

            if (!this.planted)
            {
                this.planted = true;
                this.growth = 0d;
                this.expiry = 0d;
            }
            double remainingGrowth = intervalDays * calendar.growthPerDay();
            int loops = 0;
            while (remainingGrowth > EPSILON
                && this.growth + remainingGrowth + EPSILON >= 1d
                && loops++ < MAX_HARVEST_LOOPS_PER_INTERVAL)
            {
                double needed = Math.max(0d, 1d - this.growth);
                remainingGrowth = Math.max(0d, remainingGrowth - needed);
                this.countHarvest(countYear, 1);
                this.growth = behavior.resetGrowth();
                this.expiry = 0d;
            }

            if (remainingGrowth > EPSILON && this.growth + remainingGrowth + EPSILON >= 1d)
            {
                double cycleGrowth = 1d - behavior.resetGrowth();
                double firstNeeded = Math.max(0d, 1d - this.growth);
                double afterFirst = Math.max(0d, remainingGrowth - firstNeeded);
                long extraCycles = 1L + (long) Math.floor(afterFirst / cycleGrowth);
                double consumed = firstNeeded + (extraCycles - 1L) * cycleGrowth;
                remainingGrowth = Math.max(0d, remainingGrowth - consumed);
                this.countHarvest(countYear, extraCycles);
                this.growth = behavior.resetGrowth();
                this.expiry = 0d;
            }
            this.growth = Math.min(1d - EPSILON, this.growth + remainingGrowth);
        }

        private void recordCondition(byte condition)
        {
            boolean core = condition == CORE;
            if (core)
            {
                this.coreCount++;
                this.coreCurrent++;
                if (this.corePrefixOpen) this.corePrefix++;
                this.coreBest = Math.max(this.coreBest, this.coreCurrent);
            }
            else
            {
                this.coreCurrent = 0;
                this.corePrefixOpen = false;
            }

            boolean survivable = condition != LETHAL;
            if (survivable)
            {
                this.survivableCount++;
                this.survivableCurrent++;
                if (this.survivablePrefixOpen) this.survivablePrefix++;
                this.survivableBest = Math.max(this.survivableBest, this.survivableCurrent);
            }
            else
            {
                this.survivableCurrent = 0;
                this.survivablePrefixOpen = false;
            }

            if (condition == HEALTHY_ONLY) this.healthyOnlyCount++;
            else if (condition == LETHAL) this.lethalCount++;
        }

        private void countHarvest(boolean countYear, long count)
        {
            if (!countYear) return;
            long total = (long) this.completedHarvests + count;
            this.completedHarvests = total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        }

        private void kill()
        {
            this.planted = false;
            this.growth = 0d;
            this.expiry = 0d;
        }

        CropHarvestResult toResult(
            CropHarvestBehavior behavior,
            int samplesPerYear,
            CropCalendarSettings calendar
        )
        {
            double daysPerSample = calendar.daysPerSample(samplesPerYear);
            boolean yearRound = this.coreCount == samplesPerYear;
            int longestCore = finishCircularRun(
                this.coreCount, this.corePrefix, this.coreCurrent, this.coreBest, samplesPerYear);
            int longestSurvivable = finishCircularRun(
                this.survivableCount, this.survivablePrefix, this.survivableCurrent,
                this.survivableBest, samplesPerYear);
            return new CropHarvestResult(
                classify(this.completedHarvests, yearRound),
                this.completedHarvests,
                this.coreCount * daysPerSample,
                this.healthyOnlyCount * daysPerSample,
                this.lethalCount * daysPerSample,
                longestCore * daysPerSample,
                longestSurvivable * daysPerSample,
                calendar.requiredGrowthDays(),
                yearRound,
                behavior,
                this.limitingFactor,
                calendar.daysInMonth(),
                samplesPerYear,
                this.tooCold,
                this.tooHot,
                this.tooDry,
                this.tooWet
            );
        }
    }
}
