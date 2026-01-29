package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;

public class SlowHeightmapWorkUnit extends WorkUnit {
   private final ChunkSampler sampler;

   public SlowHeightmapWorkUnit(ChunkSampler sampler, SampleUtils sampleUtils, ChunkPos chunkPos, PreviewData previewData) {
      super(sampleUtils, chunkPos, previewData, 0);
      this.sampler = sampler;
   }

   @Override
   protected List<WorkResult> doWork() {
      WorkResult res = new WorkResult(this, QuartPos.fromBlock(0), this.primarySection, new ArrayList<>(16), List.of());

      for (BlockPos p : this.sampler.blocksForChunk(this.chunkPos, this.y)) {
         this.sampler.expandRaw(p, this.sampleUtils.doHeightSlow(p), res);
      }

      return List.of(res);
   }

   @Override
   public long flags() {
      return 2L;
   }
}
