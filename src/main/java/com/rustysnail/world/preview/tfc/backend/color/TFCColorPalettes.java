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
    public static final ResourceLocation CROP_HARVEST_POTENTIAL = id("crop_harvest_potential");
    public static final ResourceLocation PERENNIAL_PRODUCTION_POTENTIAL =
        id("perennial_production_potential");

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    private TFCColorPalettes()
    {
    }
}
