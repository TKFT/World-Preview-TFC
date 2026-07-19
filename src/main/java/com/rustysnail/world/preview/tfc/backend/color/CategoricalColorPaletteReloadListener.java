package com.rustysnail.world.preview.tfc.backend.color;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.rustysnail.world.preview.tfc.WorldPreview;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads mergeable categorical palettes from data packs in normal pack priority order.
 */
public final class CategoricalColorPaletteReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, CategoricalColorPalette>>
{
    static final String DIRECTORY = "world_preview_tfc/colors";

    @Override
    protected Map<ResourceLocation, CategoricalColorPalette> prepare(ResourceManager resourceManager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, Map<ResourceLocation, CategoricalColorPalette.Entry>> merged = new HashMap<>();
        Map<String, Integer> packOrder = new HashMap<>();
        List<PackResources> packs = resourceManager.listPacks().toList();
        for (int i = 0; i < packs.size(); i++)
        {
            packOrder.put(packs.get(i).packId(), i);
        }

        loadLegacy(resourceManager, packOrder, merged, "rock_colors.json", TFCColorPalettes.ROCKS);
        loadLegacy(resourceManager, packOrder, merged, "rock_type_colors.json", TFCColorPalettes.ROCK_TYPES);

        List<Contribution> generic = new ArrayList<>();
        resourceManager.listResourceStacks(DIRECTORY, id -> id.getPath().endsWith(".json"))
            .forEach((id, stack) -> stack.forEach(resource -> generic.add(new Contribution(id, resource))));
        generic.sort(contributionOrder(packOrder));
        for (Contribution contribution : generic)
        {
            String path = contribution.id().getPath();
            String palettePath = path.substring(DIRECTORY.length() + 1, path.length() - ".json".length());
            ResourceLocation paletteId = ResourceLocation.fromNamespaceAndPath(contribution.id().getNamespace(), palettePath);
            readAndMerge(merged, paletteId, contribution);
        }

        Map<ResourceLocation, CategoricalColorPalette> result = new HashMap<>();
        merged.forEach((id, entries) -> result.put(id, new CategoricalColorPalette(entries)));
        return Map.copyOf(result);
    }

    private static void loadLegacy(
        ResourceManager resourceManager,
        Map<String, Integer> packOrder,
        Map<ResourceLocation, Map<ResourceLocation, CategoricalColorPalette.Entry>> merged,
        String filename,
        ResourceLocation paletteId
    )
    {
        List<ResourceLocation> locations = new ArrayList<>();
        for (String namespace : resourceManager.getNamespaces())
        {
            locations.add(ResourceLocation.fromNamespaceAndPath(namespace, filename));
            locations.add(ResourceLocation.fromNamespaceAndPath(namespace, "worldgen/" + filename));
        }
        locations.add(ResourceLocation.fromNamespaceAndPath("c", "worldgen/" + filename));
        locations = locations.stream().distinct().sorted(Comparator.comparing(ResourceLocation::toString)).toList();

        List<Contribution> legacy = new ArrayList<>();
        for (ResourceLocation location : locations)
        {
            resourceManager.getResourceStack(location).forEach(resource -> legacy.add(new Contribution(location, resource)));
        }
        legacy.sort(contributionOrder(packOrder));
        legacy.forEach(contribution -> readAndMerge(merged, paletteId, contribution));
    }

    private static Comparator<Contribution> contributionOrder(Map<String, Integer> packOrder)
    {
        return Comparator
            .comparingInt((Contribution value) -> packOrder.getOrDefault(value.resource().sourcePackId(), -1))
            .thenComparing(value -> value.id().toString());
    }

    private static void readAndMerge(
        Map<ResourceLocation, Map<ResourceLocation, CategoricalColorPalette.Entry>> merged,
        ResourceLocation paletteId,
        Contribution contribution
    )
    {
        try (Reader reader = contribution.resource().openAsReader())
        {
            JsonElement json = GsonHelper.fromJson(BaseMultiJsonResourceReloadListener.GSON, reader, JsonElement.class);
            mergeJson(merged.computeIfAbsent(paletteId, ignored -> new LinkedHashMap<>()), paletteId, json,
                contribution.id() + " from pack " + contribution.resource().sourcePackId());
        }
        catch (IOException | JsonParseException | IllegalStateException e)
        {
            WorldPreview.LOGGER.warn("Skipping categorical palette resource {} from pack {}: {}",
                contribution.id(), contribution.resource().sourcePackId(), e.getMessage());
        }
    }

    static void mergeJson(
        Map<ResourceLocation, CategoricalColorPalette.Entry> target,
        ResourceLocation paletteId,
        @Nullable JsonElement json,
        String source
    )
    {
        if (json == null || !json.isJsonObject())
        {
            throw new JsonParseException("palette root must be an object");
        }
        JsonObject object = json.getAsJsonObject();
        for (Entry<String, JsonElement> rawEntry : object.entrySet())
        {
            try
            {
                ResourceLocation valueId = ResourceLocation.parse(rawEntry.getKey());
                if (!rawEntry.getValue().isJsonObject())
                {
                    throw new JsonParseException("entry must be an object");
                }
                JsonObject raw = rawEntry.getValue().getAsJsonObject();
                CategoricalColorPalette.Entry parsed = new CategoricalColorPalette.Entry(
                    ColorJsonParsingHelper.parsePackedRgbColor(raw),
                    ColorJsonParsingHelper.parseOptionalName(raw)
                );
                CategoricalColorPalette.Entry previous = target.put(valueId, parsed);
                if (previous != null)
                {
                    WorldPreview.LOGGER.debug("Categorical palette {} entry {} overridden by {}", paletteId, valueId, source);
                }
            }
            catch (RuntimeException e)
            {
                WorldPreview.LOGGER.warn("Skipping malformed categorical palette entry {} in {} ({}): {}",
                    rawEntry.getKey(), paletteId, source, e.getMessage());
            }
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, CategoricalColorPalette> palettes, ResourceManager resourceManager, ProfilerFiller profiler)
    {
        WorldPreview.get().biomeColorMap().replaceCategoricalPalettes(palettes);
        int entries = palettes.values().stream().mapToInt(palette -> palette.entries().size()).sum();
        WorldPreview.LOGGER.info("Loaded {} categorical color palettes with {} entries", palettes.size(), entries);
    }

    private record Contribution(ResourceLocation id, Resource resource)
    {
    }
}
