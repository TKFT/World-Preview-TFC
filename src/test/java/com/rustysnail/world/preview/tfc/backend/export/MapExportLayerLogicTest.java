package com.rustysnail.world.preview.tfc.backend.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapExportLayerLogicTest
{
    @Test
    void landIsFixedAndNarrowWaterIsPreserved()
    {
        byte[] classes = {
            LandWaterSample.NARROW_WATER,
            LandWaterSample.LAND,
            LandWaterSample.LAND,
            LandWaterSample.LAND
        };
        int[] shades = {12, 1, 2, 3};
        assertEquals(13, MapExportLayerFactory.aggregateLandWater(classes, shades, 4));
        assertEquals(0, MapExportLayerFactory.aggregateLandWater(
            new byte[] {
                LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND
            },
            new int[] {15, 15, 15, 15}, 4));
        assertEquals(0, MapExportLayerFactory.aggregateLandWater(
            new byte[] {LandWaterSample.WATER, LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND},
            new int[] {15, 0, 0, 0}, 4));
    }

    @Test
    void waterShadeAverageIgnoresLandSamples()
    {
        byte[] classes = {
            LandWaterSample.WATER,
            LandWaterSample.WATER,
            LandWaterSample.LAND,
            LandWaterSample.LAND
        };
        assertEquals(9, MapExportLayerFactory.aggregateLandWater(classes, new int[] {4, 12, 0, 15}, 4));
    }

    @Test
    void continentShadeIsStableAndCoversDifferentBuckets()
    {
        assertEquals(MapExportLayerFactory.continentShadeBucket(0.25),
            MapExportLayerFactory.continentShadeBucket(0.25));
        assertNotEquals(MapExportLayerFactory.continentShadeBucket(0.01),
            MapExportLayerFactory.continentShadeBucket(0.99));
        assertEquals(0, MapExportLayerFactory.continentShadeBucket(-1));
        assertEquals(15, MapExportLayerFactory.continentShadeBucket(2));
    }

    @Test
    void waterPaletteStaysWithinEightPercentPerChannel()
    {
        int base = 0x5078A0;
        int[] shades = MapExportLayerFactory.waterShades(base);
        assertEquals(16, shades.length);
        for (int shade : shades)
        {
            assertChannelRange(base >>> 16 & 0xFF, shade >>> 16 & 0xFF);
            assertChannelRange(base >>> 8 & 0xFF, shade >>> 8 & 0xFF);
            assertChannelRange(base & 0xFF, shade & 0xFF);
        }
    }

    @Test
    void biomeMajorityAndTieUseNorthWestFirst()
    {
        assertEquals(2, MapExportAggregation.majority(new int[] {2, 3, 2, 4}, 4));
        assertEquals(7, MapExportAggregation.majority(new int[] {7, 8, 9, 10}, 4));
        assertEquals(7, MapExportAggregation.majority(new int[] {7, 8, 8, 7}, 4));
    }

    @Test
    void terrainClassificationUsesWaterThenMountainThenAltitude()
    {
        assertEquals(MapExportLayerFactory.TERRAIN_WATER,
            MapExportLayerFactory.terrainCategory(true, true, 3));
        assertEquals(MapExportLayerFactory.TERRAIN_MOUNTAIN,
            MapExportLayerFactory.terrainCategory(false, true, 0));
        assertEquals(MapExportLayerFactory.TERRAIN_LOWLAND,
            MapExportLayerFactory.terrainCategory(false, false, 0));
        assertEquals(MapExportLayerFactory.TERRAIN_MIDLAND,
            MapExportLayerFactory.terrainCategory(false, false, 1));
        assertEquals(MapExportLayerFactory.TERRAIN_HIGHLAND,
            MapExportLayerFactory.terrainCategory(false, false, 2));
    }

    @Test
    void climateAveragesBeforeGradientIndexing()
    {
        assertEquals(128, MapExportLayerFactory.gradientIndex(-23 + 33 - 23 + 33, 4, -23, 33));
        assertEquals(128, MapExportLayerFactory.gradientIndex(0 + 500 + 0 + 500, 4, 0, 500));
        assertEquals(0, MapExportLayerFactory.gradientIndex(-1000, 1, -23, 33));
        assertEquals(255, MapExportLayerFactory.gradientIndex(1000, 1, 0, 500));
    }

    private static void assertChannelRange(int base, int value)
    {
        assertTrue(value >= Math.round(base * 0.92F) - 1);
        assertTrue(value <= Math.round(base * 1.08F) + 1);
    }
}
