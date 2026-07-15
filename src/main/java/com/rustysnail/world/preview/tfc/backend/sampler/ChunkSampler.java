package com.rustysnail.world.preview.tfc.backend.sampler;

import java.util.List;
import com.rustysnail.world.preview.tfc.backend.worker.WorkResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public interface ChunkSampler
{
    List<BlockPos> blocksForChunk(ChunkPos chunkPos, int blocksPerChunk);

    void expandRaw(BlockPos pos, short value, WorkResult result);

    int blockStride();
}
