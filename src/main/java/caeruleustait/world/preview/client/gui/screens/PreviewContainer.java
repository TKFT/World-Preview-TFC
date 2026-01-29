package caeruleustait.world.preview.client.gui.screens;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.BiomeSearchTask;
import caeruleustait.world.preview.backend.WorkManager;
import caeruleustait.world.preview.backend.color.ColorMap;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.color.PreviewMappingData;
import caeruleustait.world.preview.backend.worker.tfc.TFCSampleUtils;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.PreviewContainerDataProvider;
import caeruleustait.world.preview.client.gui.PreviewDisplayDataProvider;
import caeruleustait.world.preview.client.gui.widgets.OldStyleImageButton;
import caeruleustait.world.preview.client.gui.widgets.PreviewDisplay;
import caeruleustait.world.preview.client.gui.widgets.ToggleButton;
import caeruleustait.world.preview.client.gui.widgets.lists.BaseObjectSelectionList;
import caeruleustait.world.preview.client.gui.widgets.lists.BiomesList;
import caeruleustait.world.preview.client.gui.widgets.lists.RocksList;
import caeruleustait.world.preview.client.gui.widgets.lists.SeedsList;
import caeruleustait.world.preview.client.gui.widgets.lists.StructuresList;
import caeruleustait.world.preview.mixin.client.ScreenAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongMap.Entry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewContainer implements AutoCloseable, PreviewDisplayDataProvider {
   public static final TagKey<Biome> C_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "caves"));
   public static final TagKey<Biome> C_IS_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cave"));
   public static final TagKey<Biome> FORGE_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "caves"));
   public static final TagKey<Biome> FORGE_IS_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_cave"));
   private static final TagKey<Biome> IS_NETHER = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_nether"));
   private static final TagKey<Biome> IS_END = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_end"));
   public static final TagKey<Structure> DISPLAY_BY_DEFAULT = TagKey.create(
      Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("c", "display_on_map_by_default")
   );
   public static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.parse("world_preview:textures/gui/buttons.png");
   private final PreviewContainerDataProvider dataProvider;
   private final Minecraft minecraft;
   private final WorldPreview worldPreview;
   private final WorldPreviewConfig cfg;
   private final WorkManager workManager;
   private final RenderSettings renderSettings;
   private final PreviewMappingData previewMappingData;
   private PreviewData previewData;
   private List<ResourceLocation> levelStemKeys;
   private Registry<LevelStem> levelStemRegistry;
   private final EditBox seedEdit;
   private final Button randomSeedButton;
   private final Button saveSeed;
   private final Button settings;
   private final Button resetToZeroZero;
   private final ToggleButton toggleShowStructures;
   private final ToggleButton toggleBiomes;
   private final ToggleButton toggleHeightmap;
   private final ToggleButton toggleExpand;
   private final ToggleButton toggleTFCTemperature;
   private final ToggleButton toggleTFCRainfall;
   private final ToggleButton toggleTFCLandWater;
   private final ToggleButton toggleTFCRockTop;
   private final ToggleButton toggleTFCRockMid;
   private final ToggleButton toggleTFCRockBot;
   private final ToggleButton toggleTFCRockType;
   private final ToggleButton toggleKaolinClay;
   private final Button cycleResolutionButton;
   private final Button resetDefaultStructureVisibility;
   private final Button switchBiomes;
   private final Button switchStructures;
   private final Button switchSeeds;
   private final PreviewDisplay previewDisplay;
   private final BiomesList biomesList;
   private final RocksList rocksList;
   private final StructuresList structuresList;
   private final SeedsList seedsList;
   private BiomesList.BiomeEntry[] allBiomes;
   private final RocksList.RockEntry[] allRocks;
   private final RocksList.RockEntry[] allRockTypes;
   private StructuresList.StructureEntry[] allStructures;
   private NativeImage[] allStructureIcons;
   private NativeImage playerIcon;
   private NativeImage spawnIcon;
   private NativeImage worldSpawnIcon;
    private ScreenRectangle lastScreenRectangle;
   private boolean inhibitUpdates = true;
   private boolean isUpdating = false;
   private boolean setupFailed = false;
   private final Executor reloadExecutor = Executors.newSingleThreadExecutor();
   private final Executor serverThreadPoolExecutor;
   private final AtomicInteger reloadRevision = new AtomicInteger(0);
   private final List<AbstractWidget> toRender = new ArrayList<>();
   private final Button searchBiomeButton;
   private final ExecutorService biomeSearchExecutor = Executors.newSingleThreadExecutor();
   private BiomeSearchTask currentSearchTask = null;
   private boolean isSearching = false;

   public PreviewContainer(Screen screen, PreviewContainerDataProvider previewContainerDataProvider) {
      Font font = ((ScreenAccessor)screen).getFont();
      this.dataProvider = previewContainerDataProvider;
      this.minecraft = Minecraft.getInstance();
      this.allBiomes = new BiomesList.BiomeEntry[0];
      this.worldPreview = WorldPreview.get();
      this.cfg = this.worldPreview.cfg();
      this.workManager = this.worldPreview.workManager();
      this.previewMappingData = this.worldPreview.biomeColorMap();
      this.renderSettings = this.worldPreview.renderSettings();
      this.serverThreadPoolExecutor = this.worldPreview.serverThreadPoolExecutor();
      this.seedEdit = new EditBox(font, 0, 0, 100, 18, WorldPreviewComponents.SEED_FIELD);
      this.seedEdit.setHint(WorldPreviewComponents.SEED_FIELD);
      this.seedEdit.setValue(this.dataProvider.seed());
      this.seedEdit.setResponder(this::setSeed);
      this.seedEdit.setTooltip(Tooltip.create(WorldPreviewComponents.SEED_LABEL));
      this.seedEdit.active = this.dataProvider.seedIsEditable();
      this.toRender.add(this.seedEdit);
      this.randomSeedButton = new OldStyleImageButton(0, 0, 20, 20, 0, 20, 20, BUTTONS_TEXTURE, 400, 60, this::randomizeSeed);
      this.randomSeedButton.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_RANDOM));
      this.randomSeedButton.active = this.dataProvider.seedIsEditable();
      this.toRender.add(this.randomSeedButton);
      this.saveSeed = new OldStyleImageButton(0, 0, 20, 20, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, this::saveCurrentSeed);
      this.saveSeed.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SAVE_SEED));
      this.saveSeed.active = false;
      this.toRender.add(this.saveSeed);
      this.settings = new OldStyleImageButton(0, 0, 20, 20, 60, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> {
          var gen = this.workManager.chunkGenerator();
          ChunkGeneratorExtension ext = (gen instanceof ChunkGeneratorExtension e) ? e : null;
          this.minecraft.setScreen(new SettingsScreen(screen, this, ext));

      });
      this.settings.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SETTINGS));
      this.settings.active = false;
      this.toRender.add(this.settings);
      this.resetToZeroZero = new OldStyleImageButton(0, 0, 20, 20, 120, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.renderSettings.resetCenter());
      this.resetToZeroZero.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_HOME));
      this.toRender.add(this.resetToZeroZero);
      this.resetDefaultStructureVisibility = Button.builder(
            WorldPreviewComponents.BTN_RESET_STRUCTURES, x -> Arrays.stream(this.allStructures).forEach(StructuresList.StructureEntry::reset)
         )
         .build();
      this.resetDefaultStructureVisibility.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_RESET_STRUCTURES_TOOLTIP));
      this.resetDefaultStructureVisibility.visible = false;
      this.toRender.add(this.resetDefaultStructureVisibility);
      this.switchBiomes = Button.builder(DisplayType.BIOMES.component(), x -> this.onTabButtonChange(x, DisplayType.BIOMES))
         .size(100, 20)
         .build();
      this.switchStructures = Button.builder(
            DisplayType.STRUCTURES.component(), x -> this.onTabButtonChange(x, DisplayType.STRUCTURES)
         )
         .size(100, 20)
         .build();
      this.switchSeeds = Button.builder(DisplayType.SEEDS.component(), x -> this.onTabButtonChange(x, DisplayType.SEEDS))
         .size(100, 20)
         .build();
      this.toRender.add(this.switchBiomes);
      this.toRender.add(this.switchStructures);
      this.toRender.add(this.switchSeeds);
      this.biomesList = new BiomesList(this, this.minecraft, 200, 300, 4, 100, true);
      this.toRender.add(this.biomesList);
      this.searchBiomeButton = Button.builder(
            Component.translatable("world_preview.preview.btn-search-biome"),
            this::onSearchBiomeClick
         ).size(100, 20).build();
      this.searchBiomeButton.visible = false;
      this.searchBiomeButton.active = false;
      this.searchBiomeButton.setTooltip(Tooltip.create(Component.translatable("world_preview.preview.btn-search-biome.tooltip")));
      this.toRender.add(this.searchBiomeButton);
      this.rocksList = new RocksList(this.minecraft, 200, 300, 4, 100);
      this.rocksList.visible = false;
      this.rocksList.active = false;
      this.toRender.add(this.rocksList);
      // Pre-create rock entries
      this.allRocks = new RocksList.RockEntry[TFCSampleUtils.ROCK_NAMES.length];
      for (short ri = 0; ri < TFCSampleUtils.ROCK_NAMES.length; ri++) {
         this.allRocks[ri] = this.rocksList.createRockEntry(ri);
      }
      this.allRockTypes = new RocksList.RockEntry[TFCSampleUtils.ROCK_TYPE_NAMES.length];
      for (short rti = 0; rti < TFCSampleUtils.ROCK_TYPE_NAMES.length; rti++) {
         this.allRockTypes[rti] = this.rocksList.createRockTypeEntry(rti);
      }
      this.structuresList = new StructuresList(this.minecraft, 200, 300, 4, 100);
      this.toRender.add(this.structuresList);
      this.seedsList = new SeedsList(this.minecraft, this);
      this.updateSeedListWidget();
      this.toRender.add(this.seedsList);
      this.previewDisplay = new PreviewDisplay(this.minecraft, this, WorldPreviewComponents.TITLE);
      this.toRender.add(this.previewDisplay);
      this.toggleShowStructures = new ToggleButton(
         0, 0, 20, 20, 140, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.renderSettings.hideAllStructures = !((ToggleButton)x).selected
      );
      this.toggleShowStructures.selected = !this.renderSettings.hideAllStructures;
      this.toggleShowStructures.active = false;
      this.toRender.add(this.toggleShowStructures);
      this.toggleBiomes = new ToggleButton(0, 0, 20, 20, 360, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.BIOMES));
      this.toggleBiomes.visible = true;
      this.toggleBiomes.active = true;
      this.toggleBiomes.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_BIOMES));
      this.toRender.add(this.toggleBiomes);
      this.toggleHeightmap = new ToggleButton(
         0, 0, 20, 20, 200, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.HEIGHTMAP)
      );
      this.toggleHeightmap.visible = true;
      this.toggleHeightmap.active = false;
      this.toRender.add(this.toggleHeightmap);
      this.toggleTFCTemperature = new ToggleButton(
         0, 0, 20, 20, 200, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_TEMPERATURE)
      );
      this.toggleTFCTemperature.visible = false;
      this.toggleTFCTemperature.active = false;
      this.toggleTFCTemperature.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_temperature")));
      this.toRender.add(this.toggleTFCTemperature);
      this.toggleTFCRainfall = new ToggleButton(
         0, 0, 20, 20, 280, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_RAINFALL)
      );
      this.toggleTFCRainfall.visible = false;
      this.toggleTFCRainfall.active = false;
      this.toggleTFCRainfall.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rainfall")));
      this.toRender.add(this.toggleTFCRainfall);
      this.toggleTFCLandWater = new ToggleButton(
         0, 0, 20, 20, 240, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_LAND_WATER)
      );
      this.toggleTFCLandWater.visible = false;
      this.toggleTFCLandWater.active = false;
      this.toggleTFCLandWater.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_land_water")));
      this.toRender.add(this.toggleTFCLandWater);
      // TFC Rock Top layer toggle
      this.toggleTFCRockTop = new ToggleButton(
         0, 0, 20, 20, 160, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_TOP)
      );
      this.toggleTFCRockTop.visible = false;
      this.toggleTFCRockTop.active = false;
      this.toggleTFCRockTop.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_top")));
      this.toRender.add(this.toggleTFCRockTop);
      this.toggleTFCRockMid = new ToggleButton(
         0, 0, 20, 20, 160, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_MID)
      );
      this.toggleTFCRockMid.visible = false;
      this.toggleTFCRockMid.active = false;
      this.toggleTFCRockMid.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_mid")));
      this.toRender.add(this.toggleTFCRockMid);
      this.toggleTFCRockBot = new ToggleButton(
         0, 0, 20, 20, 160, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_BOT)
      );
      this.toggleTFCRockBot.visible = false;
      this.toggleTFCRockBot.active = false;
      this.toggleTFCRockBot.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_bot")));
      this.toRender.add(this.toggleTFCRockBot);
      this.toggleTFCRockType = new ToggleButton(
         0, 0, 20, 20, 160, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_TYPE)
      );
      this.toggleTFCRockType.visible = false;
      this.toggleTFCRockType.active = false;
      this.toggleTFCRockType.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_type")));
      this.toRender.add(this.toggleTFCRockType);
      this.toggleKaolinClay = new ToggleButton(0, 0, 20, 20, 160, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_KAOLINITE));
      this.toggleKaolinClay.visible = false;
      this.toggleKaolinClay.active = false;
      this.toggleKaolinClay.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_kaolinite.tooltip")));
      this.toRender.add(this.toggleKaolinClay);
      this.cycleResolutionButton = Button.builder(this.getResolutionLabel(), x -> this.cycleResolution())
         .size(50, 20)
         .build();
      this.cycleResolutionButton.visible = false;
      this.cycleResolutionButton.active = true;
      this.cycleResolutionButton.setTooltip(Tooltip.create(Component.translatable("world_preview.button.resolution.tooltip")));
      this.toRender.add(this.cycleResolutionButton);
      this.toggleExpand = new ToggleButton(0, 0, 20, 20, 320, 20, 20, 20, BUTTONS_TEXTURE, 400, 60, x -> {
         boolean expanded = ((ToggleButton)x).selected;
         this.cycleResolutionButton.visible = expanded;
         // TFC buttons visibility controlled by expand and TFC generator detection
         boolean isTFC = this.workManager.isTFCEnabled();
         this.toggleTFCTemperature.visible = expanded && isTFC;
         this.toggleTFCRainfall.visible = expanded && isTFC;
         this.toggleTFCLandWater.visible = expanded && isTFC;
         this.toggleTFCRockTop.visible = expanded && isTFC;
         this.toggleTFCRockMid.visible = expanded && isTFC;
         this.toggleTFCRockBot.visible = expanded && isTFC;
         this.toggleTFCRockType.visible = expanded && isTFC;
         this.toggleKaolinClay.visible = expanded && isTFC;
         this.doLayout(this.lastScreenRectangle);
      });
      this.toggleExpand.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_EXPAND));
      this.toRender.add(this.toggleExpand);
      this.biomesList.setBiomeChangeListener(x -> {
         this.previewDisplay.setSelectedBiomeId(x == null ? -1 : x.id());
         this.searchBiomeButton.active = (x != null) && this.workManager.isSetup() && !this.isSearching;
      });
      this.rocksList.setRockChangeListener(x -> this.previewDisplay.setSelectedRockId(x == null ? -1 : x.id()));
      this.dataProvider.registerSettingsChangeListener(this::updateSettings);
      this.onTabButtonChange(this.switchBiomes, DisplayType.BIOMES);
      this.selectViewMode(RenderSettings.RenderMode.BIOMES);
   }

   public void patchColorData() {
      Map<ResourceLocation, PreviewMappingData.ColorEntry> configured = Arrays.stream(this.allBiomes)
         .filter(x -> x.dataSource() == PreviewData.DataSource.CONFIG)
         .collect(
            Collectors.toMap(
               x -> x.entry().key().location(), x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.MISSING, x.color(), x.isCave(), x.name())
            )
         );
      Map<ResourceLocation, PreviewMappingData.ColorEntry> defaults = Arrays.stream(this.allBiomes)
         .filter(x -> x.dataSource() == PreviewData.DataSource.RESOURCE)
         .collect(
            Collectors.toMap(
               x -> x.entry().key().location(), x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.RESOURCE, x.color(), x.isCave(), x.name())
            )
         );
      Map<ResourceLocation, PreviewMappingData.ColorEntry> missing = Arrays.stream(this.allBiomes)
         .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
         .collect(
            Collectors.toMap(
               x -> x.entry().key().location(), x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.CONFIG, x.color(), x.isCave(), x.name())
            )
         );
      this.previewMappingData.update(missing);
      this.previewMappingData.update(defaults);
      this.previewMappingData.update(configured);
      this.updateSettings();
   }

   private void selectViewMode(RenderSettings.RenderMode mode) {
      this.toggleBiomes.selected = false;
      this.toggleHeightmap.selected = false;
      this.toggleTFCTemperature.selected = false;
      this.toggleTFCRainfall.selected = false;
      this.toggleTFCLandWater.selected = false;
      this.toggleTFCRockTop.selected = false;
      this.toggleTFCRockMid.selected = false;
      this.toggleTFCRockBot.selected = false;
      this.toggleTFCRockType.selected = false;
      this.toggleKaolinClay.selected = false;
      synchronized (this.renderSettings) {
         switch (mode) {
            case BIOMES:
               this.toggleBiomes.selected = true;
               break;
            case HEIGHTMAP:
               this.toggleHeightmap.selected = true;
               break;
            case TFC_TEMPERATURE:
               this.toggleTFCTemperature.selected = true;
               break;
            case TFC_RAINFALL:
               this.toggleTFCRainfall.selected = true;
               break;
            case TFC_LAND_WATER:
               this.toggleTFCLandWater.selected = true;
               break;
            case TFC_ROCK_TOP:
               this.toggleTFCRockTop.selected = true;
               break;
            case TFC_ROCK_MID:
               this.toggleTFCRockMid.selected = true;
               break;
            case TFC_ROCK_BOT:
               this.toggleTFCRockBot.selected = true;
               break;
            case TFC_ROCK_TYPE:
               this.toggleTFCRockType.selected = true;
               break;
            case TFC_KAOLINITE:
               this.toggleKaolinClay.selected = true;
               break;
         }

         this.renderSettings.mode = mode;
         WorldPreview.get().workManager().onRenderModeChanged();
      }

      // Show search button and full biome list only in biome mode
      boolean isBiomeMode = mode == RenderSettings.RenderMode.BIOMES;
      this.searchBiomeButton.visible = isBiomeMode;
      if (isBiomeMode) {
         this.biomesList.replaceEntries(this.filteredBiomes());
      }
      if (!isBiomeMode && this.isSearching) {
         this.cancelBiomeSearch();
      }

      // Show/hide rocks list based on whether we're in a rock view mode
      boolean isRockMode = mode == RenderSettings.RenderMode.TFC_ROCK_TOP
            || mode == RenderSettings.RenderMode.TFC_ROCK_MID
            || mode == RenderSettings.RenderMode.TFC_ROCK_BOT
            || mode == RenderSettings.RenderMode.TFC_ROCK_TYPE;
      this.rocksList.visible = isRockMode;
      this.rocksList.active = isRockMode;
      // Recalculate layout when mode changes (search button / rock list visibility affects sizing)
      if (this.lastScreenRectangle != null) {
         this.doLayout(this.lastScreenRectangle);
      }
      if (isRockMode) {
         this.rocksList.replaceEntries(new ArrayList<>());
      } else {
         // Clear rock selection when leaving rock mode
         this.rocksList.setSelected(null);
         this.previewDisplay.setSelectedRockId((short) -1);
      }
      this.moveList(this.rocksList);
   }

   private void onSearchBiomeClick(Button btn) {
      if (this.isSearching) {
         this.cancelBiomeSearch();
         return;
      }

      BiomesList.BiomeEntry selected = this.biomesList.getSelected();
      if (selected == null || !this.workManager.isSetup()) return;

      ResourceKey<Biome> targetBiome = selected.entry().key();
      BlockPos center = this.renderSettings.center();

      this.isSearching = true;
      this.searchBiomeButton.setMessage(Component.translatable("world_preview.preview.btn-search-biome.cancel"));

      BiomeSearchTask.Callback callback = new BiomeSearchTask.Callback() {
         @Override
         public void onProgress(int currentDistance, int maxDistance) {
            minecraft.execute(() ->
               previewDisplay.setOverlayMessage(
                  Component.translatable("world_preview.preview.search.progress", currentDistance)
               )
            );
         }

         @Override
         public void onFound(BlockPos pos) {
            minecraft.execute(() -> {
               renderSettings.setCenter(pos);
               previewDisplay.setOverlayMessage(
                  Component.translatable("world_preview.preview.search.found", pos.getX(), pos.getZ())
               );
               finishSearch();
            });
         }

         @Override
         public void onNotFound() {
            minecraft.execute(() -> {
               previewDisplay.setOverlayMessage(
                  Component.translatable("world_preview.preview.search.not-found")
               );
               finishSearch();
            });
         }

         @Override
         public void onCancelled() {
            minecraft.execute(() -> {
               previewDisplay.setOverlayMessage(
                  Component.translatable("world_preview.preview.search.cancelled")
               );
               finishSearch();
            });
         }

         @Override
         public void onError(Throwable t) {
            WorldPreview.LOGGER.error("Biome search failed", t);
            minecraft.execute(() -> {
               previewDisplay.setOverlayMessage(
                  Component.translatable("world_preview.preview.search.error")
               );
               finishSearch();
            });
         }
      };

      this.currentSearchTask = new BiomeSearchTask(
         this.workManager.sampleUtils(),
         targetBiome,
         center,
         callback
      );
      this.biomeSearchExecutor.submit(this.currentSearchTask);
   }

   private void finishSearch() {
      this.isSearching = false;
      this.currentSearchTask = null;
      this.searchBiomeButton.setMessage(Component.translatable("world_preview.preview.btn-search-biome"));
      BiomesList.BiomeEntry selected = this.biomesList.getSelected();
      this.searchBiomeButton.active = (selected != null) && this.workManager.isSetup();
   }

   private void cancelBiomeSearch() {
      if (this.currentSearchTask != null) {
         this.currentSearchTask.cancel();
      }
   }

   private synchronized void updateSettings() {
      if (!this.inhibitUpdates) {
         this.inhibitUpdates = true;

         try {
            int revision;
            synchronized (this.reloadRevision) {
               revision = this.reloadRevision.incrementAndGet();
            }

            this.isUpdating = true;
            CompletableFuture.supplyAsync(
                  () -> this.reloadRevision.get() > revision ? null : this.dataProvider.previewWorldCreationContext(), this.reloadExecutor
               )
               .thenAcceptAsync(x -> {
                  if (this.reloadRevision.get() <= revision) {
                     this.updateSettings_real(x);
                     synchronized (this.reloadRevision) {
                        if (this.reloadRevision.get() <= revision) {
                           this.isUpdating = false;
                        }
                     }
                  }
               }, this.minecraft)
               .handle((r, e) -> {
                  if (e == null) {
                     this.setupFailed = false;
                  } else {
                     WorldPreview.LOGGER.error("Preview setup failed", e);
                     this.setupFailed = true;
                  }

                  return null;
               });
         } finally {
            this.inhibitUpdates = false;
         }
      }
   }

   private void updateSettings_real(@Nullable WorldCreationContext wcContext) {
      this.saveSeed.active = !this.dataProvider.seed().isEmpty() && !this.cfg.savedSeeds.contains(this.dataProvider.seed());
      this.updateSeedListWidget();
      this.seedEdit.setValue(this.dataProvider.seed());
      if (!this.seedEdit.isFocused()) {
         this.seedEdit.moveCursorToStart(false);
      }

      if (this.cfg.heightmapMinY == this.cfg.heightmapMaxY) {
         this.cfg.heightmapMaxY++;
      } else if (this.cfg.heightmapMaxY < this.cfg.heightmapMinY) {
         int tmp = this.cfg.heightmapMaxY;
         this.cfg.heightmapMaxY = this.cfg.heightmapMinY;
         this.cfg.heightmapMinY = tmp;
      }

      WorldDataConfiguration worldDataConfiguration = this.dataProvider.worldDataConfiguration(wcContext);
      Registry<Biome> biomeRegistry = this.dataProvider.registryAccess(wcContext).registryOrThrow(Registries.BIOME);
      Registry<Structure> strucutreRegistry = this.dataProvider.registryAccess(wcContext).registryOrThrow(Registries.STRUCTURE);
      this.levelStemRegistry = this.dataProvider.levelStemRegistry(wcContext);
      this.levelStemKeys = this.levelStemRegistry.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList();
      this.settings.active = true;
      if (this.renderSettings.dimension == null || !this.levelStemRegistry.containsKey(this.renderSettings.dimension)) {
         if (this.levelStemRegistry.containsKey(LevelStem.OVERWORLD)) {
            this.renderSettings.dimension = LevelStem.OVERWORLD.location();
         } else {
            this.renderSettings.dimension = this.levelStemRegistry.keySet().iterator().next();
         }
      }

      LevelStem levelStem = this.levelStemRegistry.get(this.renderSettings.dimension);
      Set<ResourceLocation> caveBiomes = new HashSet<>();

      for (TagKey<Biome> tagKey : List.of(C_CAVE, C_IS_CAVE, FORGE_CAVE, FORGE_IS_CAVE)) {
         caveBiomes.addAll(
            StreamSupport.stream(biomeRegistry.getTagOrEmpty(tagKey).spliterator(), false)
               .map(x -> x.unwrapKey().orElseThrow().location())
               .toList()
         );
      }

      this.previewData = this.previewMappingData
         .generateMapData(
            biomeRegistry.keySet(),
            caveBiomes,
            strucutreRegistry.keySet(),
            StreamSupport.stream(strucutreRegistry.getTagOrEmpty(DISPLAY_BY_DEFAULT).spliterator(), false)
               .map(x -> x.unwrapKey().orElseThrow().location())
               .collect(Collectors.toSet())
         );
      ColorMap colorMap = this.previewData.colorMaps().get(this.cfg.colorMap);
      if (colorMap == null) {
         this.cfg.colorMap = "world_preview:inferno";
      }

      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = this.dataProvider.layeredRegistryAccess(wcContext);
      this.workManager.cancel();
      Runnable changeWorldGenState = () -> this.workManager
         .changeWorldGenState(
            levelStem,
            layeredRegistryAccess,
            this.previewData,
            this.dataProvider.worldOptions(wcContext),
            worldDataConfiguration,
            this.dataProvider,
            this.minecraft.getProxy(),
            this.dataProvider.tempDataPackDir(),
            this.dataProvider.minecraftServer()
         );
      if (this.serverThreadPoolExecutor != null) {
         try {
            CompletableFuture.runAsync(changeWorldGenState, this.serverThreadPoolExecutor).get();
         } catch (InterruptedException var21) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(var21);
         } catch (ExecutionException var22) {
            throw new RuntimeException(var22);
         }
      } else {
         changeWorldGenState.run();
      }

      this.workManager.postChangeWorldGenState();
       List<String> missing = Arrays.stream(this.previewData.biomeId2BiomeData())
               .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
               .map(PreviewData.BiomeData::tag)
               .filter(rl -> !"minecraft".equals(rl.getNamespace())) // ignores vanilla biomes
               .map(ResourceLocation::toString)
               .toList();
       this.worldPreview.writeMissingColors(missing);
       this.allBiomes = biomeRegistry.holders().map(x -> {
         short id = this.previewData.biome2Id().getShort(x.key().location().toString());
         PreviewData.BiomeData biomeData = this.previewData.biomeId2BiomeData()[id];
         int color = biomeData.color();
         int initialColor = biomeData.resourceOnlyColor();
         boolean isCave = biomeData.isCave();
         boolean initialIsCave = biomeData.resourceOnlyIsCave();
         String explicitName = biomeData.name();
         PreviewData.DataSource dataSource = biomeData.dataSource();
         return this.biomesList.createEntry(x, id, color, initialColor, isCave, initialIsCave, explicitName, dataSource);
      }).sorted(Comparator.comparing(BiomesList.BiomeEntry::id)).toArray(BiomesList.BiomeEntry[]::new);
      this.biomesList.replaceEntries(this.filteredBiomes());
      this.biomesList.setSelected(null);
      missing = Arrays.stream(this.previewData.structId2StructData())
         .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
         .map(PreviewData.StructureData::tag)
         .map(ResourceLocation::toString)
         .toList();
      this.worldPreview.writeMissingStructures(missing);
      this.freeStructureIcons();
      ResourceManager builtinResourceManager = this.minecraft.getResourceManager();
      Map<ResourceLocation, NativeImage> icons = new HashMap<>();
      this.allStructureIcons = new NativeImage[this.previewData.structId2StructData().length];

      for (int i = 0; i < this.previewData.structId2StructData().length; i++) {
         PreviewData.StructureData data = this.previewData.structId2StructData()[i];
         this.allStructureIcons[i] = icons.computeIfAbsent(data.icon(), x -> {
            if (x == null) {
               x = ResourceLocation.parse("world_preview:textures/structure/unknown.png");
            }

            Optional<Resource> resource = builtinResourceManager.getResource(x);
            if (resource.isEmpty()) {
               resource = this.workManager.sampleResourceManager().getResource(x);
            }

            if (resource.isEmpty()) {
               WorldPreview.LOGGER.error("Failed to load structure icon: '{}'", x);
               resource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview:textures/structure/unknown.png"));
            }

            if (resource.isEmpty()) {
               WorldPreview.LOGGER.error("FATAL ERROR LOADING: '{}' -- unable to load fallback!", x);
               return new NativeImage(16, 16, true);
            } else {
               try {
                  NativeImage var5x;
                  try (InputStream in = resource.get().open()) {
                     var5x = NativeImage.read(in);
                  }

                  return var5x;
               } catch (IOException var9x) {
                  WorldPreview.LOGGER.error("Failed to load structure icon", var9x);
                  return new NativeImage(16, 16, true);
               }
            }
         });
      }

      Optional<Resource> playerResource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview:textures/etc/player.png"));
      Optional<Resource> spawnResource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview:textures/etc/bed.png"));

      try (
         InputStream inPlayer = playerResource.orElseThrow().open();
         InputStream inSpawn = spawnResource.orElseThrow().open()
      ) {
         this.playerIcon = NativeImage.read(inPlayer);
         this.spawnIcon = NativeImage.read(inSpawn);
      } catch (IOException var25) {
         this.playerIcon = new NativeImage(16, 16, true);
         this.spawnIcon = new NativeImage(16, 16, true);
         WorldPreview.LOGGER.error("Failed to load player/spawn icons", var25);
      }

      // Create world spawn icon (gold/yellow compass-like marker)
      this.worldSpawnIcon = createWorldSpawnIcon();

      Registry<Item> itemRegistry = layeredRegistryAccess.compositeAccess().registryOrThrow(Registries.ITEM);
      this.allStructures = strucutreRegistry.holders()
         .map(
            x -> {
               short id = this.previewData.struct2Id().getShort(x.key().location().toString());
               PreviewData.StructureData structureData = this.previewData.structId2StructData()[id];
               return this.structuresList
                  .createEntry(
                     id,
                     x.key().location(),
                     this.allStructureIcons[id],
                     structureData.item() == null ? null : itemRegistry.get(structureData.item()),
                     structureData.name(),
                     structureData.showByDefault(),
                     structureData.showByDefault()
                  );
            }
         )
         .sorted(Comparator.comparing(StructuresList.StructureEntry::id))
         .toArray(StructuresList.StructureEntry[]::new);
      this.structuresList.replaceEntries(new ArrayList<>());
      this.renderSettings.resetCenter();
      // Center on player position if available (in-game preview)
      PlayerData playerData = this.getPlayerData(this.minecraft.getUser().getProfileId());
      if (playerData.currentPos() != null) {
         this.renderSettings.setCenter(playerData.currentPos());
      }
      if (this.cfg.sampleStructures) {
         this.toggleShowStructures.active = true;
         this.toggleShowStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_STRUCTURES));
      } else {
         this.toggleShowStructures.active = false;
         this.toggleShowStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_STRUCTURES_DISABLED));
      }

      if (this.cfg.sampleHeightmap) {
         this.toggleHeightmap.active = true;
         this.toggleHeightmap.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_HEIGHTMAP));
      } else {
         this.toggleHeightmap.active = false;
         this.toggleHeightmap.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_HEIGHTMAP_DISABLED));
         this.renderSettings.mode = this.renderSettings.mode == RenderSettings.RenderMode.HEIGHTMAP
            ? RenderSettings.RenderMode.BIOMES
            : this.renderSettings.mode;
      }

      // TFC buttons - enable if TFC generator is detected
      if (this.workManager.isTFCEnabled()) {
         boolean expanded = this.toggleExpand.selected;
         this.toggleTFCTemperature.active = true;
         this.toggleTFCTemperature.visible = expanded;
         this.toggleTFCTemperature.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_temperature")));
         this.toggleTFCRainfall.active = true;
         this.toggleTFCRainfall.visible = expanded;
         this.toggleTFCRainfall.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rainfall")));
         this.toggleTFCLandWater.active = true;
         this.toggleTFCLandWater.visible = expanded;
         this.toggleTFCLandWater.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_land_water")));
         this.toggleTFCRockTop.active = true;
         this.toggleTFCRockTop.visible = expanded;
         this.toggleTFCRockTop.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_top")));
         this.toggleTFCRockMid.active = true;
         this.toggleTFCRockMid.visible = expanded;
         this.toggleTFCRockMid.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_mid")));
         this.toggleTFCRockBot.active = true;
         this.toggleTFCRockBot.visible = expanded;
         this.toggleTFCRockBot.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_bot")));
         this.toggleTFCRockType.active = true;
         this.toggleTFCRockType.visible = expanded;
         this.toggleTFCRockType.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_rock_type")));
         this.toggleKaolinClay.active = true;
         this.toggleKaolinClay.visible = expanded;
         this.toggleKaolinClay.setTooltip(Tooltip.create(Component.translatable("world_preview.button.tfc_kaolinite.tooltip")));
      } else {
         this.toggleTFCTemperature.active = false;
         this.toggleTFCTemperature.visible = false;
         this.toggleTFCRainfall.active = false;
         this.toggleTFCRainfall.visible = false;
         this.toggleTFCLandWater.active = false;
         this.toggleTFCLandWater.visible = false;
         this.toggleTFCRockTop.active = false;
         this.toggleTFCRockTop.visible = false;
         this.toggleTFCRockMid.active = false;
         this.toggleTFCRockMid.visible = false;
         this.toggleTFCRockBot.active = false;
         this.toggleTFCRockBot.visible = false;
         this.toggleTFCRockType.active = false;
         this.toggleTFCRockType.visible = false;
         this.toggleKaolinClay.active = false;
         this.toggleKaolinClay.visible = false;
         // If we were in TFC mode but TFC is no longer available, switch to BIOMES
         if (this.renderSettings.mode.isTFC()) {
            this.renderSettings.mode = RenderSettings.RenderMode.BIOMES;
         }
      }

      this.selectViewMode(this.renderSettings.mode);
      this.previewDisplay.reloadData();
      this.previewDisplay.setSelectedBiomeId((short)-1);
      this.previewDisplay.setHighlightCaves(false);
   }

   @Override
   public void onVisibleBiomesChanged(Short2LongMap visibleBiomes) {
      // In biome map mode, the full filtered list is already shown; don't replace.
      if (this.renderSettings.mode == RenderSettings.RenderMode.BIOMES) {
         return;
      }
      // In other modes, show only biomes visible on the map.
      List<BiomesList.BiomeEntry> res = visibleBiomes.short2LongEntrySet()
         .stream()
         .sorted(Comparator.comparing(Entry::getLongValue).reversed())
         .map(Entry::getShortKey)
         .map(x -> this.allBiomes[x])
         .toList();
      this.biomesList.replaceEntries(res);
   }

   @Override
   public void onVisibleStructuresChanged(Short2LongMap visibleStructures) {
      List<StructuresList.StructureEntry> res = visibleStructures.short2LongEntrySet()
         .stream()
         .sorted(Comparator.comparing(Entry::getLongValue))
         .map(Entry::getShortKey)
         .map(x -> this.allStructures[x])
         .toList();
      this.structuresList.replaceEntries(res);
   }

   @Override
   public void onVisibleRocksChanged(Short2LongMap visibleRocks) {
      RenderSettings.RenderMode mode = this.renderSettings.mode;
      boolean isRockMode = mode == RenderSettings.RenderMode.TFC_ROCK_TOP
            || mode == RenderSettings.RenderMode.TFC_ROCK_MID
            || mode == RenderSettings.RenderMode.TFC_ROCK_BOT;
      boolean isRockTypeMode = mode == RenderSettings.RenderMode.TFC_ROCK_TYPE;

      if (isRockMode || isRockTypeMode) {
         RocksList.RockEntry[] sourceEntries = isRockTypeMode ? this.allRockTypes : this.allRocks;
         List<RocksList.RockEntry> res = visibleRocks.short2LongEntrySet()
            .stream()
            .filter(e -> e.getShortKey() >= 0 && e.getShortKey() < sourceEntries.length)
            .sorted(Comparator.comparing(Entry::getLongValue).reversed())
            .map(Entry::getShortKey)
            .map(x -> sourceEntries[x])
            .toList();
         this.rocksList.replaceEntries(res);
      }
   }

   private void randomizeSeed(Button btn) {
      UUID uuid = UUID.randomUUID();
      ByteBuffer bb = ByteBuffer.allocate(16);
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
      String uuidSeed = Base64.getEncoder().encodeToString(bb.array()).substring(0, 16);
      this.setSeed(uuidSeed);
   }

   private void saveCurrentSeed(Button btn) {
      this.cfg.savedSeeds.add(this.dataProvider.seed());
      this.saveSeed.active = false;
      this.updateSeedListWidget();
   }

   public void deleteSeed(String seed) {
      this.cfg.savedSeeds.remove(seed);
      this.updateSeedListWidget();
   }

   public void setSeed(String seed) {
      if (!Objects.equals(this.dataProvider.seed(), seed) && this.dataProvider.seedIsEditable()) {
         boolean initialInhibitUpdates = this.inhibitUpdates;
         this.inhibitUpdates = true;

         try {
            this.dataProvider.updateSeed(seed);
         } finally {
            this.inhibitUpdates = initialInhibitUpdates;
         }

         this.updateSettings();
      }
   }

   private void updateSeedListWidget() {
      List<SeedsList.SeedEntry> seedEntries = this.cfg.savedSeeds.stream().map(this.seedsList::createEntry).toList();
      this.seedsList.replaceEntries(seedEntries);
      int idx = this.cfg.savedSeeds.indexOf(this.dataProvider.seed());
      if (idx >= 0) {
         this.seedsList.setSelected(seedEntries.get(idx));
      }
   }

   public void resetTabs() {
      this.onTabButtonChange(this.switchBiomes, DisplayType.BIOMES);
   }

   private void moveList(BaseObjectSelectionList<?> theList) {
      if ((!theList.active || theList.getX() <= 0) && (theList.active || theList.getX() >= 0)) {
         int newX = theList.getX() + (theList.active ? 4096 : -4096);
         theList.setPosition(newX, theList.getY());
      }
   }

   private void onTabButtonChange(Button btn, DisplayType type) {
      this.biomesList.visible = false;
      this.biomesList.active = false;
      this.structuresList.visible = false;
      this.structuresList.active = false;
      this.seedsList.visible = false;
      this.seedsList.active = false;
      this.switchBiomes.active = true;
      this.switchStructures.active = true;
      this.switchSeeds.active = true;
      this.resetDefaultStructureVisibility.visible = false;
      this.searchBiomeButton.visible = false;
      if (this.cfg.sampleStructures) {
         this.switchStructures.setTooltip(null);
      } else {
         this.switchStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SWITCH_STRUCT_DISABLED));
         this.switchStructures.active = false;
      }

      btn.active = false;
      switch (type) {
         case BIOMES:
            this.biomesList.visible = true;
            this.biomesList.active = true;
            this.searchBiomeButton.visible = true;
            break;
         case STRUCTURES:
            this.resetDefaultStructureVisibility.visible = true;
            this.structuresList.visible = true;
            this.structuresList.active = true;
            break;
         case SEEDS:
            this.seedsList.visible = true;
            this.seedsList.active = true;
      }

      this.moveList(this.biomesList);
      this.moveList(this.structuresList);
      this.moveList(this.seedsList);
   }

   public synchronized void start() {
      WorldPreview.LOGGER.info("Start generating biome data...");
      if (this.dataProvider.seed().isEmpty()) {
         this.randomizeSeed(null);
      }

      this.inhibitUpdates = false;
      this.updateSettings();
   }

   public synchronized void stop() {
      WorldPreview.LOGGER.info("Stop generating biome data...");
      this.inhibitUpdates = true;
      this.workManager.cancel();
   }

   public void doLayout(ScreenRectangle screenRectangle) {
      if (screenRectangle == null) {
          assert this.minecraft.screen != null;
          screenRectangle = this.minecraft.screen.getRectangle();
      }

      this.lastScreenRectangle = screenRectangle;
      int leftWidth = Math.max(130, Math.min(180, screenRectangle.width() / 3));
      int left = screenRectangle.left() + 3;
      int previewLeft = left + leftWidth + 3;
      int top = screenRectangle.top() + 2;
      int bottom = screenRectangle.bottom() - 32;
      int expand = this.toggleExpand.selected ? 24 : 0;
      this.previewDisplay.setPosition(previewLeft, top + expand + 1);
      this.previewDisplay.setSize(screenRectangle.right() - this.previewDisplay.getX() - 4, screenRectangle.bottom() - this.previewDisplay.getY() - 14);
      this.seedEdit.setWidth(leftWidth - 1 - 44);
      this.seedEdit.setX(left);
      this.seedEdit.setY(bottom + 1);
      this.randomSeedButton.setX(left + leftWidth - 20);
      this.randomSeedButton.setY(bottom);
      this.saveSeed.setX(left + leftWidth - 22 - 20);
      this.saveSeed.setY(bottom);
      int width = 21;
      int i = 1;
      this.resetToZeroZero.setPosition(left, top);
      this.toggleShowStructures.setPosition(left + width * i++, top);
      this.toggleBiomes.setPosition(left + width * i++, top);
      this.toggleHeightmap.setPosition(left + width * i++, top);
      this.settings.setPosition(left + width * i++, top);
      this.toggleExpand.setPosition(left + width * i, top);

      this.toggleTFCTemperature.setPosition(previewLeft, top);
      this.toggleTFCRainfall.setPosition(previewLeft + width, top);
      this.toggleTFCLandWater.setPosition(previewLeft + width * 2, top);
      this.toggleTFCRockTop.setPosition(previewLeft + width * 3, top);
      this.toggleTFCRockMid.setPosition(previewLeft + width * 4, top);
      this.toggleTFCRockBot.setPosition(previewLeft + width * 5, top);
      this.toggleTFCRockType.setPosition(previewLeft + width * 6, top);
      this.toggleKaolinClay.setPosition(previewLeft + width * 7, top);
      int resolutionBtnRight = screenRectangle.right() - 8;
      this.cycleResolutionButton.setPosition(resolutionBtnRight - 50, top);
      top += 24;
      int switchBiomesWidth = 45;
      int switchSeedsWidth = 45;
      int switchStructuresWidth = leftWidth - switchBiomesWidth - switchSeedsWidth - 4;
      this.switchBiomes.setPosition(left, top);
      this.switchStructures.setPosition(left + switchBiomesWidth + 2, top);
      this.switchSeeds.setPosition(left + switchBiomesWidth + switchStructuresWidth + 4, top);
      this.switchBiomes.setWidth(switchBiomesWidth);
      this.switchStructures.setWidth(switchStructuresWidth);
      this.switchSeeds.setWidth(switchSeedsWidth);
      top += 24;
      int biomesListBottom = this.searchBiomeButton.visible ? bottom - 28 : bottom;
      this.biomesList.setPosition(left, top);
      this.biomesList.setSize(leftWidth, biomesListBottom - top - 4);
      this.searchBiomeButton.setPosition(left, biomesListBottom);
      this.searchBiomeButton.setWidth(leftWidth);
      this.rocksList.setPosition(left, top);
      this.rocksList.setSize(leftWidth, bottom - top - 4);
      this.seedsList.setPosition(left, top);
      this.seedsList.setSize(leftWidth, bottom - top - 4);
      bottom -= 24;
      this.resetDefaultStructureVisibility.setPosition(left, bottom);
      this.resetDefaultStructureVisibility.setWidth(leftWidth);
      this.structuresList.setPosition(left, top);
      this.structuresList.setSize(leftWidth, bottom - top - 4);
      this.moveList(this.biomesList);
      this.moveList(this.rocksList);
      this.moveList(this.structuresList);
      this.moveList(this.seedsList);
   }

   @Override
   public void close() {
      this.cancelBiomeSearch();
      this.biomeSearchExecutor.shutdownNow();
      this.workManager.cancel();
      this.previewDisplay.close();
      this.freeStructureIcons();
   }

   private void freeStructureIcons() {
      if (this.allStructureIcons != null) {
         Arrays.stream(this.allStructureIcons).filter(Objects::nonNull).forEach(NativeImage::close);
      }
   }

   public List<BiomesList.BiomeEntry> allBiomes() {
      return Arrays.stream(this.allBiomes).sorted(Comparator.comparing(BiomesList.BiomeEntry::name)).toList();
   }

   /**
    * Returns biomes filtered for display in the biome list.
    * Hides vanilla overworld biomes. Shows nether/end biomes only when the matching dimension is selected.
    */
   private List<BiomesList.BiomeEntry> filteredBiomes() {
      boolean isNether = this.renderSettings.dimension != null
            && this.renderSettings.dimension.equals(LevelStem.NETHER.location());
      boolean isEnd = this.renderSettings.dimension != null
            && this.renderSettings.dimension.equals(LevelStem.END.location());

      return Arrays.stream(this.allBiomes)
            .filter(entry -> {
               ResourceLocation loc = entry.entry().key().location();
               if (!"minecraft".equals(loc.getNamespace())) {
                  return true; // Always show non-vanilla biomes (TFC, etc.)
               }
               // Vanilla biomes: only show nether/end when the matching dimension is active
               if (entry.entry().is(IS_NETHER)) {
                  return isNether;
               }
               if (entry.entry().is(IS_END)) {
                  return isEnd;
               }
               // Vanilla overworld biomes: always hidden
               return false;
            })
            .sorted(Comparator.comparing(BiomesList.BiomeEntry::name))
            .toList();
   }

   public List<ResourceLocation> levelStemKeys() {
      return this.levelStemKeys;
   }

   public Registry<LevelStem> levelStemRegistry() {
      return this.levelStemRegistry;
   }

   @Override
   public BiomesList.BiomeEntry biome4Id(int id) {
      return this.allBiomes[id];
   }

   @Override
   public StructuresList.StructureEntry structure4Id(int id) {
      return this.allStructures[id];
   }

   @Override
   public NativeImage[] structureIcons() {
      return this.allStructureIcons;
   }

   @Override
   public NativeImage playerIcon() {
      return this.playerIcon;
   }

   @Override
   public NativeImage spawnIcon() {
      return this.spawnIcon;
   }

   @Override
   public NativeImage worldSpawnIcon() {
      return this.worldSpawnIcon;
   }

   @Override
   public ItemStack[] structureItems() {
      return Arrays.stream(this.allStructures).map(StructuresList.StructureEntry::itemStack).toArray(ItemStack[]::new);
   }

   @Override
   public void onBiomeVisuallySelected(BiomesList.BiomeEntry entry) {
      this.biomesList.setSelected(entry, true);
      this.previewDisplay.setHighlightCaves(false);
   }

   @Override
   public PreviewData previewData() {
      return this.previewData;
   }

   public WorkManager workManager() {
      return this.workManager;
   }

   @Override
   public StructureRenderInfo[] renderStructureMap() {
      return this.allStructures;
   }

   @Override
   public int[] heightColorMap() {
      ColorMap colorMap = this.previewData.colorMaps().get(this.cfg.colorMap);
      if (colorMap == null) {
         int[] black = new int[this.workManager.yMax() - this.workManager.yMin()];
         Arrays.fill(black, -16777216);
         return black;
      } else {
         return colorMap.bake(this.workManager.yMin(), this.workManager.yMax(), this.cfg.heightmapMinY, this.cfg.heightmapMaxY);
      }
   }

   @Override
   public int[] tfcTemperatureColorMap() {
      // colorMaps uses String keys, not ResourceLocation
      ColorMap colorMap = this.previewData.colorMaps().get("world_preview:tfc_temperature");
      if (colorMap == null) {
         int[] black = new int[1024];
         Arrays.fill(black, -16777216);
         return black;
      } else {
         return colorMap.bake(1024);
      }
   }

   @Override
   public int[] tfcRainfallColorMap() {
      // colorMaps uses String keys, not ResourceLocation
      ColorMap colorMap = this.previewData.colorMaps().get("world_preview:tfc_rainfall");
      if (colorMap == null) {
         int[] black = new int[1024];
         Arrays.fill(black, -16777216);
         return black;
      } else {
         return colorMap.bake(1024);
      }
   }

   @Override
   public int yMin() {
      return this.workManager.yMin();
   }

   @Override
   public int yMax() {
      return this.workManager.yMax();
   }

   @Override
   public boolean isUpdating() {
      return this.isUpdating;
   }

   @Override
   public boolean setupFailed() {
      return this.setupFailed;
   }

   @NotNull
   @Override
   public PreviewDisplayDataProvider.PlayerData getPlayerData(UUID playerId) {
      if (this.workManager != null && this.workManager.sampleUtils() != null) {
         ServerPlayer player = this.workManager.sampleUtils().getPlayers(playerId);
         if (player == null) {
            return new PlayerData(null, null);
         } else {
            ResourceKey<Level> playerDimension = player.level().dimension();
            ResourceKey<Level> respawnDimension = player.getRespawnDimension();
            ResourceKey<Level> currentDimension = this.workManager.sampleUtils().dimension();
            return new PlayerData(
               currentDimension.equals(playerDimension) ? player.blockPosition() : null,
               currentDimension.equals(respawnDimension) ? player.getRespawnPosition() : null
            );
         }
      } else {
         return new PlayerData(null, null);
      }
   }

   @Nullable
   @Override
   public BlockPos getWorldSpawnPos() {
      // For TFC worlds, use spawn center from TFC settings
      if (this.workManager != null) {
         // Check for settings override first (user-modified in TFCTab)
         net.dries007.tfc.world.settings.Settings overrideSettings = this.workManager.getTFCSettingsOverride();
         if (overrideSettings != null) {
             return new BlockPos(overrideSettings.spawnCenterX(), 64, overrideSettings.spawnCenterZ());
         }

         // Check if TFC is enabled and get settings from chunk generator
         if (this.workManager.isTFCEnabled()) {
            TFCSampleUtils tfcUtils = this.workManager.tfcSampleUtils();
            if (tfcUtils != null && tfcUtils.settings() != null) {
               net.dries007.tfc.world.settings.Settings settings = tfcUtils.settings();
                return new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
            }
         }
      }
      // For vanilla worlds, spawn is typically around (0, 0)
      return new BlockPos(0, 64, 0);
   }

   @Override
   public int getWorldSpawnDistance() {
      // For TFC worlds, return spawn distance from TFC settings
      if (this.workManager != null) {
         // Check for settings override first (user-modified in TFCTab)
         net.dries007.tfc.world.settings.Settings overrideSettings = this.workManager.getTFCSettingsOverride();
         if (overrideSettings != null) {
            return overrideSettings.spawnDistance();
         }

         // Check if TFC is enabled and get settings from chunk generator
         if (this.workManager.isTFCEnabled()) {
            TFCSampleUtils tfcUtils = this.workManager.tfcSampleUtils();
            if (tfcUtils != null && tfcUtils.settings() != null) {
               return tfcUtils.settings().spawnDistance();
            }
         }
      }
      // For vanilla worlds, no spawn area to display
      return 0;
   }

   public ToggleButton toggleShowStructures() {
      return this.toggleShowStructures;
   }

   public ToggleButton toggleHeightmap() {
      return this.toggleHeightmap;
   }

   public PreviewContainerDataProvider dataProvider() {
      return this.dataProvider;
   }

   public List<AbstractWidget> widgets() {
      return this.toRender;
   }

   /**
    * Creates a simple world spawn icon (gold compass-like marker).
    * The icon is a 16x16 image with a gold diamond/star shape.
    */
   private static NativeImage createWorldSpawnIcon() {
      NativeImage icon = new NativeImage(16, 16, true);
      // Gold/yellow color in ABGR format (NativeImage uses ABGR)
      int gold = 0xFF00D4FF;  // ABGR: fully opaque gold/yellow
      int darkGold = 0xFF0090CC;  // Darker gold for outline

      // Draw a simple diamond/compass shape
      // Center point marker with compass-like arms
      int cx = 7, cy = 7;

      // Fill with transparent
      for (int x = 0; x < 16; x++) {
         for (int y = 0; y < 16; y++) {
            icon.setPixelRGBA(x, y, 0);
         }
      }

      // Draw diamond shape (rotated square)
      for (int i = 0; i <= 6; i++) {
         // Top half
         icon.setPixelRGBA(cx, cy - i, i == 6 ? darkGold : gold);
         icon.setPixelRGBA(cx, cy + i, i == 6 ? darkGold : gold);
         icon.setPixelRGBA(cx - i, cy, i == 6 ? darkGold : gold);
         icon.setPixelRGBA(cx + i, cy, i == 6 ? darkGold : gold);
      }

      // Add diagonal lines for compass effect
      for (int i = 1; i <= 4; i++) {
         icon.setPixelRGBA(cx - i, cy - i, gold);
         icon.setPixelRGBA(cx + i, cy - i, gold);
         icon.setPixelRGBA(cx - i, cy + i, gold);
         icon.setPixelRGBA(cx + i, cy + i, gold);
      }

      // Center dot
      icon.setPixelRGBA(cx, cy, darkGold);
      icon.setPixelRGBA(cx + 1, cy, darkGold);
      icon.setPixelRGBA(cx, cy + 1, darkGold);
      icon.setPixelRGBA(cx + 1, cy + 1, darkGold);

      return icon;
   }

   public enum DisplayType {
      BIOMES,
      STRUCTURES,
      SEEDS;

      public Component component() {
         return toComponent(this);
      }

      public static Component toComponent(DisplayType x) {
         return Component.translatable("world_preview.preview.btn-cycle." + x.name());
      }
   }

    public void requestReload() {
        this.workManager.cancel();
        this.updateSettings(); // calls the async rebuild path
    }

    public void requestRefresh() {
        this.updateSettings();
    }

    /**
     * Cycles through available resolution options (1, 2, 4, 8, 16 pixels per chunk).
     */
    private void cycleResolution() {
        int current = this.renderSettings.pixelsPerChunk();
        int next = switch (current) {
            case 1 -> 2;
            case 2 -> 4;
            case 4 -> 8;
            case 8 -> 16;
            case 16 -> 1;
            default -> 4;
        };
        this.renderSettings.setPixelsPerChunk(next);
        this.cycleResolutionButton.setMessage(this.getResolutionLabel());
        this.workManager.onResolutionChanged();
        this.previewDisplay.resizeImage();
    }

    /**
     * Gets the label for the resolution button showing current pixels per chunk.
     */
    private Component getResolutionLabel() {
        int ppc = this.renderSettings.pixelsPerChunk();
        return Component.literal(ppc + "x");
    }
}
