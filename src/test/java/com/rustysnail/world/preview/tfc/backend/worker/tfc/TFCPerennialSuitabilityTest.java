package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

import static com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPerennialRegistry.*;
import static com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPerennialSuitability.*;
import static org.junit.jupiter.api.Assertions.*;

class TFCPerennialSuitabilityTest
{
    @Test
    void normalizedClimateFallbackRequiresUniqueSameNamespaceMatch()
    {
        ClimateRange cherry = new ClimateRange(20, 80, 0, 2f, 20f, 0f);
        ResourceLocation block = ResourceLocation.parse("tfc:plant/cherry_sapling");
        Map<ResourceLocation, ClimateRange> ranges = Map.of(
            ResourceLocation.parse("tfc:plant/cherry_tree"), cherry,
            ResourceLocation.parse("addon:plant/cherry_tree"), ClimateRange.NOOP
        );

        assertEquals("cherry", normalizePlantName("plant/cherry_sapling"));
        assertSame(cherry, resolveResourceClimate(block, PerennialType.FRUIT_TREE, ranges));

        Map<ResourceLocation, ClimateRange> ambiguous = Map.of(
            ResourceLocation.parse("tfc:plant/cherry_tree"), cherry,
            ResourceLocation.parse("tfc:compat/cherry_bush"), ClimateRange.NOOP
        );
        assertSame(cherry, resolveResourceClimate(block, PerennialType.FRUIT_TREE, ambiguous));
        assertNull(resolveResourceClimate(
            ResourceLocation.parse("tfc:plant/cherry_plant"),
            PerennialType.ADDON_PERENNIAL,
            ambiguous
        ));
    }

    @Test
    void climateMarginRemainsSecondaryDetailedData()
    {
        assertEquals(0f, axisMargin(0f, 0f, 10f));
        assertEquals(1f, axisMargin(5f, 0f, 10f));
        assertTrue(axisMargin(-1f, 0f, 10f) < 0f);
        assertEquals(ClimateFit.BORDERLINE, classifyFit(0f));
        assertEquals(ClimateFit.SUITABLE, classifyFit(BORDERLINE_MAX_MARGIN));
        assertEquals(ClimateFit.COMFORTABLE, classifyFit(SUITABLE_MAX_MARGIN));
        assertEquals(ClimateFit.IDEAL, classifyFit(COMFORTABLE_MAX_MARGIN));
    }

    @Test
    void oneWindowUsesConfiguredMonthLengthAndBloomDelay()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING, Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT);

        ProductionProfile result = prepareProduction(
            lifecycle, 8, 4 * ICalendar.TICKS_IN_DAY);
        assertEquals(2, result.fruitingMonthCount());
        assertEquals(16, result.fruitingDays());
        assertEquals(1, result.distinctFruitingWindows());
        assertEquals(4, result.repeatDelayDays());
        assertEquals(4, result.estimatedHarvests());
        assertEquals(PERENNIAL_FOUR_PLUS_HARVESTS, result.category());
    }

    @Test
    void multipleCircularWindowsAreEstimatedSeparately()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT);

        ProductionProfile result = prepareProduction(
            lifecycle, 8, 8 * ICalendar.TICKS_IN_DAY);
        assertEquals(2, result.distinctFruitingWindows());
        assertEquals(2, result.estimatedHarvests());
        assertEquals(PERENNIAL_TWO_HARVESTS, result.category());
    }

    @Test
    void twelveFruitingMonthsAreYearRoundProduction()
    {
        ProductionProfile result = prepareProduction(
            lifecycle(
                Lifecycle.FRUITING, Lifecycle.FRUITING, Lifecycle.FRUITING,
                Lifecycle.FRUITING, Lifecycle.FRUITING, Lifecycle.FRUITING,
                Lifecycle.FRUITING, Lifecycle.FRUITING, Lifecycle.FRUITING,
                Lifecycle.FRUITING, Lifecycle.FRUITING, Lifecycle.FRUITING),
            8,
            10 * ICalendar.TICKS_IN_DAY
        );

        assertTrue(result.yearRoundFruiting());
        assertEquals(1, result.distinctFruitingWindows());
        assertEquals(PERENNIAL_YEAR_ROUND, result.category());
    }

    @Test
    void missingLifecycleIsNoDataRatherThanZeroProduction()
    {
        ProductionProfile dormant = prepareProduction(
            lifecycle(
                Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
                Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
                Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
                Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT),
            8,
            ICalendar.TICKS_IN_DAY);
        assertTrue(dormant.lifecycleAvailable());
        assertEquals(PERENNIAL_NO_PRODUCTION, dormant.category());

        ProductionProfile result = prepareProduction(null, 8, ICalendar.TICKS_IN_DAY);
        assertFalse(result.lifecycleAvailable());
        assertEquals(TFCSampleUtils.VALUE_INVALID, result.category());

        PerennialEntry entry = entry(
            PerennialHabitat.NORMAL_LAND,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            null);
        assertEquals(TFCSampleUtils.VALUE_INVALID, evaluateMapValue(
            entry, result, ChunkData.EMPTY, 0, 0, 63,
            PerennialWaterMode.RAIN_FED, (short) -1));
    }

    @Test
    void waterloggedHabitatRejectsOceanAndRequiresFreshwaterOnLand()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT);
        ProductionProfile production =
            prepareProduction(lifecycle, 8, 8 * ICalendar.TICKS_IN_DAY);
        PerennialEntry cranberry = entry(
            PerennialHabitat.FRESHWATER_WATERLOGGED,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            lifecycle);

        assertEquals(PERENNIAL_NO_PRODUCTION, evaluateMapValue(
            cranberry, production, ChunkData.EMPTY, 0, 0, 63,
            PerennialWaterMode.RAIN_FED, TFCSampleUtils.VALUE_WATER_OCEAN));
        assertEquals(PERENNIAL_FRESHWATER_REQUIRED, evaluateMapValue(
            cranberry, production, ChunkData.EMPTY, 0, 0, 63,
            PerennialWaterMode.RAIN_FED, (short) -1));
        assertEquals(PERENNIAL_ONE_HARVEST, evaluateMapValue(
            cranberry, production, ChunkData.EMPTY, 0, 0, 63,
            PerennialWaterMode.RAIN_FED, TFCSampleUtils.VALUE_WATER_LAKE));
    }

    @Test
    void climateInvalidSiteHasNoProduction()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT);
        ProductionProfile production =
            prepareProduction(lifecycle, 8, 8 * ICalendar.TICKS_IN_DAY);
        PerennialEntry tropical = entry(
            PerennialHabitat.NORMAL_LAND,
            new ClimateRange(0, 100, 0, 50f, 60f, 0f),
            lifecycle);

        assertEquals(PERENNIAL_NO_PRODUCTION, evaluateMapValue(
            tropical, production, ChunkData.EMPTY, 0, 0, 63,
            PerennialWaterMode.RAIN_FED, (short) -1));
    }

    @Test
    void circularFruitingWindowsAndSouthernMonthsRemainCorrect()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.HEALTHY, Lifecycle.HEALTHY, Lifecycle.HEALTHY,
            Lifecycle.HEALTHY, Lifecycle.HEALTHY,
            Lifecycle.FRUITING, Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.FRUITING);
        assertEquals(2, countCircularFruitingWindows(lifecycle, true));
        assertEquals(Lifecycle.FRUITING, lifecycleForLocalMonth(
            lifecycle, Month.JANUARY, true));
        assertEquals(Lifecycle.FRUITING, lifecycleForLocalMonth(
            lifecycle, Month.JANUARY, false));

        List<Lifecycle> januaryOnly = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.HEALTHY,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT);
        assertEquals(Lifecycle.HEALTHY, lifecycleForLocalMonth(
            januaryOnly, Month.JANUARY, false));
    }

    @Test
    void staleContextRejectsOldRevision()
    {
        AtomicInteger current = new AtomicInteger(4);
        ProductionProfile profile = prepareProduction(null, 8, ICalendar.TICKS_IN_DAY);
        TFCPerennialContext context = new TFCPerennialContext(
            ResourceLocation.parse("tfc:plant/cherry_sapling"),
            null,
            PerennialWaterMode.RAIN_FED,
            profile,
            4,
            current::get
        );
        assertFalse(context.isStale());
        current.incrementAndGet();
        assertTrue(context.isStale());
    }

    @Test
    void precomputedPerennialMapHotPathThroughputSample()
    {
        List<Lifecycle> lifecycle = lifecycle(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT,
            Lifecycle.DORMANT, Lifecycle.DORMANT, Lifecycle.DORMANT);
        ProductionProfile production =
            prepareProduction(lifecycle, 8, 8 * ICalendar.TICKS_IN_DAY);
        PerennialEntry entry = entry(
            PerennialHabitat.NORMAL_LAND,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            lifecycle);

        for (int i = 0; i < 5_000; i++)
        {
            evaluateMapValue(
                entry, production, ChunkData.EMPTY, i, i, 63,
                PerennialWaterMode.RAIN_FED, (short) -1);
        }
        int iterations = 200_000;
        int checksum = 0;
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++)
        {
            checksum += evaluateMapValue(
                entry, production, ChunkData.EMPTY, i, -i, 63,
                PerennialWaterMode.RAIN_FED, (short) -1);
        }
        long elapsed = System.nanoTime() - started;
        assertEquals(iterations * PERENNIAL_ONE_HARVEST, checksum);
        System.out.printf(
            Locale.ROOT,
            "Perennial map evaluator: %,d points in %.3f ms (%.1f ns/point)%n",
            iterations, elapsed / 1_000_000d, elapsed / (double) iterations);
    }

    private static List<Lifecycle> lifecycle(Lifecycle... stages)
    {
        return List.of(stages);
    }

    private static PerennialEntry entry(
        PerennialHabitat habitat,
        ClimateRange climate,
        List<Lifecycle> lifecycle
    )
    {
        return new PerennialEntry(
            ResourceLocation.parse("test:plant"),
            ResourceLocation.parse("test:plant"),
            null,
            "Test Plant",
            habitat == PerennialHabitat.FRESHWATER_WATERLOGGED
                ? PerennialType.WATERLOGGED_BERRY : PerennialType.ADDON_PERENNIAL,
            habitat,
            climate,
            lifecycle,
            -1
        );
    }
}
