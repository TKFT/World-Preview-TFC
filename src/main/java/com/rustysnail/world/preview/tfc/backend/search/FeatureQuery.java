package com.rustysnail.world.preview.tfc.backend.search;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.region.Region;

import org.jetbrains.annotations.Nullable;

public record FeatureQuery(
    long seed,
    int blockX,
    int blockZ,
    @Nullable Region.Point point,
    @Nullable BiomeExtension biomeExt,
    @Nullable BiomeLookup biomeLookup,
    @Nullable HeightSampler heights
)
{
    @FunctionalInterface
    public interface BiomeLookup
    {
        BiomeExtension getBiome(int quartX, int quartZ);
    }
}
