package caeruleustait.world.preview.backend.color;

import caeruleustait.world.preview.WorldPreview;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public class HeightmapPresetReloadListener extends SimpleJsonResourceReloadListener {
   private static final Gson GSON = new GsonBuilder().create();

   public HeightmapPresetReloadListener() {
      super(GSON, "heightmap_preview_presets");
   }

   protected void apply(Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearHeightmapPresets();
      WorldPreview.LOGGER.debug("Loading heightmap presets:");

      for (Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
         PreviewData.HeightmapPresetData value = GSON.fromJson(entry.getValue(), PreviewData.HeightmapPresetData.class);
         WorldPreview.LOGGER.debug(" - {}: {} | {} to {}", entry.getKey(), value.name(), value.minY(), value.maxY());
         previewMappingData.addHeightmapPreset(value);
      }
   }
}
