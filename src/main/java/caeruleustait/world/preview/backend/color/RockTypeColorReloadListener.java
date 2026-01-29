package caeruleustait.world.preview.backend.color;

import caeruleustait.world.preview.WorldPreview;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public class RockTypeColorReloadListener extends BaseMultiJsonResourceReloadListener {
   public RockTypeColorReloadListener() {
      super("rock_type_colors.json");
   }

   protected void apply(Map<ResourceLocation, List<JsonElement>> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearRockTypeColors();
      WorldPreview.LOGGER.debug("Loading rock type color entries");

      for (Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet()) {
         WorldPreview.LOGGER.debug(" - loading entries from {}", entry.getKey());

         for (JsonElement j : entry.getValue()) {
            Map<ResourceLocation, PreviewMappingData.RockColorEntry> curr = RockColorReloadListener.parseRockColorData(j);
            previewMappingData.updateRockTypeColors(curr);
         }
      }
   }
}
