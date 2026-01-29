package caeruleustait.world.preview.client.gui.screens;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.storage.PreviewStorage;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.PreviewContainerDataProvider;
import caeruleustait.world.preview.mixin.MinecraftServerAccessor;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jetbrains.annotations.Nullable;

public class InGamePreviewScreen extends Screen implements PreviewContainerDataProvider {
   private IntegratedServer integratedServer;
   private PreviewContainer previewContainer;
   private final WorldPreview worldPreview = WorldPreview.get();

   public InGamePreviewScreen() {
      super(WorldPreviewComponents.TITLE_FULL);
   }

   protected void init() {
      if (this.integratedServer == null) {
         this.integratedServer = this.minecraft.getSingleplayerServer();
         if (this.integratedServer == null) {
            throw new InvalidParameterException("No integrated server!");
         }
      }

      if (this.previewContainer == null) {
         this.previewContainer = new PreviewContainer(this, this);
         this.previewContainer.start();
      }

      this.previewContainer.widgets().forEach(this::addRenderableWidget);
      this.previewContainer.doLayout(new ScreenRectangle(0, 18, this.width, this.height - 38));
      Button btn = Button.builder(CommonComponents.GUI_BACK, x -> this.onClose()).width(100).pos(this.width / 2 - 50, this.height - 24).build();
      this.addRenderableWidget(btn);
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.drawCenteredString(this.minecraft.font, WorldPreviewComponents.TITLE_FULL, this.width / 2, 6, 16777215);
      guiGraphics.blit(FOOTER_SEPARATOR, 0, Mth.roundToward(this.height - 30, 2), 0.0F, 0.0F, this.width, 2, 32, 2);
   }

   public void onClose() {
      this.worldPreview.saveConfig();
      this.previewContainer.close();
      super.onClose();
   }

   @Override
   public Path cacheDir() {
      LevelStorageAccess access = ((MinecraftServerAccessor)this.integratedServer).getStorageSource();
      Path previewDir = access.getLevelPath(LevelResource.ROOT).resolve("world-preview");
      previewDir.toFile().mkdirs();
      return previewDir;
   }

   private String filename() {
      return String.format("%s.zip", this.cacheFileCompatPart());
   }

   @Override
   public void storePreviewStorage(long seed, PreviewStorage storage) {
      if (this.worldPreview.cfg().cacheInGame) {
         this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.SAVING_PREVIEW));
         this.writeCacheFile(this.previewContainer.workManager().previewStorage(), this.cacheDir().resolve(this.filename()));
      }
   }

   @Override
   public PreviewStorage loadPreviewStorage(long seed, int yMin, int yMax) {
      if (!this.worldPreview.cfg().cacheInGame) {
         return new PreviewStorage(yMin, yMax);
      } else {
         this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.LOADING_PREVIEW));
         PreviewStorage res = this.readCacheFile(yMin, yMax, this.cacheDir().resolve(this.filename()));
         this.minecraft.forceSetScreen(this);
         return res;
      }
   }

   @Nullable
   @Override
   public WorldCreationContext previewWorldCreationContext() {
      return null;
   }

   @Override
   public void registerSettingsChangeListener(Runnable listener) {
   }

   @Override
   public String seed() {
      return String.valueOf(this.integratedServer.overworld().getSeed());
   }

   @Override
   public void updateSeed(String newSeed) {
   }

   @Override
   public boolean seedIsEditable() {
      return false;
   }

   @Nullable
   @Override
   public Path tempDataPackDir() {
      return null;
   }

   @Nullable
   @Override
   public MinecraftServer minecraftServer() {
      return this.integratedServer;
   }

   @Override
   public WorldOptions worldOptions(@Nullable WorldCreationContext wcContext) {
      return this.integratedServer.getWorldData().worldGenOptions();
   }

   @Override
   public WorldDataConfiguration worldDataConfiguration(@Nullable WorldCreationContext wcContext) {
      return this.integratedServer.getWorldData().getDataConfiguration();
   }

   @Override
   public Frozen registryAccess(@Nullable WorldCreationContext wcContext) {
      return this.integratedServer.registryAccess();
   }

   @Override
   public Registry<LevelStem> levelStemRegistry(@Nullable WorldCreationContext wcContext) {
      return this.integratedServer.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
   }

   @Override
   public LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess(@Nullable WorldCreationContext wcContext) {
      return this.integratedServer.registries();
   }
}
