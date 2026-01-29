package caeruleustait.world.preview.backend.color;

import caeruleustait.world.preview.WorldPreview;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public class BiomeColorMapReloadListener extends BaseMultiJsonResourceReloadListener {
   public BiomeColorMapReloadListener() {
      super("biome_colors.json");
   }

   protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearBiomes();
      WorldPreview.LOGGER.debug("Loading color resource entries");

      for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet()) {
         WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

         for (JsonElement j : entry.getValue()) {
            Map<ResourceLocation, PreviewMappingData.ColorEntry> curr = parseColorData(j, PreviewData.DataSource.RESOURCE);
            previewMappingData.update(curr);
         }
      }

      if (Files.exists(worldPreview.userColorConfigFile())) {
         previewMappingData.makeBiomeResourceOnlyBackup();
         WorldPreview.LOGGER.debug(" - loading entries from {}", worldPreview.userColorConfigFile());
         JsonElement el;

         try {
            el = JsonParser.parseString(Files.readString(worldPreview.userColorConfigFile()));
         } catch (IOException var11) {
            throw new RuntimeException(var11);
         }

         Map<ResourceLocation, PreviewMappingData.ColorEntry> curr = parseColorData(el, PreviewData.DataSource.CONFIG);
         previewMappingData.update(curr);
      }
   }

   public static Map<ResourceLocation, PreviewMappingData.ColorEntry> parseColorData(
      JsonElement jsonElement, PreviewData.DataSource dataSource
   ) {
      Map<ResourceLocation, PreviewMappingData.ColorEntry> res = new HashMap<>();
      JsonObject obj = jsonElement.getAsJsonObject();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         ResourceLocation location = ResourceLocation.parse(entry.getKey());
         PreviewMappingData.ColorEntry value = new PreviewMappingData.ColorEntry();
         JsonElement rawEl = entry.getValue();
         value.dataSource = dataSource;

         try {
            JsonObject raw = rawEl.getAsJsonObject();
            JsonElement nameEl = raw.get("name");
            JsonElement colorEl = raw.get("color");
            JsonElement rEl = raw.get("r");
            JsonElement gEl = raw.get("g");
            JsonElement bEl = raw.get("b");
            JsonElement caveEl = raw.get("cave");
            value.name = nameEl == null ? null : nameEl.getAsString();
            value.cave = caveEl == null ? Optional.empty() : Optional.of(caveEl.getAsBoolean());
            if (colorEl != null) {
               value.color = colorEl.getAsInt() & 16777215;
            } else {
               if (rEl == null || gEl == null || bEl == null) {
                  throw new IllegalStateException("No color was provided!");
               }

               int r = rEl.getAsInt() & 0xFF;
               int g = gEl.getAsInt() & 0xFF;
               int b = bEl.getAsInt() & 0xFF;
               value.color = r << 16 | g << 8 | b;
            }
         } catch (UnsupportedOperationException | NullPointerException | IllegalStateException var20) {
            WorldPreview.LOGGER.warn("   - {}: Invalid color entry format: {}", location, var20.getMessage());
            continue;
         }

         WorldPreview.LOGGER.debug("   - {}: {}", location, String.format("0x%06X", value.color & 16777215));
         res.put(location, value);
      }

      return res;
   }
}
