package com.rustysnail.world.preview.tfc.backend.export;

import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Bounds;
import org.jetbrains.annotations.Nullable;

public record MapExportMetadata(
    String seedEntered,
    long resolvedNumericSeed,
    String dimension,
    int centerX,
    int centerZ,
    Bounds bounds,
    String layer,
    String preset,
    int blocksPerPixel,
    int quartSamplesPerAxis,
    int imageWidth,
    int imageHeight,
    int paletteEntries,
    String paletteMode,
    boolean continentCellWaterShading,
    int waterShadeCount,
    double effectiveTemperatureScale,
    double effectiveRainfallScale,
    String exporterVersion,
    String generatedAtUtc,
    boolean tfcDetected,
    @Nullable String tfcVersion,
    @Nullable String tfcLargeBiomesVersion
)
{
}
