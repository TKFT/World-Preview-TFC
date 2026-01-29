package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.WorkManager;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.storage.PreviewSection;
import caeruleustait.world.preview.backend.storage.PreviewStorage;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

public abstract class WorkUnit {
   protected final WorkManager workManager = WorldPreview.get().workManager();
   protected final SampleUtils sampleUtils;
   protected final PreviewStorage storage;
   protected final PreviewSection primarySection;
   protected final ChunkPos chunkPos;
   protected final PreviewData previewData;
   protected final int y;
   private boolean isCanceled;

   protected WorkUnit(SampleUtils sampleUtils, ChunkPos chunkPos, PreviewData previewData, int y) {
      this.sampleUtils = sampleUtils;
      this.storage = this.workManager.previewStorage();
      this.primarySection = this.storage.section4(chunkPos, y, this.flags());
      this.chunkPos = chunkPos;
      this.previewData = previewData;
      this.y = y;
   }

   public short biomeIdFrom(ResourceKey<Biome> resourceKey) {
      return this.previewData.biome2Id().getShort(resourceKey.location().toString());
   }

   public short biomeIdFrom(ResourceLocation location) {
      return this.previewData.biome2Id().getShort(location.toString());
   }

   protected abstract List<WorkResult> doWork();

   public abstract long flags();

   public boolean isCompleted() {
      return this.primarySection.isCompleted(this.chunkPos);
   }

   public void markCompleted() {
      this.primarySection.markCompleted(this.chunkPos);
   }

   public List<WorkResult> work() {
      try {
         return this.doWork();
      } catch (Throwable var2) {
         WorldPreview.LOGGER.error("Error in work unit for chunk {}", this.chunkPos, var2);
         throw var2;
      }
   }

   public ChunkPos chunk() {
      return this.chunkPos;
   }

   public int y() {
      return this.y;
   }

   public void cancel() {
      this.isCanceled = true;
   }

   public boolean isCanceled() {
      return this.isCanceled;
   }
}
