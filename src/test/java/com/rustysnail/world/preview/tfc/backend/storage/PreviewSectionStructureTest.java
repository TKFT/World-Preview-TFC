package com.rustysnail.world.preview.tfc.backend.storage;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewSectionStructureTest
{
    @Test
    void featureInsertionIsIdempotentAcrossCanceledAndRetriedWork()
    {
        PreviewSectionStructure section = new PreviewSectionStructure(0, 0);
        PreviewSection.PreviewFeature feature = new PreviewSection.PreviewFeature((short) 7, new BlockPos(12, 64, 20));

        section.addFeature(feature);
        section.addFeature(feature);

        assertEquals(1, section.features().size());
    }
}
