package caeruleustait.world.preview.backend.color;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

public abstract class BaseMultiJsonResourceReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, List<JsonElement>>> {
   protected static final Gson GSON = new GsonBuilder().create();
   private final String filename;

   protected BaseMultiJsonResourceReloadListener(String filename) {
      this.filename = filename;
   }

   protected @NotNull Map<ResourceLocation, List<JsonElement>> prepare(ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
      Map<ResourceLocation, List<JsonElement>> res = new HashMap<>();

      for (String namespace : resourceManager.getNamespaces()) {
         this.loadAllForLocation(resourceManager, res, ResourceLocation.fromNamespaceAndPath(namespace, this.filename));
      }

      this.loadAllForLocation(resourceManager, res, ResourceLocation.fromNamespaceAndPath("c", "worldgen/" + this.filename));
      return res;
   }

   private void loadAllForLocation(ResourceManager resourceManager, Map<ResourceLocation, List<JsonElement>> res, ResourceLocation rl) {
      for (Resource x : resourceManager.getResourceStack(rl)) {
         try (Reader reader = x.openAsReader()) {
            List<JsonElement> jsonElements = res.computeIfAbsent(rl, z -> new ArrayList<>());
            jsonElements.add(GsonHelper.fromJson(GSON, reader, JsonElement.class));
         } catch (IOException var11) {
            throw new RuntimeException(var11);
         }
      }
   }
}
