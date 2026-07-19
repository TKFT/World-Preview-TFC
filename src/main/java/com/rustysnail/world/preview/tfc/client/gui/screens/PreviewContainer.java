package com.rustysnail.world.preview.tfc.client.gui.screens;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.mojang.blaze3d.platform.NativeImage;
import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.BiomeSearchTask;
import com.rustysnail.world.preview.tfc.backend.WorkManager;
import com.rustysnail.world.preview.tfc.backend.color.ColorMap;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportController;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterMapExporter;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterMapExporter.Context;
import com.rustysnail.world.preview.tfc.backend.export.TFCLandWaterClassifier;
import com.rustysnail.world.preview.tfc.backend.search.FeatureDetectors;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropRegistry;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropSuitability;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.PreviewContainerDataProvider;
import com.rustysnail.world.preview.tfc.client.gui.PreviewDisplayDataProvider;
import com.rustysnail.world.preview.tfc.client.gui.widgets.OldStyleImageButton;
import com.rustysnail.world.preview.tfc.client.gui.widgets.PreviewDisplay;
import com.rustysnail.world.preview.tfc.client.gui.widgets.ToggleButton;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BaseObjectSelectionList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomesList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.RocksList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.SeedsList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.StructuresList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.TFCCropList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.TFCMapValueList;
import com.rustysnail.world.preview.tfc.mixin.client.ScreenAccessor;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongMap.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
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
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.world.ChunkGeneratorExtension;

public class PreviewContainer implements AutoCloseable, PreviewDisplayDataProvider
{
    public static final TagKey<Biome> C_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "caves"));
    public static final TagKey<Biome> C_IS_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_cave"));
    public static final TagKey<Biome> FORGE_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "caves"));
    public static final TagKey<Biome> FORGE_IS_CAVE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_cave"));
    private static final TagKey<Biome> IS_NETHER = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_nether"));
    private static final TagKey<Biome> IS_END = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_end"));
    public static final TagKey<Structure> DISPLAY_BY_DEFAULT = TagKey.create(
        Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("c", "display_on_map_by_default")
    );
    public static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.parse("world_preview_tfc:textures/gui/buttons.png");
    private final PreviewContainerDataProvider dataProvider;
    private final Minecraft minecraft;
    private final WorldPreview worldPreview;
    private final WorldPreviewConfig cfg;
    private final WorkManager workManager;
    private final RenderSettings renderSettings;
    private final PreviewMappingData previewMappingData;
    @Nullable private volatile BlockPos pendingCenter;
    private PreviewData previewData;
    private List<ResourceLocation> levelStemKeys;
    private Registry<LevelStem> levelStemRegistry;
    private final EditBox seedEdit;
    private final Button randomSeedButton;
    private final Button saveSeed;
    private final Button settings;
    private final Button resetToZeroZero;
    private final ToggleButton toggleShowStructures;
    private final ToggleButton toggleShowFeatures;
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
    private final ToggleButton toggleTFCForestType;
    private final ToggleButton toggleTFCTreeSpecies;
    private final ToggleButton toggleTFCSoilType;
    private final ToggleButton toggleTFCCropSuitability;
    private final Button toggleCropWaterMode;
    private final ToggleButton toggleTFCHotspot;
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
    private final TFCMapValueList tfcMapValueList;
    private final List<TFCMapValueList.ValueEntry> forestTypeEntries;
    private final List<TFCMapValueList.ValueEntry> soilTypeEntries;
    // Crop-suitability side panel: a crop selector (main list) plus the shared suitability legend
    // (tfcMapValueList) shown compact at the bottom, and a Rain-Fed / Irrigated toggle.
    private final TFCCropList cropList;
    private final List<TFCMapValueList.ValueEntry> suitabilityEntries;
    // Rebuilt from the runtime tree-species registry each time Tree Species mode is entered, so
    // addon species that only appear once a world is loaded are included.
    private List<TFCMapValueList.ValueEntry> treeSpeciesEntries = new ArrayList<>();
    // The currently selected side-panel tab. Together with the render mode it decides which single
    // side list is shown; see updateSidePanelVisibility().
    private DisplayType currentDisplayType = DisplayType.BIOMES;
    private StructuresList.StructureEntry[] allStructures;
    private NativeImage[] allStructureIcons;
    private NativeImage[] allFeatureIcons;
    @Nullable private NativeImage playerIcon;
    @Nullable private NativeImage spawnIcon;
    @Nullable private NativeImage worldSpawnIcon;
    private ScreenRectangle lastScreenRectangle;
    private boolean inhibitUpdates = true;
    private boolean isUpdating = false;
    private boolean setupFailed = false;
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor();
    private final Executor serverThreadPoolExecutor;
    private final AtomicInteger reloadRevision = new AtomicInteger(0);
    private final List<AbstractWidget> toRender = new ArrayList<>();
    private final Button searchBiomeButton;
    private final Checkbox islandCheckbox;
    private final ExecutorService biomeSearchExecutor = Executors.newSingleThreadExecutor();
    private final LandWaterExportController landWaterExporter;
    @Nullable private BiomeSearchTask currentSearchTask = null;
    private boolean isSearching = false;

    public PreviewContainer(Screen screen, PreviewContainerDataProvider previewContainerDataProvider)
    {
        Font font = ((ScreenAccessor) screen).getFont();
        this.dataProvider = previewContainerDataProvider;
        this.minecraft = Minecraft.getInstance();
        this.allBiomes = new BiomesList.BiomeEntry[0];
        this.worldPreview = WorldPreview.get();
        this.cfg = this.worldPreview.cfg();
        this.workManager = this.worldPreview.workManager();
        this.previewMappingData = this.worldPreview.biomeColorMap();
        this.renderSettings = this.worldPreview.renderSettings();
        this.serverThreadPoolExecutor = this.worldPreview.serverThreadPoolExecutor();
        this.landWaterExporter = new LandWaterExportController(this.cfg.numThreads());
        this.seedEdit = new EditBox(font, 0, 0, 100, 18, WorldPreviewComponents.SEED_FIELD);
        this.seedEdit.setHint(WorldPreviewComponents.SEED_FIELD);
        this.seedEdit.setValue(this.dataProvider.seed());
        this.seedEdit.setResponder(this::setSeed);
        this.seedEdit.setTooltip(Tooltip.create(WorldPreviewComponents.SEED_LABEL));
        this.seedEdit.active = this.dataProvider.seedIsEditable();
        this.toRender.add(this.seedEdit);
        this.randomSeedButton = new OldStyleImageButton(0, 0, 20, 20, 0, 20, 20, BUTTONS_TEXTURE, 920, 60, this::randomizeSeed);
        this.randomSeedButton.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_RANDOM));
        this.randomSeedButton.active = this.dataProvider.seedIsEditable();
        this.toRender.add(this.randomSeedButton);
        this.saveSeed = new OldStyleImageButton(0, 0, 20, 20, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, this::saveCurrentSeed);
        this.saveSeed.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SAVE_SEED));
        this.saveSeed.active = false;
        this.toRender.add(this.saveSeed);
        this.settings = new OldStyleImageButton(0, 0, 20, 20, 60, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> {
            ChunkGeneratorExtension vanillaExt = this.dataProvider.vanillaTFCExtension();
            boolean tfcReadOnly = (vanillaExt == null);
            ChunkGeneratorExtension ext = vanillaExt;
            if (ext == null)
            {
                var gen = this.workManager.chunkGenerator();
                ext = (gen instanceof ChunkGeneratorExtension e) ? e : null;
            }
            this.minecraft.setScreen(new SettingsScreen(screen, this, ext, false, tfcReadOnly));
        });
        this.settings.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SETTINGS));
        this.settings.active = false;
        this.toRender.add(this.settings);
        this.resetToZeroZero = new OldStyleImageButton(0, 0, 20, 20, 120, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.renderSettings.resetCenter());
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
            WorldPreviewComponents.BTN_SEARCH_BIOME,
            this::onSearchBiomeClick
        ).size(100, 20).build();
        this.searchBiomeButton.visible = false;
        this.searchBiomeButton.active = false;
        this.searchBiomeButton.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SEARCH_BIOME_TOOLTIP));
        this.toRender.add(this.searchBiomeButton);
        this.islandCheckbox = Checkbox.builder(WorldPreviewComponents.ISLAND_CHECKBOX, font)
            .selected(false)
            .build();
        this.islandCheckbox.visible = false;
        this.islandCheckbox.active = true;
        this.islandCheckbox.setTooltip(Tooltip.create(WorldPreviewComponents.ISLAND_CHECKBOX_TOOLTIP));
        this.toRender.add(this.islandCheckbox);
        this.rocksList = new RocksList(this.minecraft, 200, 300, 4, 100);
        this.rocksList.visible = false;
        this.rocksList.active = false;
        this.toRender.add(this.rocksList);
        this.allRocks = new RocksList.RockEntry[TFCSampleUtils.ROCK_NAMES.length];
        for (short ri = 0; ri < TFCSampleUtils.ROCK_NAMES.length; ri++)
        {
            this.allRocks[ri] = this.rocksList.createRockEntry(ri);
        }
        this.allRockTypes = new RocksList.RockEntry[TFCSampleUtils.ROCK_TYPE_NAMES.length];
        for (short rti = 0; rti < TFCSampleUtils.ROCK_TYPE_NAMES.length; rti++)
        {
            this.allRockTypes[rti] = this.rocksList.createRockTypeEntry(rti);
        }
        this.tfcMapValueList = new TFCMapValueList(this.minecraft, 200, 300, 4, 100);
        this.tfcMapValueList.visible = false;
        this.tfcMapValueList.active = false;
        this.toRender.add(this.tfcMapValueList);

        // Water is the first entry in both tree modes, then every forest type / species.
        this.forestTypeEntries = new ArrayList<>();
        this.forestTypeEntries.add(this.tfcMapValueList.createEntry(
            TFCSampleUtils.VALUE_WATER, "Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER)));
        for (short fi = 0; fi < TFCSampleUtils.forestTypeCount(); fi++)
        {
            this.forestTypeEntries.add(this.tfcMapValueList.createEntry(
                fi, TFCSampleUtils.getForestTypeName(fi), TFCSampleUtils.getForestTypeColor(fi)));
        }

        // Soil type legend: Water first, then every soil order (fixed set, so built once here).
        this.soilTypeEntries = new ArrayList<>();
        this.soilTypeEntries.add(this.tfcMapValueList.createEntry(
            TFCSampleUtils.VALUE_WATER, "Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER)));
        for (short si = 0; si < TFCSampleUtils.soilTypeCount(); si++)
        {
            this.soilTypeEntries.add(this.tfcMapValueList.createEntry(
                si, TFCSampleUtils.getSoilTypeName(si), TFCSampleUtils.getSoilTypeColor(si)));
        }

        // Crop suitability legend (fixed): the five suitability classes, then Water and No Data.
        this.suitabilityEntries = new ArrayList<>();
        for (short cv = 0; cv < TFCCropSuitability.suitabilityCount(); cv++)
        {
            this.suitabilityEntries.add(this.tfcMapValueList.createEntry(
                cv, TFCCropSuitability.getSuitabilityName(cv), TFCCropSuitability.getSuitabilityColor(cv)));
        }
        this.suitabilityEntries.add(this.tfcMapValueList.createEntry(
            TFCSampleUtils.VALUE_WATER, "Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER)));
        this.suitabilityEntries.add(this.tfcMapValueList.createEntry(
            TFCSampleUtils.VALUE_INVALID, TFCCropSuitability.getSuitabilityName(TFCSampleUtils.VALUE_INVALID),
            TFCCropSuitability.getSuitabilityColor(TFCSampleUtils.VALUE_INVALID)));

        // Crop selector list (main list in crop mode).
        this.cropList = new TFCCropList(this.minecraft, 200, 300, 4, 100);
        this.cropList.visible = false;
        this.cropList.active = false;
        this.toRender.add(this.cropList);

        this.structuresList = new StructuresList(this.minecraft, 200, 300, 4, 100);
        this.toRender.add(this.structuresList);
        this.seedsList = new SeedsList(this.minecraft, this);
        this.updateSeedListWidget();
        this.toRender.add(this.seedsList);
        this.previewDisplay = new PreviewDisplay(this.minecraft, this, WorldPreviewComponents.TITLE);
        this.toRender.add(this.previewDisplay);
        this.toggleShowStructures = new ToggleButton(
            0, 0, 20, 20, 140, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.renderSettings.hideAllStructures = !((ToggleButton) x).selected
        );
        this.toggleShowStructures.selected = !this.renderSettings.hideAllStructures;
        this.toggleShowStructures.active = false;
        this.toRender.add(this.toggleShowStructures);
        this.toggleShowFeatures = new ToggleButton(
            0, 0, 20, 20, 140, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> {
            boolean selected = ((ToggleButton) x).selected;
            this.previewDisplay.setShowFeatures(selected);
            this.renderSettings.featureOverlay = selected;
            this.workManager.onFeatureOverlayChanged();
        }
        );
        this.toggleShowFeatures.selected = false;
        this.toggleShowFeatures.active = true;
        this.toggleShowFeatures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_FEATURES));
        this.toRender.add(this.toggleShowFeatures);
        this.toggleBiomes = new ToggleButton(0, 0, 20, 20, 360, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.BIOMES));
        this.toggleBiomes.visible = true;
        this.toggleBiomes.active = true;
        this.toggleBiomes.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_BIOMES));
        this.toRender.add(this.toggleBiomes);
        this.toggleHeightmap = new ToggleButton(
            0, 0, 20, 20, 200, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.HEIGHTMAP)
        );
        this.toggleHeightmap.visible = true;
        this.toggleHeightmap.active = false;
        this.toRender.add(this.toggleHeightmap);
        this.toggleTFCTemperature = new ToggleButton(
            0, 0, 20, 20, 400, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_TEMPERATURE)
        );
        this.toggleTFCTemperature.visible = false;
        this.toggleTFCTemperature.active = false;
        this.toggleTFCTemperature.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_TEMPERATURE));
        this.toRender.add(this.toggleTFCTemperature);
        this.toggleTFCRainfall = new ToggleButton(
            0, 0, 20, 20, 440, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_RAINFALL)
        );
        this.toggleTFCRainfall.visible = false;
        this.toggleTFCRainfall.active = false;
        this.toggleTFCRainfall.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_RAINFALL));
        this.toRender.add(this.toggleTFCRainfall);
        this.toggleTFCLandWater = new ToggleButton(
            0, 0, 20, 20, 480, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_LAND_WATER)
        );
        this.toggleTFCLandWater.visible = false;
        this.toggleTFCLandWater.active = false;
        this.toggleTFCLandWater.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_LAND_WATER));
        this.toRender.add(this.toggleTFCLandWater);
        this.toggleTFCRockTop = new ToggleButton(
            0, 0, 20, 20, 520, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_TOP)
        );
        this.toggleTFCRockTop.visible = false;
        this.toggleTFCRockTop.active = false;
        this.toggleTFCRockTop.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_TOP));
        this.toRender.add(this.toggleTFCRockTop);
        this.toggleTFCRockMid = new ToggleButton(
            0, 0, 20, 20, 560, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_MID)
        );
        this.toggleTFCRockMid.visible = false;
        this.toggleTFCRockMid.active = false;
        this.toggleTFCRockMid.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_MID));
        this.toRender.add(this.toggleTFCRockMid);
        this.toggleTFCRockBot = new ToggleButton(
            0, 0, 20, 20, 600, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_BOT)
        );
        this.toggleTFCRockBot.visible = false;
        this.toggleTFCRockBot.active = false;
        this.toggleTFCRockBot.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_BOT));
        this.toRender.add(this.toggleTFCRockBot);
        this.toggleTFCRockType = new ToggleButton(
            0, 0, 20, 20, 640, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_ROCK_TYPE)
        );
        this.toggleTFCRockType.visible = false;
        this.toggleTFCRockType.active = false;
        this.toggleTFCRockType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_TYPE));
        this.toRender.add(this.toggleTFCRockType);
        this.toggleKaolinClay = new ToggleButton(0, 0, 20, 20, 680, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_KAOLINITE));
        this.toggleKaolinClay.visible = false;
        this.toggleKaolinClay.active = false;
        this.toggleKaolinClay.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_KAOLINITE));
        this.toRender.add(this.toggleKaolinClay);

        this.toggleTFCForestType = new ToggleButton(
            0, 0, 20, 20, 760, 20, 20, 20, BUTTONS_TEXTURE, 920, 60,
            x -> this.selectViewMode(RenderSettings.RenderMode.TFC_FOREST_TYPE)
        );
        this.toggleTFCForestType.visible = false;
        this.toggleTFCForestType.active = false;
        this.toggleTFCForestType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_FOREST_TYPE));
        this.toRender.add(this.toggleTFCForestType);

        this.toggleTFCTreeSpecies = new ToggleButton(
            0, 0, 20, 20, 800, 20, 20, 20, BUTTONS_TEXTURE, 920, 60,
            x -> this.selectViewMode(RenderSettings.RenderMode.TFC_TREE_SPECIES)
        );
        this.toggleTFCTreeSpecies.visible = false;
        this.toggleTFCTreeSpecies.active = false;
        this.toggleTFCTreeSpecies.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_TREE_SPECIES));
        this.toRender.add(this.toggleTFCTreeSpecies);

        // Soil icon lives in the widened button atlas (selected column x=840, unselected x=860).
        this.toggleTFCSoilType = new ToggleButton(
            0, 0, 20, 20, 840, 20, 20, 20, BUTTONS_TEXTURE, 920, 60,
            x -> this.selectViewMode(RenderSettings.RenderMode.TFC_SOIL_TYPE)
        );
        this.toggleTFCSoilType.visible = false;
        this.toggleTFCSoilType.active = false;
        this.toggleTFCSoilType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_SOIL_TYPE));
        this.toRender.add(this.toggleTFCSoilType);

        // Crop icon lives in the widened button atlas (selected column x=880, unselected x=900).
        this.toggleTFCCropSuitability = new ToggleButton(
            0, 0, 20, 20, 880, 20, 20, 20, BUTTONS_TEXTURE, 920, 60,
            x -> this.selectViewMode(RenderSettings.RenderMode.TFC_CROP_SUITABILITY)
        );
        this.toggleTFCCropSuitability.visible = false;
        this.toggleTFCCropSuitability.active = false;
        this.toggleTFCCropSuitability.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_CROP_SUITABILITY));
        this.toRender.add(this.toggleTFCCropSuitability);

        // Rain-Fed / Irrigated hydration toggle (crop mode only). Label reflects the current mode.
        this.toggleCropWaterMode = Button.builder(this.getCropWaterModeLabel(), x -> this.cycleCropWaterMode())
            .size(70, 20)
            .build();
        this.toggleCropWaterMode.visible = false;
        this.toggleCropWaterMode.active = false;
        this.toggleCropWaterMode.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_CROP_WATER_MODE));
        this.toRender.add(this.toggleCropWaterMode);

        this.toggleTFCHotspot = new ToggleButton(0, 0, 20, 20, 720, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> this.selectViewMode(RenderSettings.RenderMode.TFC_HOTSPOT));
        this.toggleTFCHotspot.visible = false;
        this.toggleTFCHotspot.active = false;
        this.toggleTFCHotspot.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_HOTSPOT));
        this.toRender.add(this.toggleTFCHotspot);
        this.cycleResolutionButton = Button.builder(this.getResolutionLabel(), x -> this.cycleResolution())
            .size(50, 20)
            .build();
        this.cycleResolutionButton.visible = false;
        this.cycleResolutionButton.active = true;
        this.cycleResolutionButton.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_RESOLUTION_TOOLTIP));
        this.toRender.add(this.cycleResolutionButton);
        this.toggleExpand = new ToggleButton(0, 0, 20, 20, 320, 20, 20, 20, BUTTONS_TEXTURE, 920, 60, x -> {
            boolean expanded = ((ToggleButton) x).selected;
            this.cycleResolutionButton.visible = expanded;
            boolean isTFC = this.workManager.isTFCEnabled();
            this.toggleTFCTemperature.visible = expanded && isTFC;
            this.toggleTFCRainfall.visible = expanded && isTFC;
            this.toggleTFCLandWater.visible = expanded && isTFC;
            this.toggleTFCRockTop.visible = expanded && isTFC;
            this.toggleTFCRockMid.visible = expanded && isTFC;
            this.toggleTFCRockBot.visible = expanded && isTFC;
            this.toggleTFCRockType.visible = expanded && isTFC;
            this.toggleKaolinClay.visible = expanded && isTFC;
            this.toggleTFCForestType.visible = expanded && isTFC;
            this.toggleTFCTreeSpecies.visible = expanded && isTFC;
            this.toggleTFCSoilType.visible = expanded && isTFC;
            this.toggleTFCCropSuitability.visible = expanded && isTFC;
            this.toggleTFCHotspot.visible = expanded && isTFC;
            this.doLayout(this.lastScreenRectangle);
        });
        this.toggleExpand.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_EXPAND));
        this.toRender.add(this.toggleExpand);
        this.biomesList.setBiomeChangeListener(x -> {
            this.previewDisplay.setSelectedBiomeId(x == null ? -1 : x.id());
            this.searchBiomeButton.active = (x != null) && this.workManager.isSetup() && !this.isSearching;
        });
        this.rocksList.setRockChangeListener(x -> this.previewDisplay.setSelectedRockId(x == null ? -1 : x.id()));
        this.tfcMapValueList.setChangeListener(
            x -> this.previewDisplay.setSelectedTFCMapValue(x == null ? Short.MIN_VALUE : x.id()));
        this.cropList.setChangeListener(x -> {
            if (x != null)
            {
                this.workManager.setSelectedCrop(x.cropId());
            }
        });
        this.dataProvider.registerSettingsChangeListener(this::updateSettings);
        this.onTabButtonChange(this.switchBiomes, DisplayType.BIOMES);
        this.selectViewMode(RenderSettings.RenderMode.BIOMES);
    }

    public void patchColorData()
    {
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

    private void selectViewMode(RenderSettings.RenderMode mode)
    {
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
        this.toggleTFCForestType.selected = false;
        this.toggleTFCTreeSpecies.selected = false;
        this.toggleTFCSoilType.selected = false;
        this.toggleTFCCropSuitability.selected = false;
        this.toggleTFCHotspot.selected = false;
        synchronized (this.renderSettings)
        {
            switch (mode)
            {
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
                case TFC_FOREST_TYPE:
                    this.toggleTFCForestType.selected = true;
                    break;
                case TFC_TREE_SPECIES:
                    this.toggleTFCTreeSpecies.selected = true;
                    break;
                case TFC_SOIL_TYPE:
                    this.toggleTFCSoilType.selected = true;
                    break;
                case TFC_CROP_SUITABILITY:
                    this.toggleTFCCropSuitability.selected = true;
                    break;
                case TFC_HOTSPOT:
                    this.toggleTFCHotspot.selected = true;
                    break;
            }

            this.renderSettings.mode = mode;
            WorldPreview.get().workManager().onRenderModeChanged();
        }

        boolean isBiomeMode = mode == RenderSettings.RenderMode.BIOMES;
        if (isBiomeMode)
        {
            this.biomesList.replaceEntries(this.filteredBiomes());
        }
        if (!isBiomeMode && this.isSearching)
        {
            this.cancelBiomeSearch();
        }

        boolean isRockMode = mode == RenderSettings.RenderMode.TFC_ROCK_TOP
            || mode == RenderSettings.RenderMode.TFC_ROCK_MID
            || mode == RenderSettings.RenderMode.TFC_ROCK_BOT
            || mode == RenderSettings.RenderMode.TFC_ROCK_TYPE;
        boolean isForestMode = mode == RenderSettings.RenderMode.TFC_FOREST_TYPE;
        boolean isSpeciesMode = mode == RenderSettings.RenderMode.TFC_TREE_SPECIES;
        boolean isSoilMode = mode == RenderSettings.RenderMode.TFC_SOIL_TYPE;
        boolean isCropMode = mode == RenderSettings.RenderMode.TFC_CROP_SUITABILITY;
        // Modes backed by the shared tfcMapValueList side legend (crop uses it as its fixed legend).
        boolean isMapValueMode = isForestMode || isSpeciesMode || isSoilMode || isCropMode;

        // Update entry sets while the lists are still hidden (visibility is applied last). Deactivate
        // the tree list before swapping entries so the old ValueEntry can't be re-selected.
        this.tfcMapValueList.visible = false;
        this.tfcMapValueList.active = false;

        if (isRockMode)
        {
            this.rocksList.replaceEntries(new ArrayList<>());
        }
        else
        {
            // Clear rock selection when leaving rock mode
            this.rocksList.setSelected(null);
            this.previewDisplay.setSelectedRockId((short) -1);
        }

        // Always clear the tree-map selection on any (re)entry: this covers a forest<->species
        // switch (where ids would otherwise carry over) and leaving either tree mode. Clearing
        // before replaceEntries prevents the old selected entry from being retained.
        this.tfcMapValueList.setSelected(null);
        if (isMapValueMode)
        {
            List<TFCMapValueList.ValueEntry> entries;
            if (isForestMode)
            {
                entries = this.forestTypeEntries;
            }
            else if (isSpeciesMode)
            {
                // Rebuild from the current runtime registry so addon species are present.
                this.treeSpeciesEntries = this.buildTreeSpeciesEntries();
                entries = this.treeSpeciesEntries;
            }
            else if (isSoilMode)
            {
                entries = this.soilTypeEntries;
            }
            else
            {
                entries = this.suitabilityEntries;
            }
            this.tfcMapValueList.replaceEntries(entries);
            this.tfcMapValueList.setScrollAmount(0.0); // reset scroll between the entry sets
        }
        else
        {
            this.previewDisplay.setSelectedTFCMapValue(Short.MIN_VALUE);
        }

        // Crop mode also has a crop selector (rebuilt from the runtime registry) and a water toggle.
        if (isCropMode)
        {
            this.refreshCropList();
        }

        // Apply the single-active-list visibility for the new mode (also runs doLayout/moveList).
        this.updateSidePanelVisibility();
    }

    /**
     * Water entry first, then every species in the runtime tree-species registry (TFC + addons).
     */
    private List<TFCMapValueList.ValueEntry> buildTreeSpeciesEntries()
    {
        List<TFCMapValueList.ValueEntry> entries = new ArrayList<>();
        entries.add(this.tfcMapValueList.createEntry(
            TFCSampleUtils.VALUE_WATER, "Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER)));
        int count = TFCSampleUtils.treeSpeciesCount();
        for (short ti = 0; ti < count; ti++)
        {
            entries.add(this.tfcMapValueList.createEntry(
                ti, TFCSampleUtils.getTreeSpeciesName(ti), TFCSampleUtils.getTreeSpeciesColor(ti)));
        }
        return entries;
    }

    private void onSearchBiomeClick(Button btn)
    {
        if (this.isSearching)
        {
            this.cancelBiomeSearch();
            return;
        }

        BiomesList.BiomeEntry selected = this.biomesList.getSelected();
        if (selected == null || !this.workManager.isSetup()) return;

        ResourceKey<Biome> targetBiome = selected.entry().key();
        BlockPos center = this.renderSettings.center();
        SampleUtils sampleUtils = this.workManager.sampleUtils();
        if (sampleUtils == null)
        {
            return;
        }

        this.isSearching = true;
        this.searchBiomeButton.setMessage(WorldPreviewComponents.BTN_SEARCH_BIOME_CANCEL);

        BiomeSearchTask.Callback callback = new BiomeSearchTask.Callback()
        {
            @Override
            public void onProgress(int currentDistance)
            {
                minecraft.execute(() ->
                    previewDisplay.setOverlayMessage(
                        Component.translatable("world_preview_tfc.preview.search.progress", currentDistance)
                    )
                );
            }

            @Override
            public void onFound(BlockPos pos)
            {
                minecraft.execute(() -> {
                    renderSettings.setCenter(pos);
                    previewDisplay.setOverlayMessage(
                        Component.translatable("world_preview_tfc.preview.search.found", pos.getX(), pos.getZ())
                    );
                    finishSearch();
                });
            }

            @Override
            public void onNotFound()
            {
                minecraft.execute(() -> {
                    previewDisplay.setOverlayMessage(
                        WorldPreviewComponents.SEARCH_BIOME_NOT_FOUND
                    );
                    finishSearch();
                });
            }

            @Override
            public void onCancelled()
            {
                minecraft.execute(() -> {
                    previewDisplay.setOverlayMessage(
                        WorldPreviewComponents.SEARCH_BIOME_CANCELLED
                    );
                    finishSearch();
                });
            }

            @Override
            public void onError(Throwable t)
            {
                WorldPreview.LOGGER.error("Biome search failed", t);
                minecraft.execute(() -> {
                    previewDisplay.setOverlayMessage(
                        WorldPreviewComponents.SEARCH_BIOME_ERROR
                    );
                    finishSearch();
                });
            }
        };

        this.currentSearchTask = new BiomeSearchTask(
            sampleUtils,
            this.workManager.tfcSampleUtils(),
            targetBiome,
            center,
            this.islandCheckbox.selected(),
            callback
        );
        this.biomeSearchExecutor.submit(this.currentSearchTask);
    }

    private void finishSearch()
    {
        this.isSearching = false;
        this.currentSearchTask = null;
        this.searchBiomeButton.setMessage(WorldPreviewComponents.BTN_SEARCH_BIOME);
        BiomesList.BiomeEntry selected = this.biomesList.getSelected();
        this.searchBiomeButton.active = (selected != null) && this.workManager.isSetup();
    }

    private void cancelBiomeSearch()
    {
        if (this.currentSearchTask != null)
        {
            this.currentSearchTask.cancel();
        }
    }

    private synchronized void updateSettings()
    {
        if (!this.inhibitUpdates)
        {
            this.inhibitUpdates = true;

            try
            {
                int revision;
                synchronized (this.reloadRevision)
                {
                    revision = this.reloadRevision.incrementAndGet();
                }

                this.isUpdating = true;
                CompletableFuture.supplyAsync(
                        () -> this.reloadRevision.get() > revision ? null : this.dataProvider.previewWorldCreationContext(), this.reloadExecutor
                    )
                    .thenAcceptAsync(x -> {
                        if (this.reloadRevision.get() <= revision)
                        {
                            this.updateSettings_real(x);
                            synchronized (this.reloadRevision)
                            {
                                if (this.reloadRevision.get() <= revision)
                                {
                                    this.isUpdating = false;
                                }
                            }
                        }
                    }, this.minecraft)
                    .handle((r, e) -> {
                        if (e == null)
                        {
                            this.setupFailed = false;
                        }
                        else
                        {
                            Throwable cause = unwrapCompletionCause(e);
                            if (!(cause instanceof CancellationException) && !(cause instanceof InterruptedException))
                            {
                                WorldPreview.LOGGER.error("Preview setup failed", e);
                                this.setupFailed = true;
                            }
                            synchronized (this.reloadRevision)
                            {
                                if (this.reloadRevision.get() <= revision)
                                {
                                    this.isUpdating = false;
                                }
                            }
                        }

                        return null;
                    });
            }
            finally
            {
                this.inhibitUpdates = false;
            }
        }
    }

    private static Throwable unwrapCompletionCause(Throwable throwable)
    {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException)
        {
            Throwable cause = current.getCause();
            if (cause == null)
            {
                break;
            }
            current = cause;
        }
        return current;
    }

    @SuppressWarnings("resource")
    private void updateSettings_real(@Nullable WorldCreationContext wcContext)
    {
        this.saveSeed.active = !this.dataProvider.seed().isEmpty() && !this.cfg.savedSeeds.contains(this.dataProvider.seed());
        this.updateSeedListWidget();
        this.seedEdit.setValue(this.dataProvider.seed());
        if (!this.seedEdit.isFocused())
        {
            this.seedEdit.moveCursorToStart(false);
        }

        if (this.cfg.heightmapMinY == this.cfg.heightmapMaxY)
        {
            this.cfg.heightmapMaxY++;
        }
        else if (this.cfg.heightmapMaxY < this.cfg.heightmapMinY)
        {
            int tmp = this.cfg.heightmapMaxY;
            this.cfg.heightmapMaxY = this.cfg.heightmapMinY;
            this.cfg.heightmapMinY = tmp;
        }

        WorldDataConfiguration worldDataConfiguration = this.dataProvider.worldDataConfiguration(wcContext);
        Registry<Biome> biomeRegistry = this.dataProvider.registryAccess(wcContext).registryOrThrow(Registries.BIOME);
        Registry<Structure> structureRegistry = this.dataProvider.registryAccess(wcContext).registryOrThrow(Registries.STRUCTURE);
        this.levelStemRegistry = this.dataProvider.levelStemRegistry(wcContext);
        this.levelStemKeys = this.levelStemRegistry.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList();
        this.settings.active = true;
        if (this.renderSettings.dimension == null || !this.levelStemRegistry.containsKey(this.renderSettings.dimension))
        {
            if (this.levelStemRegistry.containsKey(LevelStem.OVERWORLD))
            {
                this.renderSettings.dimension = LevelStem.OVERWORLD.location();
            }
            else
            {
                this.renderSettings.dimension = this.levelStemRegistry.keySet().iterator().next();
            }
        }

        LevelStem levelStem = this.levelStemRegistry.get(this.renderSettings.dimension);
        Set<ResourceLocation> caveBiomes = new HashSet<>();

        for (TagKey<Biome> tagKey : List.of(C_CAVE, C_IS_CAVE, FORGE_CAVE, FORGE_IS_CAVE))
        {
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
                structureRegistry.keySet(),
                StreamSupport.stream(structureRegistry.getTagOrEmpty(DISPLAY_BY_DEFAULT).spliterator(), false)
                    .map(x -> x.unwrapKey().orElseThrow().location())
                    .collect(Collectors.toSet())
            );
        ColorMap colorMap = this.previewData.colorMaps().get(this.cfg.colorMap);
        if (colorMap == null)
        {
            this.cfg.colorMap = "world_preview_tfc:inferno";
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
        if (this.serverThreadPoolExecutor != null)
        {
            try
            {
                CompletableFuture.runAsync(changeWorldGenState, this.serverThreadPoolExecutor).get();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new CancellationException("World gen state update interrupted");
            }
            catch (ExecutionException e)
            {
                Throwable cause = e.getCause();
                if (cause instanceof CancellationException)
                {
                    throw (CancellationException) cause;
                }
                if (cause instanceof InterruptedException)
                {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("World gen state update interrupted");
                }
                throw new RuntimeException(e);
            }
        }
        else
        {
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
        this.freeMarkerIcons();
        ResourceManager builtinResourceManager = this.minecraft.getResourceManager();
        Map<ResourceLocation, NativeImage> icons = new HashMap<>();
        this.allStructureIcons = new NativeImage[this.previewData.structId2StructData().length];

        for (int i = 0; i < this.previewData.structId2StructData().length; i++)
        {
            PreviewData.StructureData data = this.previewData.structId2StructData()[i];
            this.allStructureIcons[i] = icons.computeIfAbsent(data.icon(), x -> {
                if (x == null)
                {
                    x = ResourceLocation.parse("world_preview_tfc:textures/structure/unknown.png");
                }

                Optional<Resource> resource = builtinResourceManager.getResource(x);
                if (resource.isEmpty())
                {
                    resource = this.workManager.sampleResourceManager().getResource(x);
                }

                if (resource.isEmpty())
                {
                    WorldPreview.LOGGER.error("Failed to load structure icon: '{}'", x);
                    resource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview_tfc:textures/structure/unknown.png"));
                }

                if (resource.isEmpty())
                {
                    WorldPreview.LOGGER.error("FATAL ERROR LOADING: '{}' -- unable to load fallback!", x);
                    return new NativeImage(16, 16, true);
                }
                else
                {
                    try (InputStream in = resource.get().open())
                    {
                        return NativeImage.read(in);
                    }
                    catch (IOException e)
                    {
                        WorldPreview.LOGGER.error("Failed to load structure icon", e);
                        return new NativeImage(16, 16, true);
                    }
                }
            });
        }

        Optional<Resource> playerResource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview_tfc:textures/etc/player.png"));
        Optional<Resource> spawnResource = builtinResourceManager.getResource(ResourceLocation.parse("world_preview_tfc:textures/etc/bed.png"));

        try (
            InputStream inPlayer = playerResource.orElseThrow().open();
            InputStream inSpawn = spawnResource.orElseThrow().open()
        )
        {
            this.playerIcon = NativeImage.read(inPlayer);
            this.spawnIcon = NativeImage.read(inSpawn);
        }
        catch (IOException e)
        {
            NativeImage fallbackPlayerIcon = new NativeImage(16, 16, true);
            NativeImage fallbackSpawnIcon = new NativeImage(16, 16, true);
            this.playerIcon = fallbackPlayerIcon;
            this.spawnIcon = fallbackSpawnIcon;
            WorldPreview.LOGGER.error("Failed to load player/spawn icons", e);
        }

        this.worldSpawnIcon = createWorldSpawnIcon();

        this.freeFeatureIcons();
        this.allFeatureIcons = new NativeImage[FeatureDetectors.getFeatureCount()];
        for (int i = 0; i < FeatureDetectors.getFeatureCount(); i++)
        {
            SearchableFeature feature = FeatureDetectors.getFeatureById(i);
            if (feature != null)
            {
                this.allFeatureIcons[i] = createFeatureIcon(feature);
            }
            else
            {
                this.allFeatureIcons[i] = new NativeImage(16, 16, true);
            }
        }

        Registry<Item> itemRegistry = layeredRegistryAccess.compositeAccess().registryOrThrow(Registries.ITEM);
        this.allStructures = structureRegistry.holders()
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
        PlayerData playerData = this.getPlayerData(this.minecraft.getUser().getProfileId());
        if (playerData.currentPos() != null)
        {
            this.renderSettings.setCenter(playerData.currentPos());
        }
        BlockPos pending = this.pendingCenter;
        if (pending != null)
        {
            this.pendingCenter = null;
            this.renderSettings.setCenter(pending);
            this.previewDisplay.resetDragOffset();
        }
        if (this.cfg.sampleStructures)
        {
            this.toggleShowStructures.active = true;
            this.toggleShowStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_STRUCTURES));
        }
        else
        {
            this.toggleShowStructures.active = false;
            this.toggleShowStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_STRUCTURES_DISABLED));
        }

        if (this.cfg.sampleHeightmap)
        {
            this.toggleHeightmap.active = true;
            this.toggleHeightmap.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_HEIGHTMAP));
        }
        else
        {
            this.toggleHeightmap.active = false;
            this.toggleHeightmap.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TOGGLE_HEIGHTMAP_DISABLED));
            this.renderSettings.mode = this.renderSettings.mode == RenderSettings.RenderMode.HEIGHTMAP
                ? RenderSettings.RenderMode.BIOMES
                : this.renderSettings.mode;
        }

        if (this.workManager.isTFCEnabled())
        {
            boolean expanded = this.toggleExpand.selected;
            this.toggleTFCTemperature.active = true;
            this.toggleTFCTemperature.visible = expanded;
            this.toggleTFCTemperature.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_TEMPERATURE));
            this.toggleTFCRainfall.active = true;
            this.toggleTFCRainfall.visible = expanded;
            this.toggleTFCRainfall.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_RAINFALL));
            this.toggleTFCLandWater.active = true;
            this.toggleTFCLandWater.visible = expanded;
            this.toggleTFCLandWater.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_LAND_WATER));
            this.toggleTFCRockTop.active = true;
            this.toggleTFCRockTop.visible = expanded;
            this.toggleTFCRockTop.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_TOP));
            this.toggleTFCRockMid.active = true;
            this.toggleTFCRockMid.visible = expanded;
            this.toggleTFCRockMid.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_MID));
            this.toggleTFCRockBot.active = true;
            this.toggleTFCRockBot.visible = expanded;
            this.toggleTFCRockBot.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_BOT));
            this.toggleTFCRockType.active = true;
            this.toggleTFCRockType.visible = expanded;
            this.toggleTFCRockType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_ROCK_TYPE));
            this.toggleKaolinClay.active = true;
            this.toggleKaolinClay.visible = expanded;
            this.toggleKaolinClay.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_KAOLINITE));
            this.toggleTFCForestType.active = true;
            this.toggleTFCForestType.visible = expanded;
            this.toggleTFCForestType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_FOREST_TYPE));
            this.toggleTFCTreeSpecies.active = true;
            this.toggleTFCTreeSpecies.visible = expanded;
            this.toggleTFCTreeSpecies.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_TREE_SPECIES));
            this.toggleTFCSoilType.active = true;
            this.toggleTFCSoilType.visible = expanded;
            this.toggleTFCSoilType.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_SOIL_TYPE));
            this.toggleTFCCropSuitability.active = true;
            this.toggleTFCCropSuitability.visible = expanded;
            this.toggleTFCCropSuitability.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_CROP_SUITABILITY));
            this.toggleTFCHotspot.active = true;
            this.toggleTFCHotspot.visible = expanded;
            this.toggleTFCHotspot.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_TFC_HOTSPOT));
        }
        else
        {
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
            this.toggleTFCForestType.active = false;
            this.toggleTFCForestType.visible = false;
            this.toggleTFCTreeSpecies.active = false;
            this.toggleTFCTreeSpecies.visible = false;
            this.toggleTFCSoilType.active = false;
            this.toggleTFCSoilType.visible = false;
            this.toggleTFCCropSuitability.active = false;
            this.toggleTFCCropSuitability.visible = false;
            this.toggleTFCHotspot.active = false;
            this.toggleTFCHotspot.visible = false;
            if (this.renderSettings.mode.isTFC())
            {
                this.renderSettings.mode = RenderSettings.RenderMode.BIOMES;
            }
        }

        this.selectViewMode(this.renderSettings.mode);
        this.previewDisplay.reloadData();
        this.previewDisplay.setSelectedBiomeId((short) -1);
        this.previewDisplay.setHighlightCaves(false);
    }

    @Override
    public void onVisibleBiomesChanged(Short2LongMap visibleBiomes)
    {
        if (this.renderSettings.mode == RenderSettings.RenderMode.BIOMES)
        {
            return;
        }
        List<BiomesList.BiomeEntry> res = visibleBiomes.short2LongEntrySet()
            .stream()
            .sorted(Comparator.comparing(Entry::getLongValue).reversed())
            .map(Entry::getShortKey)
            .filter(x -> x >= 0 && x < this.allBiomes.length)
            .map(x -> this.allBiomes[x])
            .toList();
        this.biomesList.replaceEntries(res);
    }

    @Override
    public void onVisibleStructuresChanged(Short2LongMap visibleStructures)
    {
        List<StructuresList.StructureEntry> res = visibleStructures.short2LongEntrySet()
            .stream()
            .sorted(Comparator.comparing(Entry::getLongValue))
            .map(Entry::getShortKey)
            .filter(x -> x >= 0 && x < this.allStructures.length)
            .map(x -> this.allStructures[x])
            .toList();
        this.structuresList.replaceEntries(res);
    }

    @Override
    public void onVisibleRocksChanged(Short2LongMap visibleRocks)
    {
        RenderSettings.RenderMode mode = this.renderSettings.mode;
        boolean isRockMode = mode == RenderSettings.RenderMode.TFC_ROCK_TOP
            || mode == RenderSettings.RenderMode.TFC_ROCK_MID
            || mode == RenderSettings.RenderMode.TFC_ROCK_BOT;
        boolean isRockTypeMode = mode == RenderSettings.RenderMode.TFC_ROCK_TYPE;

        if (isRockMode || isRockTypeMode)
        {
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

    @Override
    public void onTFCMapValueVisuallySelected(RenderSettings.RenderMode mode, short value)
    {
        if (mode != this.renderSettings.mode)
        {
            return;
        }
        TFCMapValueList.ValueEntry entry = value == Short.MIN_VALUE ? null : this.tfcMapValueList.getEntryById(value);
        this.tfcMapValueList.setSelected(entry, entry != null);
    }

    private void randomizeSeed(Button btn)
    {
        UUID uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        String uuidSeed = Base64.getEncoder().encodeToString(bb.array()).substring(0, 16);
        this.setSeed(uuidSeed);
    }

    private void saveCurrentSeed(Button btn)
    {
        this.cfg.savedSeeds.add(this.dataProvider.seed());
        this.saveSeed.active = false;
        this.updateSeedListWidget();
    }

    public void deleteSeed(String seed)
    {
        this.cfg.savedSeeds.remove(seed);
        this.updateSeedListWidget();
    }

    public void setSeed(String seed)
    {
        if (!Objects.equals(this.dataProvider.seed(), seed) && this.dataProvider.seedIsEditable())
        {
            boolean initialInhibitUpdates = this.inhibitUpdates;
            this.inhibitUpdates = true;

            try
            {
                this.dataProvider.updateSeed(seed);
            }
            finally
            {
                this.inhibitUpdates = initialInhibitUpdates;
            }

            this.updateSettings();
        }
    }

    public void centerMapOn(BlockPos pos)
    {
        this.pendingCenter = pos;
        this.renderSettings.setCenter(pos);
        this.previewDisplay.resetDragOffset();
    }

    private void updateSeedListWidget()
    {
        List<SeedsList.SeedEntry> seedEntries = this.cfg.savedSeeds.stream().map(this.seedsList::createEntry).toList();
        this.seedsList.replaceEntries(seedEntries);
        int idx = this.cfg.savedSeeds.indexOf(this.dataProvider.seed());
        if (idx >= 0)
        {
            this.seedsList.setSelected(seedEntries.get(idx));
        }
    }

    public void resetTabs()
    {
        this.onTabButtonChange(this.switchBiomes, DisplayType.BIOMES);
    }

    private void moveList(BaseObjectSelectionList<?> theList)
    {
        if ((!theList.active || theList.getX() <= 0) && (theList.active || theList.getX() >= 0))
        {
            int newX = theList.getX() + (theList.active ? 4096 : -4096);
            theList.setPosition(newX, theList.getY());
        }
    }

    /**
     * Single source of truth for side-panel list visibility. Hides and deactivates every list, then
     * enables exactly one based on {@link #currentDisplayType} and the current render mode, so two
     * lists can never render or receive input at the same coordinates. Both selectViewMode(...) and
     * onTabButtonChange(...) call this instead of toggling lists independently.
     */
    private void updateSidePanelVisibility()
    {
        // 1. Hide/deactivate all side lists and their tab-scoped satellite widgets.
        this.biomesList.visible = false;
        this.biomesList.active = false;
        this.rocksList.visible = false;
        this.rocksList.active = false;
        this.tfcMapValueList.visible = false;
        this.tfcMapValueList.active = false;
        this.cropList.visible = false;
        this.cropList.active = false;
        this.toggleCropWaterMode.visible = false;
        this.toggleCropWaterMode.active = false;
        this.structuresList.visible = false;
        this.structuresList.active = false;
        this.seedsList.visible = false;
        this.seedsList.active = false;
        this.searchBiomeButton.visible = false;
        this.islandCheckbox.visible = false;
        this.resetDefaultStructureVisibility.visible = false;

        // 2. Enable exactly one list.
        RenderSettings.RenderMode mode = this.renderSettings.mode;
        switch (this.currentDisplayType)
        {
            case BIOMES ->
            {
                boolean isTreeMode = mode == RenderSettings.RenderMode.TFC_FOREST_TYPE
                    || mode == RenderSettings.RenderMode.TFC_TREE_SPECIES
                    || mode == RenderSettings.RenderMode.TFC_SOIL_TYPE;
                boolean isCropMode = mode == RenderSettings.RenderMode.TFC_CROP_SUITABILITY;
                boolean isRockMode = mode == RenderSettings.RenderMode.TFC_ROCK_TOP
                    || mode == RenderSettings.RenderMode.TFC_ROCK_MID
                    || mode == RenderSettings.RenderMode.TFC_ROCK_BOT
                    || mode == RenderSettings.RenderMode.TFC_ROCK_TYPE;
                if (isCropMode)
                {
                    // Crop selector (main) + suitability legend (compact) + water-mode toggle.
                    this.cropList.visible = true;
                    this.cropList.active = true;
                    this.tfcMapValueList.visible = true;
                    this.tfcMapValueList.active = true;
                    this.toggleCropWaterMode.visible = true;
                    this.toggleCropWaterMode.active = true;
                }
                else if (isTreeMode)
                {
                    this.tfcMapValueList.visible = true;
                    this.tfcMapValueList.active = true;
                }
                else if (isRockMode)
                {
                    this.rocksList.visible = true;
                    this.rocksList.active = true;
                }
                else
                {
                    this.biomesList.visible = true;
                    this.biomesList.active = true;
                    boolean isBiomeMode = mode == RenderSettings.RenderMode.BIOMES;
                    this.searchBiomeButton.visible = isBiomeMode;
                    this.islandCheckbox.visible = isBiomeMode && this.workManager.isTFCEnabled();
                }
            }
            case STRUCTURES ->
            {
                this.structuresList.visible = true;
                this.structuresList.active = true;
                this.resetDefaultStructureVisibility.visible = true;
            }
            case SEEDS ->
            {
                this.seedsList.visible = true;
                this.seedsList.active = true;
            }
        }

        // 3. Reposition: doLayout re-runs moveList for every list from the (now single-active) state.
        if (this.lastScreenRectangle != null)
        {
            this.doLayout(this.lastScreenRectangle);
        }
        else
        {
            this.moveList(this.biomesList);
            this.moveList(this.rocksList);
            this.moveList(this.tfcMapValueList);
            this.moveList(this.cropList);
            this.moveList(this.structuresList);
            this.moveList(this.seedsList);
        }
    }

    private void onTabButtonChange(Button btn, DisplayType type)
    {
        this.currentDisplayType = type;

        // Tab button states (not list visibility — that is centralized in updateSidePanelVisibility).
        this.switchBiomes.active = true;
        this.switchStructures.active = true;
        this.switchSeeds.active = true;
        if (this.cfg.sampleStructures)
        {
            this.switchStructures.setTooltip(null);
        }
        else
        {
            this.switchStructures.setTooltip(Tooltip.create(WorldPreviewComponents.BTN_SWITCH_STRUCT_DISABLED));
            this.switchStructures.active = false;
        }
        btn.active = false;

        this.updateSidePanelVisibility();
    }

    public synchronized void start()
    {
        WorldPreview.LOGGER.info("Start generating biome data...");
        if (this.dataProvider.seed().isEmpty())
        {
            this.randomizeSeed(null);
        }

        this.inhibitUpdates = false;
        this.updateSettings();
    }

    public synchronized void stop()
    {
        WorldPreview.LOGGER.info("Stop generating biome data...");
        this.inhibitUpdates = true;
        this.workManager.cancel();
    }

    public void doLayout(ScreenRectangle screenRectangle)
    {
        if (screenRectangle == null)
        {
            if (this.minecraft.screen == null)
            {
                return;
            }
            screenRectangle = this.minecraft.screen.getRectangle();
        }

        this.lastScreenRectangle = screenRectangle;
        int leftWidth = Math.clamp(screenRectangle.width() / 3, 130, 180);
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
        this.toggleShowFeatures.setPosition(left + width * i++, top);
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
        this.toggleTFCForestType.setPosition(previewLeft + width * 8, top);
        this.toggleTFCTreeSpecies.setPosition(previewLeft + width * 9, top);
        this.toggleTFCSoilType.setPosition(previewLeft + width * 10, top);
        this.toggleTFCCropSuitability.setPosition(previewLeft + width * 11, top);
        this.toggleTFCHotspot.setPosition(previewLeft + width * 12, top);
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
        if (this.islandCheckbox.visible)
        {
            int checkboxWidth = 60;
            int searchWidth = leftWidth - checkboxWidth - 2;
            this.searchBiomeButton.setPosition(left, biomesListBottom);
            this.searchBiomeButton.setWidth(searchWidth);
            this.islandCheckbox.setPosition(left + searchWidth + 2, biomesListBottom);
        }
        else
        {
            this.searchBiomeButton.setPosition(left, biomesListBottom);
            this.searchBiomeButton.setWidth(leftWidth);
        }
        this.rocksList.setPosition(left, top);
        this.rocksList.setSize(leftWidth, bottom - top - 4);
        this.tfcMapValueList.setPosition(left, top);
        this.tfcMapValueList.setSize(leftWidth, bottom - top - 4);
        // Crop mode splits the left column: crop selector on top, a compact suitability legend and a
        // Rain-Fed/Irrigated toggle pinned at the bottom. Non-crop modes leave the crop widgets sized
        // but hidden (moveList parks inactive lists off-screen).
        boolean cropModeLayout = this.renderSettings.mode == RenderSettings.RenderMode.TFC_CROP_SUITABILITY;
        if (cropModeLayout)
        {
            int waterBtnH = 20;
            int available = bottom - top;
            int legendH = Math.min(this.suitabilityEntries.size() * 16 + 4, Math.max(48, available / 2));
            int waterY = bottom - waterBtnH;
            int legendTop = waterY - 4 - legendH;
            this.toggleCropWaterMode.setPosition(left, waterY);
            this.toggleCropWaterMode.setWidth(leftWidth);
            this.tfcMapValueList.setPosition(left, legendTop);
            this.tfcMapValueList.setSize(leftWidth, legendH);
            this.cropList.setPosition(left, top);
            this.cropList.setSize(leftWidth, Math.max(16, legendTop - 4 - top - 4));
        }
        else
        {
            this.cropList.setPosition(left, top);
            this.cropList.setSize(leftWidth, bottom - top - 4);
        }
        this.seedsList.setPosition(left, top);
        this.seedsList.setSize(leftWidth, bottom - top - 4);
        bottom -= 24;
        this.resetDefaultStructureVisibility.setPosition(left, bottom);
        this.resetDefaultStructureVisibility.setWidth(leftWidth);
        this.structuresList.setPosition(left, top);
        this.structuresList.setSize(leftWidth, bottom - top - 4);
        this.moveList(this.biomesList);
        this.moveList(this.rocksList);
        this.moveList(this.tfcMapValueList);
        this.moveList(this.cropList);
        this.moveList(this.structuresList);
        this.moveList(this.seedsList);
    }

    @Override
    public void close()
    {
        this.landWaterExporter.close();
        this.cancelBiomeSearch();
        this.biomeSearchExecutor.shutdownNow();
        this.reloadExecutor.shutdownNow();
        this.workManager.cancel();
        this.previewDisplay.close();
        this.freeStructureIcons();
        this.freeFeatureIcons();
        this.freeMarkerIcons();
    }

    private void freeStructureIcons()
    {
        if (this.allStructureIcons != null)
        {
            Arrays.stream(this.allStructureIcons).filter(Objects::nonNull).forEach(NativeImage::close);
        }
    }

    private void freeFeatureIcons()
    {
        if (this.allFeatureIcons != null)
        {
            Arrays.stream(this.allFeatureIcons).filter(Objects::nonNull).forEach(NativeImage::close);
        }
    }

    private void freeMarkerIcons()
    {
        if (this.playerIcon != null)
        {
            this.playerIcon.close();
            this.playerIcon = null;
        }
        if (this.spawnIcon != null)
        {
            this.spawnIcon.close();
            this.spawnIcon = null;
        }
        if (this.worldSpawnIcon != null)
        {
            this.worldSpawnIcon.close();
            this.worldSpawnIcon = null;
        }
    }

    private static NativeImage createFeatureIcon(SearchableFeature feature)
    {
        NativeImage icon = new NativeImage(16, 16, true);
        String featurePath = feature.id().getPath();

        int color;
        int outlineColor;
        boolean isCircle;

        if (featurePath.contains("tuff"))
        {
            color = 0xFF888888;
            outlineColor = 0xFF555555;
            isCircle = true;
        }
        else if (featurePath.contains("cinder"))
        {
            color = 0xFF0088FF;
            outlineColor = 0xFF0044AA;
            isCircle = false;
        }
        else if (featurePath.contains("tuya"))
        {
            color = 0xFFFF8800;
            outlineColor = 0xFFAA4400;
            isCircle = false;
        }
        else if (featurePath.contains("lake"))
        {
            color = 0xFFFF8844;
            outlineColor = 0xFFAA4422;
            isCircle = true;
        }
        else if (featurePath.contains("volcano") || featurePath.contains("caldera"))
        {
            color = 0xFF0000FF;       // Red (ABGR)
            outlineColor = 0xFF0000AA;
            isCircle = true;
        }
        else
        {
            color = 0xFFFF00FF;       // Magenta (ABGR)
            outlineColor = 0xFFAA00AA;
            isCircle = true;
        }

        for (int x = 0; x < 16; x++)
        {
            for (int y = 0; y < 16; y++)
            {
                icon.setPixelRGBA(x, y, 0);
            }
        }

        int cx = 7, cy = 7;
        if (isCircle)
        {
            for (int x = 0; x < 16; x++)
            {
                for (int y = 0; y < 16; y++)
                {
                    int dx = x - cx;
                    int dy = y - cy;
                    int distSq = dx * dx + dy * dy;
                    if (distSq <= 49)
                    {
                        icon.setPixelRGBA(x, y, distSq > 36 ? outlineColor : color);
                    }
                }
            }
        }
        else
        {
            for (int y = 2; y < 14; y++)
            {
                int halfWidth = (y - 2) / 2 + 1;
                int startX = cx - halfWidth;
                int endX = cx + halfWidth;
                for (int x = startX; x <= endX; x++)
                {
                    if (x >= 0 && x < 16)
                    {
                        boolean isEdge = (x == startX || x == endX || y == 13);
                        icon.setPixelRGBA(x, y, isEdge ? outlineColor : color);
                    }
                }
            }
        }

        return icon;
    }

    public List<BiomesList.BiomeEntry> allBiomes()
    {
        return Arrays.stream(this.allBiomes).sorted(Comparator.comparing(BiomesList.BiomeEntry::name)).toList();
    }

    private List<BiomesList.BiomeEntry> filteredBiomes()
    {
        boolean isNether = this.renderSettings.dimension != null
            && this.renderSettings.dimension.equals(LevelStem.NETHER.location());
        boolean isEnd = this.renderSettings.dimension != null
            && this.renderSettings.dimension.equals(LevelStem.END.location());

        return Arrays.stream(this.allBiomes)
            .filter(entry -> {
                ResourceLocation loc = entry.entry().key().location();
                if (!"minecraft".equals(loc.getNamespace()))
                {
                    return true;
                }
                if (entry.entry().is(IS_NETHER))
                {
                    return isNether;
                }
                if (entry.entry().is(IS_END))
                {
                    return isEnd;
                }
                return false;
            })
            .sorted(Comparator.comparing(BiomesList.BiomeEntry::name))
            .toList();
    }

    public List<ResourceLocation> levelStemKeys()
    {
        return this.levelStemKeys;
    }

    public Registry<LevelStem> levelStemRegistry()
    {
        return this.levelStemRegistry;
    }

    @Override
    @Nullable
    public BiomesList.BiomeEntry biome4Id(int id)
    {
        if (id < 0 || id >= this.allBiomes.length)
        {
            WorldPreview.LOGGER.warn("biome4Id called with out-of-range id {} (allBiomes length {})", id, this.allBiomes.length);
            return null;
        }
        return this.allBiomes[id];
    }

    @Override
    @Nullable
    public StructuresList.StructureEntry structure4Id(int id)
    {
        if (id < 0 || id >= this.allStructures.length)
        {
            WorldPreview.LOGGER.warn("structure4Id called with out-of-range id {} (allStructures length {})", id, this.allStructures.length);
            return null;
        }
        return this.allStructures[id];
    }

    @Override
    public NativeImage[] structureIcons()
    {
        return this.allStructureIcons;
    }

    @Override
    public NativeImage[] featureIcons()
    {
        return this.allFeatureIcons;
    }

    @Override
    public SearchableFeature feature4Id(int id)
    {
        return FeatureDetectors.getFeatureById(id);
    }

    @Override
    @Nullable
    public Component featureVariantName(int featureId, BlockPos center)
    {
        if (this.workManager.hasWorldSeed())
        {
            return null;
        }
        SearchableFeature feature = FeatureDetectors.getFeatureById(featureId);
        return FeatureDetectors.getFeatureVariantName(feature, this.workManager.worldSeed(), center);
    }

    @Override
    public NativeImage playerIcon()
    {
        return Objects.requireNonNull(this.playerIcon, "Player icon is unavailable outside the active preview lifecycle");
    }

    @Override
    public NativeImage spawnIcon()
    {
        return Objects.requireNonNull(this.spawnIcon, "Spawn icon is unavailable outside the active preview lifecycle");
    }

    @Override
    public NativeImage worldSpawnIcon()
    {
        return Objects.requireNonNull(this.worldSpawnIcon, "World-spawn icon is unavailable outside the active preview lifecycle");
    }

    @Override
    public ItemStack[] structureItems()
    {
        return Arrays.stream(this.allStructures).map(StructuresList.StructureEntry::itemStack).toArray(ItemStack[]::new);
    }

    @Override
    public void onBiomeVisuallySelected(BiomesList.BiomeEntry entry)
    {
        this.biomesList.setSelected(entry, entry != null);
        this.previewDisplay.setHighlightCaves(false);
    }

    @Override
    public PreviewData previewData()
    {
        return this.previewData;
    }

    public WorkManager workManager()
    {
        return this.workManager;
    }

    @Override
    public StructureRenderInfo[] renderStructureMap()
    {
        return this.allStructures;
    }

    @Override
    public int[] heightColorMap()
    {
        ColorMap colorMap = this.previewData.colorMaps().get(this.cfg.colorMap);
        if (colorMap == null)
        {
            int[] black = new int[this.workManager.yMax() - this.workManager.yMin()];
            Arrays.fill(black, -16777216);
            return black;
        }
        else
        {
            return colorMap.bake(this.workManager.yMin(), this.workManager.yMax(), this.cfg.heightmapMinY, this.cfg.heightmapMaxY);
        }
    }

    @Override
    public int[] tfcTemperatureColorMap()
    {
        ColorMap colorMap = this.previewMappingData.resolveColorMap(
            this.cfg.temperatureColorMap,
            ResourceLocation.fromNamespaceAndPath("world_preview_tfc", "tfc_temperature"),
            "temperature"
        );
        if (colorMap == null)
        {
            int[] black = new int[1024];
            Arrays.fill(black, -16777216);
            return black;
        }
        else
        {
            return colorMap.bake(1024);
        }
    }

    @Override
    public int[] tfcRainfallColorMap()
    {
        ColorMap colorMap = this.previewMappingData.resolveColorMap(
            this.cfg.rainfallColorMap,
            ResourceLocation.fromNamespaceAndPath("world_preview_tfc", "tfc_rainfall"),
            "rainfall"
        );
        if (colorMap == null)
        {
            int[] black = new int[1024];
            Arrays.fill(black, -16777216);
            return black;
        }
        else
        {
            return colorMap.bake(1024);
        }
    }

    @Override
    public void onColorPalettesChanged(long revision)
    {
        for (short i = 0; i < this.allRocks.length; i++)
        {
            this.allRocks[i].update(TFCSampleUtils.getRockName(i), TFCSampleUtils.getRockColor(i));
        }
        for (short i = 0; i < this.allRockTypes.length; i++)
        {
            this.allRockTypes[i].update(TFCSampleUtils.getRockTypeName(i), TFCSampleUtils.getRockTypeColor(i));
        }

        this.forestTypeEntries.getFirst().update("Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER));
        for (short i = 0; i < TFCSampleUtils.forestTypeCount(); i++)
        {
            this.forestTypeEntries.get(i + 1).update(TFCSampleUtils.getForestTypeName(i), TFCSampleUtils.getForestTypeColor(i));
        }
        this.soilTypeEntries.getFirst().update("Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER));
        for (short i = 0; i < TFCSampleUtils.soilTypeCount(); i++)
        {
            this.soilTypeEntries.get(i + 1).update(TFCSampleUtils.getSoilTypeName(i), TFCSampleUtils.getSoilTypeColor(i));
        }
        for (short i = 0; i < TFCCropSuitability.suitabilityCount(); i++)
        {
            this.suitabilityEntries.get(i).update(TFCCropSuitability.getSuitabilityName(i), TFCCropSuitability.getSuitabilityColor(i));
        }
        this.suitabilityEntries.get(TFCCropSuitability.suitabilityCount()).update(
            "Water", TFCSampleUtils.getWaterTypeColor(TFCSampleUtils.VALUE_WATER));
        this.suitabilityEntries.get(TFCCropSuitability.suitabilityCount() + 1).update(
            TFCCropSuitability.getSuitabilityName(TFCSampleUtils.VALUE_INVALID),
            TFCCropSuitability.getSuitabilityColor(TFCSampleUtils.VALUE_INVALID));

        if (this.renderSettings.mode == RenderSettings.RenderMode.TFC_TREE_SPECIES)
        {
            short selected = this.tfcMapValueList.getSelected() == null
                ? Short.MIN_VALUE : this.tfcMapValueList.getSelected().id();
            this.treeSpeciesEntries = this.buildTreeSpeciesEntries();
            this.tfcMapValueList.replaceEntries(this.treeSpeciesEntries);
            this.tfcMapValueList.setSelected(selected == Short.MIN_VALUE ? null : this.tfcMapValueList.getEntryById(selected));
        }
        WorldPreview.LOGGER.debug("Refreshed preview palette swatches at revision {}", revision);
    }

    @Override
    public int yMin()
    {
        return this.workManager.yMin();
    }

    @Override
    public int yMax()
    {
        return this.workManager.yMax();
    }

    @Override
    public boolean isUpdating()
    {
        return this.isUpdating;
    }

    @Override
    public boolean setupFailed()
    {
        return this.setupFailed;
    }

    @NotNull
    @Override
    @SuppressWarnings("resource")
    public PreviewDisplayDataProvider.PlayerData getPlayerData(UUID playerId)
    {
        if (this.workManager != null && this.workManager.sampleUtils() != null)
        {
            SampleUtils sampleUtils = this.workManager.sampleUtils();
            if (sampleUtils == null)
            {
                return new PlayerData(null, null);
            }

            ServerPlayer player = sampleUtils.getPlayers(playerId);
            if (player == null)
            {
                return new PlayerData(null, null);
            }
            else
            {
                ResourceKey<Level> playerDimension = player.serverLevel().dimension();
                ResourceKey<Level> respawnDimension = player.getRespawnDimension();
                ResourceKey<Level> currentDimension = sampleUtils.dimension();
                return new PlayerData(
                    currentDimension.equals(playerDimension) ? player.blockPosition() : null,
                    currentDimension.equals(respawnDimension) ? player.getRespawnPosition() : null
                );
            }
        }
        else
        {
            return new PlayerData(null, null);
        }
    }

    @Nullable
    @Override
    public BlockPos getWorldSpawnPos()
    {
        if (this.workManager != null)
        {
            net.dries007.tfc.world.settings.Settings overrideSettings = this.workManager.getTFCSettingsOverride();
            if (overrideSettings != null)
            {
                return new BlockPos(overrideSettings.spawnCenterX(), 64, overrideSettings.spawnCenterZ());
            }

            if (this.workManager.isTFCEnabled())
            {
                TFCSampleUtils tfcUtils = this.workManager.tfcSampleUtils();
                if (tfcUtils != null && tfcUtils.settings() != null)
                {
                    net.dries007.tfc.world.settings.Settings settings = tfcUtils.settings();
                    return new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
                }
            }
        }
        return new BlockPos(0, 64, 0);
    }

    @Override
    public int getWorldSpawnDistance()
    {
        if (this.workManager != null)
        {
            net.dries007.tfc.world.settings.Settings overrideSettings = this.workManager.getTFCSettingsOverride();
            if (overrideSettings != null)
            {
                return overrideSettings.spawnDistance();
            }

            if (this.workManager.isTFCEnabled())
            {
                TFCSampleUtils tfcUtils = this.workManager.tfcSampleUtils();
                if (tfcUtils != null && tfcUtils.settings() != null)
                {
                    return tfcUtils.settings().spawnDistance();
                }
            }
        }
        return 0;
    }

    public @Nullable String startLandWaterExport(List<LandWaterExportPreset> presets, int centerX, int centerZ)
    {
        if (this.isUpdating || this.workManager.hasWorldSeed())
        {
            return "The preview world-generation state is still loading.";
        }

        TFCSampleUtils tfc = this.workManager.tfcSampleUtils();
        SampleUtils samples = this.workManager.sampleUtils();
        if (tfc == null || samples == null)
        {
            return "The selected generator does not expose TFC land/water data.";
        }

        try
        {
            for (LandWaterExportPreset preset : presets)
            {
                preset.spec().bounds(centerX, centerZ);
            }
        }
        catch (ArithmeticException e)
        {
            return "The selected center is too close to the integer world-coordinate limit.";
        }

        long numericSeed = this.workManager.worldSeed();
        String enteredSeed = this.dataProvider.seed();
        if (enteredSeed == null || enteredSeed.isBlank())
        {
            enteredSeed = Long.toString(numericSeed);
        }
        Path outputDirectory = this.minecraft.gameDirectory.toPath().resolve("world-preview-exports");
        Context context = new LandWaterMapExporter.Context(
            enteredSeed,
            numericSeed,
            samples.dimension().location().toString(),
            centerX,
            centerZ,
            outputDirectory,
            this.cfg.landWaterExportLandColor,
            this.cfg.landWaterExportWaterColor,
            modVersion("world_preview_tfc", "unknown"),
            true,
            modVersion("tfc", null),
            modVersion("tfc_large_biomes", null)
        );
        TFCLandWaterClassifier classifier = new TFCLandWaterClassifier();
        boolean started = this.landWaterExporter.start(
            presets,
            context,
            (quartX, quartZ) -> classifier.classify(tfc.sampleBiomeExtensionQuart(quartX, quartZ))
        );
        return started ? null : "A land/water export is already running.";
    }

    public void cancelLandWaterExport()
    {
        this.landWaterExporter.cancel();
    }

    public LandWaterExportController.Status landWaterExportStatus()
    {
        return this.landWaterExporter.status();
    }

    private static @Nullable String modVersion(String modId, @Nullable String fallback)
    {
        return ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(fallback);
    }

    public PreviewContainerDataProvider dataProvider()
    {
        return this.dataProvider;
    }

    public List<AbstractWidget> widgets()
    {
        return this.toRender;
    }

    private static NativeImage createWorldSpawnIcon()
    {
        NativeImage icon = new NativeImage(16, 16, true);
        int gold = 0xFF00D4FF;
        int darkGold = 0xFF0090CC;

        int cx = 7, cy = 7;

        for (int x = 0; x < 16; x++)
        {
            for (int y = 0; y < 16; y++)
            {
                icon.setPixelRGBA(x, y, 0);
            }
        }

        for (int i = 0; i <= 6; i++)
        {
            icon.setPixelRGBA(cx, cy - i, i == 6 ? darkGold : gold);
            icon.setPixelRGBA(cx, cy + i, i == 6 ? darkGold : gold);
            icon.setPixelRGBA(cx - i, cy, i == 6 ? darkGold : gold);
            icon.setPixelRGBA(cx + i, cy, i == 6 ? darkGold : gold);
        }

        for (int i = 1; i <= 4; i++)
        {
            icon.setPixelRGBA(cx - i, cy - i, gold);
            icon.setPixelRGBA(cx + i, cy - i, gold);
            icon.setPixelRGBA(cx - i, cy + i, gold);
            icon.setPixelRGBA(cx + i, cy + i, gold);
        }

        icon.setPixelRGBA(cx, cy, darkGold);
        icon.setPixelRGBA(cx + 1, cy, darkGold);
        icon.setPixelRGBA(cx, cy + 1, darkGold);
        icon.setPixelRGBA(cx + 1, cy + 1, darkGold);

        return icon;
    }

    public enum DisplayType
    {
        BIOMES,
        STRUCTURES,
        SEEDS;

        public Component component()
        {
            return toComponent(this);
        }

        public static Component toComponent(DisplayType x)
        {
            return Component.translatable("world_preview_tfc.preview.btn-cycle." + x.name());
        }
    }

    private void cycleResolution()
    {
        int current = this.renderSettings.pixelsPerChunk();
        int next = switch (current)
        {
            case 1 -> 2;
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

    private Component getResolutionLabel()
    {
        int ppc = this.renderSettings.pixelsPerChunk();
        return Component.literal(ppc + "x");
    }

    private Component getCropWaterModeLabel()
    {
        return this.workManager.cropWaterMode() == TFCCropSuitability.CropWaterMode.IRRIGATED
            ? WorldPreviewComponents.BTN_CROP_IRRIGATED
            : WorldPreviewComponents.BTN_CROP_RAIN_FED;
    }

    private void cycleCropWaterMode()
    {
        TFCCropSuitability.CropWaterMode next = this.workManager.cropWaterMode() == TFCCropSuitability.CropWaterMode.RAIN_FED
            ? TFCCropSuitability.CropWaterMode.IRRIGATED
            : TFCCropSuitability.CropWaterMode.RAIN_FED;
        this.workManager.setCropWaterMode(next);
        this.toggleCropWaterMode.setMessage(this.getCropWaterModeLabel());
    }

    /**
     * Rebuilds the crop selector from the runtime registry and syncs the selection to the WorkManager.
     */
    private void refreshCropList()
    {
        List<TFCCropList.CropEntry> entries = new ArrayList<>();
        for (TFCCropRegistry.Entry crop : this.workManager.cropRegistry().entries())
        {
            entries.add(this.cropList.createEntry(crop.id(), crop.displayName()));
        }
        this.cropList.replaceEntries(entries);
        this.cropList.setScrollAmount(0.0);
        var selectedId = this.workManager.selectedCropId();
        TFCCropList.CropEntry selected = selectedId != null ? this.cropList.getEntryById(selectedId) : null;
        if (selected != null)
        {
            this.cropList.setSelected(selected, true);
        }
        this.toggleCropWaterMode.setMessage(this.getCropWaterModeLabel());
    }
}
