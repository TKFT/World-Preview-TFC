package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.RenderSettings;
import org.jetbrains.annotations.Nullable;

public record TFCWorkPlan(
    boolean temperature,
    boolean rainfall,
    boolean landWater,
    boolean rocks,
    boolean kaolin,
    boolean forestType,
    boolean treeSpecies,
    boolean soilType,
    boolean cropSuitability,
    boolean hotspot,
    boolean features,
    @Nullable RenderSettings.RenderMode mode
)
{
    public static final long FEATURES_FLAG = 4L;

    public static TFCWorkPlan forMode(@Nullable RenderSettings.RenderMode mode, boolean featureOverlay)
    {
        if (mode == null)
        {
            return new TFCWorkPlan(false, false, false, false, false, false, false, false, false, false, featureOverlay, null);
        }
        return switch (mode)
        {
            // Ocean coloring in these modes reads the land/water section, so it is included.
            case TFC_TEMPERATURE -> new TFCWorkPlan(true, false, true, false, false, false, false, false, false, false, featureOverlay, mode);
            case TFC_RAINFALL -> new TFCWorkPlan(false, true, true, false, false, false, false, false, false, false, featureOverlay, mode);
            case TFC_LAND_WATER -> new TFCWorkPlan(false, false, true, false, false, false, false, false, false, false, featureOverlay, mode);
            case TFC_ROCK_TOP, TFC_ROCK_MID, TFC_ROCK_BOT, TFC_ROCK_TYPE -> new TFCWorkPlan(false, false, true, true, false, false, false, false, false, false, featureOverlay, mode);
            case TFC_KAOLINITE -> new TFCWorkPlan(false, false, true, false, true, false, false, false, false, false, featureOverlay, mode);
            // Forest/Tree water uses classifyTreeMapWater, not the river-fractal land/water map.
            case TFC_FOREST_TYPE -> new TFCWorkPlan(false, false, false, false, false, true, false, false, false, false, featureOverlay, mode);
            case TFC_TREE_SPECIES -> new TFCWorkPlan(false, false, false, false, false, true, true, false, false, false, featureOverlay, mode);
            // Soil needs ChunkData climate + forest type + the effective biome (water via classifyTreeMapWater).
            case TFC_SOIL_TYPE -> new TFCWorkPlan(false, false, false, false, false, false, false, true, false, false, featureOverlay, mode);
            // Crop needs ChunkData climate + the effective biome (water) + chunk-level surface height;
            // no rocks / trees / soil / rainfall / temperature / kaolin / hotspot outputs.
            case TFC_CROP_SUITABILITY -> new TFCWorkPlan(false, false, false, false, false, false, false, false, true, false, featureOverlay, mode);
            case TFC_HOTSPOT -> new TFCWorkPlan(false, false, true, false, false, false, false, false, false, true, featureOverlay, mode);
            // Non-TFC modes (e.g., biome map with feature overlay on): only detect features.
            default -> new TFCWorkPlan(false, false, false, false, false, false, false, false, false, false, featureOverlay, mode);
        };
    }

    public boolean needsRegionPoint()
    {
        return temperature || rainfall || landWater || rocks || hotspot || kaolin || features;
    }

    public boolean needsChunkData()
    {
        return forestType || treeSpecies || soilType || cropSuitability;
    }

    public boolean needsTreeMapBiome()
    {
        return forestType || treeSpecies || soilType || cropSuitability;
    }

    public boolean anyOutput()
    {
        return temperature || rainfall || landWater || rocks || kaolin || forestType || treeSpecies || soilType || cropSuitability || hotspot || features;
    }

    public long[] requiredCompletionFlags()
    {
        long[] tmp = new long[11];
        int n = 0;
        if (temperature) tmp[n++] = RenderSettings.RenderMode.TFC_TEMPERATURE.flag;
        if (rainfall) tmp[n++] = RenderSettings.RenderMode.TFC_RAINFALL.flag;
        if (landWater) tmp[n++] = RenderSettings.RenderMode.TFC_LAND_WATER.flag;
        if (rocks) tmp[n++] = RenderSettings.RenderMode.TFC_ROCK_TOP.flag;
        if (kaolin) tmp[n++] = RenderSettings.RenderMode.TFC_KAOLINITE.flag;
        if (forestType) tmp[n++] = RenderSettings.RenderMode.TFC_FOREST_TYPE.flag;
        if (hotspot) tmp[n++] = RenderSettings.RenderMode.TFC_HOTSPOT.flag;
        if (treeSpecies) tmp[n++] = RenderSettings.RenderMode.TFC_TREE_SPECIES.flag;
        if (soilType) tmp[n++] = RenderSettings.RenderMode.TFC_SOIL_TYPE.flag;
        if (cropSuitability) tmp[n++] = RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag;
        if (features) tmp[n++] = FEATURES_FLAG;
        long[] out = new long[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    public String describe()
    {
        StringBuilder sb = new StringBuilder();
        if (temperature) sb.append("temp ");
        if (rainfall) sb.append("rain ");
        if (landWater) sb.append("landWater ");
        if (rocks) sb.append("rocks ");
        if (kaolin) sb.append("kaolin ");
        if (forestType) sb.append("forest ");
        if (treeSpecies) sb.append("tree ");
        if (soilType) sb.append("soil ");
        if (cropSuitability) sb.append("crop ");
        if (hotspot) sb.append("hotspot ");
        if (features) sb.append("features ");
        return sb.toString().trim();
    }
}
