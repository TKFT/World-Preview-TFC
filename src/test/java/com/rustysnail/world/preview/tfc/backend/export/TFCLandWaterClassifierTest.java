package com.rustysnail.world.preview.tfc.backend.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TFCLandWaterClassifierTest
{
    @Test
    void classifiesRepresentativeTfcAndAddonBiomes()
    {
        assertLand("LOW", false, false, "tfc", "lowlands");
        assertWater("OCEAN", true, false, "tfc", "ocean", LandWaterSample.WATER);
        assertWater("OCEAN", true, false, "tfc", "deep_ocean", LandWaterSample.WATER);
        assertWater("LOW", false, false, "tfc", "river", LandWaterSample.NARROW_WATER);
        assertWater("LAKE", false, false, "tfc", "lake", LandWaterSample.NARROW_WATER);
        assertWater("LAKE", false, false, "tfc", "mountain_lake", LandWaterSample.NARROW_WATER);
        assertLand("LOW", true, true, "tfc", "shore");
        assertWater("LOW", false, false, "example_addon", "braided_river_mouth", LandWaterSample.NARROW_WATER);
        assertWater("LOW", false, false, "example_addon", "abyssal_trench", LandWaterSample.WATER);
    }

    @Test
    void doesNotMistakeRiverValleysOrOceanicMountainsForWater()
    {
        assertLand("LOW", false, false, "tfc", "river_valley");
        assertLand("HIGH", true, false, "tfc", "oceanic_volcanic_mountains");
    }

    private static void assertLand(String blend, boolean salty, boolean shore, String namespace, String path)
    {
        assertEquals(LandWaterSample.LAND, TFCLandWaterClassifier.classifyMetadata(blend, salty, shore, namespace, path));
    }

    private static void assertWater(String blend, boolean salty, boolean shore, String namespace, String path, byte expected)
    {
        assertEquals(expected, TFCLandWaterClassifier.classifyMetadata(blend, salty, shore, namespace, path));
    }
}
