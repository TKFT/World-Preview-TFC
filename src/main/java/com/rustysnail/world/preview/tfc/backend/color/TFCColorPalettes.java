package com.rustysnail.world.preview.tfc.backend.color;

import net.minecraft.resources.ResourceLocation;

public final class TFCColorPalettes
{
    public static final ResourceLocation FOREST_TYPES = id("forest_types");
    public static final ResourceLocation TREE_SPECIES = id("tree_species");
    public static final ResourceLocation SOIL_TYPES = id("soil_types");
    public static final ResourceLocation ROCK_TYPES = id("rock_types");
    public static final ResourceLocation ROCKS = id("rocks");
    public static final ResourceLocation WATER = id("water");
    public static final ResourceLocation SUITABILITY = id("suitability");

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    private TFCColorPalettes()
    {
    }
}
