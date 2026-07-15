package com.rustysnail.world.preview.tfc.backend;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.sampler.ChunkSampler;
import com.rustysnail.world.preview.tfc.backend.sampler.FullQuartSampler;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorageCacheManager;
import com.rustysnail.world.preview.tfc.backend.worker.HeightmapWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.LayerChunkWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.SlowHeightmapWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.StructStartWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.WorkBatch;
import com.rustysnail.world.preview.tfc.backend.worker.WorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.AnnualClimateSchedule;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.CropCalendarSettings;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.KaolinBiomeRules;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.QuartSurfaceHeights;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropContext;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropRegistry;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropSuitability;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPreviewClimateSampler;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCRegionWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCWorkPlan;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.Calendar;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.settings.Settings;

public class WorkManager
{
    private final Object completedSynchro = new Object();
    private final Object previewStorageSynchro = new Object();
    private WorldOptions worldOptions;
    private LevelStem levelStem;
    private DimensionType dimensionType;
    private ChunkGenerator chunkGenerator;
    private ChunkSampler chunkSampler;
    private final ChunkSampler cropSampler = new FullQuartSampler();
    private SampleUtils sampleUtils;
    private PreviewData previewData;
    private PreviewStorage previewStorage;
    private PreviewStorageCacheManager previewStorageCacheManager;
    private TFCSampleUtils tfcSampleUtils;
    private TFCTreeResolver tfcTreeResolver;
    private final KaolinBiomeRules kaolinRules = new KaolinBiomeRules();

    private TFCCropRegistry cropRegistry = TFCCropRegistry.active();
    @Nullable
    private ResourceLocation selectedCropId;
    private TFCCropSuitability.CropWaterMode cropWaterMode = TFCCropSuitability.CropWaterMode.RAIN_FED;
    private final java.util.concurrent.atomic.AtomicInteger cropRevision = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger worldGenerationRevision = new java.util.concurrent.atomic.AtomicInteger(0);
    // The active in-game server, if the preview is opened from an existing world (else null on the
    // create-world screen). Used to read the world's live calendar month length.
    @Nullable
    private MinecraftServer activeServer;
    // The calendar/growth-modifier settings the currently-generated crop data assumes. Compared each
    // queue pass so a changed month length or growth modifier regenerates flag 16 exactly once.
    @Nullable
    private CropCalendarSettings capturedCropCalendar;
    // Bounded hover caches (crop suitability breakdown, and ChunkData+four-height grid). Keyed to
    // include everything a crop result depends on, so a stale entry can never be served.
    private final CropHoverCache cropHoverCache = new CropHoverCache(256, 16);
    private final ChunkDataCache chunkDataHoverCache = new ChunkDataCache(64);

    @Nullable
    private Settings tfcSettingsOverride;
    private final RenderSettings renderSettings;
    private final WorldPreviewConfig config;
    private final List<WorkBatch> currentBatches = new ArrayList<>();
    private final List<Future<?>> futures = new ArrayList<>();
    private final List<Future<?>> queueFutures = new ArrayList<>();
    private final SplittableRandom random = new SplittableRandom();
    private ExecutorService executorService;
    private ExecutorService queueChunksService;
    private ExecutorService cropHoverExecutor;
    private ChunkPos lastQueuedTopLeft;
    private ChunkPos lastQueuedBotRight;

    private boolean lastQueuedWasTfc = false;
    private long lastQueuedModeFlag = Long.MIN_VALUE;
    private final AtomicBoolean queueIsRunning = new AtomicBoolean(false);
    private final AtomicBoolean shouldEarlyAbortQueuing = new AtomicBoolean(false);

    // Chunks per side of a combined TFC work unit. Power of two; do not reduce below 8 without
    // profiling. Smaller = faster cancellation and sooner partial results, more scheduling overhead.
    private static final int TFC_UNIT_CHUNKS = 16;
    private static final int TFC_CROP_UNIT_CHUNKS = 8;

    // Monotonic counter bumped whenever preview data changes (results applied, or sections
    // invalidated). PreviewDisplay compares it to decide whether the texture must be rebuilt.
    private final AtomicLong dataRevision = new AtomicLong(0);

    public WorkManager(RenderSettings renderSettings, WorldPreviewConfig config)
    {
        this.config = config;
        this.renderSettings = renderSettings;
    }

    public synchronized void setTFCSettingsOverride(@Nullable Settings settings)
    {
        this.tfcSettingsOverride = settings;

        if (settings != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext)
        {
            try
            {
                ext.applySettings(old -> settings);
            }
            catch (Throwable t)
            {
                WorldPreview.LOGGER.warn("Failed to apply TFC settings override to preview chunk generator", t);
            }
        }
    }

    @Nullable
    public synchronized Settings getTFCSettingsOverride()
    {
        return this.tfcSettingsOverride;
    }

    public synchronized void onTFCSettingsChanged()
    {
        if (this.chunkGenerator == null || this.worldOptions == null || this.previewStorage == null)
        {
            return;
        }

        if (this.tfcSettingsOverride != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext)
        {
            try
            {
                ext.applySettings(old -> this.tfcSettingsOverride);
                this.kaolinRules.rebuild(this.sampleUtils.resourceManager());
            }
            catch (Throwable t)
            {
                WorldPreview.LOGGER.warn("Failed to re-apply TFC settings override to preview chunk generator", t);
            }
        }

        this.tfcSampleUtils = TFCSampleUtils.create(this.chunkGenerator, this.sampleUtils.registryAccess(), this.worldOptions.seed());
        this.tfcTreeResolver = this.tfcSampleUtils != null
            ? TFCTreeResolver.create(this.sampleUtils.registryAccess(), this.tfcSampleUtils.settings())
            : null;
        this.rebuildCropRegistry();

        synchronized (this.previewStorageSynchro)
        {
            this.previewStorage.invalidateFlags(RenderSettings.RenderMode.TFC_TEMPERATURE.flag, RenderSettings.RenderMode.TFC_RAINFALL.flag);
        }
        this.bumpDataRevision();

        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;

        // restartExecutors() -> shutdownExecutors() cancels and clears currentBatches; do not clear
        // here first, or those batches would never get their isCanceled flag set.
        this.restartExecutors();
    }

    /**
     * Called when the feature-icon overlay is toggled. Forces the next queueRange to re-evaluate,
     * so TFC feature detection is queued when the overlay turns on (and not otherwise).
     */
    public synchronized void onFeatureOverlayChanged()
    {
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
    }

    public synchronized void onResolutionChanged()
    {
        if (this.chunkGenerator == null || this.worldOptions == null || this.previewStorage == null)
        {
            return;
        }

        this.chunkSampler = this.renderSettings.samplerType.create(this.renderSettings.quartStride());

        synchronized (this.previewStorageSynchro)
        {
            this.previewStorage.invalidateAll();
        }
        this.bumpDataRevision();

        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;

        // See onTFCSettingsChanged: let restartExecutors() cancel+clear currentBatches.
        this.restartExecutors();

        WorldPreview.LOGGER.info("Resolution changed to {} pixels per chunk", this.renderSettings.pixelsPerChunk());
    }

    public synchronized void onRenderModeChanged()
    {
        if (this.previewStorage == null) return;

        RenderSettings.RenderMode mode = this.renderSettings.mode;

        // Display-only switch between combined-TFC modes (e.g. Forest Type <-> Tree Species): the
        // one combined work unit already writes every TFC section, so keep running work and bounds
        // instead of discarding and restarting generation. Kaolin is excluded (it gates an
        // expensive computation), so switching to/from it still falls through to a full reset.
        boolean displayOnlyTfcSwitch = mode != null
            && mode.isTFC()
            && this.lastQueuedWasTfc
            && this.effectiveModeKey(mode) == this.lastQueuedModeFlag;
        if (displayOnlyTfcSwitch)
        {
            WorldPreview.LOGGER.debug("Render mode -> {}: display-only TFC switch, keeping in-flight generation", mode);
            return;
        }

        if (mode != null && !mode.isTFC())
        {
            synchronized (this.previewStorageSynchro)
            {
                this.previewStorage.invalidateFlags(mode.flag);
            }
            this.bumpDataRevision();
        }

        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;

        // Cancel obsolete work before dropping references (previously cleared without canceling,
        // so orphaned batches kept running and blocked the thread pool).
        synchronized (this.currentBatches)
        {
            this.currentBatches.forEach(WorkBatch::cancel);
            this.currentBatches.clear();
        }
    }


    private void restartExecutors()
    {
        this.shutdownExecutors();
        this.executorService = Executors.newFixedThreadPool(this.config.numThreads());
        this.queueChunksService = Executors.newSingleThreadExecutor();
        this.cropHoverExecutor = Executors.newSingleThreadExecutor();
        this.queueIsRunning.set(false);
        this.shouldEarlyAbortQueuing.set(false);
        this.futures.clear();
        this.queueFutures.clear();
    }

    public synchronized void changeWorldGenState(
        LevelStem _levelStem,
        LayeredRegistryAccess<RegistryLayer> _registryAccess,
        PreviewData _previewData,
        WorldOptions _worldOptions,
        WorldDataConfiguration _worldDataConfiguration,
        PreviewStorageCacheManager _previewStorageCacheManager,
        Proxy proxy,
        @Nullable Path tempDataPackDir,
        @Nullable MinecraftServer server
    )
    {
        this.cancel();
        // Invalidates any async hover computation that captured the previous world-gen state.
        this.cropRevision.incrementAndGet();
        this.worldGenerationRevision.incrementAndGet();
        this.activeServer = server;
        this.worldOptions = _worldOptions;
        this.levelStem = _levelStem;
        this.dimensionType = this.levelStem.type().value();
        this.chunkGenerator = this.levelStem.generator();

        if (this.tfcSettingsOverride != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext)
        {
            try
            {
                ext.applySettings(old -> this.tfcSettingsOverride);
            }
            catch (Throwable t)
            {
                WorldPreview.LOGGER.warn("Failed to apply TFC settings override to rebuilt preview chunk generator", t);
            }
        }

        BiomeSource biomeSource = this.chunkGenerator.getBiomeSource();
        this.previewStorageCacheManager = _previewStorageCacheManager;
        this.chunkSampler = this.renderSettings.samplerType.create(this.renderSettings.quartStride());
        this.previewData = _previewData;
        LevelHeightAccessor levelHeightAccessor = LevelHeightAccessor.create(this.dimensionType.minY(), this.dimensionType.height());

        try
        {
            if (server == null)
            {
                this.sampleUtils = new SampleUtils(
                    biomeSource,
                    this.chunkGenerator,
                    _registryAccess,
                    this.worldOptions,
                    this.levelStem,
                    levelHeightAccessor,
                    _worldDataConfiguration,
                    proxy,
                    tempDataPackDir
                );
            }
            else
            {
                this.sampleUtils = new SampleUtils(server, biomeSource, this.chunkGenerator, this.worldOptions, this.levelStem, levelHeightAccessor);
            }

            this.tfcSampleUtils = TFCSampleUtils.create(this.chunkGenerator, this.sampleUtils.registryAccess(), this.worldOptions.seed());
            if (this.tfcSampleUtils != null)
            {
                this.kaolinRules.rebuild(this.sampleUtils.resourceManager());
                this.tfcTreeResolver = TFCTreeResolver.create(this.sampleUtils.registryAccess(), this.tfcSampleUtils.settings());
                this.rebuildCropRegistry();
                WorldPreview.LOGGER.info("TFC-compatible chunk generator detected, TFC sampling enabled");
            }
            else
            {
                this.tfcTreeResolver = null;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void postChangeWorldGenState()
    {
        this.previewStorage = this.previewStorageCacheManager.loadPreviewStorage(this.worldOptions.seed(), this.yMin(), this.yMax());
        // Crop-suitability sections depend on the selected crop, water mode, month length and growth
        // modifier - none of which are stored beside flag 16 - so a loaded section could belong to a
        // different crop/calendar. Always drop flag 16 on load; crop data is session-only for now.
        this.previewStorage.invalidateFlags(RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag);
        this.capturedCropCalendar = null; // force a fresh calendar resolve + regenerate on next crop pass
        this.cropHoverCache.clear();
        this.chunkDataHoverCache.clear();
        this.executorService = Executors.newFixedThreadPool(this.config.numThreads());
        this.queueChunksService = Executors.newSingleThreadExecutor();
        this.cropHoverExecutor = Executors.newSingleThreadExecutor();
    }

    private void shutdownExecutors()
    {
        this.cropHoverCache.clear();
        if (this.cropHoverExecutor != null)
        {
            this.cropHoverExecutor.shutdownNow();
            try
            {
                this.cropHoverExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            this.cropHoverExecutor = null;
        }
        if (this.executorService != null)
        {
            this.shouldEarlyAbortQueuing.set(true);
            synchronized (this.currentBatches)
            {
                this.currentBatches.forEach(WorkBatch::cancel);
                this.currentBatches.clear();
            }

            try
            {
                List<Future<?>> allFutures = new ArrayList<>();
                synchronized (this.futures)
                {
                    allFutures.addAll(this.queueFutures);
                    allFutures.addAll(this.futures);
                }

                for (Future<?> f : allFutures)
                {
                    f.get();
                }
            }
            catch (ExecutionException | InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            this.executorService.shutdownNow();
            this.queueChunksService.shutdownNow();
            try
            {
                this.executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void cancel()
    {
        this.shutdownExecutors();
        Executor serverThreadPoolExecutor = WorldPreview.get().serverThreadPoolExecutor();
        if (this.sampleUtils != null)
        {
            try
            {
                if (serverThreadPoolExecutor != null)
                {
                    CompletableFuture.runAsync(() -> {
                        try
                        {
                            this.sampleUtils.close();
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }, serverThreadPoolExecutor).get();
                }
                else
                {
                    this.sampleUtils.close();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        if (this.previewStorageCacheManager != null)
        {
            this.previewStorageCacheManager.storePreviewStorage(this.worldOptions.seed(), this.previewStorage);
        }

        this.worldOptions = null;
        this.levelStem = null;
        this.dimensionType = null;
        this.chunkGenerator = null;
        this.sampleUtils = null;
        this.tfcSampleUtils = null;
        this.tfcTreeResolver = null;
        this.previewStorage = null;
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.queueIsRunning.set(false);
        this.futures.clear();
        this.executorService = null;
        this.queueChunksService = null;
        this.cropHoverExecutor = null;
        this.previewStorageCacheManager = null;
    }

    public void queueRange(BlockPos topLeftBlock, BlockPos bottomRightBlock)
    {
        ChunkPos topLeft = new ChunkPos(topLeftBlock);
        ChunkPos bottomRight = new ChunkPos(bottomRightBlock);

        final RenderSettings.RenderMode mode = this.renderSettings.mode;
        final boolean tfcMode = mode != null && mode.isTFC();
        final long modeFlag = this.effectiveModeKey(mode);

        // Calendar/config values can change without the viewport moving. Poll before the queue
        // identity short-circuit so crop work and hover data are invalidated immediately.
        if (mode == RenderSettings.RenderMode.TFC_CROP_SUITABILITY
            && this.tfcSampleUtils != null && this.previewStorage != null)
        {
            this.ensureCropCalendarCurrent();
        }

        if (this.executorService != null
            && this.sampleUtils != null
            && (
            !topLeft.equals(this.lastQueuedTopLeft)
                || !bottomRight.equals(this.lastQueuedBotRight)
                || tfcMode != this.lastQueuedWasTfc
                || modeFlag != this.lastQueuedModeFlag
        ))
        {
            if (this.queueIsRunning.get())
            {
                this.shouldEarlyAbortQueuing.set(true);
            }
            else
            {
                this.lastQueuedTopLeft = topLeft;
                this.lastQueuedBotRight = bottomRight;
                this.lastQueuedWasTfc = tfcMode;
                this.lastQueuedModeFlag = modeFlag;
                synchronized (this.futures)
                {
                    BlockPos normalizedTopLeft = new BlockPos(topLeftBlock.getX(), 0, topLeftBlock.getZ());
                    BlockPos normalizedBottomRight = new BlockPos(bottomRightBlock.getX(), 0, bottomRightBlock.getZ());
                    this.queueFutures.add(this.queueChunksService.submit(() -> this.queueRangeWrapper(normalizedTopLeft, normalizedBottomRight)));
                }
            }
        }
    }

    private void queueRangeWrapper(BlockPos topLeftBlock, BlockPos bottomRightBlock)
    {
        this.queueIsRunning.set(true);
        this.shouldEarlyAbortQueuing.set(false);

        try
        {
            this.queueRangeReal(topLeftBlock, bottomRightBlock);
        }
        catch (Throwable e)
        {
            WorldPreview.LOGGER.error("Error queuing range", e);
        }
        finally
        {
            this.queueIsRunning.set(false);
        }
    }

    public void queueRangeReal(BlockPos topLeftBlock, BlockPos bottomRightBlock)
    {
        Instant start = Instant.now();
        ChunkPos topLeft = new ChunkPos(topLeftBlock);
        ChunkPos bottomRight = new ChunkPos(bottomRightBlock);

        // Cancel the previous viewport's work and interrupt its futures, but do NOT block waiting
        // for large obsolete TFC units to finish — that is what left a moved viewport black. The
        // canceled batches observe isCanceled and exit fast; canceled batches never apply or mark
        // completed (see WorkBatch#process), so they cannot write stale data over the new range.
        int canceledBatches;
        synchronized (this.currentBatches)
        {
            canceledBatches = this.currentBatches.size();
            this.currentBatches.forEach(WorkBatch::cancel);
            this.currentBatches.clear();
        }

        synchronized (this.futures)
        {
            for (Future<?> f : this.futures)
            {
                f.cancel(true);
            }
            this.futures.clear();
        }

        long cancelWaitMs = Duration.between(start, Instant.now()).abs().toMillis();
        WorldPreview.LOGGER.debug("Queue replacement: canceled {} obsolete batches in {} ms (no blocking get)",
            canceledBatches, cancelWaitMs);

        List<ChunkPos> chunks = ChunkPos.rangeClosed(topLeft, bottomRight).toList();
        int units = 0;

        boolean tfcMode = this.renderSettings.mode != null && this.renderSettings.mode.isTFC();
        if (!tfcMode)
        {
            units += this.queueForLevel(chunks, topLeftBlock.getY(), 4096, this::workUnitFactory);
        }
        if (this.config.sampleStructures && !this.shouldEarlyAbortQueuing.get())
        {
            units += this.queueForLevel(chunks, 0, 256, (pos, yx) -> new StructStartWorkUnit(this.sampleUtils, pos, this.previewData));
        }

        // Only queue TFC work for TFC modes, or when the feature overlay explicitly needs TFC feature
        // icons — the normal biome map no longer triggers full TFC generation. The plan makes each
        // mode compute only what it needs (e.g. Temperature does not sample rocks or resolve trees).
        final boolean featureOverlay = this.renderSettings.featureOverlay;
        final TFCWorkPlan tfcPlan = TFCWorkPlan.forMode(this.renderSettings.mode, featureOverlay);
        final boolean queueTfc = tfcMode || (featureOverlay && tfcPlan.anyOutput());
        if (this.tfcSampleUtils != null && queueTfc && tfcPlan.anyOutput() && !this.shouldEarlyAbortQueuing.get())
        {
            TFCRegionWorkUnit.resetStats();

            LongSet queuedTFCChunks = new LongOpenHashSet(chunks.size());
            List<ChunkPos> tfcChunks = new ArrayList<>(chunks.size());

            // Smaller units (16x16 chunks, was 32x32) so cancellation responds ~4x faster, partial
            // results arrive sooner, and a moved viewport is black for less time. Must be a power of
            // two: unit origins are aligned to numChunks so the units tile the range without gaps.
            // Crop suitability uses even smaller 8x8 units - its per-point cost is higher (48 annual
            // samples), so smaller units cancel faster on crop/pan changes and stream partial results.
            final int numChunks = tfcPlan.cropSuitability() ? TFC_CROP_UNIT_CHUNKS : TFC_UNIT_CHUNKS;
            final int alignBits = Integer.numberOfTrailingZeros(numChunks);

            for (ChunkPos c : chunks)
            {
                ChunkPos shifted = new ChunkPos(c.x >> alignBits << alignBits, c.z >> alignBits << alignBits);
                if (queuedTFCChunks.add(shifted.toLong()))
                {
                    tfcChunks.add(shifted);
                }
            }

            TFCRegionWorkUnit.setTotalUnits(tfcChunks.size());
            WorldPreview.LOGGER.debug("[TFC] Queuing {} TFC units, plan[{}] for mode {}",
                tfcChunks.size(), tfcPlan.describe(), this.renderSettings.mode);

            final long worldSeed = this.worldOptions.seed();
            // Snapshot the crop selection for this queue pass. Units capture the crop revision so that
            // a later crop/water-mode change makes them stale (results discarded); see TFCCropContext.
            final TFCCropContext cropContext = tfcPlan.cropSuitability() ? this.buildCropContext() : null;
            units += this.queueForLevel(
                tfcChunks,
                0,
                1,
                (pos, yx) -> new TFCRegionWorkUnit(
                    this.chunkSampler,
                    this.cropSampler,
                    this.sampleUtils,
                    pos,
                    numChunks,
                    this.previewData,
                    this.tfcSampleUtils.regionGenerator(),
                    this.tfcSampleUtils,
                    this.tfcTreeResolver,
                    this.kaolinRules,
                    tfcPlan,
                    cropContext,
                    worldSeed
                )
            );
        }

        if (!tfcMode && this.config.sampleHeightmap && !this.shouldEarlyAbortQueuing.get() && this.sampleUtils.noiseGeneratorSettings() != null)
        {
            LongSet queuedChunks = new LongOpenHashSet(chunks.size());
            List<ChunkPos> heightMapChunks = new ArrayList<>(chunks.size());

            for (ChunkPos c : chunks)
            {
                ChunkPos shifted = new ChunkPos(c.x >> 4 << 4, c.z >> 4 << 4);
                if (queuedChunks.add(shifted.toLong()))
                {
                    heightMapChunks.add(shifted);
                }
            }

            units += this.queueForLevel(heightMapChunks, 0, 1, (pos, yx) -> new HeightmapWorkUnit(this.chunkSampler, this.sampleUtils, pos, 16, this.previewData));
        }
        else if (!tfcMode && this.config.sampleHeightmap && !this.shouldEarlyAbortQueuing.get())
        {
            units += this.queueForLevel(chunks, 0, 64, (pos, yx) -> new SlowHeightmapWorkUnit(this.chunkSampler, this.sampleUtils, pos, this.previewData));
        }

        Instant end = Instant.now();
        int pendingFutures;
        synchronized (this.futures)
        {
            pendingFutures = this.futures.size();
        }
        WorldPreview.LOGGER
            .info(
                "Queued {} chunks for generation using {} batches [{} ms] {} pending futures {}",
                units, this.currentBatches.size(), Duration.between(start, end).abs().toMillis(), pendingFutures, this.shouldEarlyAbortQueuing.get() ? "{early abort}" : "");
    }

    /**
     * Queue-identity key for a render mode. Each mode now has its own key: TFC generation is
     * mode-specific (see TFCWorkPlan), so switching modes must re-queue to compute the new mode's
     * data. Per-group completion tracking then skips chunks whose data already exists, so re-queuing
     * an already-generated mode is cheap.
     */
    private long effectiveModeKey(RenderSettings.RenderMode mode)
    {
        return mode == null ? Long.MIN_VALUE : mode.flag;
    }

    private WorkUnit workUnitFactory(ChunkPos pos, int y)
    {
        return new LayerChunkWorkUnit(this.chunkSampler, pos, this.sampleUtils, this.previewData, y);
    }

    private int queueForLevel(List<ChunkPos> chunks, int y, int maxBatchSize, BiFunction<ChunkPos, Integer, WorkUnit> workUnitFactoryFunc)
    {
        WorkUnit[] toQueue = new WorkUnit[chunks.size()];
        int size = 0;
        synchronized (this.completedSynchro)
        {
            for (ChunkPos chunkPos : chunks)
            {
                WorkUnit workUnit = workUnitFactoryFunc.apply(chunkPos, y);
                if (!workUnit.isCompleted())
                {
                    toQueue[size++] = workUnit;
                }
            }
        }

        if (size == 0)
        {
            return 0;
        }
        else
        {
            for (int i = size - 1; i > 1; i--)
            {
                int randomIndexToSwap = this.random.nextInt(size);
                WorkUnit temp = toQueue[randomIndexToSwap];
                toQueue[randomIndexToSwap] = toQueue[i];
                toQueue[i] = temp;
            }

            final int batchSize = maxBatchSize <= 1 ? 1 : Math.clamp(size / 4096, 8, maxBatchSize);
            WorkBatch[] batches = new WorkBatch[batchSize == 1 ? size : size / batchSize + 1];
            if (batchSize > 1)
            {
                int batchIdx = 0;
                batches[batchIdx] = new WorkBatch(new ArrayList<>(batchSize), this.completedSynchro, this.previewData);

                for (int i = 0; i < size; i++)
                {
                    batches[batchIdx].workUnits.add(toQueue[i]);
                    if (batches[batchIdx].workUnits.size() >= batchSize)
                    {
                        batches[++batchIdx] = new WorkBatch(new ArrayList<>(batchSize), this.completedSynchro, this.previewData);
                    }
                }
            }
            else
            {
                for (int ix = 0; ix < size; ix++)
                {
                    batches[ix] = new WorkBatch(List.of(toQueue[ix]), this.completedSynchro, this.previewData);
                }
            }

            synchronized (this.futures)
            {
                for (WorkBatch batch : batches)
                {
                    this.futures.add(this.executorService.submit(batch::process));
                }
            }

            synchronized (this.currentBatches)
            {
                this.currentBatches.addAll(Arrays.asList(batches));
                return size;
            }
        }
    }

    public int yMin()
    {
        return this.dimensionType == null ? 0 : this.dimensionType.minY();
    }

    public int yMax()
    {
        return this.yMin() + (this.dimensionType == null ? 256 : this.dimensionType.height());
    }

    public PreviewStorage previewStorage()
    {
        return this.previewStorage;
    }

    public boolean isSetup()
    {
        return this.executorService != null;
    }

    public boolean hasWorldSeed()
    {
        return this.worldOptions != null;
    }

    public long worldSeed()
    {
        WorldOptions options = this.worldOptions;
        return options != null ? options.seed() : 0L;
    }

    public WorldPreviewConfig config()
    {
        return this.config;
    }

    public ResourceManager sampleResourceManager()
    {
        return this.sampleUtils.resourceManager();
    }

    public SampleUtils sampleUtils()
    {
        return this.sampleUtils;
    }

    @Nullable
    public TFCSampleUtils tfcSampleUtils()
    {
        return this.tfcSampleUtils;
    }

    // ----------------------------- Crop suitability -----------------------------

    /**
     * Rebuilds the crop registry from the current block registry + datapack, keeping a valid selection.
     */
    private void rebuildCropRegistry()
    {
        TFCCropRegistry previous = this.cropRegistry;
        if (this.tfcSampleUtils != null)
        {
            this.cropRegistry = TFCCropRegistry.build(this.sampleUtils.resourceManager());
        }
        TFCCropRegistry.setActive(this.cropRegistry);
        if (this.selectedCropId == null || this.cropRegistry.get(this.selectedCropId) == null)
        {
            TFCCropRegistry.Entry first = this.cropRegistry.first();
            this.selectedCropId = first != null ? first.id() : null;
        }
        if (this.previewStorage != null && !previous.entries().equals(this.cropRegistry.entries()))
        {
            // Resource/addon climate changes can alter suitability without changing the crop ID.
            this.cropRevision.incrementAndGet();
            this.invalidateCropMapState();
        }
    }

    public TFCCropRegistry cropRegistry()
    {
        return this.cropRegistry;
    }

    @Nullable
    public ResourceLocation selectedCropId()
    {
        return this.selectedCropId;
    }

    public TFCCropSuitability.CropWaterMode cropWaterMode()
    {
        return this.cropWaterMode;
    }

    public int cropRevision()
    {
        return this.cropRevision.get();
    }

    /**
     * The calendar assumptions crop suitability should use right now. In-game the world's live calendar
     * wins (TFC lets {@code /time} change month length in an existing world); on the create-world screen
     * we fall back to the configured default month length, and to {@link Calendar#DEFAULT_MONTH_LENGTH}
     * only if the config read throws.
     */
    private CropCalendarSettings resolveCropCalendarSettings()
    {
        int daysInMonth;
        try
        {
            if (this.activeServer != null)
            {
                daysInMonth = Calendars.get(this.activeServer.overworld()).getCalendarDaysInMonth();
            }
            else
            {
                daysInMonth = TFCConfig.COMMON.defaultMonthLength.get();
            }
        }
        catch (Throwable t)
        {
            daysInMonth = Calendar.DEFAULT_MONTH_LENGTH;
        }

        float growthModifier;
        try
        {
            growthModifier = TFCConfig.SERVER.cropGrowthModifier.get().floatValue();
        }
        catch (Throwable t)
        {
            growthModifier = 1f;
        }

        return CropCalendarSettings.build(daysInMonth, growthModifier);
    }

    /**
     * Ensures crop data reflects the current calendar. The values are polled cheaply while crop mode
     * is active; only a real change bumps the revision, invalidates flag 16, clears hover caches, and
     * causes the current viewport to regenerate. Returns the settings to use.
     */
    private synchronized CropCalendarSettings ensureCropCalendarCurrent()
    {
        CropCalendarSettings resolved = this.resolveCropCalendarSettings();
        if (!resolved.equals(this.capturedCropCalendar))
        {
            boolean firstTime = this.capturedCropCalendar == null;
            this.capturedCropCalendar = resolved;
            this.cropRevision.incrementAndGet();
            this.invalidateCropMapState();
            WorldPreview.LOGGER.debug(
                "[TFC Crop] Crop calendar: daysInMonth={}, mapSamples={}, daysPerMapSample={}, growthModifier={}, requiredGrowthDays={}{}",
                resolved.daysInMonth(), TFCPreviewClimateSampler.MAP_SAMPLES_PER_YEAR,
                resolved.daysPerSample(TFCPreviewClimateSampler.MAP_SAMPLES_PER_YEAR), resolved.cropGrowthModifier(),
                resolved.requiredGrowthDays(),
                firstTime ? " (initial)" : " (changed -> regenerating)");
        }
        return resolved;
    }

    private synchronized TFCCropContext buildCropContext()
    {
        CropCalendarSettings calendar = this.ensureCropCalendarCurrent();
        TFCCropRegistry.Entry entry = this.selectedCropId != null ? this.cropRegistry.get(this.selectedCropId) : null;
        return new TFCCropContext(
            this.selectedCropId, entry, this.cropWaterMode, calendar, AnnualClimateSchedule.standard(),
            this.cropRevision.get(), this.cropRevision::get);
    }

    /**
     * Selects a crop; regenerates only the crop-suitability map (flag 16), leaving other maps intact.
     */
    public synchronized void setSelectedCrop(@Nullable ResourceLocation cropId)
    {
        if (java.util.Objects.equals(cropId, this.selectedCropId))
        {
            return;
        }
        this.selectedCropId = cropId;
        this.regenerateCropMap();
    }

    /**
     * Switches rain-fed / irrigated hydration; regenerates only the crop-suitability map.
     */
    public synchronized void setCropWaterMode(TFCCropSuitability.CropWaterMode mode)
    {
        if (mode == null || mode == this.cropWaterMode)
        {
            return;
        }
        this.cropWaterMode = mode;
        this.regenerateCropMap();
    }

    /**
     * Bumps the crop revision (so in-flight units go stale), invalidates only flag-16 sections, cancels
     * running batches, and forces the next queue pass to regenerate. All other cached maps (biome, soil,
     * forest, rocks, ...) are untouched. Safe to call outside crop mode - it just clears flag-16 so the
     * next entry into crop mode regenerates for the new selection instead of showing another crop's data.
     */
    private void regenerateCropMap()
    {
        this.cropRevision.incrementAndGet();
        this.invalidateCropMapState();
    }

    /**
     * Invalidates only flag 16, discards pending hover work, and promptly cancels stale work.
     */
    private void invalidateCropMapState()
    {
        this.cropHoverCache.clear();
        if (this.previewStorage == null)
        {
            return;
        }
        synchronized (this.previewStorageSynchro)
        {
            this.previewStorage.invalidateFlags(RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag);
        }
        synchronized (this.currentBatches)
        {
            this.currentBatches.forEach(WorkBatch::cancel);
            this.currentBatches.clear();
        }
        // Force requeue for the current viewport on the next frame.
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.bumpDataRevision();
    }

    /**
     * Polls the detailed result for a quart. A cache miss reserves a bounded pending slot and submits
     * the daily calculation to a worker; the render thread receives {@code null} until it completes.
     * Every dependency requested by the task is present in the key and crop revision guards publish.
     */
    @Nullable
    public synchronized TFCCropSuitability.CropSuitabilityResult requestCropDetailsAt(int blockX, int blockZ)
    {
        if (this.tfcSampleUtils == null || this.sampleUtils == null || this.cropHoverExecutor == null)
        {
            return TFCCropSuitability.NO_DATA_RESULT;
        }

        CropCalendarSettings calendar = this.ensureCropCalendarCurrent();
        TFCCropRegistry.Entry entry = this.selectedCropId != null ? this.cropRegistry.get(this.selectedCropId) : null;
        if (entry == null || !entry.hasClimateData())
        {
            return TFCCropSuitability.NO_DATA_RESULT;
        }

        int quartX = blockX >> 2;
        int quartZ = blockZ >> 2;
        int revision = this.cropRevision.get();
        int worldRevision = this.worldGenerationRevision.get();
        CropHoverCache.Key key = new CropHoverCache.Key(
            quartX, quartZ, this.selectedCropId, this.cropWaterMode,
            calendar.daysInMonth(), calendar.cropGrowthModifier(), revision);

        TFCCropSuitability.CropSuitabilityResult cached = this.cropHoverCache.get(key);
        if (cached != null || !this.cropHoverCache.reserve(key))
        {
            return cached;
        }

        // Snapshot every world/crop dependency before leaving the synchronized section. Computation
        // uses the canonical quart origin so cache results never depend on where the cursor entered it.
        TFCSampleUtils tfc = this.tfcSampleUtils;
        SampleUtils samples = this.sampleUtils;
        TFCCropSuitability.CropWaterMode waterMode = this.cropWaterMode;
        int canonicalX = quartX << 2;
        int canonicalZ = quartZ << 2;
        try
        {
            Future<?> future = this.cropHoverExecutor.submit(() -> {
                TFCCropSuitability.CropSuitabilityResult result = this.computeCropAt(
                    entry, canonicalX, canonicalZ, calendar, waterMode, revision, worldRevision, tfc, samples);
                if (this.cropRevision.get() == revision)
                {
                    this.cropHoverCache.complete(key, result);
                }
                else
                {
                    this.cropHoverCache.discard(key);
                }
            });
            this.cropHoverCache.attach(key, future);
        }
        catch (java.util.concurrent.RejectedExecutionException e)
        {
            this.cropHoverCache.discard(key);
        }
        return null;
    }

    private TFCCropSuitability.CropSuitabilityResult computeCropAt(
        TFCCropRegistry.Entry entry,
        int blockX,
        int blockZ,
        CropCalendarSettings calendar,
        TFCCropSuitability.CropWaterMode waterMode,
        int revision,
        int worldRevision,
        TFCSampleUtils tfc,
        SampleUtils samples
    )
    {
        try
        {
            BiomeExtension biome = tfc.sampleBiomeExtension(blockX, blockZ);
            if (TFCSampleUtils.isTreeMapWaterBiome(biome))
            {
                return TFCCropSuitability.WATER_RESULT;
            }

            ChunkPos cp = new ChunkPos(blockX >> 4, blockZ >> 4);
            ChunkDataCache.Key cacheKey = new ChunkDataCache.Key(cp.toLong(), worldRevision);
            ChunkDataCache.Entry cd = this.chunkDataHoverCache.get(cacheKey);
            if (cd == null)
            {
                ChunkData chunkData = tfc.sampleChunkData(cp);
                QuartSurfaceHeights heights = sampleQuartSurfaceHeights(samples, cp);
                cd = new ChunkDataCache.Entry(chunkData, heights);
                this.chunkDataHoverCache.put(cacheKey, cd);
            }

            int surfaceY = cd.surfaceHeights().interpolatedSurfaceY(blockX, blockZ);
            AnnualClimateSchedule dailySchedule = AnnualClimateSchedule.daily(calendar.daysInMonth());
            return TFCCropSuitability.evaluateDetailed(
                entry, tfc.climateSampler(), cd.chunkData(), blockX, blockZ, surfaceY,
                waterMode, dailySchedule, calendar);
        }
        catch (Exception e)
        {
            return TFCCropSuitability.NO_DATA_RESULT;
        }
    }

    private static QuartSurfaceHeights sampleQuartSurfaceHeights(SampleUtils samples, ChunkPos chunk)
    {
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int near = QuartSurfaceHeights.WEST_OR_NORTH_OFFSET;
        int far = QuartSurfaceHeights.EAST_OR_SOUTH_OFFSET;
        return new QuartSurfaceHeights(
            sampleSurfaceY(samples, minX + near, minZ + near),
            sampleSurfaceY(samples, minX + far, minZ + near),
            sampleSurfaceY(samples, minX + near, minZ + far),
            sampleSurfaceY(samples, minX + far, minZ + far));
    }

    private static int sampleSurfaceY(SampleUtils samples, int blockX, int blockZ)
    {
        try
        {
            return samples.doHeightSlow(new BlockPos(blockX, 0, blockZ));
        }
        catch (Exception e)
        {
            return 63;
        }
    }

    /**
     * Bounded result LRU plus a separately bounded/cancelable set of in-flight calculations.
     */
    private static final class CropHoverCache
    {
        record Key(int quartX, int quartZ, ResourceLocation cropId,
                   TFCCropSuitability.CropWaterMode waterMode, int daysInMonth,
                   float growthModifier, int revision) {}

        private final int maxPending;
        private final java.util.LinkedHashMap<Key, TFCCropSuitability.CropSuitabilityResult> results;
        private final java.util.LinkedHashMap<Key, Future<?>> pending = new java.util.LinkedHashMap<>();

        CropHoverCache(int maxEntries, int maxPending)
        {
            this.maxPending = maxPending;
            this.results = new java.util.LinkedHashMap<>(16, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Key, TFCCropSuitability.CropSuitabilityResult> eldest)
                {
                    return size() > maxEntries;
                }
            };
        }

        synchronized TFCCropSuitability.CropSuitabilityResult get(Key key)
        {
            return this.results.get(key);
        }

        synchronized boolean reserve(Key key)
        {
            if (this.results.containsKey(key) || this.pending.containsKey(key))
            {
                return false;
            }
            while (this.pending.size() >= this.maxPending)
            {
                var it = this.pending.entrySet().iterator();
                var eldest = it.next();
                if (eldest.getValue() != null) eldest.getValue().cancel(true);
                it.remove();
            }
            this.pending.put(key, null);
            return true;
        }

        synchronized void attach(Key key, Future<?> future)
        {
            if (this.pending.containsKey(key))
            {
                this.pending.put(key, future);
            }
            else
            {
                future.cancel(true);
            }
        }

        synchronized void complete(Key key, TFCCropSuitability.CropSuitabilityResult value)
        {
            if (this.pending.containsKey(key))
            {
                this.pending.remove(key);
                this.results.put(key, value);
            }
        }

        synchronized void discard(Key key)
        {
            Future<?> future = this.pending.remove(key);
            if (future != null && !future.isDone()) future.cancel(true);
        }

        synchronized void clear()
        {
            for (Future<?> future : this.pending.values())
            {
                if (future != null) future.cancel(true);
            }
            this.pending.clear();
            this.results.clear();
        }
    }

    /**
     * Bounded LRU of ChunkData plus the canonical four-height grid, scoped by world generation.
     */
    private static final class ChunkDataCache
    {
        record Key(long chunkPos, int worldRevision) {}

        record Entry(ChunkData chunkData, QuartSurfaceHeights surfaceHeights) {}

        private final java.util.LinkedHashMap<Key, Entry> map;

        ChunkDataCache(int maxEntries)
        {
            this.map = new java.util.LinkedHashMap<>(16, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Key, Entry> eldest)
                {
                    return size() > maxEntries;
                }
            };
        }

        synchronized Entry get(Key key)
        {
            return this.map.get(key);
        }

        synchronized void put(Key key, Entry value)
        {
            this.map.put(key, value);
        }

        synchronized void clear()
        {
            this.map.clear();
        }
    }

    /**
     * Resolves the dominant-tree result at a block position for UI (hover tooltip). Samples the
     * containing chunk's data, forest type, biome (for salt_marsh) and surface height, then runs
     * the shared resolver. Returns {@link TFCTreeResolver.Result#NONE} if TFC data is unavailable.
     */
    public TFCTreeResolver.Result resolveTreeAt(int blockX, int blockZ)
    {
        TFCSampleUtils tfcSu = this.tfcSampleUtils;
        TFCTreeResolver resolver = this.tfcTreeResolver;
        if (tfcSu == null || resolver == null)
        {
            return TFCTreeResolver.Result.NONE;
        }
        try
        {
            ChunkData chunkData = tfcSu.sampleChunkData(new ChunkPos(blockX >> 4, blockZ >> 4));
            ForestType forestType = chunkData.getForestType();
            BiomeExtension biome = tfcSu.sampleBiomeExtension(blockX, blockZ);
            int surfaceY;
            try
            {
                surfaceY = this.sampleUtils.doHeightSlow(new BlockPos(blockX, 0, blockZ));
            }
            catch (Exception e)
            {
                surfaceY = 63;
            }
            return resolver.resolve(chunkData, forestType, biome, blockX, blockZ, surfaceY);
        }
        catch (Exception e)
        {
            return TFCTreeResolver.Result.NONE;
        }
    }

    public boolean isTFCEnabled()
    {
        return this.tfcSampleUtils != null;
    }

    /**
     * Current preview-data revision; changes when results are applied or sections are invalidated.
     */
    public long dataRevision()
    {
        return this.dataRevision.get();
    }

    /**
     * Bump the revision so PreviewDisplay rebuilds its texture. Called after a batch applies results.
     */
    public void bumpDataRevision()
    {
        this.dataRevision.incrementAndGet();
    }

    public ChunkGenerator chunkGenerator()
    {
        return this.chunkGenerator;
    }

}
