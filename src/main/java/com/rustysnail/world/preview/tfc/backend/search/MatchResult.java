package com.rustysnail.world.preview.tfc.backend.search;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record MatchResult(
    String seedString,
    long seedLong,
    Set<ResourceKey<Biome>> foundBiomes,
    Set<SearchableFeature> foundFeatures,
    @Nullable BlockPos featureLocation,
    @Nullable BlockPos center
)
{
}
