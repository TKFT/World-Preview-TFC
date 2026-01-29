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

public class StructureMapReloadListener extends BaseMultiJsonResourceReloadListener {
   public StructureMapReloadListener() {
      super("structure_icons.json");
   }

   protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearStructures();
      WorldPreview.LOGGER.debug("Loading structure resource entries");

      for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet()) {
         WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

         for (JsonElement jsonElement : entry.getValue()) {
            Map<ResourceLocation, PreviewMappingData.StructureEntry> curr = parseStructureData(
                    jsonElement, PreviewData.DataSource.RESOURCE
            );
            previewMappingData.updateStruct(curr);
         }
      }
   }

   public static Map<ResourceLocation, PreviewMappingData.StructureEntry> parseStructureData(
      JsonElement jsonElement, PreviewData.DataSource dataSource
   ) {
      Map<ResourceLocation, PreviewMappingData.StructureEntry> res = new HashMap<>();
      JsonObject obj = jsonElement.getAsJsonObject();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         ResourceLocation location = ResourceLocation.parse(entry.getKey());
         PreviewMappingData.StructureEntry value = new PreviewMappingData.StructureEntry();
         JsonElement rawEl = entry.getValue();
         value.dataSource = dataSource;

         try {
            if (rawEl.isJsonPrimitive()) {
               if (rawEl.getAsString().equals("hidden")) {
                  continue;
               }

               value.item = rawEl.getAsString();
            } else {
               JsonObject raw = rawEl.getAsJsonObject();
               JsonElement nameEl = raw.get("name");
               JsonElement itemEl = raw.get("item");
               JsonElement iconEl = raw.get("icon");
               JsonElement textureEl = raw.get("texture");
               value.name = nameEl == null ? null : nameEl.getAsString();
               if (textureEl == null) {
                  textureEl = iconEl;
               }

               if (textureEl != null) {
                  value.texture = textureEl.getAsString();
               } else if (itemEl != null) {
                  if (itemEl.getAsString().equals("hidden")) {
                     continue;
                  }

                  value.item = itemEl.getAsString();
               } else {
                  value.texture = "world_preview:textures/structure/unknown.png";
               }
            }
         } catch (UnsupportedOperationException | NullPointerException | IllegalStateException var15) {
            WorldPreview.LOGGER.warn("   - {}: Invalid structure entry format: {}", location, var15.getMessage());
            continue;
         }

         WorldPreview.LOGGER.debug("   - {}: {} - {}", location, value.name, value.texture);
         res.put(location, value);
      }

      return res;
   }
}
