package caeruleustait.world.preview.backend.color;

import caeruleustait.world.preview.WorldPreview;
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

public class RockColorReloadListener extends BaseMultiJsonResourceReloadListener {
   public RockColorReloadListener() {
      super("rock_colors.json");
   }

   protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearRockColors();
      WorldPreview.LOGGER.debug("Loading rock color entries");

      for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet()) {
         WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

         for (JsonElement j : entry.getValue()) {
            Map<ResourceLocation, PreviewMappingData.RockColorEntry> curr = parseRockColorData(j);
            previewMappingData.updateRockColors(curr);
         }
      }
   }

   public static Map<ResourceLocation, PreviewMappingData.RockColorEntry> parseRockColorData(JsonElement jsonElement) {
      Map<ResourceLocation, PreviewMappingData.RockColorEntry> res = new HashMap<>();
      JsonObject obj = jsonElement.getAsJsonObject();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         ResourceLocation location = ResourceLocation.parse(entry.getKey());
         PreviewMappingData.RockColorEntry value = new PreviewMappingData.RockColorEntry();
         JsonElement rawEl = entry.getValue();

         try {
            JsonObject raw = rawEl.getAsJsonObject();
            JsonElement nameEl = raw.get("name");
            JsonElement colorEl = raw.get("color");
            JsonElement rEl = raw.get("r");
            JsonElement gEl = raw.get("g");
            JsonElement bEl = raw.get("b");
            value.name = nameEl == null ? null : nameEl.getAsString();
            if (colorEl != null) {
               value.color = colorEl.getAsInt() & 0xFFFFFF;
            } else {
               if (rEl == null || gEl == null || bEl == null) {
                  throw new IllegalStateException("No color was provided!");
               }

               int r = rEl.getAsInt() & 0xFF;
               int g = gEl.getAsInt() & 0xFF;
               int b = bEl.getAsInt() & 0xFF;
               value.color = r << 16 | g << 8 | b;
            }
         } catch (UnsupportedOperationException | NullPointerException | IllegalStateException e) {
            WorldPreview.LOGGER.warn("   - {}: Invalid rock color entry format: {}", location, e.getMessage());
            continue;
         }

         WorldPreview.LOGGER.debug("   - {}: {}", location, String.format("0x%06X", value.color & 0xFFFFFF));
         res.put(location, value);
      }

      return res;
   }
}
