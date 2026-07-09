package com.rustysnail.world.preview.tfc.backend;

import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.sampler.ChunkSampler;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorageCacheManager;
import com.rustysnail.world.preview.tfc.backend.worker.HeightmapWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.LayerChunkWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.SlowHeightmapWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.StructStartWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.KaolinBiomeRules;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCRegionWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver;
import com.rustysnail.world.preview.tfc.backend.worker.WorkBatch;
import com.rustysnail.world.preview.tfc.backend.worker.WorkUnit;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.settings.Settings;

import org.jetbrains.annotations.Nullable;

public class WorkManager
{
    private final Object completedSynchro = new Object();
    private final Object previewStorageSynchro = new Object();
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
    private TFCTreeResolver tfcTreeResolver;
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

    private boolean lastQueuedWasTfc = false;
    private long lastQueuedModeFlag = Long.MIN_VALUE;
    private final AtomicBoolean queueIsRunning = new AtomicBoolean(false);
    private final AtomicBoolean shouldEarlyAbortQueuing = new AtomicBoolean(false);

    // Chunks per side of a combined TFC work unit. Power of two; do not reduce below 8 without
    // profiling. Smaller = faster cancellation and sooner partial results, more scheduling overhead.
    private static final int TFC_UNIT_CHUNKS = 16;

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
        this.executorService = Executors.newFixedThreadPool(this.config.numThreads());
        this.queueChunksService = Executors.newSingleThreadExecutor();
    }

    private void shutdownExecutors()
    {
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
        this.previewStorageCacheManager = null;
    }

    public void queueRange(BlockPos topLeftBlock, BlockPos bottomRightBlock)
    {
        ChunkPos topLeft = new ChunkPos(topLeftBlock);
        ChunkPos bottomRight = new ChunkPos(bottomRightBlock);

        final RenderSettings.RenderMode mode = this.renderSettings.mode;
        final boolean tfcMode = mode != null && mode.isTFC();
        final long modeFlag = this.effectiveModeKey(mode);

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

        // TFC data is queued before height so that a height-induced early abort cannot prevent
        // TFC climate/feature data from being computed (needed for biome map tooltip and icons).
        if (this.tfcSampleUtils != null && !this.shouldEarlyAbortQueuing.get())
        {
            final boolean computeKaolin = this.renderSettings.mode == RenderSettings.RenderMode.TFC_KAOLINITE;
            TFCRegionWorkUnit.resetStats();

            LongSet queuedTFCChunks = new LongOpenHashSet(chunks.size());
            List<ChunkPos> tfcChunks = new ArrayList<>(chunks.size());

            // Smaller units (16x16 chunks, was 32x32) so cancellation responds ~4x faster, partial
            // results arrive sooner, and a moved viewport is black for less time. Must be a power of
            // two: unit origins are aligned to numChunks so the units tile the range without gaps.
            final int numChunks = TFC_UNIT_CHUNKS;
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

            final long worldSeed = this.worldOptions.seed();
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
                    this.tfcTreeResolver,
                    this.kaolinRules,
                    computeKaolin,
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
     * Queue-identity key for a render mode. All non-kaolin TFC modes map to the single combined
     * TFC unit's completion flag, so switching among Forest Type / Tree Species / Temperature / ...
     * is a display-only change and does not re-queue or cancel in-flight TFC generation.
     * TFC_KAOLINITE keeps its own key because it gates an expensive per-point computation.
     */
    private long effectiveModeKey(RenderSettings.RenderMode mode)
    {
        if (mode == null)
        {
            return Long.MIN_VALUE;
        }
        if (mode == RenderSettings.RenderMode.TFC_KAOLINITE)
        {
            return RenderSettings.RenderMode.TFC_KAOLINITE.flag;
        }
        if (mode.isTFC())
        {
            return TFCRegionWorkUnit.TFC_GENERATION_COMPLETE_FLAG;
        }
        return mode.flag;
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

            int batchSize = maxBatchSize == 1 ? 1 : Math.clamp(maxBatchSize, 8, size / 4096);
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

    /** Current preview-data revision; changes when results are applied or sections are invalidated. */
    public long dataRevision()
    {
        return this.dataRevision.get();
    }

    /** Bump the revision so PreviewDisplay rebuilds its texture. Called after a batch applies results. */
    public void bumpDataRevision()
    {
        this.dataRevision.incrementAndGet();
    }

    public ChunkGenerator chunkGenerator()
    {
        return this.chunkGenerator;
    }

}
