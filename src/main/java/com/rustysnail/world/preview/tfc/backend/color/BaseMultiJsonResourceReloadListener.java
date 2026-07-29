package com.rustysnail.world.preview.tfc.backend.color;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class BaseMultiJsonResourceReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, List<JsonElement>>>
{
    protected static final Gson GSON = new GsonBuilder().create();
    private final String filename;

    protected BaseMultiJsonResourceReloadListener(String filename)
    {
        this.filename = filename;
    }

    protected Map<ResourceLocation, List<JsonElement>> prepare(ResourceManager resourceManager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, List<JsonElement>> res = new LinkedHashMap<>();

        for (ResourceLocation location : resourceLocations(resourceManager.getNamespaces(), this.filename))
        {
            this.loadAllForLocation(resourceManager, res, location);
        }
        return res;
    }

    static List<ResourceLocation> resourceLocations(Set<String> namespaces, String filename)
    {
        List<ResourceLocation> locations = new ArrayList<>();
        ResourceLocation commonLocation = ResourceLocation.fromNamespaceAndPath("c", "worldgen/" + filename);
        locations.add(commonLocation);

        namespaces.stream().sorted(Comparator.naturalOrder()).forEach(namespace -> {
            ResourceLocation legacyLocation = ResourceLocation.fromNamespaceAndPath(namespace, filename);
            if (!legacyLocation.equals(commonLocation))
            {
                locations.add(legacyLocation);
            }

            ResourceLocation worldgenLocation = ResourceLocation.fromNamespaceAndPath(namespace, "worldgen/" + filename);
            if (!worldgenLocation.equals(commonLocation))
            {
                locations.add(worldgenLocation);
            }
        });
        return locations;
    }

    private void loadAllForLocation(ResourceManager resourceManager, Map<ResourceLocation, List<JsonElement>> res, ResourceLocation rl)
    {
        for (Resource x : resourceManager.getResourceStack(rl))
        {
            try (Reader reader = x.openAsReader())
            {
                List<JsonElement> jsonElements = res.computeIfAbsent(rl, z -> new ArrayList<>());
                jsonElements.add(GsonHelper.fromJson(GSON, reader, JsonElement.class));
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
