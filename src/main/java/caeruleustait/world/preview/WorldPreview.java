package caeruleustait.world.preview;

import caeruleustait.world.preview.backend.WorkManager;
import caeruleustait.world.preview.backend.color.PreviewMappingData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("world_preview_tfc")
public class WorldPreview {
   public static final Logger LOGGER = LoggerFactory.getLogger("world_preview_tfc");
   private static WorldPreview INSTANCE;
   private final Path configDir;
   private final Path configFile;
   private final Path renderConfigFile;
   private final Path missingColorsFile;
   private final Path missingStructuresFile;
   private final Path userColorConfigFile;
   private final Gson gson;
   private WorldPreviewConfig cfg;
   private final WorkManager workManager;
   private final PreviewMappingData previewMappingData;
   private RenderSettings renderSettings;

   public static WorldPreview get() {
      return INSTANCE;
   }

   public WorldPreview() {
      INSTANCE = this;
      this.gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
      this.configDir = FMLPaths.CONFIGDIR.get().resolve("world_preview_tfc");
      if (!Files.exists(this.configDir)) {
         this.configDir.toFile().mkdirs();
      }

      this.configFile = this.configDir.resolve("config.json");
      this.renderConfigFile = this.configDir.resolve("renderConfig.json");
      this.missingColorsFile = this.configDir.resolve("missing-colors.json");
      this.missingStructuresFile = this.configDir.resolve("missing-structures.json");
      this.userColorConfigFile = this.configDir.resolve("biome-colors.json");
      this.loadConfig();
      this.renderSettings = new RenderSettings();
      this.workManager = new WorkManager(this.renderSettings, this.cfg);
      this.previewMappingData = new PreviewMappingData();
   }

   public Executor serverThreadPoolExecutor() {
      return Executors.newSingleThreadExecutor(SidedThreadGroups.SERVER);
   }

   public void loaderSpecificSetup(MinecraftServer minecraftServer) {
      ServerLifecycleHooks.handleServerAboutToStart(minecraftServer);
   }

   public void loaderSpecificTeardown(MinecraftServer minecraftServer) {
      ServerLifecycleHooks.handleServerStopped(minecraftServer);
   }

   public WorldPreviewConfig cfg() {
      return this.cfg;
   }

   public WorkManager workManager() {
      return this.workManager;
   }

   public PreviewMappingData biomeColorMap() {
      return this.previewMappingData;
   }

   public RenderSettings renderSettings() {
      return this.renderSettings;
   }

   public Path userColorConfigFile() {
      return this.userColorConfigFile;
   }

   public Path configDir() {
      return this.configDir;
   }

   public void loadConfig() {
      LOGGER.info("Loading config file: {}", this.configFile);

      try {
         if (!Files.exists(this.configFile)) {
            this.cfg = new WorldPreviewConfig();
         } else {
            this.cfg = this.gson.fromJson(Files.readString(this.configFile), WorldPreviewConfig.class);
         }

         if (!Files.exists(this.renderConfigFile)) {
            this.renderSettings = new RenderSettings();
         } else {
            this.renderSettings = this.gson.fromJson(Files.readString(this.renderConfigFile), RenderSettings.class);
         }
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public void saveConfig() {
      LOGGER.info("Saving config file: {}", this.configFile);

      try {
         Files.writeString(this.configFile, this.gson.toJson(this.cfg) + "\n");
         Files.writeString(this.renderConfigFile, this.gson.toJson(this.renderSettings) + "\n");
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public void writeMissingColors(List<String> missing) {
      try {
         Files.deleteIfExists(this.missingColorsFile);
         if (!missing.isEmpty()) {
            LOGGER.warn(
               "No color mapping for {} biomes found. The list of biomes without a color mapping can be found in {}", missing.size(), this.missingColorsFile
            );
            String raw = this.gson.toJson(missing);
            Files.writeString(this.missingColorsFile, raw + "\n");
         }
      } catch (IOException var3) {
         throw new RuntimeException(var3);
      }
   }

   public void writeMissingStructures(List<String> missing) {
      try {
         Files.deleteIfExists(this.missingStructuresFile);
         if (!missing.isEmpty()) {
            LOGGER.warn(
               "No structure data for {} structure found. The list of structures without data can be found in {}", missing.size(), this.missingStructuresFile
            );
            String raw = this.gson.toJson(missing);
            Files.writeString(this.missingStructuresFile, raw + "\n");
         }
      } catch (IOException var3) {
         throw new RuntimeException(var3);
      }
   }

   public void writeUserColorConfig(Map<ResourceLocation, PreviewMappingData.ColorEntry> userColorConfig) {
      record Entry(int r, int g, int b, boolean cave) {
      }

      Map<String, Entry> writeData = userColorConfig.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toString(), x -> {
         PreviewMappingData.ColorEntry rawx = x.getValue();
         int r = rawx.color >> 16 & 0xFF;
         int g = rawx.color >> 8 & 0xFF;
         int b = rawx.color & 0xFF;
         return new Entry(r, g, b, rawx.cave.orElseThrow());
      }));
      String raw = this.gson.toJson(writeData);

      try {
         Files.writeString(this.userColorConfigFile, raw + "\n");
      } catch (IOException var5) {
         throw new RuntimeException(var5);
      }
   }

   public static int nativeColor(int orig) {
      return orig | 0xFF000000;
   }
}
