package com.rustysnail.world.preview.tfc.backend.color;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseMultiJsonResourceReloadListenerTest
{
    @Test
    void commonFallbackLoadsBeforeNamespacedWorldgenResources()
    {
        assertEquals(
            List.of(
                ResourceLocation.parse("c:worldgen/biome_colors.json"),
                ResourceLocation.parse("c:biome_colors.json"),
                ResourceLocation.parse("minecraft:biome_colors.json"),
                ResourceLocation.parse("minecraft:worldgen/biome_colors.json"),
                ResourceLocation.parse("tfc:biome_colors.json"),
                ResourceLocation.parse("tfc:worldgen/biome_colors.json")
            ),
            BaseMultiJsonResourceReloadListener.resourceLocations(
                Set.of("tfc", "minecraft", "c"), "biome_colors.json"
            )
        );
    }
}
