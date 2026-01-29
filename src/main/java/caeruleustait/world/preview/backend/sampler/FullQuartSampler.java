package caeruleustait.world.preview.backend.sampler;

import caeruleustait.world.preview.backend.worker.WorkResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public class FullQuartSampler implements ChunkSampler {
   @Override
   public List<BlockPos> blocksForChunk(ChunkPos chunkPos, int y) {
      List<BlockPos> res = new ArrayList<>(16);
      int xMin = SectionPos.sectionToBlockCoord(chunkPos.x, 0);
      int zMin = SectionPos.sectionToBlockCoord(chunkPos.z, 0);

      for (int x = 0; x < 16; x += 4) {
         for (int z = 0; z < 16; z += 4) {
            res.add(new BlockPos(xMin + x, y, zMin + z));
         }
      }

      return res;
   }

   @Override
   public void expandRaw(BlockPos pos, short raw, WorkResult result) {
      int quartX = QuartPos.fromBlock(pos.getX());
      int quartZ = QuartPos.fromBlock(pos.getZ());
      result.results().add(new WorkResult.BlockResult(quartX, quartZ, raw));
   }

   @Override
   public int blockStride() {
      return 4;
   }
}
