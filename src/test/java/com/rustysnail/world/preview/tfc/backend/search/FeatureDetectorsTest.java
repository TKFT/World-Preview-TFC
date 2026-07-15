package com.rustysnail.world.preview.tfc.backend.search;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
        assertNull(FeatureDetectors.getFeatureVariant(null, 0L, BlockPos.ZERO));
        assertNull(FeatureDetectors.getFeatureVariant(
            FeatureDetectors.getManualFeatures().getFirst(),
            0L,
            BlockPos.ZERO
        ));
    }

    @Test
    void resolvesEveryCurrentTFCStratovolcanoVariant()
    {
        Set<String> expected = Set.of("fuji", "crater_lake", "tahoma", "kelimutu", "batholith");
        Set<String> found = new HashSet<>();
        SearchableFeature stratovolcanoes = FeatureDetectors.getManualFeatures().stream()
            .filter(feature -> feature.id().getPath().equals("stratovolcanoes"))
            .findFirst()
            .orElseThrow();

        for (int x = -32768; x <= 32768 && !found.containsAll(expected); x += 1024)
        {
            for (int z = -32768; z <= 32768 && !found.containsAll(expected); z += 1024)
            {
                String variant = FeatureDetectors.getFeatureVariant(stratovolcanoes, 0L, new BlockPos(x, 64, z));
                if (variant != null) found.add(variant);
            }
        }

        assertEquals(expected, found);
    }
}
