package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TFCTreeSpeciesPaletteTest
{
    @Test
    void unknownAddonSpeciesFallbackIsStable()
    {
        ResourceLocation species = ResourceLocation.fromNamespaceAndPath("addon", "silver_tree");
        assertEquals(TFCTreeSpeciesRegistry.fallbackColor(species), TFCTreeSpeciesRegistry.fallbackColor(species));
        assertNotEquals(0, TFCTreeSpeciesRegistry.fallbackColor(species) >>> 24);
    }
}
