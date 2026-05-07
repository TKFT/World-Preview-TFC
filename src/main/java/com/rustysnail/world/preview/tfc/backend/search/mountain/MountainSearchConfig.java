package com.rustysnail.world.preview.tfc.backend.search.mountain;

import net.minecraft.core.BlockPos;

public record MountainSearchConfig(
    BlockPos center,
    int radius,
    int coarseStep,
    int refineRadius,
    int refineStep,
    int finalRadius,
    int finalStep,
    int candidatesToRefine
) {
    public static MountainSearchConfig forCurrentSeed(BlockPos center, int radius)
    {
        int coarseStep = radius <= 500 ? 32 : radius <= 2000 ? 64 : 128;
        return new MountainSearchConfig(center, radius, coarseStep, 384, 16, 96, 1, 24);
    }
}
