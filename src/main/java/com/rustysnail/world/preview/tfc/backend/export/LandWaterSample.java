package com.rustysnail.world.preview.tfc.backend.export;

/** Compact sampler result used in the exporter hot loop. */
public final class LandWaterSample
{
    public static final byte LAND = 0;
    public static final byte WATER = 1;
    public static final byte NARROW_WATER = 2;

    private LandWaterSample()
    {
    }
}
