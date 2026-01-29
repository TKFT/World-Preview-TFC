package caeruleustait.world.preview.backend.worker.tfc;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewMappingData;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.RockSettings;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for sampling TerraFirmaCraft world generation data.
 * Wraps TFC's RegionGenerator to provide access to climate and terrain data.
 */
public class TFCSampleUtils {

    private final RegionGenerator regionGenerator;
    private final Settings settings;
    private final RockLayerSettings rockLayerSettings;

    /**
     * Creates TFCSampleUtils from a TFC-compatible chunk generator.
     *
     * @param generator The chunk generator (must implement ChunkGeneratorExtension)
     * @param seed The world seed
     * @return TFCSampleUtils instance, or null if generator is not TFC-compatible
     */
    @Nullable
    public static TFCSampleUtils create(ChunkGenerator generator, long seed) {
        if (generator instanceof ChunkGeneratorExtension ext) {
            return new TFCSampleUtils(ext.settings(), ext.rockLayerSettings(), seed);
        }
        return null;
    }

    /**
     * Checks if the given chunk generator is TFC-compatible.
     */
    public static boolean isTFCGenerator(ChunkGenerator generator) {
        return generator instanceof ChunkGeneratorExtension;
    }

    private TFCSampleUtils(Settings settings, RockLayerSettings rockLayerSettings, long seed) {
        this.settings = settings;
        this.rockLayerSettings = rockLayerSettings;
        this.regionGenerator = new RegionGenerator(settings, Seed.of(seed));
    }

    /**
     * @return The TFC world generation settings.
     */
    public Settings settings() {
        return settings;
    }

    /**
     * @return The region generator for accessing climate/terrain data.
     */
    public RegionGenerator regionGenerator() {
        return regionGenerator;
    }

    /**
     * Samples the Region.Point at the given block coordinates.
     *
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return The Region.Point containing climate and terrain data
     */
    public Region.Point samplePoint(int blockX, int blockZ) {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        return regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
    }

    /**
     * Gets the temperature at the given block coordinates.
     * Temperature range is approximately -23°C to 33°C.
     *
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Temperature in degrees Celsius
     */
    public float getTemperature(int blockX, int blockZ) {
        return samplePoint(blockX, blockZ).temperature;
    }

    /**
     * Gets the rainfall at the given block coordinates.
     * Rainfall range is approximately 0 to 500 mm.
     *
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Rainfall in millimeters
     */
    public float getRainfall(int blockX, int blockZ) {
        return samplePoint(blockX, blockZ).rainfall;
    }

    /**
     * Normalizes temperature from TFC range (-23 to 33) to a short value for storage.
     * Maps to range suitable for PreviewStorage.
     */
    /*public static short normalizeTemperature(float temperature) {
        // Map -23 to 33 (56 degree range) to 0-65535
        float normalized = (temperature + 23f) / 56f;
        return (short) Math.clamp((int) (normalized * 65535f) - 32768, Short.MIN_VALUE, Short.MAX_VALUE);
    }*/
    public static short normalizeTemperature(float temperature) {
        // Map -23..33 to 0..1
        float normalized = (temperature + 23f) / 56f;
        normalized = Math.clamp(normalized, 0f, 1f);

        // Map to 0..65534 (NOT 65535) so we never hit -32768 after shifting
        int scaled = Math.round(normalized * 65534f); // 0..65534
        return (short) (scaled - 32767);              // -32767..32767 (Short.MIN_VALUE is unused)
    }

    public static float denormalizeTemperature(short stored) {
        // stored is -32767..32767
        float normalized = (stored + 32767f) / 65534f; // 0..1
        return normalized * 56f - 23f;
    }

    /**
     * Normalizes rainfall from TFC range (0 to 500) to a short value for storage.
     */
    public static short normalizeRainfall(float rainfall) {
        // Map 0-500 to 0-32767 (positive short range)
        float normalized = Math.clamp(rainfall / 500f, 0f, 1f);
        return (short) (normalized * 32767f);
    }

    /**
     * Denormalizes temperature from storage format back to degrees Celsius.
     */
    /*public static float denormalizeTemperature(short stored) {
        // Map -32768 to 32767 back to -23 to 33
        float normalized = (stored + 32768f) / 65535f;
        return normalized * 56f - 23f;
    }*/

    /**
     * Denormalizes rainfall from storage format back to millimeters.
     */
    public static float denormalizeRainfall(short stored) {
        // Map 0-32767 back to 0-500
        return (stored / 32767f) * 500f;
    }

    /**
     * @return The rock layer settings for sampling rock types.
     */
    public RockLayerSettings rockLayerSettings() {
        return rockLayerSettings;
    }

    /**
     * Gets the rock type at a specific layer for the given point.
     *
     * @param pointRock The rock value from Region.Point
     * @param layer 0=top/surface, 1=middle, 2=bottom
     * @return The RockSettings for that layer
     */
    public RockSettings sampleRockAtLayer(int pointRock, int layer) {
        return rockLayerSettings.sampleAtLayer(pointRock, layer);
    }

    /**
     * Gets the rock type category from a point's rock value.
     * @param pointRock The rock value from Region.Point
     * @return 0=Ocean, 1=Volcanic, 2=Land, 3=Uplift
     */
    public static int getRockTypeCategory(int pointRock) {
        return pointRock & 0b11;
    }

    // Rock type category constants
    public static final int ROCK_TYPE_OCEAN = 0;
    public static final int ROCK_TYPE_VOLCANIC = 1;
    public static final int ROCK_TYPE_LAND = 2;
    public static final int ROCK_TYPE_UPLIFT = 3;

    // Rock type category names
    public static final String[] ROCK_TYPE_NAMES = {
        "Oceanic", "Volcanic", "Land", "Uplift"
    };

    // Rock type resource location keys for color lookup
    public static final String[] ROCK_TYPE_KEYS = {
        "tfc:ocean", "tfc:volcanic", "tfc:land", "tfc:uplift"
    };

    // Rock type category colors (ARGB format) - fallback values
    public static final int[] ROCK_TYPE_COLORS = {
        0xFF2244AA,  // Ocean - deep blue
        0xFFCC4400,  // Volcanic - orange-red
        0xFF44AA44,  // Land - green
        0xFF8866AA   // Uplift - purple
    };

    /**
     * Gets the display name of a rock type category.
     * Checks loaded color data first, falls back to built-in names.
     */
    public static String getRockTypeName(int rockType) {
        if (rockType < 0 || rockType >= ROCK_TYPE_KEYS.length) return "Unknown";

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        String name = mappingData.getRockTypeName(ROCK_TYPE_KEYS[rockType]);
        if (name != null && !name.equals(ROCK_TYPE_KEYS[rockType])) {
            return name;
        }
        return ROCK_TYPE_NAMES[rockType];
    }

    /**
     * Gets the color for a rock type category (ARGB format).
     * Checks loaded color data first, falls back to built-in colors.
     */
    public static int getRockTypeColor(int rockType) {
        if (rockType < 0 || rockType >= ROCK_TYPE_COLORS.length) return 0xFF888888;

        PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
        int loadedColor = mappingData.getRockTypeColor(ROCK_TYPE_KEYS[rockType]);
        if (loadedColor != -1) {
            // Convert RGB to ARGB
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

    // Rock colors (ARGB format) - fallback values
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

    /**
     * Gets the resource location key for a rock by ID.
     */
    private static String getRockKey(short rockId) {
        if (rockId < 0 || rockId >= ROCK_NAMES.length) return null;
        return "tfc:" + ROCK_NAMES[rockId];
    }

    /**
     * Gets a rock ID (0-19) from RockSettings by matching the raw block name.
     * Returns -1 if unknown.
     */
    public static short getRockId(RockSettings rock) {
        if (rock == null || rock.raw() == null) return -1;

        String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(rock.raw())
            .getPath()
            .replace("rock/raw/", "");

        for (int i = 0; i < ROCK_NAMES.length; i++) {
            if (blockName.contains(ROCK_NAMES[i])) {
                return (short) i;
            }
        }
        return -1; // Unknown rock
    }

    /**
     * Gets the display name of a rock by ID.
     * Checks loaded color data first, falls back to built-in names.
     */
    public static String getRockName(short rockId) {
        if (rockId < 0 || rockId >= ROCK_NAMES.length) return "Unknown";

        String key = getRockKey(rockId);
        if (key != null) {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            String name = mappingData.getRockName(key);
            if (name != null && !name.equals(key)) {
                return name;
            }
        }
        String name = ROCK_NAMES[rockId];
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Gets the color for a rock by ID (ARGB format).
     * Checks loaded color data first, falls back to built-in colors.
     */
    public static int getRockColor(short rockId) {
        if (rockId < 0 || rockId >= ROCK_COLORS.length) return 0xFF888888;

        String key = getRockKey(rockId);
        if (key != null) {
            PreviewMappingData mappingData = WorldPreview.get().biomeColorMap();
            int loadedColor = mappingData.getRockColor(key);
            if (loadedColor != -1) {
                // Convert RGB to ARGB
                return 0xFF000000 | loadedColor;
            }
        }
        return ROCK_COLORS[rockId];
    }
}
