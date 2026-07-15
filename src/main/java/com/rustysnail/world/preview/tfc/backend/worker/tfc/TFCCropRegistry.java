package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rustysnail.world.preview.tfc.WorldPreview;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.blocks.crop.FloodedCropBlock;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.common.items.PlantableInfo;
import net.dries007.tfc.util.climate.ClimateRange;

public final class TFCCropRegistry
{
    private static volatile TFCCropRegistry active = new TFCCropRegistry(List.of());

    public static TFCCropRegistry active()
    {
        return active;
    }

    public static void setActive(TFCCropRegistry registry)
    {
        active = registry;
    }

    public static TFCCropRegistry build(ResourceManager resourceManager)
    {
        Map<ResourceLocation, ClimateRange> climateById = loadClimateRanges(resourceManager);

        List<Entry> found = new ArrayList<>();
        List<ResourceLocation> unmatched = new ArrayList<>();
        int validClimateCount = 0;
        int addonCropCount = 0;

        for (Block block : BuiltInRegistries.BLOCK)
        {
            if (!(block instanceof ICropBlock crop))
            {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            boolean flooded = block instanceof FloodedCropBlock;
            if (!id.getNamespace().equals("tfc")) addonCropCount++;

            ClimateRange range = resolveClimateRange(crop, block, id, climateById);
            if (range != null) validClimateCount++;
            else unmatched.add(id);

            found.add(new Entry(
                id, crop, range,
                crop.getNForGrowth(), crop.getPForGrowth(), crop.getKForGrowth(),
                flooded, deriveName(id)
            ));
        }

        found.sort(Comparator.comparing(e -> e.id().toString()));
        unmatched.sort(Comparator.comparing(ResourceLocation::toString));
        WorldPreview.LOGGER.info(
            "[TFC Crop] Discovery summary: discovered={}, validClimate={}, addonCrops={}, unmatched={}",
            found.size(), validClimateCount, addonCropCount, unmatched);
        return new TFCCropRegistry(found);
    }

    @Nullable
    static ClimateRange resolveResourceClimate(ResourceLocation blockId, Map<ResourceLocation, ClimateRange> climateById)
    {
        ClimateRange exact = climateById.get(blockId);
        if (exact != null)
        {
            return exact;
        }

        String normalizedBlock = normalizeCropName(blockId.getPath());
        ClimateRange match = null;
        int matches = 0;
        for (Map.Entry<ResourceLocation, ClimateRange> candidate : climateById.entrySet())
        {
            ResourceLocation candidateId = candidate.getKey();
            if (candidateId.getNamespace().equals(blockId.getNamespace())
                && normalizeCropName(candidateId.getPath()).equals(normalizedBlock))
            {
                match = candidate.getValue();
                matches++;
            }
        }
        return matches == 1 ? match : null;
    }

    static String normalizeCropName(String path)
    {
        int slash = path.lastIndexOf('/');
        String name = (slash >= 0 ? path.substring(slash + 1) : path).toLowerCase(Locale.ROOT);
        if (name.startsWith("crops_")) name = name.substring("crops_".length());
        else if (name.startsWith("crop_")) name = name.substring("crop_".length());
        if (name.endsWith("_crops")) name = name.substring(0, name.length() - "_crops".length());
        else if (name.endsWith("_crop")) name = name.substring(0, name.length() - "_crop".length());
        return name;
    }

    @Nullable
    private static ClimateRange resolveClimateRange(
        ICropBlock crop,
        Block block,
        ResourceLocation id,
        Map<ResourceLocation, ClimateRange> climateById
    )
    {
        try
        {
            return crop.getClimateRange();
        }
        catch (Throwable ignored)
        {
            // DataManager not loaded during preview - fall back to the resource-loaded map.
        }

        ClimateRange resourceRange = resolveResourceClimate(id, climateById);
        if (resourceRange != null)
        {
            return resourceRange;
        }

        try
        {
            if (block.asItem() instanceof PlantableInfo plantable)
            {
                return plantable.getClimateRangeInfo();
            }
        }
        catch (Throwable ignored)
        {
            // The item's range may use the same unloaded DataManager reference as the crop block.
        }
        return null;
    }

    private static Map<ResourceLocation, ClimateRange> loadClimateRanges(ResourceManager rm)
    {
        Map<ResourceLocation, ClimateRange> out = new HashMap<>();
        final String dir = "tfc/climate_range/crop";
        final String dataPrefix = "tfc/climate_range/";
        Map<ResourceLocation, Resource> files = rm.listResources(dir, rl -> rl.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet())
        {
            ResourceLocation fileId = e.getKey();
            String path = fileId.getPath(); // tfc/climate_range/crop/wheat.json
            if (!path.startsWith(dataPrefix)) continue;
            String rel = path.substring(dataPrefix.length(), path.length() - ".json".length()); // crop/wheat
            ResourceLocation cropId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), rel);
            try (var in = e.getValue().open())
            {
                var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                ClimateRange.CODEC.parse(JsonOps.INSTANCE, json).result().ifPresent(range -> out.put(cropId, range));
            }
            catch (Exception ignored)
            {
                // Invalid resources remain unmatched and are reported once in the discovery summary.
            }
        }
        return out;
    }

    private static String deriveName(ResourceLocation id)
    {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;

        StringBuilder sb = new StringBuilder(name.length());
        boolean cap = true;
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (c == '_')
            {
                sb.append(' ');
                cap = true;
            }
            else if (cap)
            {
                sb.append(Character.toUpperCase(c));
                cap = false;
            }
            else
            {
                sb.append(c);
            }
        }

        String title = sb.toString();
        if (!id.getNamespace().equals("tfc"))
        {
            title = title + " [" + id.getNamespace() + "]";
        }
        return title;
    }

    private final List<Entry> entries;
    private final Map<ResourceLocation, Entry> byId;

    private TFCCropRegistry(List<Entry> entries)
    {
        this.entries = List.copyOf(entries);
        this.byId = new HashMap<>();
        for (Entry e : entries)
        {
            this.byId.put(e.id(), e);
        }
    }

    public List<Entry> entries()
    {
        return this.entries;
    }

    public int size()
    {
        return this.entries.size();
    }

    @Nullable
    public Entry get(ResourceLocation id)
    {
        return this.byId.get(id);
    }

    @Nullable
    public Entry first()
    {
        return this.entries.isEmpty() ? null : this.entries.getFirst();
    }

    public record Entry(
        ResourceLocation id,
        ICropBlock block,
        @Nullable ClimateRange climateRange,
        float nitrogen,
        float phosphorus,
        float potassium,
        boolean flooded,
        String displayName
    )
    {
        public boolean hasClimateData()
        {
            return climateRange != null;
        }
    }
}
