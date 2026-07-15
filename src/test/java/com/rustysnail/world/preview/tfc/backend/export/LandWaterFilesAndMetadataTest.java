package com.rustysnail.world.preview.tfc.backend.export;

import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Bounds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LandWaterFilesAndMetadataTest
{
    @Test
    void filenamesAreDeterministicAndSanitized()
    {
        assertEquals("My_Seed_42", LandWaterExportNames.sanitizeComponent(" ../../My Seed:42\\* "));
        String expected = "tfc_land_water_My_Seed_42_50k_x-50000_z17.png";
        assertEquals(expected, LandWaterExportNames.pngFilename(" ../../My Seed:42\\* ", "50k", -50_000, 17));
        assertEquals(expected, LandWaterExportNames.pngFilename(" ../../My Seed:42\\* ", "50k", -50_000, 17));
        assertEquals("tfc_land_water_seed_100k_x0_z0.png", LandWaterExportNames.pngFilename("..", "100k", 0, 0));
    }

    @Test
    void metadataRetainsEveryRequestedDiagnosticValue()
    {
        Bounds bounds = new Bounds(-25_000, 24_999, -25_000, 24_999);
        LandWaterExportMetadata metadata = new LandWaterExportMetadata(
            "My Seed", 123456789L, "minecraft:overworld", 0, 0, bounds, 4, 12_500, 12_500,
            "3.1.0", "2026-07-15T12:00:00Z", LandWaterExportMetadata.CLASSIFICATION_MODE,
            true, "4.2.5", null
        );

        assertEquals("My Seed", metadata.seedEntered());
        assertEquals(123456789L, metadata.resolvedNumericSeed());
        assertEquals("minecraft:overworld", metadata.dimension());
        assertEquals(bounds, metadata.blockBounds());
        assertEquals(4, metadata.blocksPerPixel());
        assertEquals(12_500, metadata.imageWidth());
        assertEquals(12_500, metadata.imageHeight());
        assertEquals(LandWaterExportMetadata.CLASSIFICATION_MODE, metadata.classificationMode());
        assertNull(metadata.tfcLargeBiomesVersion());
    }
}
