package com.rustysnail.world.preview.tfc.backend.export;

import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Bounds;

import org.jetbrains.annotations.Nullable;

public record LandWaterExportMetadata(
    String seedEntered,
    long resolvedNumericSeed,
    String dimension,
    int centerX,
    int centerZ,
    Bounds blockBounds,
    int blocksPerPixel,
    int imageWidth,
    int imageHeight,
    String exporterVersion,
    String timestamp,
    String classificationMode,
    boolean tfcDetected,
    @Nullable String tfcVersion,
    @Nullable String tfcLargeBiomesVersion
)
{
    public static final String CLASSIFICATION_MODE = "final_tfc_biome_two_color_land_water";
}
