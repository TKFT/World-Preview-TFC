package com.rustysnail.world.preview.tfc.backend.worker.tfc;

public record QuartSurfaceHeights(int northWest, int northEast, int southWest, int southEast)
{
    public static final int WEST_OR_NORTH_OFFSET = 4;
    public static final int EAST_OR_SOUTH_OFFSET = 12;

    private static double lerp(double delta, double start, double end)
    {
        return start + delta * (end - start);
    }

    private static double clamp01(double value)
    {
        return value < 0d ? 0d : Math.min(value, 1d);
    }

    public int interpolatedSurfaceY(int blockX, int blockZ)
    {
        int localQuartCenterX = (blockX & 15) + 2;
        int localQuartCenterZ = (blockZ & 15) + 2;
        double x = clamp01((localQuartCenterX - WEST_OR_NORTH_OFFSET) / 8d);
        double z = clamp01((localQuartCenterZ - WEST_OR_NORTH_OFFSET) / 8d);

        double north = lerp(x, this.northWest, this.northEast);
        double south = lerp(x, this.southWest, this.southEast);
        return (int) Math.round(lerp(z, north, south));
    }
}
