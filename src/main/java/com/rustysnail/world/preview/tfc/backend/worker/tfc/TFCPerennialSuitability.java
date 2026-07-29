package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.List;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.TFCColorPalettes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.client.overworld.SolarCalculator;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.util.EnvironmentHelpers;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPerennialRegistry.PerennialHabitat;

/**
 * Theoretical mature-plant production opportunities for perennial plants. Climate safety margins
 * remain available in detailed results but are never stored as flag-17 map values.
 */
public final class TFCPerennialSuitability
{
    public static final short PERENNIAL_NO_PRODUCTION = 0;
    public static final short PERENNIAL_ONE_HARVEST = 1;
    public static final short PERENNIAL_TWO_HARVESTS = 2;
    public static final short PERENNIAL_THREE_HARVESTS = 3;
    public static final short PERENNIAL_FOUR_PLUS_HARVESTS = 4;
    public static final short PERENNIAL_YEAR_ROUND = 5;
    public static final short PERENNIAL_FRESHWATER_REQUIRED = 6;

    public static final float BORDERLINE_MAX_MARGIN = 0.10f;
    public static final float SUITABLE_MAX_MARGIN = 0.30f;
    public static final float COMFORTABLE_MAX_MARGIN = 0.60f;

    private static final ResourceLocation[] KEYS = {
        key("no_production"),
        key("one_harvest"),
        key("two_harvests"),
        key("three_harvests"),
        key("four_plus_harvests"),
        key("year_round"),
        key("freshwater_required")
    };
    private static final String[] NAMES = {
        "No Production",
        "One Estimated Harvest",
        "Two Estimated Harvests",
        "Three Estimated Harvests",
        "Four or More Estimated Harvests",
        "Year-Round Production",
        "Freshwater Required"
    };
    private static final int[] COLORS = {
        0xFF5C2626,
        0xFFB54B2D,
        0xFFD39136,
        0xFFA3B441,
        0xFF569E45,
        0xFF27B06F,
        0xFF4989B7
    };

    public static int suitabilityCount()
    {
        return KEYS.length;
    }

    public static boolean isSuitabilityValue(short value)
    {
        return value >= 0 && value < KEYS.length;
    }

    public static String getSuitabilityName(short value)
    {
        if (isSuitabilityValue(value))
        {
            String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(
                TFCColorPalettes.PERENNIAL_PRODUCTION_POTENTIAL, KEYS[value]);
            return loaded == null ? NAMES[value] : loaded;
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeName(value);
        }
        String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(
            TFCColorPalettes.PERENNIAL_PRODUCTION_POTENTIAL, key("no_data"));
        return loaded == null ? "No Data" : loaded;
    }

    public static int getSuitabilityColor(short value)
    {
        if (isSuitabilityValue(value))
        {
            return WorldPreview.get().biomeColorMap().getCategoricalColor(
                TFCColorPalettes.PERENNIAL_PRODUCTION_POTENTIAL, KEYS[value], COLORS[value]);
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeColor(value);
        }
        return WorldPreview.get().biomeColorMap().getCategoricalColor(
            TFCColorPalettes.PERENNIAL_PRODUCTION_POTENTIAL, key("no_data"), 0xFF5A5A5A);
    }

    public static ProductionProfile prepareProduction(
        @Nullable List<Lifecycle> lifecycle,
        int daysInMonth,
        int bloomDelayTicks
    )
    {
        int safeDaysInMonth = Math.max(1, daysInMonth);
        int safeBloomDelayTicks = Math.max(0, bloomDelayTicks);
        int repeatDelayDays = Math.max(1, (int) Math.ceil(
            safeBloomDelayTicks / (double) ICalendar.TICKS_IN_DAY));
        if (lifecycle == null || lifecycle.size() != 12)
        {
            return new ProductionProfile(
                false, safeDaysInMonth, safeBloomDelayTicks, repeatDelayDays,
                0, 0, 0, 0, false, TFCSampleUtils.VALUE_INVALID);
        }

        int fruitingMonths = 0;
        for (Lifecycle stage : lifecycle)
        {
            if (stage == Lifecycle.FRUITING) fruitingMonths++;
        }
        int fruitingDays = saturatingMultiply(fruitingMonths, safeDaysInMonth);
        boolean yearRound = fruitingMonths == 12;
        int windows = 0;
        long estimatedHarvests = 0L;

        if (yearRound)
        {
            windows = 1;
            estimatedHarvests = estimateWindowHarvests(12, safeDaysInMonth, repeatDelayDays);
        }
        else if (fruitingMonths > 0)
        {
            int nonFruitingMonth = 0;
            while (lifecycle.get(nonFruitingMonth) == Lifecycle.FRUITING)
            {
                nonFruitingMonth++;
            }
            int runMonths = 0;
            for (int step = 1; step <= 12; step++)
            {
                int month = (nonFruitingMonth + step) % 12;
                if (lifecycle.get(month) == Lifecycle.FRUITING)
                {
                    runMonths++;
                }
                else if (runMonths > 0)
                {
                    windows++;
                    estimatedHarvests += estimateWindowHarvests(
                        runMonths, safeDaysInMonth, repeatDelayDays);
                    runMonths = 0;
                }
            }
        }

        int harvests = estimatedHarvests >= Integer.MAX_VALUE
            ? Integer.MAX_VALUE : (int) estimatedHarvests;
        short category = classifyProduction(harvests, yearRound);
        return new ProductionProfile(
            true, safeDaysInMonth, safeBloomDelayTicks, repeatDelayDays,
            fruitingMonths, fruitingDays, windows, harvests, yearRound, category);
    }

    private static int estimateWindowHarvests(
        int fruitingMonthCount,
        int daysInMonth,
        int repeatDelayDays
    )
    {
        long windowDays = (long) fruitingMonthCount * daysInMonth;
        long opportunities = 1L + Math.max(0L, windowDays - 1L) / repeatDelayDays;
        return opportunities >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) opportunities;
    }

    static short classifyProduction(int estimatedHarvests, boolean yearRoundFruiting)
    {
        if (yearRoundFruiting) return PERENNIAL_YEAR_ROUND;
        if (estimatedHarvests <= 0) return PERENNIAL_NO_PRODUCTION;
        if (estimatedHarvests == 1) return PERENNIAL_ONE_HARVEST;
        if (estimatedHarvests == 2) return PERENNIAL_TWO_HARVESTS;
        if (estimatedHarvests == 3) return PERENNIAL_THREE_HARVESTS;
        return PERENNIAL_FOUR_PLUS_HARVESTS;
    }

    /**
     * Hot path. Lifecycle production is precomputed in the context; only site validity varies.
     */
    public static short evaluateMapValue(
        TFCPerennialRegistry.PerennialEntry entry,
        ProductionProfile production,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        PerennialWaterMode waterMode,
        short mapWater
    )
    {
        boolean waterlogged = entry.habitat() == PerennialHabitat.FRESHWATER_WATERLOGGED;
        if (!waterlogged && TFCSampleUtils.isWaterValue(mapWater))
        {
            return mapWater;
        }
        if (waterlogged && mapWater == TFCSampleUtils.VALUE_WATER_OCEAN)
        {
            return PERENNIAL_NO_PRODUCTION;
        }
        ClimateRange range = entry.climateRange();
        if (range == null)
        {
            return TFCSampleUtils.VALUE_INVALID;
        }

        float averageTemperature = EnvironmentHelpers.adjustAvgTempForElev(
            surfaceY, chunkData.getAverageSeaLevelTemp(blockX, blockZ));
        int hydration = hydration(chunkData.getAverageRainfall(blockX, blockZ), waterMode);
        float temperatureMargin =
            axisMargin(averageTemperature, range.minTemperature(), range.maxTemperature());
        float hydrationMargin = axisMargin(hydration, range.minHydration(), range.maxHydration());
        if (Math.min(temperatureMargin, hydrationMargin) < 0f)
        {
            return PERENNIAL_NO_PRODUCTION;
        }
        if (waterlogged && !TFCSampleUtils.isWaterValue(mapWater))
        {
            return PERENNIAL_FRESHWATER_REQUIRED;
        }
        if (!production.lifecycleAvailable())
        {
            return TFCSampleUtils.VALUE_INVALID;
        }
        return production.category();
    }

    public static PerennialProductionResult evaluateDetailed(
        TFCPerennialRegistry.PerennialEntry entry,
        ProductionProfile production,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        float temperatureScale,
        PerennialWaterMode waterMode,
        short mapWater
    )
    {
        boolean northern = SolarCalculator.getInNorthernHemisphere(blockZ, temperatureScale);
        LifecycleCounts lifecycle = analyzeLifecycle(entry.lifecycle(), northern);
        float averageTemperature = EnvironmentHelpers.adjustAvgTempForElev(
            surfaceY, chunkData.getAverageSeaLevelTemp(blockX, blockZ));
        int hydration = hydration(chunkData.getAverageRainfall(blockX, blockZ), waterMode);
        ClimateRange range = entry.climateRange();
        boolean waterlogged = entry.habitat() == PerennialHabitat.FRESHWATER_WATERLOGGED;

        if (!waterlogged && TFCSampleUtils.isWaterValue(mapWater))
        {
            return result(
                mapWater, averageTemperature, hydration, Float.NaN, Float.NaN,
                ClimateFit.IMPOSSIBLE, LimitingFactor.WATER, northern, lifecycle, production);
        }
        if (waterlogged && mapWater == TFCSampleUtils.VALUE_WATER_OCEAN)
        {
            return result(
                PERENNIAL_NO_PRODUCTION, averageTemperature, hydration, Float.NaN, Float.NaN,
                ClimateFit.IMPOSSIBLE, LimitingFactor.SALTWATER, northern, lifecycle, production);
        }
        if (range == null)
        {
            return result(
                TFCSampleUtils.VALUE_INVALID, averageTemperature, hydration, Float.NaN, Float.NaN,
                ClimateFit.IMPOSSIBLE, LimitingFactor.NO_DATA, northern, lifecycle, production);
        }

        float temperatureMargin =
            axisMargin(averageTemperature, range.minTemperature(), range.maxTemperature());
        float hydrationMargin = axisMargin(hydration, range.minHydration(), range.maxHydration());
        float fit = Math.min(temperatureMargin, hydrationMargin);
        ClimateFit climateFit = classifyFit(fit);
        if (fit < 0f)
        {
            return result(
                PERENNIAL_NO_PRODUCTION, averageTemperature, hydration,
                temperatureMargin, hydrationMargin, climateFit,
                outsideFactor(
                    averageTemperature, hydration, range, temperatureMargin, hydrationMargin),
                northern, lifecycle, production);
        }
        if (waterlogged && !TFCSampleUtils.isWaterValue(mapWater))
        {
            return result(
                PERENNIAL_FRESHWATER_REQUIRED, averageTemperature, hydration,
                temperatureMargin, hydrationMargin, climateFit,
                LimitingFactor.FRESHWATER_REQUIRED, northern, lifecycle, production);
        }
        if (!production.lifecycleAvailable())
        {
            return result(
                TFCSampleUtils.VALUE_INVALID, averageTemperature, hydration,
                temperatureMargin, hydrationMargin, climateFit,
                LimitingFactor.NO_DATA, northern, lifecycle, production);
        }
        return result(
            production.category(), averageTemperature, hydration,
            temperatureMargin, hydrationMargin, climateFit, LimitingFactor.NONE,
            northern, lifecycle, production);
    }

    static float axisMargin(float value, float min, float max)
    {
        if (Float.isNaN(value) || Float.isNaN(min) || Float.isNaN(max) || min > max)
        {
            return -1f;
        }
        if (Float.isInfinite(min) || Float.isInfinite(max))
        {
            return value >= min && value <= max ? 1f : -1f;
        }
        float width = max - min;
        if (width == 0f)
        {
            return value == min ? 1f : -1f;
        }
        float center = min + width * 0.5f;
        return 1f - Math.abs(value - center) / (width * 0.5f);
    }

    static ClimateFit classifyFit(float fit)
    {
        if (fit < 0f) return ClimateFit.IMPOSSIBLE;
        if (fit < BORDERLINE_MAX_MARGIN) return ClimateFit.BORDERLINE;
        if (fit < SUITABLE_MAX_MARGIN) return ClimateFit.SUITABLE;
        if (fit < COMFORTABLE_MAX_MARGIN) return ClimateFit.COMFORTABLE;
        return ClimateFit.IDEAL;
    }

    public static int hydration(float averageRainfall, PerennialWaterMode waterMode)
    {
        int rainHydration = FarmlandBlock.getInstantRainHydration(averageRainfall);
        return waterMode == PerennialWaterMode.IRRIGATED
            ? Math.clamp(rainHydration + 40, 0, 100)
            : rainHydration;
    }

    static int countCircularFruitingWindows(@Nullable List<Lifecycle> lifecycle, boolean northern)
    {
        if (lifecycle == null || lifecycle.size() != 12)
        {
            return 0;
        }
        int fruiting = 0;
        int starts = 0;
        boolean previous = localLifecycle(lifecycle, 11, northern) == Lifecycle.FRUITING;
        for (int localMonth = 0; localMonth < 12; localMonth++)
        {
            boolean current = localLifecycle(lifecycle, localMonth, northern) == Lifecycle.FRUITING;
            if (current)
            {
                fruiting++;
                if (!previous) starts++;
            }
            previous = current;
        }
        return fruiting == 12 ? 1 : starts;
    }

    public static @Nullable Lifecycle lifecycleForLocalMonth(
        @Nullable List<Lifecycle> lifecycle,
        Month localMonth,
        boolean northern
    )
    {
        if (lifecycle == null || lifecycle.size() != 12)
        {
            return null;
        }
        return localLifecycle(lifecycle, localMonth.ordinal(), northern);
    }

    private static Lifecycle localLifecycle(List<Lifecycle> lifecycle, int localMonth, boolean northern)
    {
        Month month = Month.valueOf(localMonth);
        return lifecycle.get((northern ? month : month.opposite()).ordinal());
    }

    private static LifecycleCounts analyzeLifecycle(
        @Nullable List<Lifecycle> lifecycle,
        boolean northern
    )
    {
        if (lifecycle == null || lifecycle.size() != 12)
        {
            return LifecycleCounts.NO_DATA;
        }
        int healthy = 0;
        int flowering = 0;
        int fruiting = 0;
        int dormant = 0;
        @Nullable Month firstFruiting = null;
        @Nullable Month lastFruiting = null;
        for (int localMonth = 0; localMonth < 12; localMonth++)
        {
            Lifecycle stage = localLifecycle(lifecycle, localMonth, northern);
            switch (stage)
            {
                case HEALTHY -> healthy++;
                case FLOWERING -> flowering++;
                case FRUITING -> {
                    fruiting++;
                    if (firstFruiting == null) firstFruiting = Month.valueOf(localMonth);
                    lastFruiting = Month.valueOf(localMonth);
                }
                case DORMANT -> dormant++;
            }
        }
        return new LifecycleCounts(
            healthy, flowering, fruiting, dormant, healthy + flowering + fruiting,
            countCircularFruitingWindows(lifecycle, northern), firstFruiting, lastFruiting);
    }

    private static LimitingFactor outsideFactor(
        float temperature,
        int hydration,
        ClimateRange range,
        float temperatureMargin,
        float hydrationMargin
    )
    {
        if (temperatureMargin <= hydrationMargin)
        {
            return temperature < range.minTemperature()
                ? LimitingFactor.TOO_COLD : LimitingFactor.TOO_HOT;
        }
        return hydration < range.minHydration()
            ? LimitingFactor.TOO_DRY : LimitingFactor.TOO_WET;
    }

    private static PerennialProductionResult result(
        short category,
        float averageTemperature,
        int hydration,
        float temperatureMargin,
        float hydrationMargin,
        ClimateFit climateFit,
        LimitingFactor limitingFactor,
        boolean northern,
        LifecycleCounts lifecycle,
        ProductionProfile production
    )
    {
        return new PerennialProductionResult(
            category, averageTemperature, hydration, temperatureMargin, hydrationMargin,
            climateFit, limitingFactor, northern,
            lifecycle.active, lifecycle.healthy, lifecycle.flowering, lifecycle.fruiting,
            lifecycle.dormant, lifecycle.fruitingWindows,
            lifecycle.firstFruiting, lifecycle.lastFruiting,
            lifecycle.active == 12, lifecycle.fruiting == 12,
            production.daysInMonth(), production.fruitingDays(),
            production.repeatDelayDays(), production.bloomDelayTicks(),
            production.estimatedHarvests());
    }

    private static int saturatingMultiply(int left, int right)
    {
        long value = (long) left * right;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static ResourceLocation key(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    private TFCPerennialSuitability()
    {
    }

    public enum PerennialWaterMode
    {
        RAIN_FED,
        IRRIGATED
    }

    public enum ClimateFit
    {
        IMPOSSIBLE("Impossible"),
        BORDERLINE("Borderline"),
        SUITABLE("Suitable"),
        COMFORTABLE("Comfortable"),
        IDEAL("Ideal Climate Fit");

        private final String displayName;

        ClimateFit(String displayName)
        {
            this.displayName = displayName;
        }

        public String displayName()
        {
            return this.displayName;
        }
    }

    public enum LimitingFactor
    {
        NONE,
        TOO_COLD,
        TOO_HOT,
        TOO_DRY,
        TOO_WET,
        FRESHWATER_REQUIRED,
        SALTWATER,
        NO_DATA,
        WATER
    }

    public record ProductionProfile(
        boolean lifecycleAvailable,
        int daysInMonth,
        int bloomDelayTicks,
        int repeatDelayDays,
        int fruitingMonthCount,
        int fruitingDays,
        int distinctFruitingWindows,
        int estimatedHarvests,
        boolean yearRoundFruiting,
        short category
    )
    {
    }

    public record PerennialProductionResult(
        short category,
        float averageTemperature,
        int hydration,
        float temperatureMargin,
        float hydrationMargin,
        ClimateFit climateFit,
        LimitingFactor limitingFactor,
        boolean northernHemisphere,
        int activeMonthCount,
        int healthyMonthCount,
        int floweringMonthCount,
        int fruitingMonthCount,
        int dormantMonthCount,
        int distinctFruitingWindows,
        @Nullable Month firstFruitingMonth,
        @Nullable Month lastFruitingMonth,
        boolean yearRoundActive,
        boolean yearRoundFruiting,
        int daysInMonth,
        int fruitingDays,
        int repeatDelayDays,
        int bloomDelayTicks,
        int estimatedHarvests
    )
    {
        public boolean hasLifecycleData()
        {
            return activeMonthCount >= 0;
        }
    }

    private record LifecycleCounts(
        int healthy,
        int flowering,
        int fruiting,
        int dormant,
        int active,
        int fruitingWindows,
        @Nullable Month firstFruiting,
        @Nullable Month lastFruiting
    )
    {
        private static final LifecycleCounts NO_DATA =
            new LifecycleCounts(-1, -1, -1, -1, -1, -1, null, null);
    }
}
