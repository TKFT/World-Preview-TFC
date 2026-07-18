package com.rustysnail.world.preview.tfc.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BiomeSearchTaskTest
{
    @Test
    void continuesToLaterRingsWithoutReportingNullAsFound()
    {
        List<Integer> progress = new ArrayList<>();
        AtomicInteger foundCalls = new AtomicInteger();
        AtomicReference<BlockPos> found = new AtomicReference<>();

        BiomeSearchTask task = new BiomeSearchTask(
            (x, z) -> x == -4000 && z == -4000,
            BlockPos.ZERO,
            new BiomeSearchTask.Callback()
            {
                @Override
                public void onProgress(int currentDistance, int maxDistance)
                {
                    progress.add(currentDistance);
                }

                @Override
                public void onFound(BlockPos pos)
                {
                    foundCalls.incrementAndGet();
                    found.set(pos);
                }

                @Override
                public void onNotFound()
                {
                }

                @Override
                public void onCancelled()
                {
                }

                @Override
                public void onError(Throwable t)
                {
                    throw new AssertionError(t);
                }
            }
        );

        task.run();

        assertEquals(List.of(2000, 4000), progress);
        assertEquals(1, foundCalls.get());
        assertNotNull(found.get());
        assertEquals(new BlockPos(-4000, 64, -4000), found.get());
    }
}
