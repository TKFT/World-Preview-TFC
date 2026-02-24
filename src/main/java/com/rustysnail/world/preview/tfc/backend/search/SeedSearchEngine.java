package com.rustysnail.world.preview.tfc.backend.search;

import com.rustysnail.world.preview.tfc.WorldPreview;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.AreaFactory;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.settings.Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
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

        if (!requiredFeatures.isEmpty())
        {
            if (regionGen == null)
            {
                return null;
            }

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

            Set<SearchableFeature> remaining = new HashSet<>(requiredFeatures);

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

            for (SearchableFeature f : remaining)
            {
                if (f.test() != null)
                {
                    return null;
                }
            }
        }

        Set<SearchableFeature> probeRequired = FeatureDetectors.getProbeRequiredFeatures(requiredFeatures);
        if (!probeRequired.isEmpty() && this.isTFC && this.registryAccess != null)
        {
            Set<ResourceLocation> probeIds = FeatureDetectors.getProbeFeatureIds(probeRequired);

            try
            {
                FeatureProbe probe = new FeatureProbe(seededGen, this.registryAccess);

                BlockPos probeCenter = featureLocation != null ? featureLocation : searchCenter;
                ChunkPos centerChunk = new ChunkPos(probeCenter.getX() >> 4, probeCenter.getZ() >> 4);

                int probeRadiusChunks = featureLocation != null ? KNOWN_LOCATION_PROBE_RADIUS : Math.max(1, radius / 256);

                Map<ResourceLocation, List<BlockPos>> placements = probe.probeRegion(
                    seedLong,
                    centerChunk,
                    probeRadiusChunks,
                    probeIds
                );

                for (SearchableFeature feature : probeRequired)
                {
                    ResourceLocation featureId = feature.getPlacedFeatureId();
                    List<BlockPos> positions = placements.get(featureId);
                    if (positions == null || positions.isEmpty())
                    {
                        return null;
                    }

                    if (featureLocation == null)
                    {
                        featureLocation = positions.getFirst();
                    }
                }
            }
            catch (Exception e)
            {
                WorldPreview.LOGGER.debug("Feature probe failed for seed {}: {}", seedLong, e.getMessage());
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

}
