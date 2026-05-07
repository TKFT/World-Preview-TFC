package com.rustysnail.world.preview.tfc.client.gui.screens;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.WorkManager;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.backend.search.*;
import com.rustysnail.world.preview.tfc.backend.search.HeightSampler;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainPeakScanner;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainSearchConfig;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainSearchCsvWriter;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainSearchResult;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainSeedSearchConfig;
import com.rustysnail.world.preview.tfc.backend.search.mountain.MountainSeedSearchEngine;
import com.rustysnail.world.preview.tfc.backend.search.mountain.TFCSeededHeightSampler;
import com.rustysnail.world.preview.tfc.backend.search.mountain.TFCSeededHeightSamplerFactory;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.widgets.WGLabel;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomeCheckboxList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.SeedMatchList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.TerrainFeatureList;
import com.rustysnail.world.preview.tfc.mixin.client.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.TFCBiomes;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SeedSearchContainer implements AutoCloseable
{

    private static final int[] SEARCH_RADII = {500, 2000, 5000, 10000};
    private static final String[] SEARCH_AREA_LABELS = {"1K x 1K", "4K x 4K", "10K x 10K", "20K x 20K"};

    private final Minecraft minecraft;
    private final WorldPreview worldPreview;
    private final WorkManager workManager;
    private final PreviewTab previewTab;

    private final Button searchAreaButton;
    private final EditBox maxSeedsEdit;
    private final Button tfcSettingsButton;
    private final Button biomeMatchModeButton;
    private final Button clearButton;
    private final BiomeCheckboxList biomeCheckboxList;
    private final TerrainFeatureList terrainFeatureList;
    private final Button startButton;
    private final Button stopButton;
    private final Button mountainButton;
    private final Button validateButton;
    private final Button mountainSeedSearchButton;

    private final WGLabel statusLabel;
    private final WGLabel debugBiomeLabel;
    private final SeedMatchList matchList;
    private final Button previewButton;
    private final Button copyButton;
    private final Button skipButton;

    private int searchAreaIndex = 1;
    private int maxSeeds = SearchCriteria.DEFAULT_MAX_SEEDS;
    private SearchCriteria.BiomeMatchMode biomeMatchMode = SearchCriteria.BiomeMatchMode.ANY;
    @Nullable private SeedSearchEngine engine;
    @Nullable private MountainPeakScanner mountainScanner;
    @Nullable private MountainSeedSearchEngine mountainSeedSearchEngine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile SearchState state = SearchState.IDLE;
    private final List<MatchResult> allMatches = new ArrayList<>();

    private final List<AbstractWidget> allWidgets = new ArrayList<>();
    private volatile boolean populated = false;
    private volatile boolean closed = false;

    enum SearchState
    {
        IDLE, SEARCHING, PAUSED
    }

    public SeedSearchContainer(Screen screen, PreviewTab previewTab)
    {
        Font font = ((ScreenAccessor) screen).getFont();
        this.minecraft = Minecraft.getInstance();
        this.worldPreview = WorldPreview.get();
        this.workManager = this.worldPreview.workManager();
        this.previewTab = previewTab;

        this.searchAreaButton = Button.builder(
            Component.translatable("world_preview_tfc.search.area", SEARCH_AREA_LABELS[this.searchAreaIndex]),
            btn -> this.cycleSearchArea()
        ).size(70, 18).build();
        this.searchAreaButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_AREA_TOOLTIP));
        this.allWidgets.add(this.searchAreaButton);

        this.maxSeedsEdit = new EditBox(font, 0, 0, 50, 18,
            WorldPreviewComponents.SEARCH_MAX_SEEDS);
        this.maxSeedsEdit.setValue(String.valueOf(this.maxSeeds));
        this.maxSeedsEdit.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.maxSeedsEdit.setResponder(this::onMaxSeedsChanged);
        this.maxSeedsEdit.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_MAX_SEEDS_TOOLTIP));
        this.allWidgets.add(this.maxSeedsEdit);

        this.tfcSettingsButton = Button.builder(
            WorldPreviewComponents.SEARCH_TFC_SETTINGS,
            btn -> this.onOpenTfcSettings()
        ).size(70, 18).build();
        this.tfcSettingsButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_TFC_SETTINGS_TOOLTIP));
        this.tfcSettingsButton.visible = false;
        this.tfcSettingsButton.active = false;
        this.allWidgets.add(this.tfcSettingsButton);

        this.biomeMatchModeButton = Button.builder(
            Component.translatable("world_preview_tfc.search.biome_mode." + this.biomeMatchMode.name().toLowerCase()),
            btn -> this.cycleBiomeMatchMode()
        ).size(60, 18).build();
        this.biomeMatchModeButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_BIOME_MODE_TOOLTIP));
        this.allWidgets.add(this.biomeMatchModeButton);

        this.clearButton = Button.builder(
            Component.literal("Clear"),
            btn -> this.onClearChecked()
        ).size(40, 18).build();
        this.allWidgets.add(this.clearButton);

        this.biomeCheckboxList = new BiomeCheckboxList(this.minecraft, 150, 200, 0, 0);
        this.biomeCheckboxList.setOnChanged(this::onBiomeSelectionChanged);
        this.allWidgets.add(this.biomeCheckboxList);

        this.terrainFeatureList = new TerrainFeatureList(this.minecraft, 120, 200, 0, 0);
        this.allWidgets.add(this.terrainFeatureList);

        this.startButton = Button.builder(
            WorldPreviewComponents.SEARCH_START,
            btn -> this.onStartSearch()
        ).size(60, 18).build();
        this.allWidgets.add(this.startButton);

        this.stopButton = Button.builder(
            WorldPreviewComponents.SEARCH_STOP,
            btn -> this.onStopSearch()
        ).size(60, 18).build();
        this.stopButton.visible = false;
        this.allWidgets.add(this.stopButton);

        this.mountainButton = Button.builder(
            WorldPreviewComponents.SEARCH_FIND_PEAK,
            btn -> this.onFindPeak()
        ).size(60, 18).build();
        this.mountainButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_FIND_PEAK_TOOLTIP));
        this.allWidgets.add(this.mountainButton);

        this.validateButton = Button.builder(
            WorldPreviewComponents.SEARCH_VALIDATE_PEAK,
            btn -> this.onValidatePeak()
        ).size(60, 18).build();
        this.validateButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_VALIDATE_PEAK_TOOLTIP));
        this.allWidgets.add(this.validateButton);

        this.mountainSeedSearchButton = Button.builder(
            WorldPreviewComponents.SEARCH_PEAK_SEEDS,
            btn -> this.onSearchPeaks()
        ).size(70, 18).build();
        this.mountainSeedSearchButton.setTooltip(Tooltip.create(WorldPreviewComponents.SEARCH_PEAK_SEEDS_TOOLTIP));
        this.allWidgets.add(this.mountainSeedSearchButton);

        this.statusLabel = new WGLabel(font, 0, 0, 200, 14,
            WGLabel.TextAlignment.LEFT, Component.empty(), 0xAAAAAA);
        this.allWidgets.add(this.statusLabel);

        this.debugBiomeLabel = new WGLabel(font, 0, 0, 200, 14,
            WGLabel.TextAlignment.LEFT, Component.empty(), 0x888888);
        this.allWidgets.add(this.debugBiomeLabel);

        this.matchList = new SeedMatchList(this.minecraft, 140, 200, 0, 0);
        this.allWidgets.add(this.matchList);

        this.previewButton = Button.builder(
            WorldPreviewComponents.SEARCH_PREVIEW,
            btn -> this.onPreviewMatch()
        ).size(60, 20).build();
        this.previewButton.active = false;
        this.allWidgets.add(this.previewButton);

        this.copyButton = Button.builder(
            WorldPreviewComponents.SEARCH_COPY,
            btn -> this.onCopySeed()
        ).size(40, 20).build();
        this.copyButton.active = false;
        this.allWidgets.add(this.copyButton);

        this.skipButton = Button.builder(
            WorldPreviewComponents.SEARCH_SKIP,
            btn -> this.onSkipMatch()
        ).size(100, 20).build();
        this.skipButton.visible = false;
        this.allWidgets.add(this.skipButton);
    }

    public void start()
    {
        this.previewTab.mainScreenWidget().start();

        if (!tryPopulateLists())
        {
            schedulePopulationRetry();
        }
    }

    public void stop()
    {
        // Search continues in the background.
    }

    private void schedulePopulationRetry()
    {
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute(() -> this.minecraft.execute(() -> {
                if (this.closed || this.populated) return;
                if (!tryPopulateLists())
                {
                    schedulePopulationRetry();
                }
            }));
    }

    private boolean tryPopulateLists()
    {
        if (this.populated) return true;
        if (this.workManager.isSetup())
        {
            populateLists();
            return true;
        }
        return false;
    }

    @SuppressWarnings("resource")
    private void populateLists()
    {
        this.populated = true;

        var sampleUtils = this.workManager.sampleUtils();
        if (sampleUtils != null)
        {
            PreviewMappingData mappingData = this.worldPreview.biomeColorMap();
            var biomeRegistry = sampleUtils.registryAccess().registryOrThrow(Registries.BIOME);
            List<BiomeCheckboxList.Entry> biomeEntries = biomeRegistry.holders()
                .filter(ref -> ref.key().location().getNamespace().equals("tfc"))
                .map(ref -> {
                    ResourceLocation loc = ref.key().location();
                    int color = getBiomeColor(mappingData, loc);
                    return this.biomeCheckboxList.createEntry(ref, color);
                })
                .sorted(Comparator.comparing(BiomeCheckboxList.Entry::name))
                .toList();
            this.biomeCheckboxList.replaceEntries(biomeEntries);
        }

        boolean isTFC = this.workManager.isTFCEnabled();
        this.terrainFeatureList.visible = isTFC;
        this.terrainFeatureList.active = isTFC;
        this.tfcSettingsButton.visible = isTFC;
        this.tfcSettingsButton.active = isTFC && this.state == SearchState.IDLE;
        if (isTFC)
        {
            List<TerrainFeatureList.Entry> featureEntries = new ArrayList<>();
            for (SearchableFeature manual : FeatureDetectors.getManualFeatures())
            {
                featureEntries.add(this.terrainFeatureList.createEntry(manual));
            }
            featureEntries.sort(Comparator.comparing(e -> e.feature().name().getString()));
            this.terrainFeatureList.replaceEntries(featureEntries);
        }

        onBiomeSelectionChanged();
    }

    private static int getBiomeColor(PreviewMappingData mappingData, ResourceLocation biome)
    {
        int color = mappingData.getBiomeColor(biome.toString());
        if (color != -1) return color;
        int hash = biome.toString().hashCode();
        return hash & 0xFFFFFF;
    }

    private void cycleSearchArea()
    {
        this.searchAreaIndex = (this.searchAreaIndex + 1) % SEARCH_RADII.length;
        this.searchAreaButton.setMessage(
            Component.translatable("world_preview_tfc.search.area", SEARCH_AREA_LABELS[this.searchAreaIndex])
        );
    }

    private void cycleBiomeMatchMode()
    {
        this.biomeMatchMode = (this.biomeMatchMode == SearchCriteria.BiomeMatchMode.ANY)
            ? SearchCriteria.BiomeMatchMode.ALL
            : SearchCriteria.BiomeMatchMode.ANY;
        this.biomeMatchModeButton.setMessage(
            Component.translatable("world_preview_tfc.search.biome_mode." + this.biomeMatchMode.name().toLowerCase())
        );
    }

    private void onClearChecked()
    {
        this.biomeCheckboxList.clearChecks();
        this.terrainFeatureList.clearChecks();
        onBiomeSelectionChanged();
    }

    private void onMaxSeedsChanged(String value)
    {
        try
        {
            if (!value.isEmpty())
            {
                this.maxSeeds = Math.max(1, Math.min(100000, Integer.parseInt(value)));
            }
        }
        catch (NumberFormatException ignored)
        {
        }
    }

    private void onBiomeSelectionChanged()
    {
        if (!this.workManager.isTFCEnabled()) return;

        Set<ResourceKey<Biome>> selectedBiomes = this.biomeCheckboxList.getCheckedBiomes();

        if (selectedBiomes.isEmpty())
        {
            for (int i = 0; i < terrainFeatureList.entryCount(); i++)
            {
                TerrainFeatureList.Entry e = terrainFeatureList.entryAt(i);
                if (e != null)
                {
                    e.setEnabled(true);
                }
            }
            return;
        }

        Set<BiomeExtension> selectedExts = new HashSet<>();
        Set<ResourceLocation> selectedLocs = new HashSet<>();
        for (ResourceKey<Biome> biomeKey : selectedBiomes)
        {
            selectedLocs.add(biomeKey.location());
        }

        for (BiomeExtension ext : TFCBiomes.REGISTRY)
        {
            if (selectedLocs.contains(ext.key().location()))
            {
                selectedExts.add(ext);
            }
        }

        for (int i = 0; i < terrainFeatureList.entryCount(); i++)
        {
            TerrainFeatureList.Entry entry = terrainFeatureList.entryAt(i);
            if (entry == null) continue;
            SearchableFeature feature = entry.feature();

            if (selectedExts.isEmpty())
            {
                entry.setEnabled(true);
                continue;
            }

            boolean compatible = false;
            for (BiomeExtension ext : selectedExts)
            {
                if (FeatureDetectors.isCompatibleWithBiomeExt(feature, ext))
                {
                    compatible = true;
                    break;
                }
            }
            entry.setEnabled(compatible);
        }
    }

    @SuppressWarnings("resource")
    private void onStartSearch()
    {
        if (!this.workManager.isSetup()) return;

        tryPopulateLists();

        Set<ResourceKey<Biome>> biomes = this.biomeCheckboxList.getCheckedBiomes();
        Set<SearchableFeature> features = this.terrainFeatureList.getCheckedFeatures();

        if (biomes.isEmpty() && features.isEmpty())
        {
            setStatusText(WorldPreviewComponents.SEARCH_NO_CRITERIA.getString());
            return;
        }

        BlockPos center = BlockPos.ZERO;
        if (this.workManager.isTFCEnabled())
        {
            var tfcUtils = this.workManager.tfcSampleUtils();
            if (tfcUtils != null)
            {
                var settings = tfcUtils.settings();
                center = new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
            }
        }

        int radius = SEARCH_RADII[this.searchAreaIndex];
        SearchCriteria criteria = new SearchCriteria(biomes, this.biomeMatchMode, features, center, radius, this.maxSeeds);

        ChunkGenerator generator = this.workManager.chunkGenerator();
        var sampleUtils = this.workManager.sampleUtils();
        RegistryAccess registryAccess = null;
        if (sampleUtils != null)
        {
            registryAccess = sampleUtils.registryAccess();
        }

        this.allMatches.clear();
        this.matchList.replaceEntries(new ArrayList<>());

        final RegistryAccess finalRegistryAccess = registryAccess;
        SeedSearchEngine.Callback callback = new SeedSearchEngine.Callback()
        {
            @Override
            public void onProgress(long tested, int max, String currentSeed, @Nullable String debugBiomeAtOrigin)
            {
                minecraft.execute(() -> {
                    setStatusText(Component.translatable("world_preview_tfc.search.progress_detail",
                        tested, max).getString());

                    if (debugBiomeAtOrigin != null)
                    {
                        debugBiomeLabel.setText(Component.literal("Biome @ 0,0: " + debugBiomeAtOrigin));
                    }
                    else
                    {
                        debugBiomeLabel.setText(Component.literal("Biome @ 0,0: ?"));
                    }
                });
            }

            @Override
            public void onMatchFound(MatchResult result)
            {
                minecraft.execute(() -> {
                    allMatches.add(result);
                    matchList.replaceEntries(
                        allMatches.stream().map(matchList::createEntry).toList()
                    );
                    matchList.selectAndScrollToLast();
                    setStatusText(Component.translatable("world_preview_tfc.search.found_seed",
                        result.seedString()).getString());
                    setState(SearchState.PAUSED);
                });
            }

            @Override
            public void onComplete(long totalTested, int matches)
            {
                minecraft.execute(() -> {
                    setStatusText(Component.translatable("world_preview_tfc.search.complete",
                        totalTested, matches).getString());
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onCancelled()
            {
                minecraft.execute(() -> {
                    setStatusText(WorldPreviewComponents.SEARCH_CANCELLED_DETAIL.getString());
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onError(Throwable t)
            {
                minecraft.execute(() -> {
                    setStatusText(WorldPreviewComponents.SEARCH_ERROR_DETAIL.getString());
                    setState(SearchState.IDLE);
                });
            }
        };

        this.engine = new SeedSearchEngine(generator, finalRegistryAccess, minecraft.level, criteria, callback);
        setState(SearchState.SEARCHING);
        this.executor.submit(this.engine);
    }

    private void onStopSearch()
    {
        if (this.engine != null)
        {
            this.engine.cancel();
        }
        if (this.mountainScanner != null)
        {
            this.mountainScanner.cancel();
        }
        if (this.mountainSeedSearchEngine != null)
        {
            this.mountainSeedSearchEngine.cancel();
        }
    }

    private void onFindPeak()
    {
        if (!this.workManager.isSetup()) return;
        var sampleUtils = this.workManager.sampleUtils();
        if (sampleUtils == null) return;

        BlockPos center = BlockPos.ZERO;
        if (this.workManager.isTFCEnabled())
        {
            var tfcUtils = this.workManager.tfcSampleUtils();
            if (tfcUtils != null)
            {
                var settings = tfcUtils.settings();
                center = new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
            }
        }

        int radius = SEARCH_RADII[this.searchAreaIndex];
        MountainSearchConfig config = MountainSearchConfig.forCurrentSeed(center, radius);
        HeightSampler currentSampler = (x, z) -> sampleUtils.doHeightSlow(new BlockPos(x, 0, z));

        MountainPeakScanner.Callback callback = new MountainPeakScanner.Callback()
        {
            @Override
            public void onProgress(long samplesChecked, int bestHeight, int bestX, int bestZ, String phase)
            {
                minecraft.execute(() ->
                    setStatusText("Peak scan " + phase + ": best Y=" + bestHeight + " at X=" + bestX + " Z=" + bestZ));
            }

            @Override
            public void onComplete(MountainSearchResult result)
            {
                minecraft.execute(() -> {
                    setStatusText(result.summary());
                    debugBiomeLabel.setText(Component.literal("Tallest terrain found for current seed"));
                    MatchResult matchResult = toMountainMatchResult(result, 1);
                    allMatches.add(matchResult);
                    matchList.replaceEntries(allMatches.stream().map(matchList::createEntry).toList());
                    matchList.selectAndScrollToResult(matchResult);
                    mountainScanner = null;
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onCancelled()
            {
                minecraft.execute(() -> {
                    setStatusText("Peak scan cancelled.");
                    mountainScanner = null;
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onError(Throwable t)
            {
                minecraft.execute(() -> {
                    WorldPreview.LOGGER.error("Mountain peak scan failed", t);
                    setStatusText("Peak scan failed. Check log.");
                    mountainScanner = null;
                    setState(SearchState.IDLE);
                });
            }
        };

        this.mountainScanner = new MountainPeakScanner(
            this.workManager.worldSeed(), currentSampler, config, callback
        );
        setState(SearchState.SEARCHING);
        this.executor.submit(this.mountainScanner::run);
    }

    private void onValidatePeak()
    {
        if (!this.workManager.isSetup()) return;
        if (!this.workManager.isTFCEnabled()) return;
        var sampleUtils = this.workManager.sampleUtils();
        if (sampleUtils == null) return;

        BlockPos center = BlockPos.ZERO;
        var tfcUtils = this.workManager.tfcSampleUtils();
        if (tfcUtils != null)
        {
            var settings = tfcUtils.settings();
            center = new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
        }

        int validationRadius = Math.min(SEARCH_RADII[this.searchAreaIndex], 2000);
        MountainSearchConfig config = MountainSearchConfig.forCurrentSeed(center, validationRadius);
        final long worldSeed = this.workManager.worldSeed();
        HeightSampler currentSampler = (x, z) -> sampleUtils.doHeightSlow(new BlockPos(x, 0, z));

        // Holds scan-1 result between the two async phases; accessed only on the main thread.
        final MountainSearchResult[] result1Holder = {null};

        MountainPeakScanner.Callback callback1 = new MountainPeakScanner.Callback()
        {
            @Override
            public void onProgress(long samplesChecked, int bestHeight, int bestX, int bestZ, String phase)
            {
                minecraft.execute(() -> setStatusText("Validation scan 1 " + phase + ": best Y=" + bestHeight));
            }

            @Override
            public void onComplete(MountainSearchResult result)
            {
                minecraft.execute(() -> {
                    if (state != SearchState.SEARCHING) return; // stopped between phases
                    result1Holder[0] = result;
                    setStatusText("Scan 1 done: Y=" + result.height() + ". Running seeded scan...");

                    TFCSeededHeightSampler seeded;
                    try
                    {
                        seeded = TFCSeededHeightSamplerFactory.create(workManager.chunkGenerator(), worldSeed);
                    }
                    catch (Throwable t)
                    {
                        WorldPreview.LOGGER.error("Failed to create seeded TFC height sampler", t);
                        setStatusText("Validation failed. Check log.");
                        mountainScanner = null;
                        setState(SearchState.IDLE);
                        return;
                    }

                    MountainPeakScanner.Callback callback2 = new MountainPeakScanner.Callback()
                    {
                        @Override
                        public void onProgress(long samplesChecked, int bestHeight, int bestX, int bestZ, String phase)
                        {
                            minecraft.execute(() -> setStatusText("Validation scan 2 " + phase + ": best Y=" + bestHeight));
                        }

                        @Override
                        public void onComplete(MountainSearchResult result2)
                        {
                            seeded.close();
                            minecraft.execute(() -> {
                                MountainSearchResult r1 = result1Holder[0];
                                if (r1.height() == result2.height() && r1.x() == result2.x() && r1.z() == result2.z())
                                {
                                    setStatusText("Seeded sampler validated: Y=" + r1.height() + " at X=" + r1.x() + " Z=" + r1.z());
                                    debugBiomeLabel.setText(Component.literal("Seeded TFC height sampler matches current preview seed."));
                                }
                                else
                                {
                                    WorldPreview.LOGGER.warn("Mountain validation mismatch — current: {} | seeded: {}", r1.summary(), result2.summary());
                                    setStatusText("Seeded sampler mismatch. Check log.");
                                    debugBiomeLabel.setText(Component.literal("Validation mismatch — see log."));
                                }
                                mountainScanner = null;
                                setState(SearchState.IDLE);
                            });
                        }

                        @Override
                        public void onCancelled()
                        {
                            seeded.close();
                            minecraft.execute(() -> {
                                setStatusText("Validation cancelled.");
                                mountainScanner = null;
                                setState(SearchState.IDLE);
                            });
                        }

                        @Override
                        public void onError(Throwable t)
                        {
                            seeded.close();
                            minecraft.execute(() -> {
                                WorldPreview.LOGGER.error("Validation seeded scan failed", t);
                                setStatusText("Validation seeded scan failed. Check log.");
                                mountainScanner = null;
                                setState(SearchState.IDLE);
                            });
                        }
                    };

                    MountainPeakScanner scanner2 = new MountainPeakScanner(worldSeed, seeded, config, callback2);
                    mountainScanner = scanner2;
                    executor.submit(scanner2::run);
                });
            }

            @Override
            public void onCancelled()
            {
                minecraft.execute(() -> {
                    setStatusText("Validation cancelled.");
                    mountainScanner = null;
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onError(Throwable t)
            {
                minecraft.execute(() -> {
                    WorldPreview.LOGGER.error("Validation current-seed scan failed", t);
                    setStatusText("Validation scan 1 failed. Check log.");
                    mountainScanner = null;
                    setState(SearchState.IDLE);
                });
            }
        };

        MountainPeakScanner scanner1 = new MountainPeakScanner(worldSeed, currentSampler, config, callback1);
        this.mountainScanner = scanner1;
        setState(SearchState.SEARCHING);
        this.executor.submit(scanner1::run);
    }

    private void onSearchPeaks()
    {
        if (!this.workManager.isSetup()) return;
        if (!this.workManager.isTFCEnabled()) return;
        if (this.workManager.chunkGenerator() == null) return;

        BlockPos center = BlockPos.ZERO;
        var tfcUtils = this.workManager.tfcSampleUtils();
        if (tfcUtils != null)
        {
            var settings = tfcUtils.settings();
            center = new BlockPos(settings.spawnCenterX(), 64, settings.spawnCenterZ());
        }

        int radius = SEARCH_RADII[this.searchAreaIndex];
        int maxSeedsCount = Math.max(1, Math.min(100000, this.maxSeeds));
        final MountainSeedSearchConfig config = MountainSeedSearchConfig.randomSearch(center, radius, maxSeedsCount, this.workManager.worldSeed());

        WorldPreview.LOGGER.info("Starting mountain seed search: maxSeeds={}, radius={}, randomSalt={}",
            maxSeedsCount, radius, config.randomSalt());

        final Path outputDir = this.minecraft.gameDirectory.toPath().resolve("world_preview_tfc").resolve("mountain_search");

        MountainSeedSearchEngine.Callback callback = new MountainSeedSearchEngine.Callback()
        {
            @Override
            public void onProgress(int tested, int maxSeeds, long currentSeed, String phase, int bestHeight, int bestX, int bestZ)
            {
                minecraft.execute(() ->
                    setStatusText("Peak seed search " + tested + "/" + maxSeeds
                        + " seed=" + currentSeed + " " + phase
                        + " best Y=" + bestHeight + " at X=" + bestX + " Z=" + bestZ));
            }

            @Override
            public void onSeedComplete(int tested, int maxSeeds, MountainSearchResult seedResult, MountainSearchResult currentBest)
            {
                minecraft.execute(() -> {
                    setStatusText("Peak seeds " + tested + "/" + maxSeeds
                        + ", best Y=" + currentBest.height() + " seed=" + currentBest.seed());
                    debugBiomeLabel.setText(Component.literal(
                         "Global best Y=" + currentBest.height() + ", Last seed Y=" + seedResult.height()));
                });
            }

            @Override
            public void onComplete(List<MountainSearchResult> results, int tested)
            {
                Path outPath = writeMountainResultsQuietly(outputDir, results, config, tested);
                minecraft.execute(() -> {
                    addResultsToMatchList(results);
                    String best = results.isEmpty() ? "?" : "Y=" + results.get(0).height() + " seed=" + results.get(0).seed();
                    setStatusText("Peak seed search complete. Best " + best + (outPath != null ? ". Results written." : "."));
                    debugBiomeLabel.setText(Component.literal("Top " + results.size() + " tallest terrain seeds added to results."));
                    mountainSeedSearchEngine = null;
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onCancelled(List<MountainSearchResult> results, int tested)
            {
                if (!results.isEmpty()) writeMountainResultsQuietly(outputDir, results, config, tested);
                minecraft.execute(() -> {
                    if (!results.isEmpty()) addResultsToMatchList(results);
                    setStatusText("Peak seed search cancelled. Tested " + tested + " seeds.");
                    mountainSeedSearchEngine = null;
                    setState(SearchState.IDLE);
                });
            }

            @Override
            public void onError(Throwable t)
            {
                minecraft.execute(() -> {
                    WorldPreview.LOGGER.error("Peak seed search failed", t);
                    setStatusText("Peak seed search failed. Check log.");
                    mountainSeedSearchEngine = null;
                    setState(SearchState.IDLE);
                });
            }
        };

        this.mountainSeedSearchEngine = new MountainSeedSearchEngine(this.workManager.chunkGenerator(), config, callback);
        setState(SearchState.SEARCHING);
        setStatusText("Peak seed search started. Salt=" + config.randomSalt());
        this.executor.submit(this.mountainSeedSearchEngine);
    }

    private static MatchResult toMountainMatchResult(MountainSearchResult r, int rank)
    {
        String detail = "#" + rank + " Peak Y=" + r.height() + " X=" + r.x() + " Z=" + r.z();
        return new MatchResult(
            Long.toString(r.seed()),
            r.seed(),
            Set.of(),
            Set.of(),
            r.pos(),
            r.pos(),
            detail
        );
    }

    @Nullable
    private static Path writeMountainResultsQuietly(Path dir, List<MountainSearchResult> results,
                                                    MountainSeedSearchConfig config, int testedSeeds)
    {
        try
        {
            Path p = MountainSearchCsvWriter.write(dir, results, config, testedSeeds);
            WorldPreview.LOGGER.info("Mountain seed search results written to {}", p);
            return p;
        }
        catch (IOException e)
        {
            WorldPreview.LOGGER.error("Failed to write mountain search results", e);
            return null;
        }
    }

    private void addResultsToMatchList(List<MountainSearchResult> results)
    {
        List<MatchResult> newMatches = new ArrayList<>();
        for (int i = 0; i < results.size(); i++)
        {
            newMatches.add(toMountainMatchResult(results.get(i), i + 1));
        }
        allMatches.addAll(newMatches);
        matchList.replaceEntries(allMatches.stream().map(matchList::createEntry).toList());
        if (!newMatches.isEmpty()) matchList.selectAndScrollToResult(newMatches.get(0));
    }

    private void onSkipMatch()
    {
        if (this.engine != null)
        {
            setState(SearchState.SEARCHING);
            this.engine.resume();
        }
    }

    private void onOpenTfcSettings()
    {
        if (!this.workManager.isTFCEnabled()) return;
        if (this.minecraft.screen == null) return;

        var gen = this.workManager.chunkGenerator();
        net.dries007.tfc.world.ChunkGeneratorExtension ext =
            (gen instanceof net.dries007.tfc.world.ChunkGeneratorExtension e) ? e : null;

        this.minecraft.setScreen(new SettingsScreen(this.minecraft.screen, this.previewTab.mainScreenWidget(), ext));
    }

    private void onPreviewMatch()
    {
        SeedMatchList.Entry selected = this.matchList.getSelected();
        if (selected == null) return;

        MatchResult result = selected.result();
        BlockPos location = result.center() != null ? result.center() : result.featureLocation();
        this.previewTab.previewSeedFromSearch(result.seedString(), location);
    }

    private void onCopySeed()
    {
        SeedMatchList.Entry selected = this.matchList.getSelected();
        if (selected == null) return;
        this.minecraft.keyboardHandler.setClipboard(selected.result().seedString());
    }

    private void setState(SearchState newState)
    {
        this.state = newState;

        boolean idle = newState == SearchState.IDLE;
        boolean searching = newState == SearchState.SEARCHING;
        boolean paused = newState == SearchState.PAUSED;

        this.startButton.visible = idle;
        this.startButton.active = idle && this.workManager.isSetup();
        this.stopButton.visible = searching || paused;
        this.stopButton.active = searching || paused;
        this.skipButton.visible = paused;
        this.skipButton.active = paused;
        this.mountainButton.visible = idle;
        this.mountainButton.active = idle && this.workManager.isSetup() && this.workManager.sampleUtils() != null;
        this.validateButton.visible = idle;
        this.validateButton.active = idle && this.workManager.isSetup() && this.workManager.isTFCEnabled() && this.workManager.sampleUtils() != null;
        this.mountainSeedSearchButton.visible = idle;
        this.mountainSeedSearchButton.active = idle && this.workManager.isSetup() && this.workManager.isTFCEnabled() && this.workManager.chunkGenerator() != null;

        this.biomeCheckboxList.active = idle;
        this.terrainFeatureList.active = idle && this.workManager.isTFCEnabled();
        this.tfcSettingsButton.active = idle && this.workManager.isTFCEnabled();
        this.biomeMatchModeButton.active = idle;
        this.clearButton.active = idle;
        this.searchAreaButton.active = idle;
        this.maxSeedsEdit.active = idle;

        boolean hasSelection = this.matchList.getSelected() != null;
        this.previewButton.active = hasSelection;
        this.copyButton.active = hasSelection;
    }

    public void doLayout(ScreenRectangle rect)
    {
        if (rect == null)
        {
            assert this.minecraft.screen != null;
            rect = this.minecraft.screen.getRectangle();
        }

        int totalWidth = rect.width();
        int biomeLeft = rect.left() + 4;
        int top = rect.top() + 4;
        int bottom = rect.bottom() - 8;
        int gap = 4;

        int btnW = Math.max(120, Math.min(160, totalWidth / 4));
        int criteriaWidth = totalWidth - btnW - gap * 3;

        int biomeWidth;
        int featureWidth;
        if (this.terrainFeatureList.visible)
        {
            biomeWidth = (criteriaWidth - gap) * 3 / 5;
            featureWidth = criteriaWidth - biomeWidth - gap;
        }
        else
        {
            biomeWidth = criteriaWidth;
            featureWidth = 0;
        }

        int featureLeft = biomeLeft + biomeWidth + gap;
        int resultsLeft = biomeLeft + criteriaWidth + gap;

        int y = top;
        int controlRowWidth = this.terrainFeatureList.visible ? (biomeWidth + gap + featureWidth) : biomeWidth;

        int searchAreaW = 70;
        int maxSeedsW = 50;
        int tfcSettingsW = 70;
        int startW = 60;
        int modeW = 60;
        int clearW = 40;
        int mountainW = 60;
        int validateW = 60;
        int peakSeedsW = 70;

        this.searchAreaButton.setPosition(biomeLeft, y);
        this.searchAreaButton.setWidth(searchAreaW);

        this.maxSeedsEdit.setPosition(biomeLeft + searchAreaW + gap, y);
        this.maxSeedsEdit.setWidth(maxSeedsW);

        int tfcGap = this.tfcSettingsButton.visible ? gap : 0;
        int tfcW = this.tfcSettingsButton.visible ? tfcSettingsW : 0;
        this.tfcSettingsButton.setPosition(biomeLeft + searchAreaW + gap + maxSeedsW + tfcGap, y);
        this.tfcSettingsButton.setWidth(tfcSettingsW);

        int leftClusterW = searchAreaW + gap + maxSeedsW + tfcGap + tfcW;

        // Basic controls: mode / clear / start-stop
        int basicRightClusterW = modeW + gap + clearW + gap + startW;
        // Mountain controls: Find Peak / Validate / Search Peaks
        int mountainClusterW = mountainW + gap + validateW + gap + peakSeedsW;

        // Put mountain controls on their own row when the combined total is too wide
        boolean mountainOnOwnRow = leftClusterW + gap + basicRightClusterW + gap + mountainClusterW > controlRowWidth;
        // Wrap basic controls to row 2 only when even they overflow with the left cluster
        boolean wrapRow1 = leftClusterW + gap + basicRightClusterW > controlRowWidth;

        int basicY = wrapRow1 ? y + 22 : y;

        int basicRightX, mountainX, mountainY;
        if (mountainOnOwnRow)
        {
            basicRightX = biomeLeft + controlRowWidth - basicRightClusterW;
            mountainY    = basicY + 22;
            mountainX    = biomeLeft + controlRowWidth - mountainClusterW;
        }
        else
        {
            // Both clusters fit on one row: right-align the combined group
            int fullClusterW = basicRightClusterW + gap + mountainClusterW;
            basicRightX = biomeLeft + controlRowWidth - fullClusterW;
            mountainY   = basicY;
            mountainX   = basicRightX + basicRightClusterW + gap;
        }
        if (basicRightX < biomeLeft) basicRightX = biomeLeft;
        if (mountainX  < biomeLeft) mountainX   = biomeLeft;

        this.biomeMatchModeButton.setPosition(basicRightX, basicY);
        this.biomeMatchModeButton.setWidth(modeW);

        this.clearButton.setPosition(basicRightX + modeW + gap, basicY);
        this.clearButton.setWidth(clearW);

        this.startButton.setPosition(basicRightX + modeW + gap + clearW + gap, basicY);
        this.startButton.setWidth(startW);
        this.stopButton.setPosition(basicRightX + modeW + gap + clearW + gap, basicY);
        this.stopButton.setWidth(startW);

        this.mountainButton.setPosition(mountainX, mountainY);
        this.mountainButton.setWidth(mountainW);

        this.validateButton.setPosition(mountainX + mountainW + gap, mountainY);
        this.validateButton.setWidth(validateW);

        this.mountainSeedSearchButton.setPosition(mountainX + mountainW + gap + validateW + gap, mountainY);
        this.mountainSeedSearchButton.setWidth(peakSeedsW);

        y = mountainY + 22;

        int listTop = y;
        int listHeight = bottom - listTop;
        this.biomeCheckboxList.setPosition(biomeLeft, listTop);
        this.biomeCheckboxList.setSize(biomeWidth, listHeight);

        if (this.terrainFeatureList.visible)
        {
            this.terrainFeatureList.setPosition(featureLeft, listTop);
            this.terrainFeatureList.setSize(featureWidth, listHeight);
        }

        int ry = top;

        this.statusLabel.setPosition(resultsLeft, ry);
        this.statusLabel.setWidth(btnW);
        ry += 14;

        this.debugBiomeLabel.setPosition(resultsLeft, ry);
        this.debugBiomeLabel.setWidth(btnW);
        ry += 14;

        int matchListBottom = bottom - 24;
        this.matchList.setPosition(resultsLeft, ry);
        this.matchList.setSize(btnW, matchListBottom - ry);

        int btnY = bottom - 20;

        int halfBtnW = (btnW - gap) / 2;
        this.previewButton.setPosition(resultsLeft, btnY);
        this.previewButton.setWidth(halfBtnW);
        this.copyButton.setPosition(resultsLeft + halfBtnW + gap, btnY);
        this.copyButton.setWidth(halfBtnW);

        this.skipButton.setPosition(resultsLeft, btnY - 22);
        this.skipButton.setWidth(btnW);
    }

    public List<AbstractWidget> widgets()
    {
        return this.allWidgets;
    }

    private void setStatusText(String text)
    {
        this.statusLabel.setText(Component.literal(text));
    }

    @Override
    public void close()
    {
        this.closed = true;
        if (this.engine != null)
        {
            this.engine.cancel();
        }
        if (this.mountainScanner != null)
        {
            this.mountainScanner.cancel();
        }
        if (this.mountainSeedSearchEngine != null)
        {
            this.mountainSeedSearchEngine.cancel();
        }
        this.executor.shutdownNow();
    }
}
