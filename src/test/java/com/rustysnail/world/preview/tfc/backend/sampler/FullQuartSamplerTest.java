package com.rustysnail.world.preview.tfc.backend.sampler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FullQuartSamplerTest
{
    private static void assertChunk(List<BlockPos> positions, int minX, int minZ)
    {
        assertEquals(16, positions.size());
        Set<Long> actual = new HashSet<>();
        for (BlockPos pos : positions)
        {
            actual.add((((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL));
        }
        for (int x = 0; x < 16; x += 4)
        {
            for (int z = 0; z < 16; z += 4)
            {
                long expected = (((long) minX + x) << 32) ^ ((minZ + z) & 0xffffffffL);
                assertTrue(actual.contains(expected), "missing quart at " + (minX + x) + "," + (minZ + z));
            }
        }
    }

    @Test
    void samplesEveryQuartExactlyOnceAcrossPositiveAndNegativeChunks()
    {
        FullQuartSampler sampler = new FullQuartSampler();
        assertChunk(sampler.blocksForChunk(new ChunkPos(0, 0), 0), 0, 0);
        assertChunk(sampler.blocksForChunk(new ChunkPos(-1, -1), 0), -16, -16);
        assertEquals(4, sampler.blockStride());
    }
}
