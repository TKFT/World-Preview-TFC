package caeruleustait.world.preview.backend.sampler;

import caeruleustait.world.preview.backend.worker.WorkResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public interface ChunkSampler {
   List<BlockPos> blocksForChunk(ChunkPos var1, int var2);

   void expandRaw(BlockPos var1, short var2, WorkResult var3);

   int blockStride();
}
