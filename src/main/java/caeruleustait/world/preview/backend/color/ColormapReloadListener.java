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

public class ColormapReloadListener extends SimpleJsonResourceReloadListener {
   private static final Gson GSON = new GsonBuilder().create();

   public ColormapReloadListener() {
      super(GSON, "colormap_preview");
   }

   protected void apply(Map<ResourceLocation, JsonElement> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      WorldPreview worldPreview = WorldPreview.get();
      PreviewMappingData previewMappingData = worldPreview.biomeColorMap();
      previewMappingData.clearColorMappings();
      WorldPreview.LOGGER.debug("Loading colormaps:");

      for (Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
         ColorMap.RawColorMap value = GSON.fromJson(entry.getValue(), ColorMap.RawColorMap.class);
         WorldPreview.LOGGER.debug(" - {}: {} | {} entries", entry.getKey(), value.name(), value.data().size());
         previewMappingData.addColormap(new ColorMap(entry.getKey(), value));
      }
   }
}
