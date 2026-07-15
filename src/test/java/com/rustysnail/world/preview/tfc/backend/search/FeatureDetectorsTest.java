package com.rustysnail.world.preview.tfc.backend.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureDetectorsTest
{
    @Test
    void titleCasesKnownAndFutureSerializedVariantNames()
    {
        assertEquals("Crater Lake", FeatureDetectors.titleCaseVariantName("crater_lake"));
        assertEquals("Future Volcano Type", FeatureDetectors.titleCaseVariantName("future-volcano_type"));
        assertEquals("", FeatureDetectors.titleCaseVariantName("  "));
    }

    @Test
    void returnsNoVariantForMissingOrNonStratovolcanoFeatures()
    {
        assertNull(FeatureDetectors.getFeatureVariant(null, 0L, net.minecraft.core.BlockPos.ZERO));
        assertNull(FeatureDetectors.getFeatureVariant(
            FeatureDetectors.getManualFeatures().getFirst(),
            0L,
            net.minecraft.core.BlockPos.ZERO
        ));
    }
}
