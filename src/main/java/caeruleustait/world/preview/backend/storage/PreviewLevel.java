package caeruleustait.world.preview.backend.storage;

//import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
//import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
//import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewLevel implements WorldGenLevel {
   private final RegistryAccess registryAccess;
   private final LevelHeightAccessor levelHeightAccessor;
   private final Registry<Biome> biomeRegistry;
   //private final Long2ObjectMap<ProtoChunk> chunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap());

   public PreviewLevel(RegistryAccess registryAccess, LevelHeightAccessor levelHeightAccessor) {
      this.registryAccess = registryAccess;
      this.levelHeightAccessor = levelHeightAccessor;
      this.biomeRegistry = this.registryAccess.registryOrThrow(Registries.BIOME);
   }

   @Nullable
   public ChunkAccess getChunk(int x, int z, @NotNull ChunkStatus requiredStatus, boolean nonnull) {
      return new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, this.levelHeightAccessor, this.biomeRegistry, null);
   }

   public @NotNull RegistryAccess registryAccess() {
      return this.registryAccess;
   }

   public long getSeed() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull ServerLevel getLevel() {
      throw new NotImplementedException("Not implemented");
   }

   public long nextSubTickCount() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull LevelTickAccess<Block> getBlockTicks() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull LevelTickAccess<Fluid> getFluidTicks() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull LevelData getLevelData() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull DifficultyInstance getCurrentDifficultyAt(@NotNull BlockPos pos) {
      throw new NotImplementedException("Not implemented");
   }

   @Nullable
   public MinecraftServer getServer() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull ChunkSource getChunkSource() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull RandomSource getRandom() {
      throw new NotImplementedException("Not implemented");
   }

   public void playSound(@Nullable Player player, @NotNull BlockPos pos, @NotNull SoundEvent sound, @NotNull SoundSource source, float volume, float pitch) {
      throw new NotImplementedException("Not implemented");
   }

   public void addParticle(@NotNull ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
      throw new NotImplementedException("Not implemented");
   }

   public void levelEvent(@Nullable Player player, int type, @NotNull BlockPos pos, int data) {
      throw new NotImplementedException("Not implemented");
   }

   public void gameEvent(@NotNull Holder<GameEvent> holder, @NotNull Vec3 vec3, @NotNull Context context) {
      throw new NotImplementedException("Not implemented");
   }

   public float getShade(@NotNull Direction direction, boolean shade) {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull LevelLightEngine getLightEngine() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull WorldBorder getWorldBorder() {
      throw new NotImplementedException("Not implemented");
   }

   @Nullable
   public BlockEntity getBlockEntity(@NotNull BlockPos pos) {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull FluidState getFluidState(@NotNull BlockPos pos) {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull List<Entity> getEntities(@Nullable Entity entity, @NotNull AABB area, @NotNull Predicate<? super Entity> predicate) {
      throw new NotImplementedException("Not implemented");
   }

   public <T extends Entity> @NotNull List<T> getEntities(@NotNull EntityTypeTest<Entity, T> entityTypeTest, @NotNull AABB bounds, @NotNull Predicate<? super T> predicate) {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull List<? extends Player> players() {
      throw new NotImplementedException("Not implemented");
   }

   public int getHeight(@NotNull Types heightmapType, int x, int z) {
      throw new NotImplementedException("Not implemented");
   }

   public int getSkyDarken() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull BiomeManager getBiomeManager() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
      throw new NotImplementedException("Not implemented");
   }

   public boolean isClientSide() {
      throw new NotImplementedException("Not implemented");
   }

   public int getSeaLevel() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull DimensionType dimensionType() {
      throw new NotImplementedException("Not implemented");
   }

   public @NotNull FeatureFlagSet enabledFeatures() {
      throw new NotImplementedException("Not implemented");
   }

   public boolean isStateAtPosition(@NotNull BlockPos pos, @NotNull Predicate<BlockState> state) {
      throw new NotImplementedException("Not implemented");
   }

   public boolean isFluidAtPosition(@NotNull BlockPos pos, @NotNull Predicate<FluidState> predicate) {
      throw new NotImplementedException("Not implemented");
   }

   public boolean setBlock(@NotNull BlockPos pos, @NotNull BlockState state, int flags, int recursionLeft) {
      throw new NotImplementedException("Not implemented");
   }

   public boolean removeBlock(@NotNull BlockPos pos, boolean isMoving) {
      throw new NotImplementedException("Not implemented");
   }

   public boolean destroyBlock(@NotNull BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
      throw new NotImplementedException("Not implemented");
   }
}
