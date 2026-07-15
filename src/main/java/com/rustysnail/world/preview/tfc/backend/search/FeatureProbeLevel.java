package com.rustysnail.world.preview.tfc.backend.search;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FeatureProbeLevel implements WorldGenLevel, LevelHeightAccessor
{

    private static final int SEA_LEVEL = 63;
    private static final WorldBorder INFINITE_BORDER = new WorldBorder();

    static
    {
        INFINITE_BORDER.setSize(30000000);
    }

    private final RegistryAccess registryAccess;
    private final int minY;
    private final int maxY;
    private final int height;
    private final DimensionType dimensionType;
    private final long seed;
    private final RandomSource random;

    private final Long2ObjectMap<ProtoChunk> chunks = new Long2ObjectOpenHashMap<>();

    public FeatureProbeLevel(
        RegistryAccess registryAccess,
        int minY,
        int height,
        long seed
    )
    {
        this.registryAccess = registryAccess;
        this.minY = minY;
        this.maxY = minY + height;
        this.height = height;
        this.dimensionType = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE)
            .getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        this.seed = seed;
        this.random = RandomSource.create(seed);
    }

    public void addChunk(ProtoChunk chunk)
    {
        chunks.put(chunk.getPos().toLong(), chunk);
    }

    public ProtoChunk getOrCreateChunk(ChunkPos pos)
    {
        return chunks.computeIfAbsent(pos.toLong(), k -> {
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            return new ProtoChunk(pos, UpgradeData.EMPTY, this, biomeRegistry, null);
        });
    }

    public void clear()
    {
        chunks.clear();
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull)
    {
        ChunkPos pos = new ChunkPos(x, z);
        ProtoChunk chunk = chunks.get(pos.toLong());
        if (chunk != null)
        {
            return chunk;
        }
        if (nonnull)
        {
            return getOrCreateChunk(pos);
        }
        return null;
    }

    @Override
    public int getHeight(Heightmap.Types heightmapType, int x, int z)
    {
        ChunkAccess chunk = getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        if (chunk != null)
        {
            return chunk.getHeight(heightmapType, x & 15, z & 15);
        }
        return SEA_LEVEL;
    }

    @Override
    public int getSkyDarken()
    {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager()
    {
        throw new UnsupportedOperationException("FeatureProbeLevel does not have a BiomeManager");
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z)
    {
        return registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
    }

    @Override
    public boolean isClientSide()
    {
        return false;
    }

    @Override
    public int getSeaLevel()
    {
        return SEA_LEVEL;
    }

    @Override
    public DimensionType dimensionType()
    {
        return dimensionType;
    }

    @Override
    public int getMinBuildHeight()
    {
        return minY;
    }

    @Override
    public int getHeight()
    {
        return height;
    }

    @Override
    public RegistryAccess registryAccess()
    {
        return registryAccess;
    }

    @Override
    public FeatureFlagSet enabledFeatures()
    {
        return FeatureFlagSet.of();
    }

    @Override
    public long getSeed()
    {
        return seed;
    }

    @Override
    public ServerLevel getLevel()
    {
        throw new UnsupportedOperationException("FeatureProbeLevel does not have a backing ServerLevel");
    }

    @Override
    public long nextSubTickCount()
    {
        return 0;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks()
    {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks()
    {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelData getLevelData()
    {
        throw new UnsupportedOperationException("FeatureProbeLevel does not have LevelData");
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos)
    {
        return new DifficultyInstance(Difficulty.NORMAL, 0, 0, 0);
    }

    @Override
    public @Nullable MinecraftServer getServer()
    {
        return null;
    }

    @Override
    public ChunkSource getChunkSource()
    {
        throw new UnsupportedOperationException("FeatureProbeLevel does not have a ChunkSource");
    }

    @Override
    public RandomSource getRandom()
    {
        return random;
    }

    @Override
    public void playSound(@Nullable Player player, BlockPos pos, SoundEvent sound,
                          SoundSource source, float volume, float pitch)
    {
        // No-op
    }

    @Override
    public void addParticle(ParticleOptions particleData, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed)
    {
        // No-op
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data)
    {
        // No-op
    }

    @Override
    public void gameEvent(Holder<GameEvent> holder, Vec3 vec3,
                          GameEvent.Context context)
    {
        // No-op
    }

    @Override
    public float getShade(Direction direction, boolean shade)
    {
        return 1.0f;
    }

    @Override
    public LevelLightEngine getLightEngine()
    {
        throw new UnsupportedOperationException("FeatureProbeLevel does not have a LightEngine");
    }

    @Override
    public WorldBorder getWorldBorder()
    {
        return INFINITE_BORDER;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos)
    {
        ChunkAccess chunk = getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk != null ? chunk.getBlockEntity(pos) : null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        if (pos.getY() < minY || pos.getY() >= maxY)
        {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        ChunkAccess chunk = getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk != null)
        {
            return chunk.getBlockState(pos);
        }
        return pos.getY() < SEA_LEVEL ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB area,
                                    Predicate<? super Entity> predicate)
    {
        return Collections.emptyList();
    }

    @Override
    public <T extends Entity> @NotNull List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest,
                                                           AABB bounds,
                                                           Predicate<? super T> predicate)
    {
        return Collections.emptyList();
    }

    @Override
    public List<? extends Player> players()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state)
    {
        return state.test(getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate)
    {
        return predicate.test(getFluidState(pos));
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft)
    {
        if (pos.getY() < minY || pos.getY() >= maxY)
        {
            return false;
        }
        ChunkAccess chunk = getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
        if (chunk != null)
        {
            chunk.setBlockState(pos, state, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving)
    {
        return setBlock(pos, Blocks.AIR.defaultBlockState(), 3, 512);
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft)
    {
        return setBlock(pos, Blocks.AIR.defaultBlockState(), 3, recursionLeft);
    }
}
