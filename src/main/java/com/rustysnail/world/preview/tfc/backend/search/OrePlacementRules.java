package com.rustysnail.world.preview.tfc.backend.search;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.river.MidpointFractal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class OrePlacementRules
{
    private static final String OPAL_PATH = "vein/opal";
    private static final String AMETHYST_PATH = "vein/amethyst";
    private static final float RIVER_WIDTH = 0.35f;
    private static final float RIVER_BOUND_CHECK = 0.1f;

    private OrePlacementRules() {}

    public static boolean hasExtraConstraints(ResourceLocation configuredFeatureId)
    {
        String path = configuredFeatureId.getPath();
        return OPAL_PATH.equals(path) || AMETHYST_PATH.equals(path);
    }

    public static boolean matchesConfiguredPlacement(
        ResourceLocation configuredFeatureId,
        BlockPos center,
        @Nullable RegionGenerator regionGenerator,
        @Nullable Region.Point sampledPoint
    )
    {
        String path = configuredFeatureId.getPath();
        int y = center.getY();

        if (!OPAL_PATH.equals(path) && !AMETHYST_PATH.equals(path))
        {
            return true;
        }

        if (y < 40 || y > 60 || regionGenerator == null)
        {
            return false;
        }

        Region.Point point = sampledPoint;
        if (point == null)
        {
            point = samplePoint(regionGenerator, center.getX(), center.getZ());
        }

        if (point == null || !isRiverBiome(point))
        {
            return false;
        }
        return isUnderRiver(regionGenerator, center.getX(), center.getZ());
    }

    public static @Nullable Region.Point samplePoint(RegionGenerator regionGenerator, int blockX, int blockZ)
    {
        try
        {
            int gridX = Units.blockToGrid(blockX);
            int gridZ = Units.blockToGrid(blockZ);
            return regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static boolean isRiverBiome(Region.Point point)
    {
        BiomeExtension ext = TFCLayers.getFromLayerId(point.biome);
        return ext.key().location().getPath().contains("river");
    }

    private static boolean isUnderRiver(RegionGenerator regionGenerator, int blockX, int blockZ)
    {
        try
        {
            int gridX = Units.blockToGrid(blockX);
            int gridZ = Units.blockToGrid(blockZ);
            Region region = regionGenerator.getOrCreateRegion(gridX, gridZ);
            if (region == null)
            {
                return false;
            }

            float exactGridX = (float) blockX / Units.GRID_WIDTH_IN_BLOCK;
            float exactGridZ = (float) blockZ / Units.GRID_WIDTH_IN_BLOCK;

            for (RiverEdge edge : region.rivers())
            {
                MidpointFractal fractal = edge.fractal();
                if (fractal.maybeIntersect(exactGridX, exactGridZ, RIVER_BOUND_CHECK) &&
                    fractal.intersect(exactGridX, exactGridZ, RIVER_WIDTH))
                {
                    return true;
                }
            }
        }
        catch (Exception ignored)
        {
            return false;
        }

        return false;
    }
}
