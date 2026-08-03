package com.rustysnail.world.preview.tfc.backend.export;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.ColorMap;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import net.minecraft.resources.ResourceLocation;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.region.Region;

public final class MapExportLayerFactory
{
    static final int TERRAIN_WATER = 0;
    static final int TERRAIN_LOWLAND = 1;
    static final int TERRAIN_MIDLAND = 2;
    static final int TERRAIN_HIGHLAND = 3;
    static final int TERRAIN_MOUNTAIN = 4;
    static final int UNKNOWN_BIOME_RGB = 0xFF00FF;
    static final int[] TERRAIN_PALETTE = {
        0x24558F,
        0x6E9F57,
        0xA9B96E,
        0x8C805F,
        0xD8D8D8
    };

    private MapExportLayerFactory()
    {
    }

    public static List<MapExportPlan> create(
        Collection<MapExportLayer> selectedLayers,
        TFCSampleUtils tfc,
        PreviewMappingData mappingData,
        String configuredTemperatureColorMap,
        String configuredRainfallColorMap,
        int landRgb,
        int waterRgb
    )
    {
        TFCLandWaterClassifier classifier = new TFCLandWaterClassifier();
        List<MapExportPlan> plans = new ArrayList<>(selectedLayers.size());
        selectedLayers.stream().sorted(Comparator.comparingInt(Enum::ordinal)).forEach(layer -> {
            plans.add(switch (layer)
            {
                case BIOMES -> biomes(tfc, mappingData);
                case LAND_WATER -> landWater(tfc, classifier, landRgb, waterRgb);
                case TERRAIN -> terrain(tfc, classifier);
                case TEMPERATURE -> temperature(tfc, mappingData, configuredTemperatureColorMap);
                case RAINFALL -> rainfall(tfc, mappingData, configuredRainfallColorMap);
            });
        });
        return List.copyOf(plans);
    }

    static int continentShadeBucket(double cellNoise)
    {
        return Math.min(15, (int) (Math.clamp(cellNoise, 0D, 0.999999D) * 16D));
    }

    static int[] waterShades(int baseRgb)
    {
        int[] palette = new int[16];
        int red = baseRgb >>> 16 & 0xFF;
        int green = baseRgb >>> 8 & 0xFF;
        int blue = baseRgb & 0xFF;
        for (int i = 0; i < palette.length; i++)
        {
            double factor = 0.92D + 0.16D * i / 15D;
            int shadedRed = Math.clamp((int) Math.round(red * factor), 0, 255);
            int shadedGreen = Math.clamp((int) Math.round(green * factor), 0, 255);
            int shadedBlue = Math.clamp((int) Math.round(blue * factor), 0, 255);
            palette[i] = shadedRed << 16 | shadedGreen << 8 | shadedBlue;
        }
        return palette;
    }

    static int aggregateLandWater(byte[] waterClasses, int[] shadeBuckets, int sampleCount)
    {
        int openWaterCount = 0;
        int narrowWaterCount = 0;
        int waterShadeSum = 0;
        for (int i = 0; i < sampleCount; i++)
        {
            if (waterClasses[i] == LandWaterSample.NARROW_WATER)
            {
                narrowWaterCount++;
                waterShadeSum += shadeBuckets[i];
            }
            else if (waterClasses[i] == LandWaterSample.WATER)
            {
                openWaterCount++;
                waterShadeSum += shadeBuckets[i];
            }
        }
        int waterCount = narrowWaterCount + openWaterCount;
        if (narrowWaterCount == 0 && openWaterCount * 2 < sampleCount)
        {
            return 0;
        }
        int averagedShade = Math.clamp((waterShadeSum + waterCount / 2) / waterCount, 0, 15);
        return 1 + averagedShade;
    }

    static int terrainCategory(boolean water, boolean mountain, int discreteBiomeAltitude)
    {
        if (water)
        {
            return TERRAIN_WATER;
        }
        if (mountain)
        {
            return TERRAIN_MOUNTAIN;
        }
        if (discreteBiomeAltitude <= 0)
        {
            return TERRAIN_LOWLAND;
        }
        if (discreteBiomeAltitude == 1)
        {
            return TERRAIN_MIDLAND;
        }
        return TERRAIN_HIGHLAND;
    }

    static int gradientIndex(double total, int sampleCount, double minimum, double maximum)
    {
        return MapExportAggregation.averageIndex(total, sampleCount, minimum, maximum);
    }

    private static MapExportPlan biomes(TFCSampleUtils tfc, PreviewMappingData mappingData)
    {
        List<BiomeExtension> registered = new ArrayList<>();
        for (BiomeExtension biome : TFCBiomes.REGISTRY)
        {
            registered.add(biome);
        }
        registered.sort(Comparator.comparing(biome -> biome.key().location().toString()));

        int included = Math.min(255, registered.size());
        int[] palette = new int[included + 1];
        palette[0] = UNKNOWN_BIOME_RGB;
        IdentityHashMap<BiomeExtension, Integer> indexes = new IdentityHashMap<>(included * 2);
        for (int i = 0; i < included; i++)
        {
            BiomeExtension biome = registered.get(i);
            String key = biome.key().location().toString();
            int configured = mappingData.getBiomeColor(key);
            palette[i + 1] = configured == -1 ? fallbackBiomeColor(key) : configured & 0xFFFFFF;
            indexes.put(biome, i + 1);
        }
        if (registered.size() > included)
        {
            WorldPreview.LOGGER.warn(
                "Map export biome palette contains {} entries; {} deterministic excess entries use the unknown index",
                registered.size(), registered.size() - included);
        }

        ThreadLocal<int[]> samples = ThreadLocal.withInitial(() -> new int[16]);
        return new MapExportPlan(
            MapExportLayer.BIOMES,
            palette,
            "final_active_biome_source_world_preview_colors_unknown_index_0",
            false,
            (west, north, sampleAxis) -> {
                int[] values = samples.get();
                int count = 0;
                for (int z = 0; z < sampleAxis; z++)
                {
                    for (int x = 0; x < sampleAxis; x++)
                    {
                        BiomeExtension biome = tfc.sampleBiomeExtensionQuart(west + x, north + z);
                        values[count++] = biome == null ? 0 : indexes.getOrDefault(biome, 0);
                    }
                }
                return MapExportAggregation.majority(values, count);
            }
        );
    }

    private static MapExportPlan landWater(
        TFCSampleUtils tfc,
        TFCLandWaterClassifier classifier,
        int landRgb,
        int waterRgb
    )
    {
        int[] shades = waterShades(waterRgb);
        int[] palette = new int[17];
        palette[0] = landRgb & 0xFFFFFF;
        System.arraycopy(shades, 0, palette, 1, shades.length);
        ThreadLocal<WaterScratch> scratch = ThreadLocal.withInitial(WaterScratch::new);

        return new MapExportPlan(
            MapExportLayer.LAND_WATER,
            palette,
            "final_active_biome_land_index_0_water_cell_shades_1_16",
            true,
            (west, north, sampleAxis) -> {
                WaterScratch values = scratch.get();
                int count = 0;
                for (int z = 0; z < sampleAxis; z++)
                {
                    for (int x = 0; x < sampleAxis; x++)
                    {
                        int quartX = west + x;
                        int quartZ = north + z;
                        byte waterClass = classifier.classify(tfc.sampleBiomeExtensionQuart(quartX, quartZ));
                        values.waterClasses[count] = waterClass;
                        values.shadeBuckets[count] = waterClass == LandWaterSample.LAND
                            ? 0
                            : continentShadeBucket(tfc.sampleContinentCellNoiseQuart(quartX, quartZ));
                        count++;
                    }
                }
                return aggregateLandWater(values.waterClasses, values.shadeBuckets, count);
            }
        );
    }

    private static MapExportPlan terrain(TFCSampleUtils tfc, TFCLandWaterClassifier classifier)
    {
        ThreadLocal<int[]> samples = ThreadLocal.withInitial(() -> new int[16]);
        return new MapExportPlan(
            MapExportLayer.TERRAIN,
            TERRAIN_PALETTE,
            "water_lowland_midland_or_plains_highland_mountain",
            false,
            (west, north, sampleAxis) -> {
                int[] values = samples.get();
                int count = 0;
                for (int z = 0; z < sampleAxis; z++)
                {
                    for (int x = 0; x < sampleAxis; x++)
                    {
                        int quartX = west + x;
                        int quartZ = north + z;
                        boolean water = classifier.classify(tfc.sampleBiomeExtensionQuart(quartX, quartZ))
                            != LandWaterSample.LAND;
                        Region.Point point = tfc.samplePointQuart(quartX, quartZ);
                        values[count++] = terrainCategory(water, point.mountain(), point.discreteBiomeAltitude());
                    }
                }
                return MapExportAggregation.majority(values, count);
            }
        );
    }

    private static MapExportPlan temperature(
        TFCSampleUtils tfc,
        PreviewMappingData mappingData,
        String configuredColorMap
    )
    {
        int[] palette = gradientPalette(mappingData.resolveColorMap(
            configuredColorMap,
            ResourceLocation.fromNamespaceAndPath("world_preview_tfc", "tfc_temperature"),
            "temperature export"
        ));
        return new MapExportPlan(
            MapExportLayer.TEMPERATURE,
            palette,
            "world_preview_tfc_temperature_gradient_-23_to_33_c",
            false,
            (west, north, sampleAxis) -> {
                double total = 0D;
                for (int z = 0; z < sampleAxis; z++)
                {
                    for (int x = 0; x < sampleAxis; x++)
                    {
                        total += tfc.samplePointQuart(west + x, north + z).temperature;
                    }
                }
                return gradientIndex(total, sampleAxis * sampleAxis, -23D, 33D);
            }
        );
    }

    private static MapExportPlan rainfall(
        TFCSampleUtils tfc,
        PreviewMappingData mappingData,
        String configuredColorMap
    )
    {
        int[] palette = gradientPalette(mappingData.resolveColorMap(
            configuredColorMap,
            ResourceLocation.fromNamespaceAndPath("world_preview_tfc", "tfc_rainfall"),
            "rainfall export"
        ));
        return new MapExportPlan(
            MapExportLayer.RAINFALL,
            palette,
            "world_preview_tfc_rainfall_gradient_0_to_500_mm",
            false,
            (west, north, sampleAxis) -> {
                double total = 0D;
                for (int z = 0; z < sampleAxis; z++)
                {
                    for (int x = 0; x < sampleAxis; x++)
                    {
                        total += tfc.samplePointQuart(west + x, north + z).rainfall;
                    }
                }
                return gradientIndex(total, sampleAxis * sampleAxis, 0D, 500D);
            }
        );
    }

    private static int[] gradientPalette(ColorMap colorMap)
    {
        int[] palette = new int[256];
        if (colorMap == null)
        {
            return palette;
        }
        for (int i = 0; i < palette.length; i++)
        {
            int nativeAbgr = colorMap.getARGB(i / 255F);
            int red = nativeAbgr & 0xFF;
            int green = nativeAbgr >>> 8 & 0xFF;
            int blue = nativeAbgr >>> 16 & 0xFF;
            palette[i] = red << 16 | green << 8 | blue;
        }
        return palette;
    }

    private static int fallbackBiomeColor(String key)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA1").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, 4).getInt() & 0xFFFFFF;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA1 unavailable", e);
        }
    }

    private static final class WaterScratch
    {
        private final byte[] waterClasses = new byte[16];
        private final int[] shadeBuckets = new int[16];
    }
}
