package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.sampler.ChunkSampler;
import com.rustysnail.world.preview.tfc.backend.search.FeatureDetectors;
import com.rustysnail.world.preview.tfc.backend.search.FeatureQuery;
import com.rustysnail.world.preview.tfc.backend.search.FeatureTest;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.backend.search.VeinLocator;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.WorkResult;
import com.rustysnail.world.preview.tfc.backend.worker.WorkUnit;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.river.MidpointFractal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TFCRegionWorkUnit extends WorkUnit
{
    private final KaolinBiomeRules kaolinRules;
    private final boolean computeKaolin;
    public static final short LAND_WATER_OCEAN = 0;
    public static final short LAND_WATER_LAND = 1;
    public static final short LAND_WATER_SHORE = 2;
    public static final short LAND_WATER_LAKE = 3;
    public static final short LAND_WATER_RIVER = 4;

    private static final AtomicInteger totalUnitsQueued = new AtomicInteger(0);
    private static final AtomicInteger unitsCompleted = new AtomicInteger(0);
    private static final AtomicLong totalTimeMs = new AtomicLong(0);
    private static final AtomicInteger gridCellsSampled = new AtomicInteger(0);
    private static long generationStartTime = 0;

    public static void resetStats()
    {
        totalUnitsQueued.set(0);
        unitsCompleted.set(0);
        totalTimeMs.set(0);
        gridCellsSampled.set(0);
        generationStartTime = System.currentTimeMillis();
        WorldPreview.LOGGER.info("[TFC] Starting new TFC generation batch");
    }

    public static void setTotalUnits(int total)
    {
        totalUnitsQueued.set(total);
        WorldPreview.LOGGER.info("[TFC] Queued {} TFC work units", total);
    }

    private static void reportProgress(int gridCells, long timeMs)
    {
        int completed = unitsCompleted.incrementAndGet();
        int total = totalUnitsQueued.get();
        totalTimeMs.addAndGet(timeMs);
        gridCellsSampled.addAndGet(gridCells);

        if (completed % 10 == 0 || completed == total)
        {
            long elapsed = System.currentTimeMillis() - generationStartTime;
            WorldPreview.LOGGER.info("[TFC] Progress: {}/{} units ({} grid cells) - elapsed: {}ms, avg: {}ms/unit",
                completed, total, gridCellsSampled.get(), elapsed,
                completed > 0 ? totalTimeMs.get() / completed : 0);
        }

        if (completed == total && total > 0)
        {
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
    private final long seed;

    private final Set<Long> detectedFeatureCenters = new HashSet<>();

    public TFCRegionWorkUnit(
        ChunkSampler sampler,
        SampleUtils sampleUtils,
        ChunkPos chunkPos,
        int numChunks,
        PreviewData previewData,
        RegionGenerator regionGenerator,
        TFCSampleUtils tfcSampleUtils,
        KaolinBiomeRules kaolinRules,
        boolean computeKaolin,
        long seed
    )
    {
        super(sampleUtils, chunkPos, previewData, 0);
        this.sampler = sampler;
        this.numChunks = numChunks;
        this.regionGenerator = regionGenerator;
        this.tfcSampleUtils = tfcSampleUtils;
        this.computeKaolin = computeKaolin;
        this.kaolinRules = computeKaolin ? kaolinRules : null;
        this.seed = seed;
    }

    private record AggregatedSample(
        float temperature,
        float rainfall,
        int landWater,
        short rockTop,
        short rockMid,
        short rockBot,
        short rockType
    )
    {
    }

    @Override
    protected List<WorkResult> doWork()
    {
        long startTime = System.currentTimeMillis();
        final int[] gridCellsProcessed = {0};

        try
        {
            Map<Long, Region.Point> gridCache = new HashMap<>();

            Map<Long, ResourceKey<Biome>> biomeKeyCache = new HashMap<>();
            Map<ResourceLocation, Boolean> biomeAllowedCache = new HashMap<>();

            List<WorkResult> allResults = new ArrayList<>();

            int baseChunkX = this.chunkPos.x;
            int baseChunkZ = this.chunkPos.z;
            boolean sampleStructures = WorldPreview.get().cfg().sampleStructures;

            ChunkPos minChunk = new ChunkPos(baseChunkX, baseChunkZ);
            ChunkPos maxChunk = new ChunkPos(baseChunkX + this.numChunks - 1, baseChunkZ + this.numChunks - 1);
            Map<Long, List<PreviewSection.PreviewFeature>> veinFeaturesByChunk = sampleStructures
                ? VeinLocator.findVeinsForRegion(
                this.sampleUtils,
                this.tfcSampleUtils,
                this.seed,
                minChunk,
                maxChunk,
                this.detectedFeatureCenters,
                this::isCanceled
            )
                : Map.of();

            for (int dx = 0; dx < this.numChunks && !this.isCanceled(); dx++)
            {
                for (int dz = 0; dz < this.numChunks && !this.isCanceled(); dz++)
                {
                    ChunkPos cp = new ChunkPos(baseChunkX + dx, baseChunkZ + dz);

                    PreviewSection tempSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_TEMPERATURE.flag);
                    PreviewSection rainSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_RAINFALL.flag);
                    PreviewSection landWaterSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_LAND_WATER.flag);
                    PreviewSection rockTopSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_ROCK_TOP.flag);
                    PreviewSection rockMidSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_ROCK_MID.flag);
                    PreviewSection rockBotSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_ROCK_BOT.flag);
                    PreviewSection rockTypeSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_ROCK_TYPE.flag);
                    PreviewSection kaolinSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_KAOLINITE.flag);
                    PreviewSection hotspotSection = this.storage.section4(cp, 0, RenderSettings.RenderMode.TFC_HOTSPOT.flag);

                    WorkResult tempResult = new WorkResult(this, 0, tempSection, new ArrayList<>(16), List.of());
                    WorkResult rainResult = new WorkResult(this, 0, rainSection, new ArrayList<>(16), List.of());
                    WorkResult landWaterResult = new WorkResult(this, 0, landWaterSection, new ArrayList<>(16), List.of());
                    WorkResult rockTopResult = new WorkResult(this, 0, rockTopSection, new ArrayList<>(16), List.of());
                    WorkResult rockMidResult = new WorkResult(this, 0, rockMidSection, new ArrayList<>(16), List.of());
                    WorkResult rockBotResult = new WorkResult(this, 0, rockBotSection, new ArrayList<>(16), List.of());
                    WorkResult rockTypeResult = new WorkResult(this, 0, rockTypeSection, new ArrayList<>(16), List.of());
                    WorkResult kaolinResult = new WorkResult(this, 0, kaolinSection, new ArrayList<>(16), List.of());
                    WorkResult hotspotResult = new WorkResult(this, 0, hotspotSection, new ArrayList<>(16), List.of());

                    PreviewSection structureSection = this.storage.section4(cp, 0, 1L);
                    if (sampleStructures)
                    {
                        List<PreviewSection.PreviewFeature> veinFeatures = veinFeaturesByChunk.get(cp.toLong());
                        if (veinFeatures != null)
                        {
                            for (PreviewSection.PreviewFeature feature : veinFeatures)
                            {
                                structureSection.addFeature(feature);
                            }
                        }
                    }

                    for (BlockPos pos : this.sampler.blocksForChunk(cp, 0))
                    {
                        if (this.isCanceled()) break;

                        Region.Point point;
                        try
                        {
                            point = getPointCached(pos.getX(), pos.getZ(), gridCache);
                        }
                        catch (Exception e)
                        {
                            continue;
                        }
                        if (point == null) continue;

                        if (sampleStructures)
                        {
                            detectFeatures(point, pos.getX(), pos.getZ(), structureSection);
                        }

                        short landWaterValue = sampleLandWater(pos, gridCache);
                        short rockTopId = -1;
                        short rockMidId = -1;
                        short rockBotId = -1;
                        short rockTypeCategory = (short) TFCSampleUtils.getRockTypeCategory(point.rock);
                        if (this.tfcSampleUtils != null)
                        {
                            try
                            {
                                rockTopId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 0));
                                rockMidId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 1));
                                rockBotId = TFCSampleUtils.getRockId(this.tfcSampleUtils.sampleRockAtLayer(point.rock, 2));
                            }
                            catch (Exception e)
                            {
                                WorldPreview.LOGGER.debug("TFC rock sampling failed for rock type {}: {}", point.rock, e.getMessage());
                            }
                        }
                        AggregatedSample sample = new AggregatedSample(
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

                        short kaolinValue;
                        if (landWaterValue == LAND_WATER_LAND)
                        {
                            if (this.computeKaolin && this.kaolinRules != null)
                            {
                                final boolean rainfallCheck = sample.rainfall() >= 300f;
                                final boolean temperatureCheck = sample.temperature() >= 18f;
                                long bkey = packBlockKey(pos.getX(), pos.getZ());

                                var biomeKey = biomeKeyCache.computeIfAbsent(bkey, ignored -> this.sampleUtils.getBiomeKey(pos));
                                ResourceLocation biomeId = biomeKey.location();

                                Boolean biomeAllowed = biomeAllowedCache.computeIfAbsent(biomeId, this.kaolinRules::isBiomeAllowed);

                                final boolean kaolinPossible = rainfallCheck && temperatureCheck && biomeAllowed;
                                kaolinValue = (short) (kaolinPossible ? 2 : 1);
                            }
                            else
                            {
                                kaolinValue = 1;
                            }
                        }
                        else
                        {
                            kaolinValue = 0;
                        }

                        short hotspotAge = point.hotSpotAge;

                        this.sampler.expandRaw(pos, tempValue, tempResult);
                        this.sampler.expandRaw(pos, rainValue, rainResult);
                        this.sampler.expandRaw(pos, landWaterValue, landWaterResult);
                        this.sampler.expandRaw(pos, rockTopId, rockTopResult);
                        this.sampler.expandRaw(pos, rockMidId, rockMidResult);
                        this.sampler.expandRaw(pos, rockBotId, rockBotResult);
                        this.sampler.expandRaw(pos, rockTypeCategory, rockTypeResult);
                        this.sampler.expandRaw(pos, kaolinValue, kaolinResult);
                        this.sampler.expandRaw(pos, hotspotAge, hotspotResult);

                    }

                    allResults.add(tempResult);
                    allResults.add(rainResult);
                    allResults.add(landWaterResult);
                    allResults.add(rockTopResult);
                    allResults.add(rockMidResult);
                    allResults.add(rockBotResult);
                    allResults.add(rockTypeResult);
                    allResults.add(kaolinResult);
                    allResults.add(hotspotResult);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            reportProgress(gridCellsProcessed[0], elapsed);

            return allResults;

        }
        catch (Throwable t)
        {
            WorldPreview.LOGGER.error("[TFC] doWork() FAILED for chunk ({}, {}): {}",
                this.chunkPos.x, this.chunkPos.z, t.getMessage(), t);
            throw t;
        }
    }

    private static long packBlockKey(int blockX, int blockZ)
    {
        return (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
    }

    private static final float RIVER_WIDTH = 0.35f;

    private short sampleLandWater(BlockPos pos, Map<Long, Region.Point> gridCache)
    {
        Region.Point p = getPointCached(pos.getX(), pos.getZ(), gridCache);
        if (p == null) return Short.MIN_VALUE;

        if (isInRiver(pos.getX(), pos.getZ()))
        {
            return LAND_WATER_RIVER;
        }

        if (p.lake()) return LAND_WATER_LAKE;
        if (p.shore()) return LAND_WATER_SHORE;
        if (!p.land()) return LAND_WATER_OCEAN;
        return LAND_WATER_LAND;
    }

    private boolean isInRiver(int blockX, int blockZ)
    {
        try
        {
            int gridX = Units.blockToGrid(blockX);
            int gridZ = Units.blockToGrid(blockZ);

            Region region = regionGenerator.getOrCreateRegion(gridX, gridZ);
            if (region == null) return false;

            float exactGridX = (float) blockX / Units.GRID_WIDTH_IN_BLOCK;
            float exactGridZ = (float) blockZ / Units.GRID_WIDTH_IN_BLOCK;

            for (RiverEdge edge : region.rivers())
            {
                MidpointFractal fractal = edge.fractal();
                if (fractal.maybeIntersect(exactGridX, exactGridZ, 0.1f) &&
                    fractal.intersect(exactGridX, exactGridZ, RIVER_WIDTH))
                {
                    return true;
                }
            }
        }
        catch (Exception e)
        {
            // Silently fail - river detection is a nice-to-have
        }
        return false;
    }

    private Region.Point getPointCached(int blockX, int blockZ, Map<Long, Region.Point> gridCache)
    {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        long key = (((long) gridX) << 32) ^ (gridZ & 0xffffffffL);

        Region.Point point = gridCache.get(key);
        if (point == null)
        {
            try
            {
                point = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
                gridCache.put(key, point);
            }
            catch (Exception e)
            {
                return null;
            }
        }
        return point;
    }

    private void detectFeatures(Region.Point point, int blockX, int blockZ, PreviewSection structureSection)
    {
        if (this.tfcSampleUtils == null) return;

        BiomeExtension biomeExt = TFCSampleUtils.getBiomeExtensionFromPoint(point);

        for (SearchableFeature feature : FeatureDetectors.getManualFeatures())
        {
            if (feature.requiresProbe()) continue;

            FeatureTest test = feature.test();
            if (test == null) continue;

            FeatureQuery query = new FeatureQuery(
                this.seed,
                blockX,
                blockZ,
                point,
                biomeExt,
                this.tfcSampleUtils.biomeLookup(),
                null
            );

            if (test.matches(query))
            {
                BlockPos center = test.findCenter(query);
                if (center == null)
                {
                    continue;
                }

                long key = ((long) center.getX() << 32) | (center.getZ() & 0xFFFFFFFFL);
                synchronized (this.detectedFeatureCenters)
                {
                    if (this.detectedFeatureCenters.add(key))
                    {
                        short id = FeatureDetectors.getFeatureId(feature);
                        if (id >= 0)
                        {
                            structureSection.addFeature(new PreviewSection.PreviewFeature(id, center));
                        }
                    }
                }
            }
        }
    }

    @Override
    public long flags()
    {
        return RenderSettings.RenderMode.TFC_TEMPERATURE.flag;
    }
}
