package com.rustysnail.world.preview.tfc.backend.search;

@FunctionalInterface
public interface HeightSampler
{
    int surfaceY(int blockX, int blockZ);
}
