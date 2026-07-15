package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.backend.color.TFCColorPalettes;
import com.rustysnail.world.preview.tfc.backend.search.FeatureQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.util.EnvironmentHelpers;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.AreaFactory;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.RockSettings;
import net.dries007.tfc.world.settings.Settings;

import static com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeSpeciesRegistry.*;

public class TFCSampleUtils
{
    public static final short VALUE_WATER_OCEAN = 32760;
    public static final short VALUE_WATER_LAKE = 32761;
    public static final short VALUE_WATER_RIVER = 32762;
    public static final short VALUE_WATER = VALUE_WATER_OCEAN;
    public static final short VALUE_INVALID = 32763;

    public static final int COLOR_WATER = 0xFF2868B2;
    public static final int COLOR_INVALID = 0xFF2A2A2A;
    public static final ResourceLocation WATER_OCEAN = previewId("ocean");
    public static final ResourceLocation WATER_LAKE = previewId("lake");
    public static final ResourceLocation WATER_RIVER = previewId("river");
    public static final ResourceLocation WATER_SHORE = previewId("shore");
    public static final ResourceLocation WATER_LAND = previewId("land");
    public static final ResourceLocation WATER_UNKNOWN = previewId("unknown");
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
    public static final short SOIL_ENTISOL = 0;
    public static final short SOIL_ARIDISOL = 1;
    public static final short SOIL_OXISOL = 2;
    public static final short SOIL_FLUVISOL = 3;
    public static final short SOIL_ANDISOL = 4;
    public static final short SOIL_PODZOL = 5;
    public static final short SOIL_ALFISOL = 6;
    public static final short SOIL_MOLLISOL = 7;
    public static final float SOIL_HOT_TEMP = 20.0f;
    public static final float SOIL_COOL_TEMP = 5.0f;
    public static final float SOIL_DRY_GROUNDWATER = 80.0f;
    public static final float SOIL_WET_GROUNDWATER = 220.0f;
    public static final float SOIL_MODERATE_GROUNDWATER = 150.0f;
    public static final float SOIL_FLUVISOL_RAIN_VARIANCE = 0.45f;
    private static final String[] SOIL_NAMES = {
        "Entisol", "Aridisol", "Oxisol", "Fluvisol", "Andisol", "Podzol", "Alfisol", "Mollisol"
    };
    private static final int[] SOIL_COLORS = {
        0xFFB89A6A,  // Entisol  - pale tan
        0xFFD6A24C,  // Aridisol - desert ochre
        0xFFB85A32,  // Oxisol   - tropical red
        0xFF6D8F75,  // Fluvisol - alluvial green-grey
        0xFF4E4A44,  // Andisol  - volcanic dark
        0xFF6B5A7A,  // Podzol   - boreal purple-grey
        0xFF8A6E45,  // Alfisol  - temperate brown
        0xFF4F6F3A   // Mollisol - grassland dark green
    };
    private static final ResourceLocation[] SOIL_KEYS = {
        previewId("entisol"), previewId("aridisol"), previewId("oxisol"), previewId("fluvisol"),
        previewId("andisol"), previewId("podzol"), previewId("alfisol"), previewId("mollisol")
    };
    private static final int[] STABLE_CATEGORY_COLORS = {
        0xFF6E8B3D, 0xFF4E7A4E, 0xFF8A9A5B, 0xFFB5A642, 0xFF7C6A40, 0xFF5F8A6B
    };
    private static volatile TFCTreeSpeciesRegistry activeRegistry = TFCTreeSpeciesRegistry.fallback();

    public static boolean isWaterValue(short value)
    {
        return value == VALUE_WATER_OCEAN || value == VALUE_WATER_LAKE || value == VALUE_WATER_RIVER;
    }

    public static short canonicalMapValue(short value)
    {
        return isWaterValue(value) ? VALUE_WATER : value;
    }

    public static String getWaterTypeName(short value)
    {
        ResourceLocation key = waterKey(value);
        String fallback = switch (value)
        {
            case VALUE_WATER_OCEAN -> "Ocean";
            case VALUE_WATER_LAKE -> "Lake";
            case VALUE_WATER_RIVER -> "River";
            default -> "Water";
        };
        if (key == null) return fallback;
        String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.WATER, key);
        return loaded == null ? fallback : loaded;
    }

    public static int getWaterTypeColor(short value)
    {
        ResourceLocation key = waterKey(value);
        return key == null ? COLOR_WATER : getWaterColor(key, COLOR_WATER);
    }

    public static int getWaterColor(ResourceLocation category, int fallbackArgb)
    {
        return WorldPreview.get().biomeColorMap().getCategoricalColor(TFCColorPalettes.WATER, category, fallbackArgb);
    }

    public static String getWaterName(ResourceLocation category, String fallback)
    {
        String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.WATER, category);
        return loaded == null ? fallback : loaded;
    }

    public static boolean isTreeMapWaterBiome(@Nullable BiomeExtension biome)
    {
        if (biome == null)
        {
            return false;
        }

        BiomeBlendType blendType = biome.biomeBlendType();
        String path = biome.key().location().getPath();

        return blendType == BiomeBlendType.OCEAN
            || blendType == BiomeBlendType.LAKE
            || path.equals("river")
            || path.equals("lake")
            || path.endsWith("_lake")
            || path.equals("tower_karst_bay");
    }

    @Nullable
    public static TFCSampleUtils create(ChunkGenerator generator, RegistryAccess registryAccess, long seed)
    {
        if (generator instanceof ChunkGeneratorExtension ext
            && generator.getBiomeSource() instanceof BiomeSourceExtension biomeSource)
        {
            TFCTreeSpeciesRegistry registry = TFCTreeSpeciesRegistry.build(registryAccess);
            activeRegistry = registry;
            return new TFCSampleUtils(ext.settings(), ext.rockLayerSettings(), ext.chunkDataGenerator(), biomeSource, registry, seed);
        }
        return null;
    }

    public static short treeSpeciesId(ResourceLocation species)
    {
        return activeRegistry.idFor(species).orElse(VALUE_INVALID);
    }

    public static BiomeExtension getBiomeExtensionFromPoint(Region.Point point)
    {
        return TFCLayers.getFromLayerId(point.biome);
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

    public static int getRockTypeCategory(int pointRock)
    {
        return pointRock & 0b11;
    }

    public static String getRockTypeName(int rockType)
    {
        if (rockType < 0 || rockType >= ROCK_TYPE_KEYS.length) return "Unknown";

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        ResourceLocation key = ResourceLocation.parse(ROCK_TYPE_KEYS[rockType]);
        String name = mappingData.getCategoricalName(TFCColorPalettes.ROCK_TYPES, key);
        return name == null ? ROCK_TYPE_NAMES[rockType] : name;
    }

    public static int getRockTypeColor(int rockType)
    {
        if (rockType < 0 || rockType >= ROCK_TYPE_COLORS.length) return 0xFF888888;

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        return mappingData.getCategoricalColor(
            TFCColorPalettes.ROCK_TYPES,
            ResourceLocation.parse(ROCK_TYPE_KEYS[rockType]),
            ROCK_TYPE_COLORS[rockType]
        );
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

        ResourceLocation key = getRockKey(rockId);
        if (key != null)
        {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            String name = mappingData.getCategoricalName(TFCColorPalettes.ROCKS, key);
            if (name != null)
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

        ResourceLocation key = getRockKey(rockId);
        if (key != null)
        {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            return mappingData.getCategoricalColor(TFCColorPalettes.ROCKS, key, ROCK_COLORS[rockId]);
        }
        return ROCK_COLORS[rockId];
    }

    public static boolean forestTypeHasVegetation(ForestType type)
    {
        return type != ForestType.GRASSLAND && type != ForestType.CLEARING;
    }

    public static int treeSpeciesCount()
    {
        return activeRegistry.size();
    }

    public static int getTreeSpeciesColor(short treeId)
    {
        return activeRegistry.color(treeId);
    }

    public static String getTreeSpeciesName(short treeId)
    {
        return activeRegistry.name(treeId);
    }

    public static String getForestTypeName(short forestId)
    {
        if (forestId < 0 || forestId >= ForestType.values().length)
        {
            return "Unknown";
        }

        ForestType type = ForestType.valueOf(forestId);
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("tfc", type.getSerializedName());
        String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.FOREST_TYPES, key);
        if (loaded != null) return loaded;
        return getString(type.getSerializedName().replace('_', ' '));
    }

    public static int getForestTypeColor(short forestId)
    {
        if (forestId < 0 || forestId >= ForestType.values().length)
        {
            return COLOR_INVALID;
        }

        ForestType type = ForestType.valueOf(forestId);
        return WorldPreview.get().biomeColorMap().getCategoricalColor(
            TFCColorPalettes.FOREST_TYPES,
            ResourceLocation.fromNamespaceAndPath("tfc", type.getSerializedName()),
            forestColor(type)
        );
    }

    public static int forestTypeCount()
    {
        return ForestType.values().length;
    }

    public static String getForestDensityLabel(short forestId)
    {
        if (forestId < 0 || forestId >= ForestType.values().length) return null;
        ForestType type = ForestType.valueOf(forestId);
        if (type.isPrimary()) return "Dense Forest";
        if (type.isSecondary()) return "Closed Forest";
        if (type.isEdge()) return "Open Forest";
        if (type.isSavanna()) return "Savanna";
        if (type.isDead()) return "Dead Forest";
        return switch (type.getSerializedName())
        {
            case "shrubland" -> "Shrubs Only";
            case "sparse" -> "Sparse Trees";
            default -> "No Trees"; // grassland, clearing
        };
    }

    public static int soilTypeCount()
    {
        return SOIL_NAMES.length;
    }

    public static boolean isSoilTypeValue(short value)
    {
        return value >= 0 && value < SOIL_NAMES.length;
    }

    public static String getSoilTypeName(short value)
    {
        if (isSoilTypeValue(value))
        {
            String loaded = WorldPreview.get().biomeColorMap().getCategoricalName(TFCColorPalettes.SOIL_TYPES, SOIL_KEYS[value]);
            return loaded == null ? SOIL_NAMES[value] : loaded;
        }
        if (isWaterValue(value))
        {
            return "Water";
        }
        return "Unknown / No Soil";
    }

    public static int getSoilTypeColor(short value)
    {
        if (isSoilTypeValue(value))
        {
            return WorldPreview.get().biomeColorMap().getCategoricalColor(TFCColorPalettes.SOIL_TYPES, SOIL_KEYS[value], SOIL_COLORS[value]);
        }
        if (isWaterValue(value))
        {
            return getWaterTypeColor(value);
        }
        return COLOR_INVALID;
    }

    public static short resolveSoilType(
        ChunkData chunkData,
        @Nullable BiomeExtension biome,
        ForestType forestType,
        BlockPos pos,
        int surfaceY,
        short waterValue
    )
    {
        if (isWaterValue(waterValue))
        {
            return waterValue;
        }

        if (biome == null)
        {
            return VALUE_INVALID;
        }
        String path = biome.key().location().getPath();
        if (isNoSoilBiome(path))
        {
            return VALUE_INVALID;
        }

        float groundwater = chunkData.getAverageGroundwater(pos);
        float seaLevelTemp = chunkData.getAverageSeaLevelTemp(pos);
        float rainVariance = chunkData.getRainVariance(pos);
        float adjustedTemp = EnvironmentHelpers.adjustAvgTempForElev(surfaceY, seaLevelTemp);

        if (isVolcanicSoilBiome(path))
        {
            return SOIL_ANDISOL;
        }

        if (isFluvisolBiome(path)
            || (groundwater > SOIL_WET_GROUNDWATER && Math.abs(rainVariance) > SOIL_FLUVISOL_RAIN_VARIANCE))
        {
            return SOIL_FLUVISOL;
        }

        if (groundwater < SOIL_DRY_GROUNDWATER
            || (adjustedTemp > SOIL_HOT_TEMP && groundwater < SOIL_MODERATE_GROUNDWATER))
        {
            return SOIL_ARIDISOL;
        }

        if (adjustedTemp > SOIL_HOT_TEMP && groundwater >= SOIL_WET_GROUNDWATER)
        {
            return SOIL_OXISOL;
        }

        if (adjustedTemp < SOIL_COOL_TEMP
            && isForested(forestType)
            && groundwater >= SOIL_MODERATE_GROUNDWATER)
        {
            return SOIL_PODZOL;
        }

        if (isGrasslandLike(forestType, path) && groundwater >= SOIL_DRY_GROUNDWATER)
        {
            return SOIL_MOLLISOL;
        }

        if (isForested(forestType))
        {
            return SOIL_ALFISOL;
        }

        return SOIL_ENTISOL;
    }

    @Nullable
    private static ResourceLocation getRockKey(short rockId)
    {
        if (rockId < 0 || rockId >= ROCK_NAMES.length) return null;
        return ResourceLocation.fromNamespaceAndPath("tfc", ROCK_NAMES[rockId]);
    }

    @Nullable
    private static ResourceLocation waterKey(short value)
    {
        return switch (value)
        {
            case VALUE_WATER_OCEAN -> WATER_OCEAN;
            case VALUE_WATER_LAKE -> WATER_LAKE;
            case VALUE_WATER_RIVER -> WATER_RIVER;
            default -> null;
        };
    }

    private static ResourceLocation previewId(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("world_preview_tfc", path);
    }

    private static int forestColor(ForestType type)
    {
        return switch (type)
        {
            // Open / no forest
            case GRASSLAND -> 0xFFD6E38A;
            case CLEARING -> 0xFFE4EDAA;

            // Low woody vegetation
            case SHRUBLAND -> 0xFFB6C46E;
            case SPARSE -> 0xFF95B45F;

            // Savanna palette
            case SAVANNA_MONOCULTURE -> 0xFFD3BE63;
            case SAVANNA_DIVERSE -> 0xFFC3AF54;
            case SAVANNA_ALTERNATE -> 0xFFB69F48;
            case SAVANNA_SHRUB_MONOCULTURE -> 0xFFAB9440;
            case SAVANNA_SHRUB_DIVERSE -> 0xFF9D883A;
            case SAVANNA_SHRUB_ALTERNATE -> 0xFF8F7B34;

            // Forest edge palette
            case EDGE_MONOCULTURE -> 0xFF8AC75B;
            case EDGE_DIVERSE -> 0xFF74B74D;
            case EDGE_ALTERNATE -> 0xFF63A844;
            case EDGE_BAMBOO -> 0xFF7BD06A;

            // Secondary forest palette
            case SECONDARY_MONOCULTURE -> 0xFF4EA24B;
            case SECONDARY_MONOCULTURE_TALL -> 0xFF449846;
            case SECONDARY_DIVERSE -> 0xFF3E8E41;
            case SECONDARY_DIVERSE_TALL -> 0xFF367F3A;
            case SECONDARY_ALTERNATE -> 0xFF2F7335;
            case SECONDARY_DENSE -> 0xFF28682F;
            case SECONDARY_DENSE_TALL -> 0xFF215B2A;
            case SECONDARY_BAMBOO -> 0xFF58B75D;

            // Primary forest palette
            case PRIMARY_MONOCULTURE -> 0xFF245F3B;
            case PRIMARY_DIVERSE -> 0xFF1A5234;
            case PRIMARY_ALTERNATE -> 0xFF12462C;

            // Dead forest palette
            case DEAD_MONOCULTURE -> 0xFF8A7965;
            case DEAD_DIVERSE -> 0xFF776857;
            case DEAD_ALTERNATE -> 0xFF65574A;
            case DEAD_BAMBOO -> 0xFF7A7561;
            default -> stableCategoryColor(ResourceLocation.fromNamespaceAndPath("tfc", type.getSerializedName()));
        };
    }

    private static int stableCategoryColor(ResourceLocation id)
    {
        return STABLE_CATEGORY_COLORS[Math.floorMod(id.toString().hashCode(), STABLE_CATEGORY_COLORS.length)];
    }

    private static boolean isForested(ForestType forestType)
    {
        return forestType != null && forestTypeHasVegetation(forestType);
    }

    private static boolean isGrasslandLike(ForestType forestType, String biomePath)
    {
        if (forestType != null && !forestTypeHasVegetation(forestType))
        {
            return true;
        }
        if (biomePath == null)
        {
            return false;
        }
        return biomePath.contains("plains")
            || biomePath.contains("grass")
            || biomePath.contains("meadow")
            || biomePath.contains("prairie")
            || biomePath.contains("steppe");
    }

    private static boolean isVolcanicSoilBiome(String path)
    {
        if (path == null)
        {
            return false;
        }
        return path.contains("volcanic")
            || path.contains("volcano")
            || path.contains("shield_volcano")
            || path.contains("tuyas");
    }

    private static boolean isFluvisolBiome(String path)
    {
        if (path == null)
        {
            return false;
        }
        return path.equals("river")
            || path.equals("lake")
            || path.endsWith("_lake")
            || path.equals("tower_karst_bay")
            || path.contains("flats");
    }

    private static boolean isNoSoilBiome(String path)
    {
        if (path == null)
        {
            return false;
        }
        return path.contains("ice")
            || path.contains("glacier")
            || path.contains("bare_rock");
    }

    private final RegionGenerator regionGenerator;
    private final Settings settings;
    private final RockLayerSettings rockLayerSettings;
    private final ChunkDataGenerator chunkDataGenerator;
    private final TFCTreeSpeciesRegistry treeSpeciesRegistry;
    private final ConcurrentArea<BiomeExtension> biomeLayer;
    private final BiomeSourceExtension biomeSource;
    private final TFCPreviewClimateSampler climateSampler;

    private TFCSampleUtils(Settings settings, RockLayerSettings rockLayerSettings, ChunkDataGenerator chunkDataGenerator, BiomeSourceExtension biomeSource, TFCTreeSpeciesRegistry treeSpeciesRegistry, long seed)
    {
        this.settings = settings;
        this.rockLayerSettings = rockLayerSettings;
        this.biomeSource = biomeSource;
        this.treeSpeciesRegistry = treeSpeciesRegistry;
        Seed tfcSeed = Seed.of(seed);
        this.regionGenerator = new RegionGenerator(settings, tfcSeed);

        AreaFactory biomeFactory = TFCLayers.createRegionBiomeLayer(this.regionGenerator, tfcSeed);
        this.biomeLayer = new ConcurrentArea<>(biomeFactory, TFCLayers::getFromLayerId);
        this.chunkDataGenerator = chunkDataGenerator;
        this.climateSampler = new TFCPreviewClimateSampler(seed, settings.temperatureScale());
    }

    public TFCPreviewClimateSampler climateSampler()
    {
        return this.climateSampler;
    }

    public TFCTreeSpeciesRegistry treeSpeciesRegistry()
    {
        return this.treeSpeciesRegistry;
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

    public @Nullable BiomeExtension sampleBiomeExtension(int blockX, int blockZ)
    {
        return sampleBiomeExtensionQuart(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockZ));
    }

    public @Nullable BiomeExtension sampleBiomeExtensionQuart(int quartX, int quartZ)
    {
        return this.biomeSource.getBiomeExtension(quartX, quartZ);
    }

    public Region.Point samplePoint(int blockX, int blockZ)
    {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        return regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
    }

    public RockSettings sampleRockAtLayer(int pointRock, int layer)
    {
        return rockLayerSettings.sampleAtLayer(pointRock, layer);
    }

    public ChunkData sampleChunkData(ChunkPos chunkPos)
    {
        ChunkData data = new ChunkData(this.chunkDataGenerator, chunkPos);
        return this.chunkDataGenerator.generate(data);
    }

}
