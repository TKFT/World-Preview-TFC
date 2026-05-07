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
    @Nullable BlockPos center,
    @Nullable String detailText
)
{
    /** Compatibility constructor for existing callers that do not supply detailText. */
    public MatchResult(
        String seedString,
        long seedLong,
        Set<ResourceKey<Biome>> foundBiomes,
        Set<SearchableFeature> foundFeatures,
        @Nullable BlockPos featureLocation,
        @Nullable BlockPos center
    )
    {
        this(seedString, seedLong, foundBiomes, foundFeatures, featureLocation, center, null);
    }
}
