package com.rustysnail.world.preview.tfc;

import java.util.ArrayList;
import java.util.List;

public class WorldPreviewConfig
{
    public final List<String> savedSeeds = new ArrayList<>();
    public final boolean storeNoiseSamples = false;
    public boolean showInPauseMenu = true;
    public boolean showPlayer = true;
    public boolean showControls = true;
    public boolean showFrameTime = false;
    public boolean sampleStructures = false;
    public boolean sampleHeightmap = false;
    public int heightmapMinY = 32;
    public int heightmapMaxY = 255;
    public boolean onlySampleInVisualRange = true;
    public boolean cacheInGame = true;
    public boolean cacheInNew = false;
    public boolean enableCompression = true;
    public String colorMap = "world_preview_tfc:inferno";
    public int landWaterExportLandColor = 0x8B9B65;
    public int landWaterExportWaterColor = 0x173F5F;
    private int numThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    public int numThreads()
    {
        this.setNumThreads(this.numThreads);
        return this.numThreads;
    }

    public void setNumThreads(int numThreads)
    {
        numThreads = Math.clamp(numThreads, 1, Runtime.getRuntime().availableProcessors());
        this.numThreads = numThreads;
    }
}
