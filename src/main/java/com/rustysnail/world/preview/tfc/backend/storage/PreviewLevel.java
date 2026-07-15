package com.rustysnail.world.preview.tfc.backend.storage;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

public class PreviewLevel implements WorldGenLevel
{
    private final RegistryAccess registryAccess;
    private final LevelHeightAccessor levelHeightAccessor;
    private final Registry<Biome> biomeRegistry;

    public PreviewLevel(RegistryAccess registryAccess, LevelHeightAccessor levelHeightAccessor)
    {
        this.registryAccess = registryAccess;
        this.levelHeightAccessor = levelHeightAccessor;
        this.biomeRegistry = this.registryAccess.registryOrThrow(Registries.BIOME);
    }

    @Nullable
    public ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull)
    {
        return new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, this.levelHeightAccessor, this.biomeRegistry, null);
    }

    public int getHeight(Types heightmapType, int x, int z)
    {
        throw new NotImplementedException("Not implemented");
    }

    public int getSkyDarken()
    {
        throw new NotImplementedException("Not implemented");
    }

    public BiomeManager getBiomeManager()
    {
        throw new NotImplementedException("Not implemented");
    }

    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z)
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean isClientSide()
    {
        throw new NotImplementedException("Not implemented");
    }

    public int getSeaLevel()
    {
        throw new NotImplementedException("Not implemented");
    }

    public DimensionType dimensionType()
    {
        throw new NotImplementedException("Not implemented");
    }

    public RegistryAccess registryAccess()
    {
        return this.registryAccess;
    }

    public FeatureFlagSet enabledFeatures()
    {
        throw new NotImplementedException("Not implemented");
    }

    public long getSeed()
    {
        throw new NotImplementedException("Not implemented");
    }

    public ServerLevel getLevel()
    {
        throw new NotImplementedException("Not implemented");
    }

    public long nextSubTickCount()
    {
        throw new NotImplementedException("Not implemented");
    }

    public LevelTickAccess<Block> getBlockTicks()
    {
        throw new NotImplementedException("Not implemented");
    }

    public LevelTickAccess<Fluid> getFluidTicks()
    {
        throw new NotImplementedException("Not implemented");
    }

    public LevelData getLevelData()
    {
        throw new NotImplementedException("Not implemented");
    }

    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos)
    {
        throw new NotImplementedException("Not implemented");
    }

    @Nullable
    public MinecraftServer getServer()
    {
        throw new NotImplementedException("Not implemented");
    }

    public ChunkSource getChunkSource()
    {
        throw new NotImplementedException("Not implemented");
    }

    public RandomSource getRandom()
    {
        throw new NotImplementedException("Not implemented");
    }

    public void playSound(@Nullable Player player, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch)
    {
        throw new NotImplementedException("Not implemented");
    }

    public void addParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed)
    {
        throw new NotImplementedException("Not implemented");
    }

    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data)
    {
        throw new NotImplementedException("Not implemented");
    }

    public void gameEvent(Holder<GameEvent> holder, Vec3 vec3, Context context)
    {
        throw new NotImplementedException("Not implemented");
    }

    public float getShade(Direction direction, boolean shade)
    {
        throw new NotImplementedException("Not implemented");
    }

    public LevelLightEngine getLightEngine()
    {
        throw new NotImplementedException("Not implemented");
    }

    public WorldBorder getWorldBorder()
    {
        throw new NotImplementedException("Not implemented");
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        throw new NotImplementedException("Not implemented");
    }

    public BlockState getBlockState(BlockPos pos)
    {
        throw new NotImplementedException("Not implemented");
    }

    public FluidState getFluidState(BlockPos pos)
    {
        throw new NotImplementedException("Not implemented");
    }

    public List<Entity> getEntities(@Nullable Entity entity, AABB area, Predicate<? super Entity> predicate)
    {
        throw new NotImplementedException("Not implemented");
    }

    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate)
    {
        throw new NotImplementedException("Not implemented");
    }

    public List<? extends Player> players()
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state)
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate)
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft)
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean removeBlock(BlockPos pos, boolean isMoving)
    {
        throw new NotImplementedException("Not implemented");
    }

    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft)
    {
        throw new NotImplementedException("Not implemented");
    }
}
