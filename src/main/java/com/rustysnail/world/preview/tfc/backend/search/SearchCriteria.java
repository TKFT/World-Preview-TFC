package com.rustysnail.world.preview.tfc.backend.search;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Set;

public record SearchCriteria(
    Set<ResourceKey<Biome>> requiredBiomes,
    BiomeMatchMode biomeMatchMode,
    Set<SearchableFeature> requiredFeatures,
    BlockPos searchCenter,
    int searchRadius,
    int maxSeeds
)
{
    public enum BiomeMatchMode
    {
        ANY,
        ALL
    }

    public static final int DEFAULT_MAX_SEEDS = 1000;
}
