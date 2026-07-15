package com.rustysnail.world.preview.tfc.backend.search;

import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.dries007.tfc.common.TFCAttachments;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;

public class FeatureProbe
{

    private static final int TFC_MIN_Y = -64;
    private static final int TFC_HEIGHT = 448;
    private static final int TFC_SEA_LEVEL = 63;

    private final RegistryAccess registryAccess;
    private final ChunkGenerator chunkGenerator;
    private final Settings tfcSettings;

    public FeatureProbe(
        ChunkGenerator chunkGenerator,
        RegistryAccess registryAccess
    )
    {
        if (!(chunkGenerator instanceof ChunkGeneratorExtension ext))
        {
            throw new IllegalArgumentException("FeatureProbe requires TFC ChunkGenerator");
        }

        this.chunkGenerator = chunkGenerator;
        this.registryAccess = registryAccess;
        this.tfcSettings = ext.settings();
    }

    public Map<ResourceLocation, List<BlockPos>> probeRegion(
        long seed,
        ChunkPos centerChunk,
        int radius,
        Set<ResourceLocation> targetFeatures
    )
    {
        Seed tfcSeed = Seed.of(seed);
        RegionGenerator regionGen = new RegionGenerator(this.tfcSettings, tfcSeed);
        ChunkDataGenerator chunkDataGen = new RegionChunkDataGenerator(
            regionGen,
            this.tfcSettings.rockLayerSettings(),
            tfcSeed
        );

        FeatureProbeLevel probeLevel = new FeatureProbeLevel(
            this.registryAccess,
            TFC_MIN_Y,
            TFC_HEIGHT,
            seed
        );

        Long2ObjectMap<ProtoChunk> chunks = new Long2ObjectOpenHashMap<>();

        int minX = centerChunk.x - radius;
        int maxX = centerChunk.x + radius;
        int minZ = centerChunk.z - radius;
        int maxZ = centerChunk.z + radius;

        for (int cx = minX; cx <= maxX; cx++)
        {
            for (int cz = minZ; cz <= maxZ; cz++)
            {
                ChunkPos pos = new ChunkPos(cx, cz);
                ProtoChunk chunk = createChunkWithData(pos, chunkDataGen, probeLevel);
                chunks.put(pos.toLong(), chunk);
                probeLevel.addChunk(chunk);
            }
        }

        for (ProtoChunk chunk : chunks.values())
        {
            fillBasicTerrain(chunk);
        }

        FeaturePlacementTracker.setWhitelist(targetFeatures);
        FeaturePlacementTracker.clearAll();

        try
        {
            FeaturePlacementTracker.setRecording(true);
            runFeaturePlacement(probeLevel, centerChunk, seed);
        }
        finally
        {
            FeaturePlacementTracker.setRecording(false);
        }

        ResourceLocation dimension = probeLevel.dimensionType().effectsLocation();
        return FeaturePlacementTracker.query(
            dimension,
            new ChunkPos(minX, minZ),
            new ChunkPos(maxX, maxZ)
        );
    }

    private ProtoChunk createChunkWithData(
        ChunkPos pos,
        ChunkDataGenerator chunkDataGen,
        FeatureProbeLevel level
    )
    {
        var biomeRegistry = this.registryAccess.registryOrThrow(Registries.BIOME);

        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomeRegistry, null);

        ChunkData chunkData = new ChunkData(chunkDataGen, pos);
        chunkDataGen.generate(chunkData);

        chunk.setData(TFCAttachments.CHUNK_DATA.holder().get(), chunkData);

        return chunk;
    }

    private void fillBasicTerrain(ProtoChunk chunk)
    {
        ChunkPos pos = chunk.getPos();

        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int y = TFC_MIN_Y; y < TFC_SEA_LEVEL; y++)
                {
                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    chunk.setBlockState(blockPos, Blocks.STONE.defaultBlockState(), false);
                }
            }
        }

        var types = Set.of(
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE_WG
        );
        Heightmap.primeHeightmaps(chunk, types);
    }

    private void runFeaturePlacement(
        FeatureProbeLevel level,
        ChunkPos centerChunk,
        long seed
    )
    {
        var featureRegistry = this.registryAccess.registryOrThrow(Registries.PLACED_FEATURE);

        BlockPos origin = centerChunk.getWorldPosition();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(seed, origin.getX(), origin.getZ());

        for (var entry : featureRegistry.entrySet())
        {
            ResourceLocation id = entry.getKey().location();

            if (!"tfc".equals(id.getNamespace())) continue;

            Holder<PlacedFeature> holder = featureRegistry.getHolderOrThrow(entry.getKey());
            PlacedFeature feature = holder.value();

            try
            {
                feature.place(level, this.chunkGenerator, random, origin);
            }
            catch (Exception e)
            {
                // Features may fail due to missing blocks, entities.
            }
        }
    }
}
