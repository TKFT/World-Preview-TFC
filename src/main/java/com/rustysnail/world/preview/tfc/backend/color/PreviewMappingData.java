package com.rustysnail.world.preview.tfc.backend.color;

import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class PreviewMappingData
{
    private final Map<String, ColorEntry> resourceOnlyColorMappingData = new HashMap<>();
    private final Map<String, ColorEntry> colorMappingData = new HashMap<>();
    private final Map<String, StructureEntry> structMappingData = new HashMap<>();
    private final Map<String, RockColorEntry> rockColorData = new HashMap<>();
    private final Map<String, RockColorEntry> rockTypeColorData = new HashMap<>();
    private final List<PreviewData.HeightmapPresetData> heightmapPresets = new ArrayList<>();
    private final List<ColorMap> colorMaps = new ArrayList<>();
    private static final MessageDigest sha1;

    void clearBiomes()
    {
        this.colorMappingData.clear();
        this.resourceOnlyColorMappingData.clear();
    }

    void clearStructures()
    {
        this.structMappingData.clear();
    }

    void clearColorMappings()
    {
        this.colorMaps.clear();
    }

    void clearHeightmapPresets()
    {
        this.heightmapPresets.clear();
    }

    void clearRockColors()
    {
        this.rockColorData.clear();
    }

    void clearRockTypeColors()
    {
        this.rockTypeColorData.clear();
    }

    public void updateRockColors(Map<ResourceLocation, RockColorEntry> newData)
    {
        this.rockColorData.putAll(newData.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toString(), Entry::getValue)));
    }

    public void updateRockTypeColors(Map<ResourceLocation, RockColorEntry> newData)
    {
        this.rockTypeColorData.putAll(newData.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toString(), Entry::getValue)));
    }

    public int getRockColor(String rockKey)
    {
        RockColorEntry entry = this.rockColorData.get(rockKey);
        return entry != null ? entry.color : -1;
    }

    public int getRockTypeColor(String rockTypeKey)
    {
        RockColorEntry entry = this.rockTypeColorData.get(rockTypeKey);
        return entry != null ? entry.color : -1;
    }

    public String getRockName(String rockKey)
    {
        RockColorEntry entry = this.rockColorData.get(rockKey);
        return entry != null && entry.name != null ? entry.name : rockKey;
    }

    public String getRockTypeName(String rockTypeKey)
    {
        RockColorEntry entry = this.rockTypeColorData.get(rockTypeKey);
        return entry != null && entry.name != null ? entry.name : rockTypeKey;
    }

    public int getBiomeColor(String biomeKey)
    {
        ColorEntry entry = this.colorMappingData.get(biomeKey);
        return entry != null ? entry.color : -1;
    }

    public void makeBiomeResourceOnlyBackup()
    {
        this.resourceOnlyColorMappingData.putAll(this.colorMappingData);
    }

    public void update(Map<ResourceLocation, ColorEntry> newData)
    {
        this.colorMappingData.putAll(newData.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toString(), Entry::getValue)));
    }

    public void updateStruct(Map<ResourceLocation, StructureEntry> newData)
    {
        this.structMappingData.putAll(newData.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toString(), Entry::getValue)));
    }

    public void addHeightmapPreset(PreviewData.HeightmapPresetData presetData)
    {
        this.heightmapPresets.add(presetData);
    }

    public void addColormap(ColorMap colorMap)
    {
        this.colorMaps.add(colorMap);
    }

    public PreviewData generateMapData(
        Set<ResourceLocation> biomesSet,
        Set<ResourceLocation> caveBiomesSet,
        Set<ResourceLocation> structuresSet,
        Set<ResourceLocation> displayByDefaultStructuresSet
    )
    {
        List<String> biomes = biomesSet.stream().map(ResourceLocation::toString).sorted().toList();
        List<String> structures = structuresSet.stream().map(ResourceLocation::toString).sorted().toList();
        PreviewData res = new PreviewData(
            new PreviewData.BiomeData[biomes.size()],
            new PreviewData.StructureData[structures.size()],
            new Object2ShortOpenHashMap<>(),
            new Object2ShortOpenHashMap<>(),
            this.heightmapPresets,
            this.colorMaps.stream().collect(Collectors.toMap(x -> x.key().toString(), x -> x))
        );

        for (short id = 0; id < biomes.size(); id++)
        {
            String biome = biomes.get(id);
            res.biome2Id().put(biome, id);
            ColorEntry color = this.colorMappingData.get(biome);
            if (color == null)
            {
                color = new ColorEntry();
                color.dataSource = PreviewData.DataSource.MISSING;
                byte[] hash = sha1.digest(biome.getBytes(StandardCharsets.UTF_8));
                ByteBuffer byteBuffer = ByteBuffer.allocate(4);

                for (int i = 0; i < 4 && i < hash.length; i++)
                {
                    byteBuffer.put(hash[i]);
                }

                color.color = byteBuffer.getInt(0) & 16777215;
                color.name = null;
            }

            ColorEntry resourceOnlyColor = this.resourceOnlyColorMappingData.get(biome);
            if (resourceOnlyColor == null)
            {
                resourceOnlyColor = color;
            }

            ResourceLocation biomeRes = ResourceLocation.parse(biome);
            res.biomeId2BiomeData()[id] = new PreviewData.BiomeData(
                id,
                biomeRes,
                color.color,
                resourceOnlyColor.color,
                booleanOr(color.cave, caveBiomesSet.contains(biomeRes)),
                booleanOr(resourceOnlyColor.cave, caveBiomesSet.contains(biomeRes)),
                color.name,
                resourceOnlyColor.name,
                color.dataSource
            );
        }

        for (short id = 0; id < structures.size(); id++)
        {
            String structTag = structures.get(id);
            res.struct2Id().put(structTag, id);
            StructureEntry structure = this.structMappingData.get(structTag);
            if (structure == null)
            {
                structure = new StructureEntry();
                structure.dataSource = PreviewData.DataSource.MISSING;
                structure.texture = "world_preview_tfc:textures/structure/unknown.png";
                structure.name = structTag;
            }

            ResourceLocation structureRes = ResourceLocation.parse(structTag);
            res.structId2StructData()[id] = new PreviewData.StructureData(
                id,
                structureRes,
                structure.name,
                structure.texture == null ? null : ResourceLocation.parse(structure.texture),
                structure.item == null ? null : ResourceLocation.parse(structure.item),
                booleanOr(structure.showByDefault, displayByDefaultStructuresSet.contains(structureRes)),
                structure.dataSource
            );
        }

        return res;
    }

    static
    {
        try
        {
            sha1 = MessageDigest.getInstance("SHA1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static class ColorEntry
    {
        public PreviewData.DataSource dataSource;
        public int color;
        @Nullable
        public Boolean cave = null;
        public String name = null;

        public ColorEntry()
        {
        }

        public ColorEntry(PreviewData.DataSource dataSource, int color, boolean cave, String name)
        {
            this.dataSource = dataSource;
            this.color = color;
            this.cave = cave;
            this.name = name;
        }
    }

    public static class StructureEntry
    {
        public PreviewData.DataSource dataSource;
        public String name = null;
        public String texture = null;
        public String item = null;
        @Nullable
        public Boolean showByDefault = null;
    }

    public static class RockColorEntry
    {
        public int color;
        public String name = null;

        public RockColorEntry()
        {
        }
    }

    private static boolean booleanOr(@Nullable Boolean value, boolean fallback)
    {
        return value != null ? value : fallback;
    }
}
