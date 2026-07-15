package com.rustysnail.world.preview.tfc.backend.search;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

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
