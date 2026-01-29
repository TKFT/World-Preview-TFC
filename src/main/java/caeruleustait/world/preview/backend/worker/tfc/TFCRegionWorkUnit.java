package caeruleustait.world.preview.backend.worker.tfc;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import caeruleustait.world.preview.backend.storage.PreviewSection;
import caeruleustait.world.preview.backend.worker.SampleUtils;
import caeruleustait.world.preview.backend.worker.WorkResult;
import caeruleustait.world.preview.backend.worker.WorkUnit;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WorkUnit for sampling TerraFirmaCraft region data (temperature, rainfall, land/water, rock).
 * This unit samples data from TFC's RegionGenerator and stores it for preview rendering.
 */
public class TFCRegionWorkUnit extends WorkUnit {



    /**
     * Accurate sampling for TFC preview modes.
     * TFC's {@link RegionGenerator} is queried on a coarser "grid" than blocks.
     * When the preview is zoomed out (multiple chunks per pixel), single-point sampling can
     * create visible "blockiness" along region boundaries.
     * For TFC modes we default to a small multi-sample aggregation (2x2 in block space) to
     * improve accuracy and visual quality.
     */
    private static final boolean ACCURATE_TFC_SAMPLING = true;
    private static final int ACCURATE_SAMPLES_PER_AXIS = 2; // 2x2 = 4 samples per preview sample
    private static final int ACCURATE_SAMPLE_STEP_BLOCKS = 8; // sample within a 16x16 area

    // Kaolin rules are only needed for the kaolin preview mode.
    // Keep nullable so other TFC modes don't pay I/O/cache costs.
    private final KaolinBiomeRules kaolinRules;
    private final boolean computeKaolin;
    // Using flags 5-12 for TFC data
    public static final long FLAG_TFC_TEMPERATURE = 5L;
    public static final long FLAG_TFC_RAINFALL = 6L;
    public static final long FLAG_TFC_LAND_WATER = 7L; // Simple land and water map
    public static final long FLAG_TFC_ROCK_TOP = 8L;   // Surface rock ID (0-19)
    public static final long FLAG_TFC_ROCK_MID = 9L;   // Middle rock ID (0-19)
    public static final long FLAG_TFC_ROCK_BOT = 10L;  // Bottom rock ID (0-19)
    public static final long FLAG_TFC_ROCK_TYPE = 11L; // Rock type category (0=Ocean, 1=Volcanic, 2=Land, 3=Uplift)
    public static final long FLAG_TFC_KAOLINITE = 12L; // Kaolin Clay spawn areas
    public static final long FLAG_TEST = 13L;       // Used to test

    // Land/water encoding: 0=Ocean, 1=Land, 2=Shore
    public static final short LAND_WATER_OCEAN = 0;
    public static final short LAND_WATER_LAND = 1;
    public static final short LAND_WATER_SHORE = 2;
    public static final short LAND_WATER_LAKE = 3;
    public static final short LAND_WATER_RIVER = 4;
    // Debug tracking for TFC generation progress
    private static final AtomicInteger totalUnitsQueued = new AtomicInteger(0);
    private static final AtomicInteger unitsCompleted = new AtomicInteger(0);
    private static final AtomicLong totalTimeMs = new AtomicLong(0);
    private static final AtomicInteger gridCellsSampled = new AtomicInteger(0);
    private static long generationStartTime = 0;

    public static void resetStats() {
        totalUnitsQueued.set(0);
        unitsCompleted.set(0);
        totalTimeMs.set(0);
        gridCellsSampled.set(0);
        generationStartTime = System.currentTimeMillis();
        WorldPreview.LOGGER.info("[TFC] Starting new TFC generation batch");
    }

    public static void setTotalUnits(int total) {
        totalUnitsQueued.set(total);
        WorldPreview.LOGGER.info("[TFC] Queued {} TFC work units", total);
    }

    private static void reportProgress(int gridCells, long timeMs) {
        int completed = unitsCompleted.incrementAndGet();
        int total = totalUnitsQueued.get();
        totalTimeMs.addAndGet(timeMs);
        gridCellsSampled.addAndGet(gridCells);

        // Log progress every 10 units or when complete
        if (completed % 10 == 0 || completed == total) {
            long elapsed = System.currentTimeMillis() - generationStartTime;
            WorldPreview.LOGGER.info("[TFC] Progress: {}/{} units ({} grid cells) - elapsed: {}ms, avg: {}ms/unit",
                    completed, total, gridCellsSampled.get(), elapsed,
                    completed > 0 ? totalTimeMs.get() / completed : 0);
        }

        if (completed == total && total > 0) {
            long elapsed = System.currentTimeMillis() - generationStartTime;
            WorldPreview.LOGGER.info("[TFC] COMPLETED: {} units, {} grid cells in {}ms (avg {}ms/unit, {}ms/cell)",
                    total, gridCellsSampled.get(), elapsed,
                    totalTimeMs.get() / total,
                    gridCellsSampled.get() > 0 ? totalTimeMs.get() / gridCellsSampled.get() : 0);
        }
    }

    private final ChunkSampler sampler;
    private final RegionGenerator regionGenerator;
    private final TFCSampleUtils tfcSampleUtils;
    private final int numChunks;

    public TFCRegionWorkUnit(
            ChunkSampler sampler,
            SampleUtils sampleUtils,
            ChunkPos chunkPos,
            int numChunks,
            PreviewData previewData,
            RegionGenerator regionGenerator,
            TFCSampleUtils tfcSampleUtils,
            KaolinBiomeRules kaolinRules,
            boolean computeKaolin
    ) {
        super(sampleUtils, chunkPos, previewData, 0);
        this.sampler = sampler;
        this.numChunks = numChunks;
        this.regionGenerator = regionGenerator;
        this.tfcSampleUtils = tfcSampleUtils;
        this.computeKaolin = computeKaolin;
        this.kaolinRules = computeKaolin ? kaolinRules : null;
    }

    private record AggregatedSample(
            float temperature,
            float rainfall,
            int landWater,
            short rockTop,
            short rockMid,
            short rockBot,
            short rockType
    ) {
    }

    private Region.Point getRegionPointCached(
            int blockX,
            int blockZ,
            Map<Long, Region.Point> gridCache,
            int[] gridCellsProcessedRef
    ) {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        long key = (((long) gridX) << 32) ^ (gridZ & 0xffffffffL);

        Region.Point point = gridCache.get(key);
        if (point == null) {
            point = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
            gridCache.put(key, point);
            gridCellsProcessedRef[0]++;
        }
        return point;
    }

    /**
     * Higher accuracy sampling: take a small NxN set of samples around the base coordinate and
     * aggregate them.
     */
    private AggregatedSample sampleAggregated(
            int baseX,
            int baseZ,
            Map<Long, Region.Point> gridCache,
            int[] gridCellsProcessedRef
    ) {
        // 2x2 samples at 8 block spacing works well as a default and is still fast with caching.
        final int[] offsets = {0, 8};

        float tempSum = 0f;
        float rainSum = 0f;
        int samples = 0;

        int ocean = 0, land = 0, shore = 0;
        int[] rockTopCounts = new int[ROCK_ID_MAX + 2]; // +1 for "unknown" (-1) bucket
        int[] rockMidCounts = new int[ROCK_ID_MAX + 2];
        int[] rockBotCounts = new int[ROCK_ID_MAX + 2];
        int[] rockTypeCounts = new int[4];

        for (int ox : offsets) {
            for (int oz : offsets) {
                Region.Point p = getRegionPointCached(baseX + ox, baseZ + oz, gridCache, gridCellsProcessedRef);
                tempSum += p.temperature;
                rainSum += p.rainfall;
                samples++;

                if (!p.land()) ocean++;
                else if (p.shore()) shore++;
                else land++;

                if (this.tfcSampleUtils != null) {
                    try {
                        short t = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(p.rock, 0));
                        short m = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(p.rock, 1));
                        short b = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(p.rock, 2));
                        rockTopCounts[rockIdToBucket(t)]++;
                        rockMidCounts[rockIdToBucket(m)]++;
                        rockBotCounts[rockIdToBucket(b)]++;
                    } catch (Exception ignored) {
                        // Keep unknown bucket in that case.
                    }
                }

                int cat = TFCSampleUtils.getRockTypeCategory(p.rock);
                if (cat >= 0 && cat < rockTypeCounts.length) rockTypeCounts[cat]++;
            }
        }

        int landWater;
        // Majority; in ties prefer shore (it looks better as a thin outline), then land, then ocean.
        if (shore >= land && shore >= ocean) landWater = LAND_WATER_SHORE;
        else if (land >= ocean) landWater = LAND_WATER_LAND;
        else landWater = LAND_WATER_OCEAN;

        short rockTop = bucketToRockId(argMax(rockTopCounts));
        short rockMid = bucketToRockId(argMax(rockMidCounts));
        short rockBot = bucketToRockId(argMax(rockBotCounts));
        short rockType = (short) argMax(rockTypeCounts);

        return new AggregatedSample(tempSum / samples, rainSum / samples, landWater, rockTop, rockMid, rockBot, rockType);
    }

    // Rock IDs are 0-19, with -1 as unknown.
    private static final int ROCK_ID_MAX = 19;

    private static int rockIdToBucket(short rockId) {
        return rockId < 0 ? (ROCK_ID_MAX + 1) : Math.min(ROCK_ID_MAX, rockId);
    }

    private static short bucketToRockId(int bucket) {
        return bucket == (ROCK_ID_MAX + 1) ? (short) -1 : (short) bucket;
    }

    private static int argMax(int[] counts) {
        int bestIdx = 0;
        int best = counts[0];
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > best) {
                best = counts[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    @Override
    protected List<WorkResult> doWork() {
        long startTime = System.currentTimeMillis();
        final int[] gridCellsProcessed = {0};

        try {
            Map<Long, Region.Point> gridCache = new HashMap<>();

            Map<Long, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>> biomeKeyCache = new HashMap<>();
            Map<ResourceLocation, Boolean> biomeAllowedCache = new HashMap<>();

            List<WorkResult> allResults = new ArrayList<>();

            int baseChunkX = this.chunkPos.x;
            int baseChunkZ = this.chunkPos.z;

            for (int dx = 0; dx < this.numChunks && !this.isCanceled(); dx++) {
                for (int dz = 0; dz < this.numChunks && !this.isCanceled(); dz++) {
                    ChunkPos cp = new ChunkPos(baseChunkX + dx, baseChunkZ + dz);

                    PreviewSection tempSection = this.storage.section4(cp, 0, FLAG_TFC_TEMPERATURE);
                    PreviewSection rainSection = this.storage.section4(cp, 0, FLAG_TFC_RAINFALL);
                    PreviewSection landWaterSection = this.storage.section4(cp, 0, FLAG_TFC_LAND_WATER);
                    PreviewSection rockTopSection = this.storage.section4(cp, 0, FLAG_TFC_ROCK_TOP);
                    PreviewSection rockMidSection = this.storage.section4(cp, 0, FLAG_TFC_ROCK_MID);
                    PreviewSection rockBotSection = this.storage.section4(cp, 0, FLAG_TFC_ROCK_BOT);
                    PreviewSection rockTypeSection = this.storage.section4(cp, 0, FLAG_TFC_ROCK_TYPE);
                    PreviewSection kaolinSection = this.storage.section4(cp, 0, FLAG_TFC_KAOLINITE);

                    WorkResult tempResult = new WorkResult(this, 0, tempSection, new ArrayList<>(16), List.of());
                    WorkResult rainResult = new WorkResult(this, 0, rainSection, new ArrayList<>(16), List.of());
                    WorkResult landWaterResult = new WorkResult(this, 0, landWaterSection, new ArrayList<>(16), List.of());
                    WorkResult rockTopResult = new WorkResult(this, 0, rockTopSection, new ArrayList<>(16), List.of());
                    WorkResult rockMidResult = new WorkResult(this, 0, rockMidSection, new ArrayList<>(16), List.of());
                    WorkResult rockBotResult = new WorkResult(this, 0, rockBotSection, new ArrayList<>(16), List.of());
                    WorkResult rockTypeResult = new WorkResult(this, 0, rockTypeSection, new ArrayList<>(16), List.of());
                    WorkResult kaolinResult = new WorkResult(this, 0, kaolinSection, new ArrayList<>(16), List.of());

                    /*for (BlockPos pos : this.sampler.blocksForChunk(cp, 0)) {
                        if (this.isCanceled()) break;

                        int gridX = Units.blockToGrid(pos.getX());
                        int gridZ = Units.blockToGrid(pos.getZ());
                        long key = (((long) gridX) << 32) ^ (gridZ & 0xffffffffL);

                        Region.Point point = gridCache.get(key);
                        if (point == null) {
                            try {
                                point = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
                                gridCache.put(key, point);
                                gridCellsProcessed++;
                            } catch (Exception e) {
                                WorldPreview.LOGGER.debug("TFC region generation failed at grid ({}, {}): {}", gridX, gridZ, e.getMessage());
                                continue;
                            }
                        }

                        // Normalize once per sample (cheap) using cached point values
                        short tempValue = TFCSampleUtils.normalizeTemperature(point.temperature);
                        short rainValue = TFCSampleUtils.normalizeRainfall(point.rainfall);

                        short landWaterValue;
                        if (point.shore()) {
                            landWaterValue = LAND_WATER_SHORE;
                        } else if (point.lake()){
                            landWaterValue = LAND_WATER_LAKE;
                        } else if (point.river()){
                            landWaterValue = LAND_WATER_RIVER;
                        } else if (point.island()){
                            landWaterValue = LAND_WATER_ISLAND;
                        } else if (point.land()) {
                            landWaterValue = LAND_WATER_LAND;
                        } else {
                            landWaterValue = LAND_WATER_OCEAN;
                        }

                        // Sample actual rock types for each layer (0=top, 1=mid, 2=bot)
                        short rockTopId = -1;
                        short rockMidId = -1;
                        short rockBotId = -1;
                        short rockTypeCategory = (short) TFCSampleUtils.getRockTypeCategory(point.rock);
                        if (this.tfcSampleUtils != null) {
                            try {
                                rockTopId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 0));
                                rockMidId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 1));
                                rockBotId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 2));
                            } catch (Exception e) {
                                WorldPreview.LOGGER.debug("TFC rock sampling failed for rock type {}: {}", point.rock, e.getMessage());
                            }
                        }

                        // Encode kaolin overlay as:
                        //   0 = water (ocean/shore)
                        //   1 = land (no kaolin)
                        //   2 = land (kaolin possible)
                        boolean kaolinPossible;
                        short kaolinValue;
                        if (point.land()) {
                            boolean rainfallCheck = point.rainfall >= 300f;
                            boolean temperatureCheck = point.temperature >= 18f;
                            // Fast biome lookup: we only need the biome id for allow-list checks.
                            boolean biomeAllowed = this.kaolinRules.isBiomeAllowed(this.sampleUtils.getBiomeKey(pos).location());
                            kaolinPossible = rainfallCheck && temperatureCheck && biomeAllowed;
                            if (kaolinPossible){
                                kaolinValue = 2;
                            } else{
                                kaolinValue = 1;
                            }
                        } else {
                            kaolinValue = 0;
                        }*/

                    for (BlockPos pos : this.sampler.blocksForChunk(cp, 0)) {
                        sampleLandWater(pos, gridCache);
                        short landWaterValue;

                        if (this.isCanceled()) break;

                        final AggregatedSample sample;
                        Region.Point point;
                        try {
                            point = getPointCached(pos.getX(), pos.getZ(), gridCache);
                        } catch (Exception e) {
                            continue;
                        }
                        assert point != null;
                        if (point.shore()) {
                            landWaterValue = LAND_WATER_SHORE;
                        } else if (point.lake()){
                            landWaterValue = LAND_WATER_LAKE;
                        } else if (point.river()){
                            landWaterValue = LAND_WATER_RIVER;
                        } else if (point.land()) {
                            landWaterValue = LAND_WATER_LAND;
                        } else {
                            landWaterValue = LAND_WATER_OCEAN;
                        }
                        short rockTopId = -1;
                        short rockMidId = -1;
                        short rockBotId = -1;
                        short rockTypeCategory = (short) TFCSampleUtils.getRockTypeCategory(point.rock);
                        if (this.tfcSampleUtils != null) {
                            try {
                                rockTopId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 0));
                                rockMidId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 1));
                                rockBotId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 2));
                            } catch (Exception e) {
                                WorldPreview.LOGGER.debug("TFC rock sampling failed for rock type {}: {}", point.rock, e.getMessage());
                            }
                        }
                        sample = new AggregatedSample(
                                point.temperature,
                                point.rainfall,
                                landWaterValue,
                                rockTopId,
                                rockMidId,
                                rockBotId,
                                rockTypeCategory
                        );

                        short tempValue = TFCSampleUtils.normalizeTemperature(sample.temperature());
                        short rainValue = TFCSampleUtils.normalizeRainfall(sample.rainfall());


                        // Encode kaolin overlay as:
                        //   0 = water (ocean/shore)
                        //   1 = land (no kaolin)
                        //   2 = land (kaolin possible)
                        short kaolinValue;
                        if (landWaterValue == LAND_WATER_LAND) {
                            if (this.computeKaolin && this.kaolinRules != null) {
                                final boolean rainfallCheck = sample.rainfall >= 300f;
                                final boolean temperatureCheck = sample.temperature >= 18f;
                                long bkey = packBlockKey(pos.getX(), pos.getZ());

                                var biomeKey = biomeKeyCache.get(bkey);
                                if (biomeKey == null) {
                                    biomeKey = this.sampleUtils.getBiomeKey(pos);
                                    biomeKeyCache.put(bkey, biomeKey);
                                }
                                ResourceLocation biomeId = biomeKey.location();

                                Boolean biomeAllowed = biomeAllowedCache.get(biomeId);
                                if (biomeAllowed == null) {
                                    biomeAllowed = this.kaolinRules.isBiomeAllowed(biomeId);
                                    biomeAllowedCache.put(biomeId, biomeAllowed);
                                }

                                final boolean kaolinPossible = rainfallCheck && temperatureCheck && biomeAllowed;
                                kaolinValue = (short) (kaolinPossible ? 2 : 1);
                            } else {
                                kaolinValue = 1;
                            }
                        } else {
                            kaolinValue = 0;
                        }

                        // expandRaw fills the correct quart-cells for the active sampler.
                        this.sampler.expandRaw(pos, tempValue, tempResult);
                        this.sampler.expandRaw(pos, rainValue, rainResult);
                        this.sampler.expandRaw(pos, landWaterValue, landWaterResult);
                        this.sampler.expandRaw(pos, rockTopId, rockTopResult);
                        this.sampler.expandRaw(pos, rockMidId, rockMidResult);
                        this.sampler.expandRaw(pos, rockBotId, rockBotResult);
                        this.sampler.expandRaw(pos, rockTypeCategory, rockTypeResult);
                        this.sampler.expandRaw(pos, kaolinValue, kaolinResult);

                    }

                    allResults.add(tempResult);
                    allResults.add(rainResult);
                    allResults.add(landWaterResult);
                    allResults.add(rockTopResult);
                    allResults.add(rockMidResult);
                    allResults.add(rockBotResult);
                    allResults.add(rockTypeResult);
                    allResults.add(kaolinResult);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            reportProgress(gridCellsProcessed[0], elapsed);

            return allResults;

        } catch (Throwable t) {
            WorldPreview.LOGGER.error("[TFC] doWork() FAILED for chunk ({}, {}): {}",
                    this.chunkPos.x, this.chunkPos.z, t.getMessage(), t);
            throw t;
        }
    }

    private static long packBlockKey(int blockX, int blockZ) {
        return (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
    }

    private short sampleLandWater(BlockPos pos, Map<Long, Region.Point> gridCache) {
        Region.Point p = getPointCached(pos.getX(), pos.getZ(), gridCache);
        if (p == null) return Short.MIN_VALUE;
        if (p.river()) return LAND_WATER_RIVER;
        if (p.lake())  return LAND_WATER_LAKE;
        if (p.shore()) return LAND_WATER_SHORE;
        if (!p.land()) return LAND_WATER_OCEAN;
        return LAND_WATER_LAND;
    }

    private Region.Point getPointCached(int blockX, int blockZ, Map<Long, Region.Point> gridCache) {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        long key = (((long) gridX) << 32) ^ (gridZ & 0xffffffffL);

        Region.Point point = gridCache.get(key);
        if (point == null) {
            try {
                point = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
                gridCache.put(key, point);
            } catch (Exception e) {
                return null;
            }
        }
        return point;
    }


    @Override
    public long flags() {
        return FLAG_TFC_TEMPERATURE;
    }
}
