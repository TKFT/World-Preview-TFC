package caeruleustait.world.preview.backend;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import caeruleustait.world.preview.backend.storage.PreviewStorage;
import caeruleustait.world.preview.backend.storage.PreviewStorageCacheManager;
import caeruleustait.world.preview.backend.worker.FullChunkWorkUnit;
import caeruleustait.world.preview.backend.worker.HeightmapWorkUnit;
import caeruleustait.world.preview.backend.worker.LayerChunkWorkUnit;
import caeruleustait.world.preview.backend.worker.SampleUtils;
import caeruleustait.world.preview.backend.worker.SlowHeightmapWorkUnit;
import caeruleustait.world.preview.backend.worker.StructStartWorkUnit;
import caeruleustait.world.preview.backend.worker.tfc.KaolinBiomeRules;
import caeruleustait.world.preview.backend.worker.tfc.TFCRegionWorkUnit;
import caeruleustait.world.preview.backend.worker.tfc.TFCSampleUtils;
import caeruleustait.world.preview.backend.worker.WorkBatch;
import caeruleustait.world.preview.backend.worker.WorkUnit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
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
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.settings.Settings;
import org.jetbrains.annotations.Nullable;

public class WorkManager {
    private final Object completedSynchro = new Object();
    private WorldOptions worldOptions;
    private LevelStem levelStem;
    private DimensionType dimensionType;
    private ChunkGenerator chunkGenerator;
    private ChunkSampler chunkSampler;
    private SampleUtils sampleUtils;
    private PreviewData previewData;
    private PreviewStorage previewStorage;
    private PreviewStorageCacheManager previewStorageCacheManager;
    private TFCSampleUtils tfcSampleUtils;
    private final KaolinBiomeRules kaolinRules = new KaolinBiomeRules();

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
    private ChunkPos lastQueuedTopLeft;
    private ChunkPos lastQueuedBotRight;
    // Track the *effective* Y used by the current render mode.
    // Some modes (e.g. most TFC modes) ignore Y entirely and always sample at a fixed Y (0).
    // If we only track the camera Y, switching modes can fail to requeue until the map is moved.
    private int lastEffectiveY;
    private boolean lastQueuedWasTfc = false;
    private long lastQueuedModeFlag = Long.MIN_VALUE;
    private boolean queueIsRunning = false;
    private boolean shouldEarlyAbortQueuing = false;

    public WorkManager(RenderSettings renderSettings, WorldPreviewConfig config) {
        this.config = config;
        this.renderSettings = renderSettings;
    }

    /**
     * Sets an override for TFC worldgen Settings that should be applied to the preview generator.
     * If the current generator supports TFC, the override is applied immediately.
     */
    public synchronized void setTFCSettingsOverride(@Nullable Settings settings) {
        this.tfcSettingsOverride = settings;

        if (settings != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext) {
            try {
                ext.applySettings(old -> settings);
            } catch (Throwable t) {
                WorldPreview.LOGGER.warn("Failed to apply TFC settings override to preview chunk generator", t);
            }
        }
    }

    @Nullable
    public synchronized Settings getTFCSettingsOverride() {
        return this.tfcSettingsOverride;
    }

    public synchronized void onTFCSettingsChanged() {
        if (this.chunkGenerator == null || this.worldOptions == null || this.previewStorage == null) {
            return;
        }

        // If we have an override, ensure it is applied to the current generator before rebuilding sampling utils.
        if (this.tfcSettingsOverride != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext) {
            try {
                ext.applySettings(old -> this.tfcSettingsOverride);
                this.kaolinRules.rebuild(this.sampleUtils.resourceManager());
            } catch (Throwable t) {
                WorldPreview.LOGGER.warn("Failed to re-apply TFC settings override to preview chunk generator", t);
            }
        }

        this.tfcSampleUtils = TFCSampleUtils.create(this.chunkGenerator, this.worldOptions.seed());

        synchronized (this.previewStorage) {
            this.previewStorage.invalidateFlags(RenderSettings.RenderMode.TFC_TEMPERATURE.flag, RenderSettings.RenderMode.TFC_RAINFALL.flag);
        }

        // Clear queued range bookkeeping so queueRange will enqueue again.
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastEffectiveY = Integer.MIN_VALUE;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.currentBatches.clear();

        // Restart worker executors to interrupt any in-flight tasks.
        this.restartExecutors();
    }

    /**
     * Called when the resolution (pixels per chunk) setting changes.
     * Rebuilds the chunk sampler and invalidates existing data.
     */
    public synchronized void onResolutionChanged() {
        if (this.chunkGenerator == null || this.worldOptions == null || this.previewStorage == null) {
            return;
        }

        // Rebuild the chunk sampler with the new resolution settings
        this.chunkSampler = this.renderSettings.samplerType.create(this.renderSettings.quartStride());

        // Invalidate all stored data since the resolution changed
        synchronized (this.previewStorage) {
            this.previewStorage.invalidateAll();
        }

        // Clear queued range bookkeeping so queueRange will enqueue again
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastEffectiveY = Integer.MIN_VALUE;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.currentBatches.clear();

        // Restart worker executors to interrupt any in-flight tasks
        this.restartExecutors();

        WorldPreview.LOGGER.info("Resolution changed to {} pixels per chunk", this.renderSettings.pixelsPerChunk());
    }

    /**
     * Called when the TFC sampling quality (FAST/AUTO/ACCURATE) changes.
     *
     * This affects only TFC-derived render modes, so we avoid invalidating non-TFC cached data.
     */
    /*public synchronized void onTFCMapQualityChanged() {
        if (this.chunkGenerator == null || this.worldOptions == null || this.previewStorage == null) {
            return;
        }

        // Invalidate only TFC flags (temperature/rainfall/land-water/rocks/kaolin).
        synchronized (this.previewStorage) {
            this.previewStorage.invalidateFlags(
                    RenderSettings.RenderMode.TFC_TEMPERATURE.flag,
                    RenderSettings.RenderMode.TFC_RAINFALL.flag,
                    RenderSettings.RenderMode.TFC_LAND_WATER.flag,
                    RenderSettings.RenderMode.TFC_ROCK_TOP.flag,
                    RenderSettings.RenderMode.TFC_ROCK_MID.flag,
                    RenderSettings.RenderMode.TFC_ROCK_BOT.flag,
                    RenderSettings.RenderMode.TFC_ROCK_TYPE.flag,
                    RenderSettings.RenderMode.TFC_KAOLINITE.flag
            );
        }

        // Clear queued range bookkeeping so queueRange will enqueue again
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastEffectiveY = Integer.MIN_VALUE;
        this.currentBatches.clear();

        // Restart worker executors to interrupt any in-flight tasks
        this.restartExecutors();

        WorldPreview.LOGGER.info("TFC map sampling quality changed to {}", this.renderSettings.tfcMapQuality);
    }*/

    /**
     * Called when the active render mode changes.
     *
     * <p>TFC modes skip standard chunk sampling. If the user switches between TFC modes without
     * moving the map, the view rectangle stays the same and {@link #queueRange} would otherwise
     * not enqueue new work. Clearing the queued-range bookkeeping forces a refresh on the next
     * render tick.
     */
    public synchronized void onRenderModeChanged() {
        if (this.previewStorage == null) return;

        // Invalidate just the active mode flag (and kaolin composite flag) so pixels get re-rendered.
        long flag = this.renderSettings.mode != null ? this.renderSettings.mode.flag : -1L;
        if (flag >= 0) {
            synchronized (this.previewStorage) {
                if (this.renderSettings.mode != null && this.renderSettings.mode.isTFC()) {
                    this.previewStorage.invalidateFlags(RenderSettings.RenderMode.TFC_TEMPERATURE.flag, flag);
                } else {
                    this.previewStorage.invalidateFlags(flag);
                }
            }
        }

        // Force queueRange() to enqueue again even if the bounds didn't change.
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastEffectiveY = Integer.MIN_VALUE;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.currentBatches.clear();
    }


    private void restartExecutors() {
        this.shutdownExecutors();
        this.executorService = Executors.newFixedThreadPool(this.config.numThreads());
        this.queueChunksService = Executors.newSingleThreadExecutor();
        this.queueIsRunning = false;
        this.shouldEarlyAbortQueuing = false;
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
    ) {
        this.cancel();
        this.worldOptions = _worldOptions;
        this.levelStem = _levelStem;
        this.dimensionType = this.levelStem.type().value();
        this.chunkGenerator = this.levelStem.generator();

        // If a TFC settings override has been applied, ensure any newly-created generator gets updated
        // before we build sampling utils.
        if (this.tfcSettingsOverride != null && this.chunkGenerator instanceof ChunkGeneratorExtension ext) {
            try {
                ext.applySettings(old -> this.tfcSettingsOverride);
            } catch (Throwable t) {
                WorldPreview.LOGGER.warn("Failed to apply TFC settings override to rebuilt preview chunk generator", t);
            }
        }

        BiomeSource biomeSource = this.chunkGenerator.getBiomeSource();
        this.previewStorageCacheManager = _previewStorageCacheManager;
        this.chunkSampler = this.renderSettings.samplerType.create(this.renderSettings.quartStride());
        this.previewData = _previewData;
        LevelHeightAccessor levelHeightAccessor = LevelHeightAccessor.create(this.dimensionType.minY(), this.dimensionType.height());

        try {
            if (server == null) {
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
            } else {
                this.sampleUtils = new SampleUtils(server, biomeSource, this.chunkGenerator, this.worldOptions, this.levelStem, levelHeightAccessor);
            }

            // Initialize TFC sample utils if this is a TFC-compatible generator
            this.tfcSampleUtils = TFCSampleUtils.create(this.chunkGenerator, this.worldOptions.seed());
            if (this.tfcSampleUtils != null) {
                this.kaolinRules.rebuild(this.sampleUtils.resourceManager());
                WorldPreview.LOGGER.info("TFC-compatible chunk generator detected, TFC sampling enabled");
            }
        } catch (IOException var12) {
            throw new RuntimeException(var12);
        }
    }

    public void postChangeWorldGenState() {
        this.previewStorage = this.previewStorageCacheManager.loadPreviewStorage(this.worldOptions.seed(), this.yMin(), this.yMax());
        this.executorService = Executors.newFixedThreadPool(this.config.numThreads());
        this.queueChunksService = Executors.newSingleThreadExecutor();
    }

    private void shutdownExecutors() {
        if (this.executorService != null) {
            this.shouldEarlyAbortQueuing = true;
            synchronized (this.currentBatches) {
                this.currentBatches.forEach(WorkBatch::cancel);
                this.currentBatches.clear();
            }

            try {
                List<Future<?>> allFutures = new ArrayList<>();
                synchronized (this.futures) {
                    allFutures.addAll(this.queueFutures);
                    allFutures.addAll(this.futures);
                }

                for (Future<?> f : allFutures) {
                    f.get();
                }
            } catch (ExecutionException | InterruptedException var6) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(var6);
            }

            this.executorService.shutdownNow();
            this.queueChunksService.shutdownNow();
        }
    }

    public void cancel() {
        this.shutdownExecutors();
        Executor serverThreadPoolExecutor = WorldPreview.get().serverThreadPoolExecutor();
        if (this.sampleUtils != null) {
            try {
                if (serverThreadPoolExecutor != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            this.sampleUtils.close();
                        } catch (Exception var2) {
                            throw new RuntimeException(var2);
                        }
                    }, serverThreadPoolExecutor).get();
                } else {
                    this.sampleUtils.close();
                }
            } catch (Exception var3) {
                throw new RuntimeException(var3);
            }
        }

        if (this.previewStorageCacheManager != null) {
            this.previewStorageCacheManager.storePreviewStorage(this.worldOptions.seed(), this.previewStorage);
        }

        this.worldOptions = null;
        this.levelStem = null;
        this.dimensionType = null;
        this.chunkGenerator = null;
        this.sampleUtils = null;
        this.tfcSampleUtils = null;
        this.previewStorage = null;
        this.lastQueuedTopLeft = null;
        this.lastQueuedBotRight = null;
        this.lastEffectiveY = Integer.MIN_VALUE;
        this.lastQueuedWasTfc = false;
        this.lastQueuedModeFlag = Long.MIN_VALUE;
        this.queueIsRunning = false;
        this.futures.clear();
        this.executorService = null;
        this.queueChunksService = null;
        this.previewStorageCacheManager = null;
    }

    private boolean requeueOnYOnlyChange() {
        return !this.config.buildFullVertChunk;
    }

    public void queueRange(BlockPos topLeftBlock, BlockPos bottomRightBlock) {
        ChunkPos topLeft = new ChunkPos(topLeftBlock);
        ChunkPos bottomRight = new ChunkPos(bottomRightBlock);

        // Some render modes (notably most TFC modes) ignore Y; in those cases we always sample at Y=0.
        final RenderSettings.RenderMode mode = this.renderSettings.mode;
        final boolean useY = mode != null && mode.useY;
        final int effectiveY = useY ? topLeftBlock.getY() : 0;
        final boolean tfcMode = mode != null && mode.isTFC();
        final long modeFlag = mode != null ? mode.flag : Long.MIN_VALUE;

        if (this.executorService != null
                && this.sampleUtils != null
                && (
                !topLeft.equals(this.lastQueuedTopLeft)
                        || !bottomRight.equals(this.lastQueuedBotRight)
                        || effectiveY != this.lastEffectiveY && this.requeueOnYOnlyChange()
                        || tfcMode != this.lastQueuedWasTfc
                        || modeFlag != this.lastQueuedModeFlag
        )) {
            if (this.queueIsRunning) {
                this.shouldEarlyAbortQueuing = true;
            } else {
                this.lastQueuedTopLeft = topLeft;
                this.lastQueuedBotRight = bottomRight;
                this.lastEffectiveY = effectiveY;
                this.lastQueuedWasTfc = tfcMode;
                this.lastQueuedModeFlag = modeFlag;
                synchronized (this.futures) {
                    // Normalize the Y passed into queueRangeReal so the queued work matches the requeue decision.
                    BlockPos normalizedTopLeft = useY ? topLeftBlock : new BlockPos(topLeftBlock.getX(), 0, topLeftBlock.getZ());
                    BlockPos normalizedBottomRight = useY ? bottomRightBlock : new BlockPos(bottomRightBlock.getX(), 0, bottomRightBlock.getZ());
                    this.queueFutures.add(this.queueChunksService.submit(() -> this.queueRangeWrapper(normalizedTopLeft, normalizedBottomRight)));
                }
            }
        }
    }

    private void queueRangeWrapper(BlockPos topLeftBlock, BlockPos bottomRightBlock) {
        this.queueIsRunning = true;
        this.shouldEarlyAbortQueuing = false;

        try {
            this.queueRangeReal(topLeftBlock, bottomRightBlock);
        } catch (Throwable var7) {
            WorldPreview.LOGGER.error("Error queuing range", var7);
        } finally {
            this.queueIsRunning = false;
        }
    }

    public void queueRangeReal(BlockPos topLeftBlock, BlockPos bottomRightBlock) {
        Instant start = Instant.now();
        ChunkPos topLeft = new ChunkPos(topLeftBlock);
        ChunkPos bottomRight = new ChunkPos(bottomRightBlock);
        synchronized (this.currentBatches) {
            this.currentBatches.forEach(WorkBatch::cancel);
            this.currentBatches.clear();
        }

        synchronized (this.futures) {
            for (Future<?> f : this.futures) {
                try {
                    f.get();
                } catch (ExecutionException | InterruptedException var15) {
                    throw new RuntimeException(var15);
                }
            }

            this.futures.clear();
        }

        List<ChunkPos> chunks = ChunkPos.rangeClosed(topLeft, bottomRight).toList();
        int units = 0;

        // TFC temperature/rainfall/land-water previews don't need standard biome/noise generation.
        // Skipping those work units dramatically improves responsiveness.
        boolean tfcMode = this.renderSettings.mode != null && this.renderSettings.mode.isTFC();
        if (!tfcMode) {
            units += this.queueForLevel(chunks, topLeftBlock.getY(), 4096, this::workUnitFactory);
        }
        if (this.config.sampleStructures && !this.shouldEarlyAbortQueuing) {
            units += this.queueForLevel(chunks, 0, 256, (pos, yx) -> new StructStartWorkUnit(this.sampleUtils, pos, this.previewData));
        }

        if (!tfcMode && this.config.sampleHeightmap && !this.shouldEarlyAbortQueuing && this.sampleUtils.noiseGeneratorSettings() != null) {
            LongSet queuedChunks = new LongOpenHashSet(chunks.size());
            List<ChunkPos> heightMapChunks = new ArrayList<>(chunks.size());

            for (ChunkPos c : chunks) {
                ChunkPos shifted = new ChunkPos(c.x >> 4 << 4, c.z >> 4 << 4);
                if (queuedChunks.add(shifted.toLong())) {
                    heightMapChunks.add(shifted);
                }
            }

            units += this.queueForLevel(heightMapChunks, 0, 1, (pos, yx) -> new HeightmapWorkUnit(this.chunkSampler, this.sampleUtils, pos, 16, this.previewData));
        } else if (!tfcMode && this.config.sampleHeightmap && !this.shouldEarlyAbortQueuing) {
            units += this.queueForLevel(chunks, 0, 64, (pos, yx) -> new SlowHeightmapWorkUnit(this.chunkSampler, this.sampleUtils, pos, this.previewData));
        }

        // TFC sampling (temperature, rainfall, land/water)
        // These previews can be computed directly from TFC's region/climate generator and
        // do not require full biome/noise chunk sampling.
        if (tfcMode && this.tfcSampleUtils != null && !this.shouldEarlyAbortQueuing) {
            final boolean computeKaolin = this.renderSettings.mode == RenderSettings.RenderMode.TFC_KAOLINITE;
            TFCRegionWorkUnit.resetStats();

            LongSet queuedTFCChunks = new LongOpenHashSet(chunks.size());
            List<ChunkPos> tfcChunks = new ArrayList<>(chunks.size());

            // Larger regions = fewer work units. 32x32 chunks = 512x512 blocks per work unit.
            int numChunks = 32;

            for (ChunkPos c : chunks) {
                ChunkPos shifted = new ChunkPos(c.x >> 5 << 5, c.z >> 5 << 5); // align to 32x32 chunk regions
                if (queuedTFCChunks.add(shifted.toLong())) {
                    tfcChunks.add(shifted);
                }
            }

            TFCRegionWorkUnit.setTotalUnits(tfcChunks.size());

            units += this.queueForLevel(
                    tfcChunks,
                    0,
                    1,
                    (pos, yx) -> new TFCRegionWorkUnit(
                            this.chunkSampler,
                            this.sampleUtils,
                            pos,
                            numChunks,
                            this.previewData,
                            this.tfcSampleUtils.regionGenerator(),
                            this.tfcSampleUtils,
                            this.kaolinRules,
                            computeKaolin
                    )
            );
        }

        if (!tfcMode && this.config.backgroundSampleVertChunk && !this.config.buildFullVertChunk) {
            for (int y : this.genAdjacentYLevels(topLeftBlock.getY())) {
                if (this.shouldEarlyAbortQueuing) {
                    break;
                }

                units += this.queueForLevel(chunks, y, 4096, this::workUnitFactory);
            }
        }

        Instant end = Instant.now();
        WorldPreview.LOGGER
                .info(
                        "Queued {} chunks for generation using {} batches [{} ms] {}",
                        units, this.currentBatches.size(), Duration.between(start, end).abs().toMillis(), this.shouldEarlyAbortQueuing ? "{early abort}" : "");
    }

    private WorkUnit workUnitFactory(ChunkPos pos, int y) {
        return this.config.buildFullVertChunk
                ? new FullChunkWorkUnit(this.chunkSampler, pos, this.sampleUtils, this.previewData, this.yMin(), this.yMax(), 8)
                : new LayerChunkWorkUnit(this.chunkSampler, pos, this.sampleUtils, this.previewData, y);
    }

    private int queueForLevel(List<ChunkPos> chunks, int y, int maxBatchSize, BiFunction<ChunkPos, Integer, WorkUnit> workUnitFactoryFunc) {
        WorkUnit[] toQueue = new WorkUnit[chunks.size()];
        int size = 0;
        synchronized (this.completedSynchro) {
            for (ChunkPos chunkPos : chunks) {
                WorkUnit workUnit = workUnitFactoryFunc.apply(chunkPos, y);
                if (!workUnit.isCompleted()) {
                    toQueue[size++] = workUnit;
                }
            }
        }

        if (size == 0) {
            return 0;
        } else {
            for (int i = size - 1; i > 1; i--) {
                int randomIndexToSwap = this.random.nextInt(size);
                WorkUnit temp = toQueue[randomIndexToSwap];
                toQueue[randomIndexToSwap] = toQueue[i];
                toQueue[i] = temp;
            }

            int batchSize = maxBatchSize == 1 ? 1 : Math.max(8, Math.min(maxBatchSize, size / 4096));
            WorkBatch[] batches = new WorkBatch[batchSize == 1 ? size : size / batchSize + 1];
            if (batchSize > 1) {
                int batchIdx = 0;
                batches[batchIdx] = new WorkBatch(new ArrayList<>(batchSize), this.completedSynchro, this.previewData);

                for (int i = 0; i < size; i++) {
                    batches[batchIdx].workUnits.add(toQueue[i]);
                    if (batches[batchIdx].workUnits.size() >= batchSize) {
                        batches[++batchIdx] = new WorkBatch(new ArrayList<>(batchSize), this.completedSynchro, this.previewData);
                    }
                }
            } else {
                for (int ix = 0; ix < size; ix++) {
                    batches[ix] = new WorkBatch(List.of(toQueue[ix]), this.completedSynchro, this.previewData);
                }
            }

            synchronized (this.futures) {
                for (WorkBatch batch : batches) {
                    this.futures.add(this.executorService.submit(batch::process));
                }
            }

            synchronized (this.currentBatches) {
                this.currentBatches.addAll(Arrays.asList(batches));
                return size;
            }
        }
    }

    private List<Integer> genAdjacentYLevels(int y) {
        int yMin = this.yMin();
        int yMax = this.yMax();
        List<Integer> res = new ArrayList<>();
        int max = this.dimensionType.height() / 8 + 1;

        for (int i = 1; i <= max; i++) {
            int y1 = y + i * 8;
            int y2 = y - i * 8;
            if (y2 >= yMin) {
                res.add(y2);
            }

            if (y1 <= yMax) {
                res.add(y1);
            }

            if (y1 > yMax && y2 < yMin) {
                break;
            }
        }

        return res;
    }

    public int yMin() {
        return this.dimensionType == null ? 0 : this.dimensionType.minY();
    }

    public int yMax() {
        return this.yMin() + (this.dimensionType == null ? 256 : this.dimensionType.height());
    }

    public PreviewStorage previewStorage() {
        return this.previewStorage;
    }

    public boolean isSetup() {
        return this.executorService != null;
    }

    public WorldPreviewConfig config() {
        return this.config;
    }

    public ResourceManager sampleResourceManager() {
        return this.sampleUtils.resourceManager();
    }

    public SampleUtils sampleUtils() {
        return this.sampleUtils;
    }

    /**
     * @return TFC sample utils if a TFC-compatible generator is loaded, null otherwise.
     */
    @Nullable
    public TFCSampleUtils tfcSampleUtils() {
        return this.tfcSampleUtils;
    }

    /**
     * @return true if TFC-compatible world generation is active.
     */
    public boolean isTFCEnabled() {
        return this.tfcSampleUtils != null;
    }

    /**
     * @return The current chunk generator, or null if not initialized.
     */
    public ChunkGenerator chunkGenerator() {
        return this.chunkGenerator;
    }

}
