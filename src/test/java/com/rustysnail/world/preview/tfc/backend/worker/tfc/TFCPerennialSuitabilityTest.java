package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.ClimateRange;

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
    void climateMarginThresholdsAreStable()
    {
        assertEquals(0f, axisMargin(0f, 0f, 10f));
        assertEquals(1f, axisMargin(5f, 0f, 10f));
        assertTrue(axisMargin(-1f, 0f, 10f) < 0f);
        assertEquals(PERENNIAL_BORDERLINE, classifyFit(0f));
        assertEquals(PERENNIAL_SUITABLE, classifyFit(BORDERLINE_MAX_MARGIN));
        assertEquals(PERENNIAL_COMFORTABLE, classifyFit(SUITABLE_MAX_MARGIN));
        assertEquals(PERENNIAL_IDEAL, classifyFit(COMFORTABLE_MAX_MARGIN));
        assertEquals(1f, axisMargin(5f, 5f, 5f));
        assertEquals(1f, axisMargin(100f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY));
    }

    @Test
    void circularFruitingWindowsAndSouthernMonths()
    {
        List<Lifecycle> lifecycle = List.of(
            Lifecycle.FRUITING,
            Lifecycle.HEALTHY,
            Lifecycle.HEALTHY,
            Lifecycle.HEALTHY,
            Lifecycle.HEALTHY,
            Lifecycle.HEALTHY,
            Lifecycle.FRUITING,
            Lifecycle.FRUITING,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.FRUITING
        );
        assertEquals(2, countCircularFruitingWindows(lifecycle, true));
        assertEquals(Lifecycle.FRUITING, lifecycleForLocalMonth(lifecycle, Month.JANUARY, true));
        assertEquals(Lifecycle.FRUITING, lifecycleForLocalMonth(lifecycle, Month.JANUARY, false));

        List<Lifecycle> januaryOnly = List.of(
            Lifecycle.FRUITING,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.HEALTHY,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT,
            Lifecycle.DORMANT
        );
        assertEquals(Lifecycle.HEALTHY, lifecycleForLocalMonth(januaryOnly, Month.JANUARY, false));
    }

    @Test
    void waterloggedHabitatRejectsOceanAndRequiresFreshwaterOnLand()
    {
        PerennialHabitat habitat = PerennialHabitat.FRESHWATER_WATERLOGGED;
        assertEquals(PERENNIAL_IMPOSSIBLE,
            applyHabitat(habitat, TFCSampleUtils.VALUE_WATER_OCEAN, PERENNIAL_IDEAL));
        assertEquals(PERENNIAL_COMFORTABLE,
            applyHabitat(habitat, TFCSampleUtils.VALUE_WATER_LAKE, PERENNIAL_COMFORTABLE));
        assertEquals(PERENNIAL_SUITABLE,
            applyHabitat(habitat, TFCSampleUtils.VALUE_WATER_RIVER, PERENNIAL_SUITABLE));
        assertEquals(PERENNIAL_FRESHWATER_REQUIRED,
            applyHabitat(habitat, (short) -1, PERENNIAL_IDEAL));
        assertEquals(PERENNIAL_IMPOSSIBLE,
            applyHabitat(habitat, (short) -1, PERENNIAL_IMPOSSIBLE));
    }

    @Test
    void staleContextRejectsOldRevision()
    {
        AtomicInteger current = new AtomicInteger(4);
        TFCPerennialContext context = new TFCPerennialContext(
            ResourceLocation.parse("tfc:plant/cherry_sapling"),
            null,
            PerennialWaterMode.RAIN_FED,
            4,
            current::get
        );
        assertFalse(context.isStale());
        current.incrementAndGet();
        assertTrue(context.isStale());
    }
}
