package com.rustysnail.world.preview.tfc.backend.export;

import java.util.Arrays;

public record MapExportPlan(
    MapExportLayer layer,
    int[] paletteRgb,
    String paletteMode,
    boolean continentCellWaterShading,
    PixelSampler sampler
)
{
    public MapExportPlan
    {
        if (layer == null || paletteRgb == null || paletteRgb.length < 1 || paletteRgb.length > 256
            || paletteMode == null || paletteMode.isBlank() || sampler == null)
        {
            throw new IllegalArgumentException("Invalid map export plan");
        }
        paletteRgb = Arrays.copyOf(paletteRgb, paletteRgb.length);
    }

    @Override
    public int[] paletteRgb()
    {
        return Arrays.copyOf(this.paletteRgb, this.paletteRgb.length);
    }

    int[] paletteRgbUnsafe()
    {
        return this.paletteRgb;
    }

    @FunctionalInterface
    public interface PixelSampler
    {
        int sample(int northWestQuartX, int northWestQuartZ, int samplesPerAxis);
    }
}
