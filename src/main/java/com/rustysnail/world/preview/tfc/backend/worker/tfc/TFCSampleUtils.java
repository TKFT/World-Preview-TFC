package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.backend.search.FeatureQuery;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.AreaFactory;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.RockSettings;
import net.dries007.tfc.world.settings.Settings;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.chunkdata.ForestType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

public class TFCSampleUtils
{
    public static final short VALUE_INVALID = -1;
    public static final short VALUE_WATER   = -2;
    public static final int   COLOR_INVALID = 0xFF222222;
    public static final int   COLOR_WATER   = 0xFF1C5596;

    public static boolean isOceanBiome(ResourceLocation biomeId)
    {
        return biomeId.getPath().contains("ocean");
    }


    private final RegionGenerator regionGenerator;
    private final Settings settings;
    private final RockLayerSettings rockLayerSettings;
    private final ChunkDataGenerator chunkDataGenerator;

    @Nullable
    public static TFCSampleUtils create(ChunkGenerator generator, long seed)
    {
        if (generator instanceof ChunkGeneratorExtension ext)
        {
            return new TFCSampleUtils(ext.settings(), ext.rockLayerSettings(), ext.chunkDataGenerator(), seed);
        }
        return null;
    }

    private final ConcurrentArea<BiomeExtension> biomeLayer;

    private TFCSampleUtils(Settings settings, RockLayerSettings rockLayerSettings, ChunkDataGenerator chunkDataGenerator, long seed)
    {
        this.settings = settings;
        this.rockLayerSettings = rockLayerSettings;
        Seed tfcSeed = Seed.of(seed);
        this.regionGenerator = new RegionGenerator(settings, tfcSeed);

        AreaFactory biomeFactory = TFCLayers.createRegionBiomeLayer(this.regionGenerator, tfcSeed);
        this.biomeLayer = new ConcurrentArea<>(biomeFactory, TFCLayers::getFromLayerId);
        this.chunkDataGenerator = chunkDataGenerator;
    }

    public Settings settings()
    {
        return settings;
    }

    public RegionGenerator regionGenerator()
    {
        return regionGenerator;
    }

    public FeatureQuery.BiomeLookup biomeLookup()
    {
        return this.biomeLayer::get;
    }

    public static BiomeExtension getBiomeExtensionFromPoint(Region.Point point)
    {
        return TFCLayers.getFromLayerId(point.biome);
    }

    public Region.Point samplePoint(int blockX, int blockZ)
    {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        return regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
    }

    public static short normalizeTemperature(float temperature)
    {
        float normalized = (temperature + 23f) / 56f;
        normalized = Math.clamp(normalized, 0f, 1f);

        int scaled = Math.round(normalized * 65534f);
        return (short) (scaled - 32767);
    }

    public static float denormalizeTemperature(short stored)
    {
        float normalized = (stored + 32767f) / 65534f;
        return normalized * 56f - 23f;
    }

    public static short normalizeRainfall(float rainfall)
    {
        float normalized = Math.clamp(rainfall / 500f, 0f, 1f);
        return (short) (normalized * 32767f);
    }

    public static float denormalizeRainfall(short stored)
    {
        return (stored / 32767f) * 500f;
    }

    public RockSettings sampleRockAtLayer(int pointRock, int layer)
    {
        return rockLayerSettings.sampleAtLayer(pointRock, layer);
    }

    public static int getRockTypeCategory(int pointRock)
    {
        return pointRock & 0b11;
    }

    public static final String[] ROCK_TYPE_NAMES = {
        "Oceanic", "Volcanic", "Land", "Uplift"
    };

    public static final String[] ROCK_TYPE_KEYS = {
        "tfc:ocean", "tfc:volcanic", "tfc:land", "tfc:uplift"
    };

    public static final int[] ROCK_TYPE_COLORS = {
        0xFF2244AA,  // Ocean - deep blue
        0xFFCC4400,  // Volcanic - orange-red
        0xFF44AA44,  // Land - green
        0xFF8866AA   // Uplift - purple
    };

    public static String getRockTypeName(int rockType)
    {
        if (rockType < 0 || rockType >= ROCK_TYPE_KEYS.length) return "Unknown";

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        String name = mappingData.getRockTypeName(ROCK_TYPE_KEYS[rockType]);
        if (name != null && !name.equals(ROCK_TYPE_KEYS[rockType]))
        {
            return name;
        }
        return ROCK_TYPE_NAMES[rockType];
    }

    public static int getRockTypeColor(int rockType)
    {
        if (rockType < 0 || rockType >= ROCK_TYPE_COLORS.length) return 0xFF888888;

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        int loadedColor = mappingData.getRockTypeColor(ROCK_TYPE_KEYS[rockType]);
        if (loadedColor != -1)
        {
            return 0xFF000000 | loadedColor;
        }
        return ROCK_TYPE_COLORS[rockType];
    }

    public static final String[] ROCK_NAMES = {
        "granite", "diorite", "gabbro", "shale", "claystone",
        "limestone", "conglomerate", "dolomite", "chert", "chalk",
        "rhyolite", "basalt", "andesite", "dacite", "quartzite",
        "slate", "phyllite", "schist", "gneiss", "marble"
    };

    public static final int[] ROCK_COLORS = {
        0xFF4A4655,  // granite - gray-purple
        0xFF8E8E8E,  // diorite - gray
        0xFF445544,  // gabbro - dark green-gray
        0xFF434346,  // shale - dark gray
        0xFF446688,  // claystone - brown
        0xFF6B7F88,  // limestone - tan-gray
        0xFF65716F,  // conglomerate - gray-green
        0xFF3C4659,  // dolomite - blue-gray
        0xFF4E4E7A,  // chert - purple-gray
        0xFFC1C7C7,  // chalk - white
        0xFF676273,  // rhyolite - purple-gray
        0xFF1D2021,  // basalt - near black
        0xFF606060,  // andesite - medium gray
        0xFF7A7B7B,  // dacite - light gray
        0xFF80818C,  // quartzite - light gray-blue
        0xFF67747D,  // slate - blue-gray
        0xFF949DA9,  // phyllite - silver
        0xFF415444,  // schist - green-gray
        0xFF606D73,  // gneiss - gray
        0xFFE3EBEB   // marble - white
    };

    private static String getRockKey(short rockId)
    {
        if (rockId < 0 || rockId >= ROCK_NAMES.length) return null;
        return "tfc:" + ROCK_NAMES[rockId];
    }

    public static short getRockId(RockSettings rock)
    {
        if (rock == null || rock.raw() == null) return -1;

        String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(rock.raw())
            .getPath()
            .replace("rock/raw/", "");

        for (int i = 0; i < ROCK_NAMES.length; i++)
        {
            if (blockName.contains(ROCK_NAMES[i]))
            {
                return (short) i;
            }
        }
        return -1;
    }

    public static String getRockName(short rockId)
    {
        if (rockId < 0 || rockId >= ROCK_NAMES.length) return "Unknown";

        String key = getRockKey(rockId);
        if (key != null)
        {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            String name = mappingData.getRockName(key);
            if (name != null && !name.equals(key))
            {
                return name;
            }
        }
        String name = ROCK_NAMES[rockId];
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static int getRockColor(short rockId)
    {
        if (rockId < 0 || rockId >= ROCK_COLORS.length) return 0xFF888888;

        String key = getRockKey(rockId);
        if (key != null)
        {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            int loadedColor = mappingData.getRockColor(key);
            if (loadedColor != -1)
            {
                return 0xFF000000 | loadedColor;
            }
        }
        return ROCK_COLORS[rockId];
    }

    public static String getForestTypeName(short forestId)
    {
        if (forestId < 0 || forestId >= ForestType.values().length)
        {
            return "Unknown";
        }

        String name = ForestType.valueOf(forestId).getSerializedName().replace('_', ' ');
        StringBuilder result = new StringBuilder(name.length());

        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (capitalize && Character.isLetter(c))
            {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            }
            else
            {
                result.append(c);
            }

            if (c == ' ')
            {
                capitalize = true;
            }
        }

        return result.toString();
    }

    public static int getForestTypeColor(short forestId)
    {
        if (forestId < 0 || forestId >= ForestType.values().length)
        {
            return 0xFF222222;
        }

        return forestColor(ForestType.valueOf(forestId));
    }

    private static int forestColor(ForestType type)
    {
        return switch (type)
        {
            // Open / no forest
            case GRASSLAND -> 0xFFCEDB7A;
            case CLEARING -> 0xFFD9E28F;

            // Low woody vegetation
            case SHRUBLAND -> 0xFFA8B85E;
            case SPARSE -> 0xFF8EA95A;

            // Savanna palette
            case SAVANNA_MONOCULTURE -> 0xFFC9B65A;
            case SAVANNA_DIVERSE -> 0xFFBDA94D;
            case SAVANNA_ALTERNATE -> 0xFFB19B43;
            case SAVANNA_SHRUB_MONOCULTURE -> 0xFFA88F3D;
            case SAVANNA_SHRUB_DIVERSE -> 0xFF9B8237;
            case SAVANNA_SHRUB_ALTERNATE -> 0xFF8E7632;

            // Forest edge palette
            case EDGE_MONOCULTURE -> 0xFF88B45B;
            case EDGE_DIVERSE -> 0xFF76A84F;
            case EDGE_ALTERNATE -> 0xFF669A45;
            case EDGE_BAMBOO -> 0xFF7FAF64;

            // Secondary forest palette
            case SECONDARY_MONOCULTURE -> 0xFF4F8F3F;
            case SECONDARY_MONOCULTURE_TALL -> 0xFF467F39;
            case SECONDARY_DIVERSE -> 0xFF3F803A;
            case SECONDARY_DIVERSE_TALL -> 0xFF357136;
            case SECONDARY_ALTERNATE -> 0xFF2F6B32;
            case SECONDARY_DENSE -> 0xFF286331;
            case SECONDARY_DENSE_TALL -> 0xFF20582D;
            case SECONDARY_BAMBOO -> 0xFF5E9B4B;

            // Primary forest palette
            case PRIMARY_MONOCULTURE -> 0xFF1F6235;
            case PRIMARY_DIVERSE -> 0xFF154F2D;
            case PRIMARY_ALTERNATE -> 0xFF0F4328;

            // Dead forest palette
            case DEAD_MONOCULTURE -> 0xFF766A58;
            case DEAD_DIVERSE -> 0xFF675C50;
            case DEAD_ALTERNATE -> 0xFF50483F;
            case DEAD_BAMBOO -> 0xFF6D6A4E;
        };
    }

    public ChunkData sampleChunkData(ChunkPos chunkPos)
    {
        ChunkData data = new ChunkData(this.chunkDataGenerator, chunkPos);
        return this.chunkDataGenerator.generate(data);
    }
}
