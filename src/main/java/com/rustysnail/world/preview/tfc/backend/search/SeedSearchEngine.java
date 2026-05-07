package com.rustysnail.world.preview.tfc.backend.search;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.feature.vein.IVeinConfig;
import net.dries007.tfc.world.feature.vein.PipeVeinFeature;
import net.dries007.tfc.world.feature.vein.VeinFeature;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.AreaFactory;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.settings.RockSettings;
import net.dries007.tfc.world.settings.Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SeedSearchEngine implements Runnable
{

    public interface Callback
    {
        void onProgress(long seedsTested, int maxSeeds, String currentSeed,
                        @Nullable String debugBiomeAtOrigin);

        void onMatchFound(MatchResult result);

        void onComplete(long totalTested, int matchesFound);

        void onCancelled();

        void onError(Throwable t);
    }

    private static final int KNOWN_LOCATION_PROBE_RADIUS = 8;

    private final ChunkGenerator chunkGenerator;
    private final net.minecraft.world.level.LevelHeightAccessor heightAccessor;
    @Nullable private final RegistryAccess registryAccess;
    @Nullable private final Settings tfcSettings;
    private final boolean isTFC;

    private final SearchCriteria criteria;
    private final Callback callback;

    private volatile boolean cancelled = false;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private java.lang.reflect.Method cachedWithSeedMethod;
    private boolean withSeedMethodLookedUp = false;

    public SeedSearchEngine(
        ChunkGenerator chunkGenerator,
        @Nullable RegistryAccess registryAccess,
        net.minecraft.world.level.LevelHeightAccessor heightAccessor,
        SearchCriteria criteria,
        Callback callback
    )
    {
        this.chunkGenerator = chunkGenerator;
        this.heightAccessor = heightAccessor;
        this.registryAccess = registryAccess;
        this.criteria = criteria;
        this.callback = callback;

        if (chunkGenerator instanceof ChunkGeneratorExtension ext)
        {
            this.tfcSettings = ext.settings();
            this.isTFC = true;
        }
        else
        {
            this.tfcSettings = null;
            this.isTFC = false;
        }
    }

    public void cancel()
    {
        this.cancelled = true;

        synchronized (this.pauseLock)
        {
            this.pauseLock.notifyAll();
        }
    }

    public void resume()
    {
        this.paused = false;
        synchronized (this.pauseLock)
        {
            this.pauseLock.notifyAll();
        }
    }

    @Override
    public void run()
    {
        try
        {
            SplittableRandom random = new SplittableRandom();
            int matchesFound = 0;
            long seedsTested = 0;

            while (seedsTested < this.criteria.maxSeeds() && !this.cancelled)
            {
                checkPause();
                if (this.cancelled) break;

                long seedLong = random.nextLong();
                String seedString = Long.toString(seedLong);
                seedsTested++;

                if (seedsTested % 10 == 0)
                {
                    this.callback.onProgress(
                        seedsTested,
                        this.criteria.maxSeeds(),
                        seedString,
                        sampleBiomeKeyStringAtOrigin(seedLong)
                    );
                }

                MatchResult result = testSeed(seedLong, seedString);
                if (result != null)
                {
                    matchesFound++;
                    this.callback.onMatchFound(result);

                    this.paused = true;
                    checkPause();
                    if (this.cancelled) break;
                }
            }

            if (this.cancelled)
            {
                this.callback.onCancelled();
            }
            else
            {
                this.callback.onComplete(seedsTested, matchesFound);
            }
        }
        catch (Throwable t)
        {
            WorldPreview.LOGGER.error("Seed search failed", t);
            this.callback.onError(t);
        }
    }

    @Nullable
    private MatchResult testSeed(long seedLong, String seedString)
    {
        BlockPos searchCenter = this.criteria.searchCenter();
        int radius = this.criteria.searchRadius();
        Set<SearchableFeature> requiredFeatures = this.criteria.requiredFeatures();
        boolean anyFeatureMode = this.criteria.featureMatchMode() == SearchCriteria.FeatureMatchMode.ANY;
        Set<ResourceKey<Biome>> requiredBiomes = this.criteria.requiredBiomes();

        final ChunkGenerator seededGen = generatorForSeed(seedLong);

        Set<SearchableFeature> foundFeatures = new HashSet<>();
        BlockPos featureLocation = null;
        BlockPos featureCenter = null;

        RegionGenerator regionGen = null;
        FeatureQuery.BiomeLookup biomeLookup = null;
        if (this.isTFC && this.tfcSettings != null)
        {
            Seed tfcSeed = Seed.of(seedLong);
            regionGen = new RegionGenerator(this.tfcSettings, tfcSeed);

            AreaFactory biomeFactory = TFCLayers.createRegionBiomeLayer(regionGen, tfcSeed);
            ConcurrentArea<BiomeExtension> biomeLayer = new ConcurrentArea<>(biomeFactory, TFCLayers::getFromLayerId);

            biomeLookup = biomeLayer::get;
        }

        Set<SearchableFeature> manualRequired = new HashSet<>();
        for (SearchableFeature feature : requiredFeatures)
        {
            if (feature.test() != null)
            {
                manualRequired.add(feature);
            }
        }

        if (!manualRequired.isEmpty() && regionGen != null)
        {
            HeightSampler heights = null;
            if (this.registryAccess != null && this.heightAccessor != null)
            {
                final NoiseGeneratorSettings settings =
                    (seededGen instanceof NoiseBasedChunkGenerator nbg)
                        ? nbg.generatorSettings().value()
                        : NoiseGeneratorSettings.dummy();

                final RandomState rs = RandomState.create(
                    settings,
                    this.registryAccess.lookupOrThrow(Registries.NOISE),
                    seedLong
                );

                heights = (x, z) -> seededGen.getBaseHeight(
                    x,
                    z,
                    net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                    this.heightAccessor,
                    rs
                );
            }

            Set<SearchableFeature> remaining = new HashSet<>(manualRequired);

            int minGridX = Units.blockToGrid(searchCenter.getX() - radius);
            int maxGridX = Units.blockToGrid(searchCenter.getX() + radius);
            int minGridZ = Units.blockToGrid(searchCenter.getZ() - radius);
            int maxGridZ = Units.blockToGrid(searchCenter.getZ() + radius);

            for (int gx = minGridX; gx <= maxGridX && !remaining.isEmpty(); gx++)
            {
                for (int gz = minGridZ; gz <= maxGridZ && !remaining.isEmpty(); gz++)
                {
                    if (this.cancelled) return null;

                    Region.Point point = regionGen.getOrCreateRegionPoint(gx, gz);
                    BiomeExtension ext = TFCLayers.getFromLayerId(point.biome);

                    final int blockX = Units.gridToBlock(gx) + Units.GRID_WIDTH_IN_BLOCK / 2;
                    final int blockZ = Units.gridToBlock(gz) + Units.GRID_WIDTH_IN_BLOCK / 2;

                    FeatureQuery q = new FeatureQuery(seedLong, blockX, blockZ, point, ext, biomeLookup, heights);

                    for (var it = remaining.iterator(); it.hasNext(); )
                    {
                        SearchableFeature feature = it.next();
                        FeatureTest test = feature.test();
                        if (test == null) continue;

                        if (test.matches(q))
                        {
                            foundFeatures.add(feature);
                            it.remove();

                            BlockPos center = test.findCenter(q);
                            if (center != null)
                            {
                                if (featureCenter == null) featureCenter = center;
                                if (featureLocation == null) featureLocation = center;
                            }
                            else if (featureLocation == null)
                            {
                                featureLocation = new BlockPos(blockX, 64, blockZ);
                            }
                        }
                    }
                }
            }
        }
        else if (!manualRequired.isEmpty() && !anyFeatureMode)
        {
            return null;
        }

        Set<SearchableFeature> probeRequired = FeatureDetectors.getProbeRequiredFeatures(requiredFeatures);
        if (!probeRequired.isEmpty() && this.isTFC && this.registryAccess != null && (!anyFeatureMode || foundFeatures.isEmpty()))
        {
            Set<ResourceLocation> probeIds = FeatureDetectors.getProbeFeatureIds(probeRequired);
            Map<Long, Region.Point> pointCache = new HashMap<>();

            try
            {
                FeatureProbe probe = new FeatureProbe(seededGen, this.registryAccess);

                BlockPos probeCenter = featureLocation != null ? featureLocation : searchCenter;
                ChunkPos centerChunk = new ChunkPos(probeCenter.getX() >> 4, probeCenter.getZ() >> 4);

                int probeRadiusChunks = featureLocation != null ? KNOWN_LOCATION_PROBE_RADIUS : Math.max(1, radius / 256);

                Map<ResourceLocation, List<BlockPos>> placements = new HashMap<>(probe.probeRegion(
                    seedLong,
                    centerChunk,
                    probeRadiusChunks,
                    probeIds
                ));

                Set<ResourceLocation> missingProbeIds = findMissingProbeIds(probeIds, placements);
                if (!missingProbeIds.isEmpty())
                {
                    Map<ResourceLocation, List<BlockPos>> fallbackPlacements = findFallbackVeinPlacements(
                        seedLong,
                        centerChunk,
                        probeRadiusChunks,
                        missingProbeIds,
                        seededGen
                    );
                    mergePlacements(placements, fallbackPlacements);
                }

                for (SearchableFeature feature : probeRequired)
                {
                    BlockPos matchedPosition = null;
                    for (ResourceLocation configuredId : FeatureDetectors.getConfiguredVeinIds(feature))
                    {
                        List<BlockPos> positions = placements.get(configuredId);
                        if (positions == null || positions.isEmpty())
                        {
                            continue;
                        }

                        BlockPos validPosition = findFirstValidProbePosition(configuredId, positions, regionGen, pointCache);
                        if (validPosition != null)
                        {
                            matchedPosition = validPosition;
                            break;
                        }
                    }

                    if (anyFeatureMode)
                    {
                        if (matchedPosition != null)
                        {
                            foundFeatures.add(feature);
                            if (featureLocation == null)
                            {
                                featureLocation = matchedPosition;
                            }
                            break;
                        }
                    }
                    else
                    {
                        if (matchedPosition == null)
                        {
                            return null;
                        }
                        foundFeatures.add(feature);
                        if (featureLocation == null)
                        {
                            featureLocation = matchedPosition;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                WorldPreview.LOGGER.debug("Feature probe failed for seed {}: {}", seedLong, e.getMessage());
                return null;
            }
        }

        if (!requiredFeatures.isEmpty())
        {
            if (anyFeatureMode)
            {
                if (foundFeatures.isEmpty())
                {
                    return null;
                }
            }
            else if (!foundFeatures.containsAll(manualRequired))
            {
                return null;
            }
        }

        Set<ResourceKey<Biome>> foundBiomes = new HashSet<>();
        BlockPos biomeMatchLocation = null;
        if (!requiredBiomes.isEmpty())
        {
            if (this.registryAccess == null)
            {
                return null;
            }

            final NoiseGeneratorSettings settings =
                (seededGen instanceof NoiseBasedChunkGenerator nbg)
                    ? nbg.generatorSettings().value()
                    : NoiseGeneratorSettings.dummy();

            RandomState randomState = RandomState.create(
                settings,
                this.registryAccess.lookupOrThrow(Registries.NOISE),
                seedLong
            );
            Climate.Sampler sampler = randomState.sampler();

            BiomeSource seededBiomeSource = seededGen.getBiomeSource();


            Set<ResourceKey<Biome>> remaining = new HashSet<>(requiredBiomes);
            boolean isAnyMode = this.criteria.biomeMatchMode() == SearchCriteria.BiomeMatchMode.ANY;

            int minX = searchCenter.getX() - radius;
            int maxX = searchCenter.getX() + radius;
            int minZ = searchCenter.getZ() - radius;
            int maxZ = searchCenter.getZ() + radius;

            final int spacing = 64;

            boolean shouldContinue = isAnyMode || !remaining.isEmpty();

            for (int x = minX; x <= maxX && shouldContinue; x += spacing)
            {
                for (int z = minZ; z <= maxZ && shouldContinue; z += spacing)
                {
                    if (this.cancelled) return null;

                    ResourceKey<Biome> biomeKey = seededBiomeSource
                        .getNoiseBiome(
                            QuartPos.fromBlock(x),
                            QuartPos.fromBlock(64),
                            QuartPos.fromBlock(z),
                            sampler
                        )
                        .unwrapKey()
                        .orElse(null);

                    if (biomeKey != null && requiredBiomes.contains(biomeKey))
                    {
                        remaining.remove(biomeKey);
                        foundBiomes.add(biomeKey);
                        biomeMatchLocation = new BlockPos(x, 64, z);

                        shouldContinue = !isAnyMode && !remaining.isEmpty();
                    }
                }
            }

            if (isAnyMode)
            {
                if (foundBiomes.isEmpty())
                {
                    return null;
                }
            }
            else
            {
                if (!remaining.isEmpty())
                {
                    return null;
                }
            }
        }

        BlockPos previewCenter = featureLocation != null ? featureLocation : biomeMatchLocation;
        return new MatchResult(seedString, seedLong, foundBiomes, foundFeatures, previewCenter, featureCenter);
    }

    private void checkPause()
    {
        synchronized (this.pauseLock)
        {
            while (this.paused && !this.cancelled)
            {
                try
                {
                    this.pauseLock.wait();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    this.cancelled = true;
                }
            }
        }
    }

    private ChunkGenerator generatorForSeed(long seed)
    {
        if (!this.withSeedMethodLookedUp)
        {
            this.withSeedMethodLookedUp = true;
            try
            {
                this.cachedWithSeedMethod = this.chunkGenerator.getClass().getMethod("withSeed", long.class);
            }
            catch (NoSuchMethodException ignored)
            {
                this.cachedWithSeedMethod = null;
            }
        }

        if (this.cachedWithSeedMethod != null)
        {
            try
            {
                Object g = this.cachedWithSeedMethod.invoke(this.chunkGenerator, seed);
                return (ChunkGenerator) g;
            }
            catch (ReflectiveOperationException ignored)
            {
                // fallback below
            }
        }
        return this.chunkGenerator;
    }

    @Nullable
    private String sampleBiomeKeyStringAtOrigin(long seedLong)
    {
        if (this.registryAccess == null) return null;

        final ChunkGenerator seededGen = generatorForSeed(seedLong);
        final NoiseGeneratorSettings settings =
            (seededGen instanceof NoiseBasedChunkGenerator nbg)
                ? nbg.generatorSettings().value()
                : NoiseGeneratorSettings.dummy();

        RandomState randomState = RandomState.create(
            settings,
            this.registryAccess.lookupOrThrow(Registries.NOISE),
            seedLong
        );
        Climate.Sampler sampler = randomState.sampler();

        BiomeSource seededBiomeSource = seededGen.getBiomeSource();

        ResourceKey<Biome> biomeKey = seededBiomeSource
            .getNoiseBiome(
                QuartPos.fromBlock(0),
                QuartPos.fromBlock(64),
                QuartPos.fromBlock(0),
                sampler
            )
            .unwrapKey()
            .orElse(null);

        return biomeKey == null ? null : biomeKey.location().toString();
    }

    @Nullable
    private BlockPos findFirstValidProbePosition(
        ResourceLocation configuredId,
        List<BlockPos> positions,
        @Nullable RegionGenerator regionGen,
        Map<Long, Region.Point> pointCache
    )
    {
        if (!OrePlacementRules.hasExtraConstraints(configuredId))
        {
            return positions.getFirst();
        }
        if (regionGen == null)
        {
            return null;
        }

        for (BlockPos pos : positions)
        {
            Region.Point point = getPointCached(regionGen, pos.getX(), pos.getZ(), pointCache);
            if (OrePlacementRules.matchesConfiguredPlacement(configuredId, pos, regionGen, point))
            {
                return pos;
            }
        }

        return null;
    }

    @Nullable
    private Region.Point getPointCached(
        RegionGenerator regionGen,
        int blockX,
        int blockZ,
        Map<Long, Region.Point> pointCache
    )
    {
        int gridX = Units.blockToGrid(blockX);
        int gridZ = Units.blockToGrid(blockZ);
        long key = (((long) gridX) << 32) ^ (gridZ & 0xffffffffL);

        Region.Point point = pointCache.get(key);
        if (point != null)
        {
            return point;
        }

        point = OrePlacementRules.samplePoint(regionGen, blockX, blockZ);
        if (point != null)
        {
            pointCache.put(key, point);
        }
        return point;
    }

    private static Set<ResourceLocation> findMissingProbeIds(
        Set<ResourceLocation> requested,
        Map<ResourceLocation, List<BlockPos>> placements
    )
    {
        Set<ResourceLocation> missing = new HashSet<>();
        for (ResourceLocation id : requested)
        {
            List<BlockPos> positions = placements.get(id);
            if (positions == null || positions.isEmpty())
            {
                missing.add(id);
            }
        }
        return missing;
    }

    private static void mergePlacements(
        Map<ResourceLocation, List<BlockPos>> target,
        Map<ResourceLocation, List<BlockPos>> additions
    )
    {
        for (Map.Entry<ResourceLocation, List<BlockPos>> entry : additions.entrySet())
        {
            if (entry.getValue().isEmpty())
            {
                continue;
            }
            target.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    private Map<ResourceLocation, List<BlockPos>> findFallbackVeinPlacements(
        long seed,
        ChunkPos centerChunk,
        int radiusChunks,
        Set<ResourceLocation> targetIds,
        ChunkGenerator seededGen
    )
    {
        if (targetIds.isEmpty() || this.registryAccess == null)
        {
            return Map.of();
        }

        TFCSampleUtils tfcSampleUtils = TFCSampleUtils.create(seededGen, seed);
        if (tfcSampleUtils == null)
        {
            return Map.of();
        }

        List<FallbackVeinTarget> targets = resolveFallbackVeinTargets(this.registryAccess, targetIds);
        if (targets.isEmpty())
        {
            return Map.of();
        }

        Set<ResourceLocation> remaining = new HashSet<>();
        for (FallbackVeinTarget target : targets)
        {
            remaining.add(target.configuredFeatureId());
        }

        int minChunkX = centerChunk.x - radiusChunks;
        int maxChunkX = centerChunk.x + radiusChunks;
        int minChunkZ = centerChunk.z - radiusChunks;
        int maxChunkZ = centerChunk.z + radiusChunks;

        Map<ResourceLocation, List<BlockPos>> result = new HashMap<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX && !remaining.isEmpty(); chunkX++)
        {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ && !remaining.isEmpty(); chunkZ++)
            {
                if (this.cancelled)
                {
                    return result;
                }

                for (FallbackVeinTarget target : targets)
                {
                    if (!remaining.contains(target.configuredFeatureId()))
                    {
                        continue;
                    }

                    BlockPos center = sampleFallbackCandidateCenter(seed, chunkX, chunkZ, target);
                    if (center == null)
                    {
                        continue;
                    }

                    Region.Point point = OrePlacementRules.samplePoint(tfcSampleUtils.regionGenerator(), center.getX(), center.getZ());
                    if (point == null)
                    {
                        continue;
                    }
                    if (!matchesFallbackReplaceableRock(point, target, tfcSampleUtils))
                    {
                        continue;
                    }
                    if (!OrePlacementRules.matchesConfiguredPlacement(
                        target.configuredFeatureId(),
                        center,
                        tfcSampleUtils.regionGenerator(),
                        point
                    ))
                    {
                        continue;
                    }

                    result.computeIfAbsent(target.configuredFeatureId(), ignored -> new ArrayList<>()).add(center);
                    remaining.remove(target.configuredFeatureId());
                }
            }
        }

        return result;
    }

    private static List<FallbackVeinTarget> resolveFallbackVeinTargets(
        RegistryAccess registryAccess,
        Set<ResourceLocation> targetIds
    )
    {
        Registry<ConfiguredFeature<?, ?>> configuredFeatures = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);
        List<FallbackVeinTarget> targets = new ArrayList<>();

        for (ResourceLocation configuredFeatureId : targetIds)
        {
            ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE, configuredFeatureId);
            ConfiguredFeature<?, ?> configured = configuredFeatures.get(key);
            if (configured == null)
            {
                continue;
            }
            if (!(configured.feature() instanceof VeinFeature<?, ?> veinFeature))
            {
                continue;
            }
            if (!(configured.config() instanceof IVeinConfig veinConfig))
            {
                continue;
            }
            if (veinConfig.config().states().isEmpty())
            {
                continue;
            }

            targets.add(new FallbackVeinTarget(
                configuredFeatureId,
                veinConfig,
                veinConfig.config().states().keySet(),
                veinFeature instanceof PipeVeinFeature
            ));
        }

        return targets;
    }

    private static @Nullable BlockPos sampleFallbackCandidateCenter(
        long worldSeed,
        int chunkX,
        int chunkZ,
        FallbackVeinTarget target
    )
    {
        int rarity = target.config().config().rarity();
        if (rarity <= 0)
        {
            return null;
        }

        RandomSource random = new XoroshiroRandomSource(
            worldSeed ^ chunkX * 61728364132L,
            target.config().config().seed() ^ chunkZ * 16298364123L
        );

        if (random.nextInt(rarity) != 0)
        {
            return null;
        }

        if (target.consumeAngleBeforeCenter())
        {
            random.nextFloat();
        }

        int blockX = (chunkX << 4) + random.nextInt(16);
        int blockY = sampleFallbackVeinY(random, target.config().verticalRadius(), target.config().minY(), target.config().maxY());
        int blockZ = (chunkZ << 4) + random.nextInt(16);
        return new BlockPos(blockX, blockY, blockZ);
    }

    private static int sampleFallbackVeinY(RandomSource random, int verticalRadius, int minY, int maxY)
    {
        int range = maxY - minY - 2 * verticalRadius;
        if (range > 0)
        {
            return minY + verticalRadius + random.nextInt(range);
        }
        return (minY + maxY) / 2;
    }

    private static boolean matchesFallbackReplaceableRock(
        Region.Point point,
        FallbackVeinTarget target,
        TFCSampleUtils tfcSampleUtils
    )
    {
        for (int layer = 0; layer < 3; layer++)
        {
            RockSettings rock = tfcSampleUtils.sampleRockAtLayer(point.rock, layer);
            if (rock != null && target.replaceableBlocks().contains(rock.raw()))
            {
                return true;
            }
        }
        return false;
    }

    private record FallbackVeinTarget(
        ResourceLocation configuredFeatureId,
        IVeinConfig config,
        Set<net.minecraft.world.level.block.Block> replaceableBlocks,
        boolean consumeAngleBeforeCenter
    )
    {
    }

}
