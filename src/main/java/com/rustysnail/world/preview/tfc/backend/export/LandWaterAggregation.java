package com.rustysnail.world.preview.tfc.backend.export;

public final class LandWaterAggregation
{
    private LandWaterAggregation()
    {
    }

    public static boolean isWater(byte sample)
    {
        return sample == LandWaterSample.WATER || sample == LandWaterSample.NARROW_WATER;
    }

    public static boolean aggregate2x2(byte northWest, byte northEast, byte southWest, byte southEast)
    {
        if (northWest == LandWaterSample.NARROW_WATER || northEast == LandWaterSample.NARROW_WATER
            || southWest == LandWaterSample.NARROW_WATER || southEast == LandWaterSample.NARROW_WATER)
        {
            return true;
        }

        int water = 0;
        if (northWest == LandWaterSample.WATER) water++;
        if (northEast == LandWaterSample.WATER) water++;
        if (southWest == LandWaterSample.WATER) water++;
        if (southEast == LandWaterSample.WATER) water++;
        return water >= 2;
    }
}
