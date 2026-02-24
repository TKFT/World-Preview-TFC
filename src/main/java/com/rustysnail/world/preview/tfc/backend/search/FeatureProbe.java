package com.rustysnail.world.preview.tfc.backend.search;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.dries007.tfc.common.TFCAttachments;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.*;

public class FeatureProbe
{

    private static final int TFC_MIN_Y = -64;
    private static final int TFC_HEIGHT = 448;

    private final RegistryAccess registryAccess;
    private final ChunkGenerator chunkGenerator;
    private final Settings tfcSettings;
    private final int minY;
    private final int height;

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

        this.minY = TFC_MIN_Y;
        this.height = TFC_HEIGHT;
    }

    public Map<ResourceLocation, List<BlockPos>> probeRegion(
        long seed,
        ChunkPos centerChunk,
        int radius,
        Set<ResourceLocation> targetFeatures
    )
    {
        Seed tfcSeed = Seed.of(seed);
        RegionGenerator regionGen = new RegionGenerator(tfcSettings, tfcSeed);
        ChunkDataGenerator chunkDataGen = new RegionChunkDataGenerator(
            regionGen,
            tfcSettings.rockLayerSettings(),
            tfcSeed
        );

        FeatureProbeLevel probeLevel = new FeatureProbeLevel(
            registryAccess,
            minY,
            height,
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
        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomeRegistry, null);

        ChunkData chunkData = new ChunkData(chunkDataGen, pos);
        chunkDataGen.generate(chunkData);

        chunk.setData(TFCAttachments.CHUNK_DATA.holder().get(), chunkData);

        return chunk;
    }

    private void fillBasicTerrain(ProtoChunk chunk)
    {
        int seaLevel = 63;
        ChunkPos pos = chunk.getPos();

        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int y = minY; y < seaLevel; y++)
                {
                    BlockPos blockPos = new BlockPos(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    chunk.setBlockState(blockPos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), false);
                }
            }
        }

        var types = Set.of(
            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG
        );
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, types);
    }

    private void runFeaturePlacement(
        FeatureProbeLevel level,
        ChunkPos centerChunk,
        long seed
    )
    {
        var featureRegistry = registryAccess.registryOrThrow(Registries.PLACED_FEATURE);

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
                feature.place(level, chunkGenerator, random, origin);
            }
            catch (Exception e)
            {
                // Features may fail due to missing blocks, entities.
            }
        }
    }
}
