package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.sampler.ChunkSampler;
import com.rustysnail.world.preview.tfc.backend.search.FeatureDetectors;
import com.rustysnail.world.preview.tfc.backend.search.FeatureQuery;
import com.rustysnail.world.preview.tfc.backend.search.FeatureTest;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.WorkResult;
import com.rustysnail.world.preview.tfc.backend.worker.WorkUnit;

import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ForestType;
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
import org.jetbrains.annotations.Nullable;

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

    // Temporary debug counters for tree-map water classification
    private static final AtomicInteger treeMapOceanPoints = new AtomicInteger(0);
    private static final AtomicInteger treeMapLakePoints = new AtomicInteger(0);
    private static final AtomicInteger treeMapRiverPoints = new AtomicInteger(0);
    private static final AtomicInteger treeMapLandPoints = new AtomicInteger(0);

    public static void resetStats()
    {
        totalUnitsQueued.set(0);
        unitsCompleted.set(0);
        totalTimeMs.set(0);
        gridCellsSampled.set(0);
        treeMapOceanPoints.set(0);
        treeMapLakePoints.set(0);
        treeMapRiverPoints.set(0);
        treeMapLandPoints.set(0);
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
            WorldPreview.LOGGER.info("[TFC] Tree-map water points: ocean={}, lake={}, river={}, land={}",
                treeMapOceanPoints.get(), treeMapLakePoints.get(), treeMapRiverPoints.get(), treeMapLandPoints.get());
        }
    }

    private final ChunkSampler sampler;
    private final RegionGenerator regionGenerator;
    private final TFCSampleUtils tfcSampleUtils;
    @Nullable
    private final TFCTreeResolver treeResolver;
    private final int numChunks;
    private final long seed;
    private final TFCWorkPlan plan;
    private final List<PreviewSection> completionSections;

    private final Set<Long> detectedFeatureCenters = new HashSet<>();

    public TFCRegionWorkUnit(
        ChunkSampler sampler,
        SampleUtils sampleUtils,
        ChunkPos chunkPos,
        int numChunks,
        PreviewData previewData,
        RegionGenerator regionGenerator,
        TFCSampleUtils tfcSampleUtils,
        @Nullable TFCTreeResolver treeResolver,
        KaolinBiomeRules kaolinRules,
        TFCWorkPlan plan,
        long seed
    )
    {
        super(sampleUtils, chunkPos, previewData, 0);
        this.sampler = sampler;
        this.numChunks = numChunks;
        this.regionGenerator = regionGenerator;
        this.tfcSampleUtils = tfcSampleUtils;
        this.treeResolver = treeResolver;
        this.plan = plan;
        this.kaolinRules = plan.kaolin() ? kaolinRules : null;
        this.seed = seed;

        // Completion is tracked per output group (see TFCWorkPlan#requiredCompletionFlags), so this
        // unit is skipped only when the active mode's data is genuinely present. The completion
        // bitmaps live on the render sections themselves (separate from their pixel data).
        this.completionSections = new ArrayList<>();
        for (long flag : plan.requiredCompletionFlags())
        {
            this.completionSections.add(this.storage.section4(chunkPos, 0, flag));
        }
    }

    @Override
    public boolean isCompleted()
    {
        if (this.completionSections.isEmpty())
        {
            return false;
        }
        for (PreviewSection section : this.completionSections)
        {
            if (!section.isCompleted(this.chunkPos))
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public void markCompleted()
    {
        for (PreviewSection section : this.completionSections)
        {
            section.markCompleted(this.chunkPos);
        }
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

            for (int dx = 0; dx < this.numChunks && !this.isCanceled(); dx++)
            {
                for (int dz = 0; dz < this.numChunks && !this.isCanceled(); dz++)
                {
                    ChunkPos cp = new ChunkPos(baseChunkX + dx, baseChunkZ + dz);

                    // Create sections/results only for outputs this plan needs.
                    WorkResult tempResult = this.plan.temperature() ? newResult(cp, RenderSettings.RenderMode.TFC_TEMPERATURE.flag) : null;
                    WorkResult rainResult = this.plan.rainfall() ? newResult(cp, RenderSettings.RenderMode.TFC_RAINFALL.flag) : null;
                    WorkResult landWaterResult = this.plan.landWater() ? newResult(cp, RenderSettings.RenderMode.TFC_LAND_WATER.flag) : null;
                    WorkResult rockTopResult = this.plan.rocks() ? newResult(cp, RenderSettings.RenderMode.TFC_ROCK_TOP.flag) : null;
                    WorkResult rockMidResult = this.plan.rocks() ? newResult(cp, RenderSettings.RenderMode.TFC_ROCK_MID.flag) : null;
                    WorkResult rockBotResult = this.plan.rocks() ? newResult(cp, RenderSettings.RenderMode.TFC_ROCK_BOT.flag) : null;
                    WorkResult rockTypeResult = this.plan.rocks() ? newResult(cp, RenderSettings.RenderMode.TFC_ROCK_TYPE.flag) : null;
                    WorkResult kaolinResult = this.plan.kaolin() ? newResult(cp, RenderSettings.RenderMode.TFC_KAOLINITE.flag) : null;
                    WorkResult hotspotResult = this.plan.hotspot() ? newResult(cp, RenderSettings.RenderMode.TFC_HOTSPOT.flag) : null;
                    WorkResult forestTypeResult = this.plan.forestType() ? newResult(cp, RenderSettings.RenderMode.TFC_FOREST_TYPE.flag) : null;
                    WorkResult treeSpeciesResult = this.plan.treeSpecies() ? newResult(cp, RenderSettings.RenderMode.TFC_TREE_SPECIES.flag) : null;
                    WorkResult soilTypeResult = this.plan.soilType() ? newResult(cp, RenderSettings.RenderMode.TFC_SOIL_TYPE.flag) : null;

                    PreviewSection structureSection = this.plan.features() ? this.storage.section4(cp, 0, 1L) : null;

                    // ChunkData / forest type only when forest or tree species is requested.
                    short forestTypeId = TFCSampleUtils.VALUE_INVALID;
                    ChunkData chunkData = null;
                    ForestType forestType = null;
                    if (this.plan.needsChunkData() && this.tfcSampleUtils != null)
                    {
                        try
                        {
                            chunkData = this.tfcSampleUtils.sampleChunkData(cp);
                            forestType = chunkData.getForestType();
                            forestTypeId = (short) forestType.ordinal();
                        }
                        catch (Exception e)
                        {
                            WorldPreview.LOGGER.debug("TFC chunk data sampling failed for chunk {}: {}", cp, e.getMessage());
                        }
                    }

                    final boolean needsPoint = this.plan.needsRegionPoint();
                    final boolean needsTreeMap = this.plan.forestType() || this.plan.treeSpecies();
                    // Soil, like the tree maps, needs the effective biome sampled per point (for water
                    // classification and biome-based soil rules) - share that sampling below.
                    final boolean needsBiomeSample = needsTreeMap || this.plan.soilType();

                    for (BlockPos pos : this.sampler.blocksForChunk(cp, 0))
                    {
                        if (this.isCanceled()) break;

                        Region.Point point = null;
                        if (needsPoint)
                        {
                            try
                            {
                                point = getPointCached(pos.getX(), pos.getZ(), gridCache);
                            }
                            catch (Exception e)
                            {
                                continue;
                            }
                            if (point == null) continue;
                        }

                        if (this.plan.features() && point != null)
                        {
                            detectFeatures(point, pos.getX(), pos.getZ(), structureSection);
                        }

                        // Land/water (with river fractal) only when requested; also drives kaolin's land test.
                        short landWaterValue = LAND_WATER_LAND;
                        if (this.plan.landWater() && point != null)
                        {
                            landWaterValue = sampleLandWater(pos, gridCache);
                            this.sampler.expandRaw(pos, landWaterValue, landWaterResult);
                        }

                        if (this.plan.temperature() && point != null)
                        {
                            this.sampler.expandRaw(pos, TFCSampleUtils.normalizeTemperature(point.temperature), tempResult);
                        }
                        if (this.plan.rainfall() && point != null)
                        {
                            this.sampler.expandRaw(pos, TFCSampleUtils.normalizeRainfall(point.rainfall), rainResult);
                        }
                        if (this.plan.hotspot() && point != null)
                        {
                            this.sampler.expandRaw(pos, point.hotSpotAge, hotspotResult);
                        }

                        if (this.plan.rocks() && point != null && this.tfcSampleUtils != null)
                        {
                            short rockTopId = -1;
                            short rockMidId = -1;
                            short rockBotId = -1;
                            short rockTypeCategory = (short) TFCSampleUtils.getRockTypeCategory(point.rock);
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
                            this.sampler.expandRaw(pos, rockTopId, rockTopResult);
                            this.sampler.expandRaw(pos, rockMidId, rockMidResult);
                            this.sampler.expandRaw(pos, rockBotId, rockBotResult);
                            this.sampler.expandRaw(pos, rockTypeCategory, rockTypeResult);
                        }

                        if (this.plan.kaolin() && point != null)
                        {
                            short kaolinValue;
                            if (landWaterValue == LAND_WATER_LAND)
                            {
                                if (this.kaolinRules != null)
                                {
                                    final boolean rainfallCheck = point.rainfall >= 300f;
                                    final boolean temperatureCheck = point.temperature >= 18f;
                                    long bkey = packBlockKey(pos.getX(), pos.getZ());
                                    var biomeKey = biomeKeyCache.computeIfAbsent(bkey, ignored -> this.sampleUtils.getBiomeKey(pos));
                                    ResourceLocation biomeId = biomeKey.location();
                                    Boolean biomeAllowed = biomeAllowedCache.computeIfAbsent(biomeId, this.kaolinRules::isBiomeAllowed);
                                    kaolinValue = (short) (rainfallCheck && temperatureCheck && biomeAllowed ? 2 : 1);
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
                            this.sampler.expandRaw(pos, kaolinValue, kaolinResult);
                        }

                        // Forest type / tree species / soil: water via classifyTreeMapWater (no river fractal).
                        if (needsBiomeSample)
                        {
                            BiomeExtension treeMapBiome = null;
                            if (this.tfcSampleUtils != null)
                            {
                                try
                                {
                                    treeMapBiome = this.tfcSampleUtils.sampleBiomeExtension(pos.getX(), pos.getZ());
                                }
                                catch (Exception e)
                                {
                                    // fall through to non-water land
                                }
                            }

                            short treeMapWater = classifyTreeMapWater(treeMapBiome);
                            switch (treeMapWater)
                            {
                                case TFCSampleUtils.VALUE_WATER_OCEAN -> treeMapOceanPoints.incrementAndGet();
                                case TFCSampleUtils.VALUE_WATER_LAKE -> treeMapLakePoints.incrementAndGet();
                                case TFCSampleUtils.VALUE_WATER_RIVER -> treeMapRiverPoints.incrementAndGet();
                                default -> treeMapLandPoints.incrementAndGet();
                            }
                            boolean isWaterPoint = treeMapWater >= 0;

                            if (this.plan.forestType())
                            {
                                this.sampler.expandRaw(pos, isWaterPoint ? treeMapWater : forestTypeId, forestTypeResult);
                            }
                            if (this.plan.treeSpecies())
                            {
                                short treeSpeciesValue = TFCSampleUtils.VALUE_INVALID;
                                if (isWaterPoint)
                                {
                                    treeSpeciesValue = treeMapWater;
                                }
                                else if (this.treeResolver != null && chunkData != null && forestType != null)
                                {
                                    int surfaceY = sampleSurfaceY(pos);
                                    treeSpeciesValue = this.treeResolver
                                        .resolve(chunkData, forestType, treeMapBiome, pos.getX(), pos.getZ(), surfaceY)
                                        .dominantId();
                                }
                                this.sampler.expandRaw(pos, treeSpeciesValue, treeSpeciesResult);
                            }
                            if (this.plan.soilType())
                            {
                                short soilValue;
                                if (isWaterPoint)
                                {
                                    soilValue = treeMapWater;
                                }
                                else if (chunkData != null && forestType != null)
                                {
                                    int surfaceY = sampleSurfaceY(pos);
                                    soilValue = TFCSampleUtils.resolveSoilType(
                                        chunkData, treeMapBiome, forestType, pos, surfaceY, treeMapWater);
                                }
                                else
                                {
                                    soilValue = TFCSampleUtils.VALUE_INVALID;
                                }
                                this.sampler.expandRaw(pos, soilValue, soilTypeResult);
                            }
                        }
                    }

                    addIfPresent(allResults, tempResult);
                    addIfPresent(allResults, rainResult);
                    addIfPresent(allResults, landWaterResult);
                    addIfPresent(allResults, rockTopResult);
                    addIfPresent(allResults, rockMidResult);
                    addIfPresent(allResults, rockBotResult);
                    addIfPresent(allResults, rockTypeResult);
                    addIfPresent(allResults, kaolinResult);
                    addIfPresent(allResults, forestTypeResult);
                    addIfPresent(allResults, treeSpeciesResult);
                    addIfPresent(allResults, soilTypeResult);
                    addIfPresent(allResults, hotspotResult);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            reportProgress(gridCellsProcessed[0], elapsed);

            WorldPreview.LOGGER.debug("[TFC] Unit ({},{}) plan[{}] {} chunks in {}ms (features={}, treeSpecies={})",
                this.chunkPos.x, this.chunkPos.z, this.plan.describe(), this.numChunks * this.numChunks, elapsed,
                this.plan.features(), this.plan.treeSpecies());

            return allResults;

        }
        catch (Throwable t)
        {
            WorldPreview.LOGGER.error("[TFC] doWork() FAILED for chunk ({}, {}): {}",
                this.chunkPos.x, this.chunkPos.z, t.getMessage(), t);
            throw t;
        }
    }

    private WorkResult newResult(ChunkPos cp, long flag)
    {
        return new WorkResult(this, 0, this.storage.section4(cp, 0, flag), new ArrayList<>(16), List.of());
    }

    private static void addIfPresent(List<WorkResult> results, @Nullable WorkResult result)
    {
        if (result != null)
        {
            results.add(result);
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

    /**
     * Per-position water classification for the forest-type / tree-species maps.
     * Uses only the sampled BiomeExtension from the generator's BiomeSourceExtension - the
     * same effective biome lookup (including TFC's river overlay) as the normal biome map -
     * so the water boundary matches the biome map at quart resolution. The coarse
     * Region.Point land/water grid is deliberately not consulted here; it stays in use for
     * the standalone TFC_LAND_WATER map only.
     * Returns VALUE_WATER_OCEAN / VALUE_WATER_LAKE / VALUE_WATER_RIVER for water points,
     * or -1 for land. Takes the biome sampled once by the caller.
     */
    private short classifyTreeMapWater(@Nullable BiomeExtension biome)
    {
        if (!TFCSampleUtils.isTreeMapWaterBiome(biome))
        {
            return -1;
        }
        if (biome.biomeBlendType() == BiomeBlendType.OCEAN)
        {
            return TFCSampleUtils.VALUE_WATER_OCEAN;
        }
        if (biome.key().location().getPath().equals("river"))
        {
            return TFCSampleUtils.VALUE_WATER_RIVER;
        }
        // LAKE blend type, "lake", "*_lake", "tower_karst_bay"
        return TFCSampleUtils.VALUE_WATER_LAKE;
    }

    private static volatile boolean loggedSeaLevelFallback = false;

    /**
     * Surface height for elevation-adjusted climate, via the same per-column path the heightmap
     * render uses ({@link SampleUtils#doHeightSlow}), which approximates OCEAN_FLOOR_WG. Falls back
     * to TFC sea level (63) when the height sampler is unavailable, so resolution still works
     * (without elevation cooling). The fallback is logged once. A dedicated surface-height resolver
     * can be plugged in here later.
     */
    private int sampleSurfaceY(BlockPos pos)
    {
        try
        {
            return this.sampleUtils.doHeightSlow(pos);
        }
        catch (Exception e)
        {
            if (!loggedSeaLevelFallback)
            {
                loggedSeaLevelFallback = true;
                WorldPreview.LOGGER.warn("[TFC] Tree species elevation using sea-level (63) fallback: {}", e.getMessage());
            }
            return 63; // TFC SEA_LEVEL_Y fallback
        }
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

            BlockPos center = test.findCenter(query);
            if (center == null)
            {
                continue;
            }

            short id = FeatureDetectors.getFeatureId(feature);
            if (id < 0)
            {
                continue;
            }

            long key = (((long) id & 0xFFFFL) << 48)
                ^ (((long) center.getX() & 0xFFFFFFL) << 24)
                ^ ((long) center.getZ() & 0xFFFFFFL);

            synchronized (this.detectedFeatureCenters)
            {
                if (this.detectedFeatureCenters.add(key))
                {
                    structureSection.addFeature(new PreviewSection.PreviewFeature(id, center));
                }
            }
        }
    }

    @Override
    public long flags()
    {
        // Only used by the base class to build an (unused) primary section during construction;
        // real completion tracking is per-plan via completionSections. Return a valid storage flag.
        return TFCWorkPlan.FEATURES_FLAG;
    }
}
