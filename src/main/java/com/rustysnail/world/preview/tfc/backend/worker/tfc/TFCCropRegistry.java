package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.dries007.tfc.common.blocks.crop.FloodedCropBlock;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.util.climate.ClimateRange;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime registry of cultivable TFC crops, discovered from the block registry (any block
 * implementing {@link ICropBlock}) so TFC addon crops participate automatically without hardcoding.
 * Dead / wild crop blocks do not implement {@link ICropBlock} and are therefore excluded; each crop
 * is a single block (double crops are one block with a HALF state), so there are no upper/lower
 * duplicates to dedupe.
 *
 * <p>{@link ClimateRange} data lives in TFC's {@code climate_range} DataManager, which is not
 * populated on the world-creation screen. We therefore load the ranges directly from the datapack
 * {@link ResourceManager} (files under {@code data/&lt;ns&gt;/tfc/climate_range/crop/*.json}), keyed by
 * the matching crop-block id. A crop whose climate range cannot be found is retained with a null
 * range and rendered as "No Data" - never guessed.
 */
public final class TFCCropRegistry
{
    /** One cultivable crop. {@code climateRange == null} means the range could not be loaded (No Data). */
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

    private final List<Entry> entries;
    private final Map<ResourceLocation, Entry> byId;

    private static volatile TFCCropRegistry active = new TFCCropRegistry(List.of());

    private TFCCropRegistry(List<Entry> entries)
    {
        this.entries = List.copyOf(entries);
        this.byId = new HashMap<>();
        for (Entry e : entries)
        {
            this.byId.put(e.id(), e);
        }
    }

    public static TFCCropRegistry active()
    {
        return active;
    }

    public static void setActive(TFCCropRegistry registry)
    {
        active = registry;
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
        return id == null ? null : this.byId.get(id);
    }

    /** First crop by id order (alphabetical), used as the default selection. Null if none. */
    @Nullable
    public Entry first()
    {
        return this.entries.isEmpty() ? null : this.entries.get(0);
    }

    public static TFCCropRegistry build(ResourceManager resourceManager)
    {
        Map<ResourceLocation, ClimateRange> climateById = loadClimateRanges(resourceManager);

        List<Entry> found = new ArrayList<>();
        boolean loggedMissing = false;

        for (Block block : BuiltInRegistries.BLOCK)
        {
            if (!(block instanceof ICropBlock crop))
            {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            boolean flooded = block instanceof FloodedCropBlock;

            ClimateRange range = resolveClimateRange(crop, id, climateById);
            if (range == null && !loggedMissing)
            {
                loggedMissing = true;
                WorldPreview.LOGGER.warn("[TFC Crop] No climate_range data for crop {} (shown as No Data); further misses suppressed", id);
            }

            found.add(new Entry(
                id, crop, range,
                crop.getNForGrowth(), crop.getPForGrowth(), crop.getKForGrowth(),
                flooded, deriveName(id)
            ));
        }

        found.sort(Comparator.comparing(e -> e.id().toString()));
        WorldPreview.LOGGER.info("[TFC Crop] Discovered {} cultivable crops ({} with climate data)",
            found.size(), found.stream().filter(Entry::hasClimateData).count());
        return new TFCCropRegistry(found);
    }

    /** Fast path: the block's own range if the DataManager happens to be loaded; else the resource map. */
    @Nullable
    private static ClimateRange resolveClimateRange(ICropBlock crop, ResourceLocation id, Map<ResourceLocation, ClimateRange> climateById)
    {
        try
        {
            ClimateRange range = crop.getClimateRange();
            if (range != null)
            {
                return range;
            }
        }
        catch (Throwable ignored)
        {
            // DataManager not loaded during preview - fall back to the resource-loaded map.
        }
        return climateById.get(id);
    }

    /**
     * Scans {@code tfc/climate_range/crop/*.json} across all datapacks. The DataManager id for a file
     * {@code ns:tfc/climate_range/crop/wheat.json} is {@code ns:crop/wheat}, which matches the crop
     * block id, so entries are keyed by that block-id-shaped ResourceLocation.
     */
    private static Map<ResourceLocation, ClimateRange> loadClimateRanges(ResourceManager rm)
    {
        Map<ResourceLocation, ClimateRange> out = new HashMap<>();
        final String dir = "tfc/climate_range/crop";
        Map<ResourceLocation, Resource> files = rm.listResources(dir, rl -> rl.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet())
        {
            ResourceLocation fileId = e.getKey();
            String path = fileId.getPath(); // tfc/climate_range/crop/wheat.json
            String rel = path.substring(dir.length() - "crop".length()); // crop/wheat.json
            rel = rel.substring(0, rel.length() - ".json".length());       // crop/wheat
            ResourceLocation cropId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), rel);
            try (var in = e.getValue().open())
            {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                out.put(cropId, parseClimateRange(obj));
            }
            catch (Exception ex)
            {
                WorldPreview.LOGGER.debug("[TFC Crop] Failed to parse climate_range {}: {}", fileId, ex.toString());
            }
        }
        return out;
    }

    /** Manual parse matching {@link ClimateRange#CODEC}'s optional fields + defaults (no RegistryOps needed). */
    private static ClimateRange parseClimateRange(JsonObject o)
    {
        int minH = o.has("min_hydration") ? o.get("min_hydration").getAsInt() : 0;
        int maxH = o.has("max_hydration") ? o.get("max_hydration").getAsInt() : 100;
        int hWiggle = o.has("hydration_wiggle_range") ? o.get("hydration_wiggle_range").getAsInt() : 0;
        float minT = o.has("min_temperature") ? o.get("min_temperature").getAsFloat() : Float.NEGATIVE_INFINITY;
        float maxT = o.has("max_temperature") ? o.get("max_temperature").getAsFloat() : Float.POSITIVE_INFINITY;
        float tWiggle = o.has("temperature_wiggle_range") ? o.get("temperature_wiggle_range").getAsFloat() : 0f;
        return new ClimateRange(minH, maxH, hWiggle, minT, maxT, tWiggle);
    }

    /** Title-cases the crop name from its registry path; non-TFC namespaces get a "[ns]" suffix. */
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
}
