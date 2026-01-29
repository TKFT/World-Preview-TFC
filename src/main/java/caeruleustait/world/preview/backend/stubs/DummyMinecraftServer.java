package caeruleustait.world.preview.backend.stubs;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.net.Proxy;
import java.util.UUID;
import net.minecraft.SystemReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jetbrains.annotations.NotNull;

public class DummyMinecraftServer extends MinecraftServer {
   public DummyMinecraftServer(
      Thread thread,
      LevelStorageAccess levelStorageAccess,
      PackRepository packRepository,
      WorldStem worldStem,
      Proxy proxy,
      DataFixer dataFixer,
      Services services,
      ChunkProgressListenerFactory chunkProgressListenerFactory
   ) {
      super(thread, levelStorageAccess, packRepository, worldStem, proxy, dataFixer, services, chunkProgressListenerFactory);
      this.setSingleplayerProfile(new GameProfile(UUID.randomUUID(), "world-preview"));
      this.setDemo(false);
      this.setPlayerList(new DummyPlayerList(this, this.registries(), this.playerDataStorage, 1));
   }

   protected boolean initServer() throws IOException {
      return false;
   }

   public int getOperatorUserPermissionLevel() {
      return 0;
   }

   public int getFunctionCompilationLevel() {
      return 0;
   }

   public boolean shouldRconBroadcast() {
      return false;
   }

   protected @NotNull SampleLogger getTickTimeLogger() {
      return new SampleLogger() {
         public void logFullSample(long[] ls) {
         }

         public void logSample(long l) {
         }

         public void logPartialSample(long l, int i) {
         }
      };
   }

   public boolean isTickTimeLoggingEnabled() {
      return false;
   }

   @NotNull
   public SystemReport fillServerSystemReport(@NotNull SystemReport report) {
      return report;
   }

   public boolean isDedicatedServer() {
      return false;
   }

   public int getRateLimitPacketsPerSecond() {
      return 0;
   }

   public boolean isEpollEnabled() {
      return false;
   }

   public boolean isCommandBlockEnabled() {
      return false;
   }

   public boolean isPublished() {
      return false;
   }

   public boolean shouldInformAdmins() {
      return false;
   }

   public boolean isSingleplayerOwner(@NotNull GameProfile profile) {
      return false;
   }
}
