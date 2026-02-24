package com.rustysnail.world.preview.tfc.backend.color;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public class RockColorReloadListener extends BaseMultiJsonResourceReloadListener
{
    public RockColorReloadListener()
    {
        super("rock_colors.json");
    }

    protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler)
    {
        WorldPreview worldPreview = WorldPreview.get();
        PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
        previewMappingData.clearRockColors();
        WorldPreview.LOGGER.debug("Loading rock color entries");

        for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet())
        {
            WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

            for (JsonElement j : entry.getValue())
            {
                Map<ResourceLocation, PreviewMappingData.RockColorEntry> curr = parseRockColorData(j);
                previewMappingData.updateRockColors(curr);
            }
        }
    }

    public static Map<ResourceLocation, PreviewMappingData.RockColorEntry> parseRockColorData(JsonElement jsonElement)
    {
        Map<ResourceLocation, PreviewMappingData.RockColorEntry> res = new HashMap<>();
        JsonObject obj = jsonElement.getAsJsonObject();

        for (Entry<String, JsonElement> entry : obj.entrySet())
        {
            ResourceLocation location = ResourceLocation.parse(entry.getKey());
            PreviewMappingData.RockColorEntry value = new PreviewMappingData.RockColorEntry();
            JsonElement rawEl = entry.getValue();

            try
            {
                JsonObject raw = rawEl.getAsJsonObject();
                value.name = ColorJsonParsingHelper.parseOptionalName(raw);
                value.color = ColorJsonParsingHelper.parsePackedRgbColor(raw);
            }
            catch (UnsupportedOperationException | NullPointerException | IllegalStateException e)
            {
                WorldPreview.LOGGER.warn("   - {}: Invalid rock color entry format: {}", location, e.getMessage());
                continue;
            }

            WorldPreview.LOGGER.debug("   - {}: {}", location, String.format("0x%06X", value.color & 0xFFFFFF));
            res.put(location, value);
        }

        return res;
    }
}
