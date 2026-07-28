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
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPerennialRegistry.PerennialHabitat;

/**
 * Annual site-climate evaluation for repeatedly-producing plants. The safety-margin categories are
 * preview guidance; TFC itself uses a binary core-range check. Rain hydration is an estimate because
 * nearby freshwater and block-below modifiers require generated blocks.
 */
public final class TFCPerennialSuitability
{
    public static final short PERENNIAL_IMPOSSIBLE = 0;
    public static final short PERENNIAL_BORDERLINE = 1;
    public static final short PERENNIAL_SUITABLE = 2;
    public static final short PERENNIAL_COMFORTABLE = 3;
    public static final short PERENNIAL_IDEAL = 4;
    public static final short PERENNIAL_FRESHWATER_REQUIRED = 5;

    public static final float BORDERLINE_MAX_MARGIN = 0.10f;
    public static final float SUITABLE_MAX_MARGIN = 0.30f;
    public static final float COMFORTABLE_MAX_MARGIN = 0.60f;

    private static final ResourceLocation[] KEYS = {
        key("impossible"),
        key("borderline"),
        key("suitable"),
        key("comfortable"),
        key("ideal"),
        key("freshwater_required")
    };
    private static final String[] NAMES = {
        "Impossible",
        "Borderline",
        "Suitable",
        "Comfortable",
        "Ideal Climate Fit",
        "Freshwater Required"
    };
    private static final int[] COLORS = {
        0xFF5C2626,
        0xFFB54B2D,
        0xFFD3A43D,
        0xFF66A34B,
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
            String loaded = WorldPreview.get().biomeColorMap()
                .getCategoricalName(TFCColorPalettes.PERENNIAL_SUITABILITY, KEYS[value]);
            return loaded == null ? NAMES[value] : loaded;
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeName(value);
        }
        return "No Data";
    }

    public static int getSuitabilityColor(short value)
    {
        if (isSuitabilityValue(value))
        {
            return WorldPreview.get().biomeColorMap().getCategoricalColor(
                TFCColorPalettes.PERENNIAL_SUITABILITY, KEYS[value], COLORS[value]);
        }
        if (TFCSampleUtils.isWaterValue(value))
        {
            return TFCSampleUtils.getWaterTypeColor(value);
        }
        return WorldPreview.get().biomeColorMap().getCategoricalColor(
            TFCColorPalettes.PERENNIAL_SUITABILITY, key("no_data"), 0xFF5A5A5A);
    }

    /**
     * Hot-path evaluation. This method intentionally creates no records, arrays, lists, strings or
     * Components.
     */
    public static short evaluateMapValue(
        TFCPerennialRegistry.PerennialEntry entry,
        ChunkData chunkData,
        int blockX,
        int blockZ,
        int surfaceY,
        PerennialWaterMode waterMode,
        short mapWater
    )
    {
        boolean waterlogged = entry.habitat() == PerennialHabitat.FRESHWATER_WATERLOGGED;
        if (waterlogged && mapWater == TFCSampleUtils.VALUE_WATER_OCEAN)
        {
            return PERENNIAL_IMPOSSIBLE;
        }
        if (!waterlogged && TFCSampleUtils.isWaterValue(mapWater))
        {
            return mapWater;
        }
        ClimateRange range = entry.climateRange();
        if (range == null)
        {
            return TFCSampleUtils.VALUE_INVALID;
        }

        float averageTemperature = EnvironmentHelpers.adjustAvgTempForElev(
            surfaceY, chunkData.getAverageSeaLevelTemp(blockX, blockZ));
        int hydration = hydration(chunkData.getAverageRainfall(blockX, blockZ), waterMode);
        float tempMargin = axisMargin(averageTemperature, range.minTemperature(), range.maxTemperature());
        float hydrationMargin = axisMargin(hydration, range.minHydration(), range.maxHydration());
        float fit = Math.min(tempMargin, hydrationMargin);
        return applyHabitat(entry.habitat(), mapWater, classifyFit(fit));
    }

    public static PerennialSuitabilityResult evaluateDetailed(
        TFCPerennialRegistry.PerennialEntry entry,
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
        if (waterlogged && mapWater == TFCSampleUtils.VALUE_WATER_OCEAN)
        {
            return result(
                PERENNIAL_IMPOSSIBLE, averageTemperature, hydration, Float.NaN, Float.NaN,
                LimitingFactor.SALTWATER, northern, lifecycle
            );
        }
        if (!waterlogged && TFCSampleUtils.isWaterValue(mapWater))
        {
            return result(
                mapWater, averageTemperature, hydration, Float.NaN, Float.NaN,
                LimitingFactor.WATER, northern, lifecycle
            );
        }
        if (range == null)
        {
            return result(
                TFCSampleUtils.VALUE_INVALID, averageTemperature, hydration, Float.NaN, Float.NaN,
                LimitingFactor.NO_DATA, northern, lifecycle
            );
        }
        float tempMargin = axisMargin(averageTemperature, range.minTemperature(), range.maxTemperature());
        float hydrationMargin = axisMargin(hydration, range.minHydration(), range.maxHydration());

        float fit = Math.min(tempMargin, hydrationMargin);
        if (fit < 0f)
        {
            return result(
                PERENNIAL_IMPOSSIBLE, averageTemperature, hydration, tempMargin, hydrationMargin,
                outsideFactor(averageTemperature, hydration, range, tempMargin, hydrationMargin),
                northern, lifecycle
            );
        }
        if (waterlogged && !TFCSampleUtils.isWaterValue(mapWater))
        {
            return result(
                PERENNIAL_FRESHWATER_REQUIRED, averageTemperature, hydration, tempMargin, hydrationMargin,
                LimitingFactor.FRESHWATER_REQUIRED, northern, lifecycle
            );
        }
        return result(
            classifyFit(fit), averageTemperature, hydration, tempMargin, hydrationMargin,
            LimitingFactor.NONE, northern, lifecycle
        );
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

    static short classifyFit(float fit)
    {
        if (fit < 0f) return PERENNIAL_IMPOSSIBLE;
        if (fit < BORDERLINE_MAX_MARGIN) return PERENNIAL_BORDERLINE;
        if (fit < SUITABLE_MAX_MARGIN) return PERENNIAL_SUITABLE;
        if (fit < COMFORTABLE_MAX_MARGIN) return PERENNIAL_COMFORTABLE;
        return PERENNIAL_IDEAL;
    }

    static short applyHabitat(PerennialHabitat habitat, short mapWater, short climateValue)
    {
        if (habitat != PerennialHabitat.FRESHWATER_WATERLOGGED)
        {
            return TFCSampleUtils.isWaterValue(mapWater) ? mapWater : climateValue;
        }
        if (mapWater == TFCSampleUtils.VALUE_WATER_OCEAN)
        {
            return PERENNIAL_IMPOSSIBLE;
        }
        if (mapWater == TFCSampleUtils.VALUE_WATER_LAKE || mapWater == TFCSampleUtils.VALUE_WATER_RIVER)
        {
            return climateValue;
        }
        if (climateValue == TFCSampleUtils.VALUE_INVALID || climateValue == PERENNIAL_IMPOSSIBLE)
        {
            return climateValue;
        }
        return PERENNIAL_FRESHWATER_REQUIRED;
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

    private static LifecycleCounts analyzeLifecycle(@Nullable List<Lifecycle> lifecycle, boolean northern)
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
            countCircularFruitingWindows(lifecycle, northern), firstFruiting, lastFruiting
        );
    }

    private static LimitingFactor outsideFactor(
        float temperature,
        int hydration,
        ClimateRange range,
        float tempMargin,
        float hydrationMargin
    )
    {
        if (tempMargin <= hydrationMargin)
        {
            return temperature < range.minTemperature() ? LimitingFactor.TOO_COLD : LimitingFactor.TOO_HOT;
        }
        return hydration < range.minHydration() ? LimitingFactor.TOO_DRY : LimitingFactor.TOO_WET;
    }

    private static PerennialSuitabilityResult result(
        short suitability,
        float averageTemperature,
        int hydration,
        float temperatureMargin,
        float hydrationMargin,
        LimitingFactor limitingFactor,
        boolean northern,
        LifecycleCounts lifecycle
    )
    {
        return new PerennialSuitabilityResult(
            suitability, averageTemperature, hydration, temperatureMargin, hydrationMargin,
            limitingFactor, northern,
            lifecycle.active, lifecycle.healthy, lifecycle.flowering, lifecycle.fruiting,
            lifecycle.dormant, lifecycle.fruitingWindows,
            lifecycle.firstFruiting, lifecycle.lastFruiting,
            lifecycle.active == 12, lifecycle.fruiting == 12
        );
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

    public record PerennialSuitabilityResult(
        short suitability,
        float averageTemperature,
        int hydration,
        float temperatureMargin,
        float hydrationMargin,
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
        boolean yearRoundFruiting
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
