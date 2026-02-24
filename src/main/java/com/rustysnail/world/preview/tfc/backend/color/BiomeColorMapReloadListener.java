package com.rustysnail.world.preview.tfc.backend.color;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public class BiomeColorMapReloadListener extends BaseMultiJsonResourceReloadListener
{
    public BiomeColorMapReloadListener()
    {
        super("biome_colors.json");
    }

    protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler)
    {
        WorldPreview worldPreview = WorldPreview.get();
        PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
        previewMappingData.clearBiomes();
        WorldPreview.LOGGER.debug("Loading color resource entries");

        for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet())
        {
            WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

            for (JsonElement j : entry.getValue())
            {
                Map<ResourceLocation, PreviewMappingData.ColorEntry> curr = parseColorData(j, PreviewData.DataSource.RESOURCE);
                previewMappingData.update(curr);
            }
        }

        if (Files.exists(worldPreview.userColorConfigFile()))
        {
            previewMappingData.makeBiomeResourceOnlyBackup();
            WorldPreview.LOGGER.debug(" - loading entries from {}", worldPreview.userColorConfigFile());
            JsonElement el;

            try
            {
                el = JsonParser.parseString(Files.readString(worldPreview.userColorConfigFile()));
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }

            Map<ResourceLocation, PreviewMappingData.ColorEntry> curr = parseColorData(el, PreviewData.DataSource.CONFIG);
            previewMappingData.update(curr);
        }
    }

    public static Map<ResourceLocation, PreviewMappingData.ColorEntry> parseColorData(
        JsonElement jsonElement, PreviewData.DataSource dataSource
    )
    {
        Map<ResourceLocation, PreviewMappingData.ColorEntry> res = new HashMap<>();
        JsonObject obj = jsonElement.getAsJsonObject();

        for (Entry<String, JsonElement> entry : obj.entrySet())
        {
            ResourceLocation location = ResourceLocation.parse(entry.getKey());
            PreviewMappingData.ColorEntry value = new PreviewMappingData.ColorEntry();
            JsonElement rawEl = entry.getValue();
            value.dataSource = dataSource;

            try
            {
                JsonObject raw = rawEl.getAsJsonObject();
                JsonElement caveEl = raw.get("cave");
                value.name = ColorJsonParsingHelper.parseOptionalName(raw);
                value.cave = caveEl == null ? null : caveEl.getAsBoolean();
                value.color = ColorJsonParsingHelper.parsePackedRgbColor(raw);
            }
            catch (UnsupportedOperationException | NullPointerException | IllegalStateException e)
            {
                WorldPreview.LOGGER.warn("   - {}: Invalid color entry format: {}", location, e.getMessage());
                continue;
            }

            WorldPreview.LOGGER.debug("   - {}: {}", location, String.format("0x%06X", value.color & 0xFFFFFF));
            res.put(location, value);
        }

        return res;
    }
}
