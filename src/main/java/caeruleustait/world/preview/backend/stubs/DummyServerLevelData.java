package caeruleustait.world.preview.backend.stubs;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder.Settings;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyServerLevelData implements ServerLevelData {
   public @NotNull String getLevelName() {
      return "dummy";
   }

   public void setThundering(boolean thundering) {
   }

   public int getRainTime() {
      return 0;
   }

   public void setRainTime(int time) {
   }

   public void setThunderTime(int time) {
   }

   public int getThunderTime() {
      return 0;
   }

   public int getClearWeatherTime() {
      return 0;
   }

   public void setClearWeatherTime(int time) {
   }

   public int getWanderingTraderSpawnDelay() {
      return 0;
   }

   public void setWanderingTraderSpawnDelay(int delay) {
   }

   public int getWanderingTraderSpawnChance() {
      return 0;
   }

   public void setWanderingTraderSpawnChance(int chance) {
   }

   @Nullable
   public UUID getWanderingTraderId() {
      return UUID.randomUUID();
   }

   public void setWanderingTraderId(@NotNull UUID id) {
   }

   public @NotNull GameType getGameType() {
      return GameType.SPECTATOR;
   }

   public void setWorldBorder(@NotNull Settings serializer) {
   }

   @Nullable
   public Settings getWorldBorder() {
      return null;
   }

   public boolean isInitialized() {
      return false;
   }

   public void setInitialized(boolean initialized) {
   }

   public boolean isAllowCommands() {
      return false;
   }

   public void setGameType(@NotNull GameType type) {
   }

   @Nullable
   public TimerQueue<MinecraftServer> getScheduledEvents() {
      return null;
   }

   public void setGameTime(long time) {
   }

   public void setDayTime(long time) {
   }

   public float getDayTimeFraction() {
      return 0.0F;
   }

   public float getDayTimePerTick() {
      return 0.0F;
   }

   public void setDayTimeFraction(float v) {
   }

   public void setDayTimePerTick(float v) {
   }

   public @NotNull BlockPos getSpawnPos() {
      return BlockPos.ZERO;
   }

   public float getSpawnAngle() {
      return 0.0F;
   }

   public long getGameTime() {
      return 0L;
   }

   public long getDayTime() {
      return 0L;
   }

   public boolean isThundering() {
      return false;
   }

   public boolean isRaining() {
      return false;
   }

   public void setRaining(boolean raining) {
   }

   public boolean isHardcore() {
      return false;
   }

   public @NotNull GameRules getGameRules() {
      return new GameRules();
   }

   public @NotNull Difficulty getDifficulty() {
      return Difficulty.HARD;
   }

   public boolean isDifficultyLocked() {
      return false;
   }

   public void setSpawn(@NotNull BlockPos spawnPoint, float spawnAngle) {
   }
}
