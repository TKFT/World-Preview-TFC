package com.rustysnail.world.preview.tfc.backend.color;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import com.rustysnail.world.preview.tfc.WorldPreview;
import java.util.stream.Collectors;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class PreviewMappingData
{
    private static final MessageDigest sha1;

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

    private static boolean booleanOr(@Nullable Boolean value, boolean fallback)
    {
        return value != null ? value : fallback;
    }

    private final Map<String, ColorEntry> resourceOnlyColorMappingData = new HashMap<>();
    private final Map<String, ColorEntry> colorMappingData = new HashMap<>();
    private final Map<String, StructureEntry> structMappingData = new HashMap<>();
    private final Map<String, RockColorEntry> rockColorData = new HashMap<>();
    private final Map<String, RockColorEntry> rockTypeColorData = new HashMap<>();
    private final List<PreviewData.HeightmapPresetData> heightmapPresets = new ArrayList<>();
    private volatile Map<ResourceLocation, ColorMap> colorMaps = Map.of();
    private volatile Map<ResourceLocation, CategoricalColorPalette> categoricalPalettes = Map.of();
    private final AtomicLong paletteRevision = new AtomicLong();
    private final Set<String> warnedMissingColorMaps = ConcurrentHashMap.newKeySet();

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
        Map<ResourceLocation, ColorMap> updated = new HashMap<>(this.colorMaps);
        updated.put(colorMap.key(), colorMap);
        this.colorMaps = Map.copyOf(updated);
    }

    public void replaceColorMaps(Map<ResourceLocation, ColorMap> colorMaps)
    {
        this.colorMaps = Map.copyOf(colorMaps);
        this.paletteRevision.incrementAndGet();
    }

    public void replaceCategoricalPalettes(Map<ResourceLocation, CategoricalColorPalette> palettes)
    {
        this.categoricalPalettes = Map.copyOf(palettes);
        this.paletteRevision.incrementAndGet();
    }

    public long paletteRevision()
    {
        return this.paletteRevision.get();
    }

    @Nullable
    public ColorMap getColorMap(ResourceLocation id)
    {
        return this.colorMaps.get(id);
    }

    @Nullable
    public ColorMap resolveColorMap(String configuredId, ResourceLocation fallbackId, String purpose)
    {
        ResourceLocation configured = ResourceLocation.tryParse(configuredId);
        ColorMap selected = configured == null ? null : this.colorMaps.get(configured);
        if (selected != null)
        {
            return selected;
        }

        String warningKey = purpose + '|' + configuredId;
        if (this.warnedMissingColorMaps.add(warningKey))
        {
            WorldPreview.LOGGER.warn("Configured {} colormap '{}' is invalid or unavailable; using {}",
                purpose, configuredId, fallbackId);
        }
        ColorMap fallback = this.colorMaps.get(fallbackId);
        if (fallback == null && this.warnedMissingColorMaps.add(purpose + "|missing-default|" + fallbackId))
        {
            WorldPreview.LOGGER.warn("Default {} colormap {} is unavailable; using black", purpose, fallbackId);
        }
        return fallback;
    }

    @Nullable
    public CategoricalColorPalette getCategoricalPalette(ResourceLocation id)
    {
        return this.categoricalPalettes.get(id);
    }

    public int getCategoricalColor(ResourceLocation paletteId, ResourceLocation valueId, int fallbackArgb)
    {
        CategoricalColorPalette palette = this.categoricalPalettes.get(paletteId);
        CategoricalColorPalette.Entry entry = palette == null ? null : palette.get(valueId);
        return entry == null ? 0xFF000000 | (fallbackArgb & 0xFFFFFF) : entry.argb();
    }

    @Nullable
    public String getCategoricalName(ResourceLocation paletteId, ResourceLocation valueId)
    {
        CategoricalColorPalette palette = this.categoricalPalettes.get(paletteId);
        CategoricalColorPalette.Entry entry = palette == null ? null : palette.get(valueId);
        return entry == null ? null : entry.name();
    }

    public String getCategoricalName(ResourceLocation paletteId, ResourceLocation valueId, String fallbackName)
    {
        String name = this.getCategoricalName(paletteId, valueId);
        return name == null ? fallbackName : name;
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
            this.colorMaps.values().stream().collect(Collectors.toMap(x -> x.key().toString(), x -> x))
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
                booleanOr(null, displayByDefaultStructuresSet.contains(structureRes)),
                structure.dataSource
            );
        }

        return res;
    }

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
        this.colorMaps = Map.of();
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
    }

    public static class RockColorEntry
    {
        public int color;
        public String name = null;

        public RockColorEntry()
        {
        }
    }
}
