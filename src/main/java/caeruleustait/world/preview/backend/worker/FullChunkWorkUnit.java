package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;

public class FullChunkWorkUnit extends WorkUnit {
   private final ChunkSampler sampler;
   private final int yMin;
   private final int yMax;
   private final int yStride;

   public FullChunkWorkUnit(ChunkSampler sampler, ChunkPos pos, SampleUtils sampleUtils, PreviewData previewData, int yMin, int yMax, int yStride) {
      super(sampleUtils, pos, previewData, 0);
      this.sampler = sampler;
      this.yMin = yMin;
      this.yMax = yMax;
      this.yStride = yStride;
   }

   @Override
   protected List<WorkResult> doWork() {
      return this.sampleUtils.hasRawNoiseInfo() ? this.doRawNoiseWork() : this.doNormalWork();
   }

   private List<WorkResult> doRawNoiseWork() {
      List<WorkResult> results = new ArrayList<>((this.yMax - this.yMin) / this.yStride);

      for (int y = this.yMin; y <= this.yMax; y += this.yStride) {
         WorkResult res = new WorkResult(
            this,
            QuartPos.fromBlock(y),
            y == this.y ? this.primarySection : this.storage.section4(this.chunkPos, y, this.flags()),
            new ArrayList<>(16),
            List.of()
         );
         WorkResult temperature = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 9L), new ArrayList<>(16), List.of());
         WorkResult humidity = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 10L), new ArrayList<>(16), List.of());
         WorkResult continentalness = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 11L), new ArrayList<>(16), List.of());
         WorkResult erosion = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 12L), new ArrayList<>(16), List.of());
         WorkResult depth = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 13L), new ArrayList<>(16), List.of());
         WorkResult weirdness = new WorkResult(this, QuartPos.fromBlock(y), this.storage.section4(this.chunkPos, y, 14L), new ArrayList<>(16), List.of());

         for (BlockPos p : this.sampler.blocksForChunk(this.chunkPos, y)) {
            SampleUtils.BiomeResult sample = this.sampleUtils.doSample(p);
            this.sampler.expandRaw(p, this.biomeIdFrom(sample.biome()), res);
            if (sample.noiseResult() != null) {
               this.sampler.expandRaw(p, sample.noiseResult()[0], temperature);
               this.sampler.expandRaw(p, sample.noiseResult()[1], humidity);
               this.sampler.expandRaw(p, sample.noiseResult()[2], continentalness);
               this.sampler.expandRaw(p, sample.noiseResult()[3], erosion);
               this.sampler.expandRaw(p, sample.noiseResult()[4], depth);
               this.sampler.expandRaw(p, sample.noiseResult()[5], weirdness);
            }
         }

         results.add(res);
         results.add(temperature);
         results.add(humidity);
         results.add(continentalness);
         results.add(erosion);
         results.add(depth);
         results.add(weirdness);
      }

      return results;
   }

   private List<WorkResult> doNormalWork() {
      List<WorkResult> results = new ArrayList<>((this.yMax - this.yMin) / this.yStride * 7);

      for (int y = this.yMin; y <= this.yMax; y += this.yStride) {
         WorkResult res = new WorkResult(
            this,
            QuartPos.fromBlock(y),
            y == this.y ? this.primarySection : this.storage.section4(this.chunkPos, y, this.flags()),
            new ArrayList<>(16),
            List.of()
         );

         for (BlockPos p : this.sampler.blocksForChunk(this.chunkPos, y)) {
            SampleUtils.BiomeResult sample = this.sampleUtils.doSample(p);
            this.sampler.expandRaw(p, this.biomeIdFrom(sample.biome()), res);
         }

         results.add(res);
      }

      return results;
   }

   @Override
   public long flags() {
      return 0L;
   }
}
