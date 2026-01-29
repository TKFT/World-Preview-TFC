package caeruleustait.world.preview.client.gui.screens;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.storage.PreviewStorage;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.PreviewContainerDataProvider;
import caeruleustait.world.preview.mixin.client.CreateWorldScreenAccessor;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldDimensions.Complete;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewTab implements Tab, AutoCloseable, PreviewContainerDataProvider {
   private final CreateWorldScreen createWorldScreen;
   private final WorldCreationUiState uiState;
   private final PreviewContainer previewContainer;
   private final WorldPreview worldPreview = WorldPreview.get();
   private final Minecraft minecraft;
   private final Executor loadingExecutor = Executors.newFixedThreadPool(2);

   public PreviewTab(CreateWorldScreen screen, Minecraft _minecraft) {
      this.createWorldScreen = screen;
      this.uiState = screen.getUiState();
      this.minecraft = _minecraft;
      this.previewContainer = new PreviewContainer(screen, this);
   }

   @NotNull
   public Component getTabTitle() {
      return WorldPreviewComponents.TITLE;
   }

   public void visitChildren(Consumer<AbstractWidget> consumer) {
      this.previewContainer.widgets().forEach(consumer);
   }

   public void doLayout(ScreenRectangle screenRectangle) {
      this.previewContainer.doLayout(screenRectangle);
   }

   @Override
   public void close() {
      this.previewContainer.close();
   }

   @SuppressWarnings("unchecked")
   @Nullable
   @Override
   public WorldCreationContext previewWorldCreationContext() {
      WorldCreationContext wcContext = this.uiState.getSettings();
      WorldDataConfiguration worldDataConfiguration = wcContext.dataConfiguration();
      PackRepository packRepository = ((CreateWorldScreenAccessor)this.createWorldScreen)
         .invokeGetDataPackSelectionSettings(worldDataConfiguration)
         .getSecond();
      PackConfig packConfig = new PackConfig(packRepository, worldDataConfiguration, false, true);
      InitConfig initConfig = new InitConfig(packConfig, CommandSelection.INTEGRATED, 2);

      record Cookie(WorldGenSettings worldGenSettings) {
      }

      CompletableFuture<WorldCreationContext> completableFuture = WorldLoader.load(initConfig, dataLoadContext -> {
         WorldDimensions worldDimensions;
         try {
            ResourceKey<WorldPreset> worldPresetKey = this.uiState.getWorldType().preset().unwrapKey().orElseThrow();
            WorldPreset worldPreset = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.WORLD_PRESET).getOrThrow(worldPresetKey);
            worldDimensions = worldPreset.createWorldDimensions();
         } catch (NoSuchElementException | IllegalStateException | NullPointerException var6x) {
            worldDimensions = WorldPresets.createNormalWorldDimensions(dataLoadContext.datapackWorldgen());
         }

         WorldGenSettings worldGenSettings = new WorldGenSettings(wcContext.options(), worldDimensions);
         return new DataLoadOutput(new Cookie(worldGenSettings), dataLoadContext.datapackDimensions());
      }, (closeableResourceManager, reloadableServerResources, layeredRegistryAccess, cookie) -> {
         closeableResourceManager.close();
         return new WorldCreationContext(((Cookie)cookie).worldGenSettings(), layeredRegistryAccess, reloadableServerResources, worldDataConfiguration);
      }, this.loadingExecutor, this.loadingExecutor);

      try {
         return completableFuture.get();
      } catch (ExecutionException | InterruptedException var8) {
         throw new RuntimeException(var8);
      }
   }

   @Override
   public Path cacheDir() {
      Path previewDir = this.worldPreview.configDir().resolve("world-preview");
      previewDir.toFile().mkdirs();
      return previewDir;
   }

   private String filename(long seed) {
      return String.format("%s-%s.zip", seed, this.cacheFileCompatPart());
   }

   @Override
   public void storePreviewStorage(long seed, PreviewStorage storage) {
      if (this.worldPreview.cfg().cacheInNew) {
         this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.SAVING_PREVIEW));
         this.writeCacheFile(this.previewContainer.workManager().previewStorage(), this.cacheDir().resolve(this.filename(seed)));
         this.minecraft.forceSetScreen(this.createWorldScreen);
      }
   }

   @Override
   public PreviewStorage loadPreviewStorage(long seed, int yMin, int yMax) {
      if (!this.worldPreview.cfg().cacheInNew) {
         return new PreviewStorage(yMin, yMax);
      } else {
         this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.LOADING_PREVIEW));
         PreviewStorage res = this.readCacheFile(yMin, yMax, this.cacheDir().resolve(this.filename(seed)));
         this.minecraft.forceSetScreen(this.createWorldScreen);
         return res;
      }
   }

   public PreviewContainer mainScreenWidget() {
      return this.previewContainer;
   }

   @Override
   public void registerSettingsChangeListener(Runnable listener) {
      this.uiState.addListener(x -> listener.run());
   }

   @Override
   public String seed() {
      return this.uiState.getSeed();
   }

   @Override
   public void updateSeed(String newSeed) {
      this.uiState.setSeed(newSeed);
   }

   @Override
   public boolean seedIsEditable() {
      return true;
   }

   @Nullable
   @Override
   public Path tempDataPackDir() {
      return ((CreateWorldScreenAccessor)this.createWorldScreen).invokeGetTempDataPackDir();
   }

   @Nullable
   @Override
   public MinecraftServer minecraftServer() {
      return null;
   }

   @Override
   public WorldOptions worldOptions(@Nullable WorldCreationContext wcContext) {
      if (wcContext == null) {
         throw new AssertionError();
      } else {
         return wcContext.options();
      }
   }

   @Override
   public WorldDataConfiguration worldDataConfiguration(@Nullable WorldCreationContext wcContext) {
      if (wcContext == null) {
         throw new AssertionError();
      } else {
         return wcContext.dataConfiguration();
      }
   }

   @Override
   public Frozen registryAccess(@Nullable WorldCreationContext wcContext) {
      if (wcContext == null) {
         throw new AssertionError();
      } else {
         return wcContext.worldgenLoadContext();
      }
   }

   @Override
   public Registry<LevelStem> levelStemRegistry(@Nullable WorldCreationContext wcContext) {
      if (wcContext == null) {
         throw new AssertionError();
      } else {
         Complete worldDimensions = wcContext.selectedDimensions().bake(wcContext.datapackDimensions());
         return worldDimensions.dimensions();
      }
   }

   @Override
   public LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess(@Nullable WorldCreationContext wcContext) {
      if (wcContext == null) {
         throw new AssertionError();
      } else {
         Complete worldDimensions = wcContext.selectedDimensions().bake(wcContext.datapackDimensions());
         return wcContext.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, worldDimensions.dimensionsRegistryAccess());
      }
   }
}
