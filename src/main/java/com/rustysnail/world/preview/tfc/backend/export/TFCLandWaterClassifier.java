package com.rustysnail.world.preview.tfc.backend.export;

import java.util.IdentityHashMap;
import java.util.Locale;

import net.dries007.tfc.world.biome.BiomeExtension;
import org.jetbrains.annotations.Nullable;

/** Classifies final TFC biome extensions without consulting the underlying no-river layer. */
public final class TFCLandWaterClassifier
{
    private final ThreadLocal<IdentityHashMap<BiomeExtension, Byte>> cache =
        ThreadLocal.withInitial(IdentityHashMap::new);

    public byte classify(@Nullable BiomeExtension biome)
    {
        if (biome == null)
        {
            return LandWaterSample.LAND;
        }

        IdentityHashMap<BiomeExtension, Byte> local = this.cache.get();
        Byte cached = local.get(biome);
        if (cached != null)
        {
            return cached;
        }

        byte result = classifyMetadata(
            biome.biomeBlendType().name(),
            biome.isSalty(),
            biome.isShore(),
            biome.key().location().getNamespace(),
            biome.key().location().getPath()
        );
        local.put(biome, result);
        return result;
    }

    /**
     * Pure compatibility classifier. Salinity is intentionally not sufficient: TFC lakes and
     * rivers are fresh water, while a salty shore is still land.
     */
    public static byte classifyMetadata(String blendType, boolean salty, boolean shore, String namespace, String path)
    {
        if (shore)
        {
            return LandWaterSample.LAND;
        }

        String normalizedBlend = blendType == null ? "" : blendType.toUpperCase(Locale.ROOT);
        if (normalizedBlend.equals("OCEAN"))
        {
            return LandWaterSample.WATER;
        }
        if (normalizedBlend.equals("LAKE"))
        {
            return LandWaterSample.NARROW_WATER;
        }

        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (hasLandMarker(normalizedPath))
        {
            return LandWaterSample.LAND;
        }
        if (isNarrowWaterPath(normalizedPath))
        {
            return LandWaterSample.NARROW_WATER;
        }
        if (isOpenWaterPath(normalizedPath))
        {
            return LandWaterSample.WATER;
        }

        // Salinity and namespace remain in the signature for compatibility tests and addon
        // diagnostics; neither turns an otherwise land biome into water.
        return LandWaterSample.LAND;
    }

    private static boolean hasLandMarker(String path)
    {
        return path.equals("shore") || path.endsWith("_shore") || path.startsWith("shore_")
            || path.equals("beach") || path.endsWith("_beach") || path.startsWith("beach_")
            || path.equals("river_bank") || path.endsWith("_river_bank")
            || path.equals("river_valley") || path.endsWith("_river_valley");
    }

    private static boolean isNarrowWaterPath(String path)
    {
        return path.equals("river") || path.endsWith("_river")
            || path.equals("lake") || path.endsWith("_lake") || path.startsWith("lake_") || path.contains("_lake_")
            || path.equals("channel") || path.endsWith("_channel") || path.startsWith("channel_")
            || path.equals("river_mouth") || path.contains("_river_mouth")
            || path.equals("estuary") || path.endsWith("_estuary")
            || path.equals("bay") || path.endsWith("_bay");
    }

    private static boolean isOpenWaterPath(String path)
    {
        return path.equals("ocean") || path.startsWith("ocean_") || path.endsWith("_ocean")
            || path.equals("deep_ocean") || path.startsWith("deep_ocean_")
            || path.equals("trench") || path.endsWith("_trench")
            || path.equals("open_water") || path.endsWith("_open_water")
            || path.equals("water") || path.endsWith("_water") || path.startsWith("water_");
    }
}
