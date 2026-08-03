package com.rustysnail.world.preview.tfc.backend.export;

public enum MapExportLayer
{
    BIOMES("biomes", "Biomes"),
    LAND_WATER("land_water", "Land / Water"),
    TERRAIN("terrain", "Terrain"),
    TEMPERATURE("temperature", "Temperature"),
    RAINFALL("rainfall", "Rainfall");

    private final String id;
    private final String displayName;

    MapExportLayer(String id, String displayName)
    {
        this.id = id;
        this.displayName = displayName;
    }

    public String id()
    {
        return this.id;
    }

    public String displayName()
    {
        return this.displayName;
    }
}
