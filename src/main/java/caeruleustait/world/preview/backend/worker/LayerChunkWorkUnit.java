package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;

public class LayerChunkWorkUnit extends WorkUnit {
   private final ChunkSampler sampler;

   public LayerChunkWorkUnit(ChunkSampler sampler, ChunkPos pos, SampleUtils sampleUtils, PreviewData previewData, int y) {
      super(sampleUtils, pos, previewData, y);
      this.sampler = sampler;
   }

   @Override
   protected List<WorkResult> doWork() {
      return this.sampleUtils.hasRawNoiseInfo() ? this.doRawNoiseWork() : this.doNormalWork();
   }

   private List<WorkResult> doNormalWork() {
      WorkResult res = new WorkResult(this, QuartPos.fromBlock(this.y), this.primarySection, new ArrayList<>(16), List.of());

      for (BlockPos p : this.sampler.blocksForChunk(this.chunkPos, this.y)) {
         SampleUtils.BiomeResult sample = this.sampleUtils.doSample(p);
         this.sampler.expandRaw(p, this.biomeIdFrom(sample.biome()), res);
      }

      return List.of(res);
   }

   private List<WorkResult> doRawNoiseWork() {
      WorkResult res = new WorkResult(this, QuartPos.fromBlock(this.y), this.primarySection, new ArrayList<>(16), List.of());
      WorkResult temperature = new WorkResult(
         this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 9L), new ArrayList<>(16), List.of()
      );
      WorkResult humidity = new WorkResult(this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 10L), new ArrayList<>(16), List.of());
      WorkResult continentalness = new WorkResult(
         this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 11L), new ArrayList<>(16), List.of()
      );
      WorkResult erosion = new WorkResult(this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 12L), new ArrayList<>(16), List.of());
      WorkResult depth = new WorkResult(this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 13L), new ArrayList<>(16), List.of());
      WorkResult weirdness = new WorkResult(this, QuartPos.fromBlock(this.y), this.storage.section4(this.chunkPos, this.y, 14L), new ArrayList<>(16), List.of());

      for (BlockPos p : this.sampler.blocksForChunk(this.chunkPos, this.y)) {
         SampleUtils.BiomeResult sample = this.sampleUtils.doSample(p);
         this.sampler.expandRaw(p, this.biomeIdFrom(sample.biome()), res);
         this.sampler.expandRaw(p, sample.noiseResult()[0], temperature);
         this.sampler.expandRaw(p, sample.noiseResult()[1], humidity);
         this.sampler.expandRaw(p, sample.noiseResult()[2], continentalness);
         this.sampler.expandRaw(p, sample.noiseResult()[3], erosion);
         this.sampler.expandRaw(p, sample.noiseResult()[4], depth);
         this.sampler.expandRaw(p, sample.noiseResult()[5], weirdness);
      }

      return List.of(res, temperature, humidity, continentalness, erosion, depth, weirdness);
   }

   @Override
   public long flags() {
      return 0L;
   }
}
