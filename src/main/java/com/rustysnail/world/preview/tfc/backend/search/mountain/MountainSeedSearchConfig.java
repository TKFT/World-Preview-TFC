package com.rustysnail.world.preview.tfc.backend.search.mountain;

import net.minecraft.core.BlockPos;

public record MountainSeedSearchConfig(
    BlockPos center,
    int radius,
    int maxSeeds,
    int topResults,
    long randomSalt,
    MountainSearchConfig scanConfig
) {
    public static MountainSeedSearchConfig randomSearch(BlockPos center, int radius, int maxSeeds, long currentWorldSeed)
    {
        MountainSearchConfig scanConfig = MountainSearchConfig.forCurrentSeed(center, radius);
        long randomSalt = currentWorldSeed ^ 0x5DEECE66DL ^ System.nanoTime();
        return new MountainSeedSearchConfig(center, radius, maxSeeds, 25, randomSalt, scanConfig);
    }
}
