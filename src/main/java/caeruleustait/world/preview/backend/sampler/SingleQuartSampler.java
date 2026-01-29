package caeruleustait.world.preview.backend.sampler;

import caeruleustait.world.preview.backend.worker.WorkResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public class SingleQuartSampler implements ChunkSampler {
   @Override
   public List<BlockPos> blocksForChunk(ChunkPos chunkPos, int y) {
      int xMin = SectionPos.sectionToBlockCoord(chunkPos.x, 0);
      int zMin = SectionPos.sectionToBlockCoord(chunkPos.z, 0);
      return List.of(new BlockPos(xMin, y, zMin));
   }

   @Override
   public void expandRaw(BlockPos pos, short raw, WorkResult result) {
      int quartX = QuartPos.fromBlock(pos.getX());
      int quartZ = QuartPos.fromBlock(pos.getZ());

      for (int x = 0; x < 4; x++) {
         for (int z = 0; z < 4; z++) {
            result.results().add(new WorkResult.BlockResult(quartX + x, quartZ + z, raw));
         }
      }
   }

   @Override
   public int blockStride() {
      return 16;
   }
}
