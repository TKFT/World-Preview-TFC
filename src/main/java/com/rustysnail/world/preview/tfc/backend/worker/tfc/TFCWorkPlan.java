package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.RenderSettings;

/**
 * Describes which outputs a {@link TFCRegionWorkUnit} must compute for the active render mode, so
 * each TFC map only does the work it needs (e.g. Tree Species does not sample rocks; Temperature
 * does not resolve trees). Built once per queue pass from {@link #forMode}.
 */
public record TFCWorkPlan(
    boolean temperature,
    boolean rainfall,
    boolean landWater,
    boolean rocks,
    boolean kaolin,
    boolean forestType,
    boolean treeSpecies,
    boolean hotspot,
    boolean features,
    RenderSettings.RenderMode mode
)
{
    // Dedicated completion flag for TFC feature detection (an unused storage flag, not a render mode).
    public static final long FEATURES_FLAG = 4L;

    public static TFCWorkPlan forMode(RenderSettings.RenderMode mode, boolean featureOverlay)
    {
        boolean f = featureOverlay;
        if (mode == null)
        {
            return new TFCWorkPlan(false, false, false, false, false, false, false, false, f, null);
        }
        return switch (mode)
        {
            // Ocean coloring in these modes reads the land/water section, so it is included.
            case TFC_TEMPERATURE -> new TFCWorkPlan(true, false, true, false, false, false, false, false, f, mode);
            case TFC_RAINFALL -> new TFCWorkPlan(false, true, true, false, false, false, false, false, f, mode);
            case TFC_LAND_WATER -> new TFCWorkPlan(false, false, true, false, false, false, false, false, f, mode);
            case TFC_ROCK_TOP, TFC_ROCK_MID, TFC_ROCK_BOT, TFC_ROCK_TYPE ->
                new TFCWorkPlan(false, false, true, true, false, false, false, false, f, mode);
            case TFC_KAOLINITE -> new TFCWorkPlan(false, false, true, false, true, false, false, false, f, mode);
            // Forest/Tree water uses classifyTreeMapWater, not the river-fractal land/water map.
            case TFC_FOREST_TYPE -> new TFCWorkPlan(false, false, false, false, false, true, false, false, f, mode);
            case TFC_TREE_SPECIES -> new TFCWorkPlan(false, false, false, false, false, true, true, false, f, mode);
            case TFC_HOTSPOT -> new TFCWorkPlan(false, false, true, false, false, false, false, true, f, mode);
            // Non-TFC modes (e.g. biome map with feature overlay on): only detect features.
            default -> new TFCWorkPlan(false, false, false, false, false, false, false, false, f, mode);
        };
    }

    /** Any output that needs the per-position Region.Point (climate, rocks, land/water, hotspot, kaolin, features). */
    public boolean needsRegionPoint()
    {
        return temperature || rainfall || landWater || rocks || hotspot || kaolin || features;
    }

    /** Whether a chunk's ChunkData must be sampled (forest type / tree species). */
    public boolean needsChunkData()
    {
        return forestType || treeSpecies;
    }

    /** Whether the effective biome must be sampled per point (tree-map water / config selection). */
    public boolean needsTreeMapBiome()
    {
        return forestType || treeSpecies;
    }

    public boolean anyOutput()
    {
        return temperature || rainfall || landWater || rocks || kaolin || forestType || treeSpecies || hotspot || features;
    }

    /**
     * Storage flags whose completion bitmap represents "this output is generated for the chunk".
     * These are the render-section flags themselves (their completion bitmap is separate from the
     * pixel data), plus the dedicated FEATURES_FLAG. Rocks are tracked by the single TFC_ROCK_TOP
     * flag since all rock layers are computed together. A work unit is complete only when every
     * required flag is marked, so e.g. Temperature completion never implies Tree Species completion.
     */
    public long[] requiredCompletionFlags()
    {
        long[] tmp = new long[9];
        int n = 0;
        if (temperature) tmp[n++] = RenderSettings.RenderMode.TFC_TEMPERATURE.flag;
        if (rainfall) tmp[n++] = RenderSettings.RenderMode.TFC_RAINFALL.flag;
        if (landWater) tmp[n++] = RenderSettings.RenderMode.TFC_LAND_WATER.flag;
        if (rocks) tmp[n++] = RenderSettings.RenderMode.TFC_ROCK_TOP.flag;
        if (kaolin) tmp[n++] = RenderSettings.RenderMode.TFC_KAOLINITE.flag;
        if (forestType) tmp[n++] = RenderSettings.RenderMode.TFC_FOREST_TYPE.flag;
        if (hotspot) tmp[n++] = RenderSettings.RenderMode.TFC_HOTSPOT.flag;
        if (treeSpecies) tmp[n++] = RenderSettings.RenderMode.TFC_TREE_SPECIES.flag;
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
        if (hotspot) sb.append("hotspot ");
        if (features) sb.append("features ");
        return sb.toString().trim();
    }
}
