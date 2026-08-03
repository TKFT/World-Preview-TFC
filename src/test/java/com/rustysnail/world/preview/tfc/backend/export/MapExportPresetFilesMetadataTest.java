package com.rustysnail.world.preview.tfc.backend.export;

import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Bounds;
import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Spec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapExportPresetFilesMetadataTest
{
    @Test
    void presetsInclude50k100kAnd200kAtFixedImageDimensions()
    {
        assertPreset(MapExportPreset.FIFTY_K.spec(), 50_000, 4, 1);
        assertPreset(MapExportPreset.HUNDRED_K.spec(), 100_000, 8, 2);
        assertPreset(MapExportPreset.TWO_HUNDRED_K.spec(), 200_000, 16, 4);
    }

    @Test
    void negativeCentersUseExactInclusiveBoundsAndFloorQuartCoordinates()
    {
        Spec spec = MapExportPreset.TWO_HUNDRED_K.spec();
        Bounds bounds = spec.bounds(-123, -456);
        assertEquals(new Bounds(-100_123, 99_876, -100_456, 99_543), bounds);
        assertEquals(Math.floorDiv(bounds.minX(), 4), spec.minimumQuartX(bounds));
        assertEquals(Math.floorDiv(bounds.minZ(), 4), spec.minimumQuartZ(bounds));
    }

    @Test
    void filenamesIncludeLayerPresetAndSanitizedSeed()
    {
        assertEquals(
            "tfc_map_my_seed_50k_land_water_x-50000_z17.png",
            MapExportNames.pngFilename(" ../../My Seed ", "50k", MapExportLayer.LAND_WATER, -50_000, 17));
        assertEquals(
            "tfc_map_seed_200k_temperature_x0_z0.json",
            MapExportNames.metadataFilename("..", "200k", MapExportLayer.TEMPERATURE, 0, 0));
    }

    @Test
    void metadataRecordsLayerPaletteAndEffectiveClimateSettings()
    {
        Bounds bounds = MapExportPreset.FIFTY_K.spec().bounds(0, 0);
        MapExportMetadata metadata = new MapExportMetadata(
            "seed", 1L, "minecraft:overworld", 0, 0, bounds,
            "rainfall", "50k", 4, 1, 12_500, 12_500,
            256, "gradient", false, 0, 32_000D, 48_000D,
            "3.1.0", "2026-07-30T00:00:00Z", true, "4.2.5", null
        );
        assertEquals("rainfall", metadata.layer());
        assertEquals(32_000D, metadata.effectiveTemperatureScale());
        assertEquals(48_000D, metadata.effectiveRainfallScale());
        assertEquals(256, metadata.paletteEntries());
        assertFalse(metadata.continentCellWaterShading());
    }

    @Test
    void enumOrderDefinesStableLayerAndPresetBatchOrder()
    {
        assertArrayEquals(
            new MapExportLayer[] {
                MapExportLayer.BIOMES,
                MapExportLayer.LAND_WATER,
                MapExportLayer.TERRAIN,
                MapExportLayer.TEMPERATURE,
                MapExportLayer.RAINFALL
            },
            MapExportLayer.values()
        );
        assertArrayEquals(
            new MapExportPreset[] {
                MapExportPreset.FIFTY_K,
                MapExportPreset.HUNDRED_K,
                MapExportPreset.TWO_HUNDRED_K
            },
            MapExportPreset.values()
        );
    }

    private static void assertPreset(Spec spec, int coverage, int blocksPerPixel, int samplesPerAxis)
    {
        assertEquals(coverage, spec.coverageBlocks());
        assertEquals(blocksPerPixel, spec.blocksPerPixel());
        assertEquals(samplesPerAxis, spec.quartSamplesPerAxis());
        assertEquals(12_500, spec.imageWidth());
        assertEquals(12_500, spec.imageHeight());
        assertEquals(coverage, spec.bounds(0, 0).width());
        assertEquals(coverage, spec.bounds(0, 0).height());
    }
}
