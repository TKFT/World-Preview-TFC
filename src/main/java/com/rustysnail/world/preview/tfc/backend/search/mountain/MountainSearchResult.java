package com.rustysnail.world.preview.tfc.backend.search.mountain;

import net.minecraft.core.BlockPos;

public record MountainSearchResult(
    long seed,
    int height,
    int x,
    int z,
    int coarseHeight,
    int refinedHeight,
    long samplesChecked,
    long elapsedMillis
) {
    public BlockPos pos()
    {
        return new BlockPos(x, height, z);
    }

    public String summary()
    {
        return "Peak Y=" + height + " at X=" + x + " Z=" + z
               + ", samples=" + samplesChecked + ", time=" + elapsedMillis + "ms";
    }
}
