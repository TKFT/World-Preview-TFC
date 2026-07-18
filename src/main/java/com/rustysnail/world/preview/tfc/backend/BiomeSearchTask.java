package com.rustysnail.world.preview.tfc.backend;

import java.util.function.BiPredicate;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

public class BiomeSearchTask implements Runnable
{

    private static final int RING_STEP = 2000;
    private static final int MAX_DISTANCE = 100_000;
    private static final int SAMPLE_SPACING = 16;
    private static final int COARSE_SPACING = 32;
    private static final int COARSE_THRESHOLD = 20_000;
    private final BiPredicate<Integer, Integer> pointMatcher;
    private final BlockPos searchCenter;
    private final Callback callback;
    private volatile boolean cancelled = false;

    public BiomeSearchTask(
        SampleUtils sampleUtils,
        @Nullable TFCSampleUtils tfcSampleUtils,
        ResourceKey<Biome> targetBiome,
        BlockPos searchCenter,
        boolean requireIsland,
        Callback callback
    )
    {
        this(
            (x, z) -> {
                ResourceKey<Biome> found = sampleUtils.getBiomeKey(new BlockPos(x, 64, z));
                if (!found.equals(targetBiome))
                {
                    return false;
                }
                return !requireIsland || tfcSampleUtils == null || tfcSampleUtils.samplePoint(x, z).island();
            },
            searchCenter,
            callback
        );
    }

    BiomeSearchTask(BiPredicate<Integer, Integer> pointMatcher, BlockPos searchCenter, Callback callback)
    {
        this.pointMatcher = pointMatcher;
        this.searchCenter = searchCenter;
        this.callback = callback;
    }

    public void cancel()
    {
        this.cancelled = true;
    }

    @Override
    public void run()
    {
        try
        {
            int cx = this.searchCenter.getX();
            int cz = this.searchCenter.getZ();

            if (checkPoint(cx, cz))
            {
                this.callback.onFound(new BlockPos(cx, 64, cz));
                return;
            }

            int prevRadius = 0;
            for (int dist = RING_STEP; dist <= MAX_DISTANCE; dist += RING_STEP)
            {
                if (this.cancelled)
                {
                    this.callback.onCancelled();
                    return;
                }
                this.callback.onProgress(dist, MAX_DISTANCE);

                BlockPos result = searchRingBand(cx, cz, prevRadius, dist);
                if (result != null)
                {
                    this.callback.onFound(result);
                    return;
                }
                prevRadius = dist;
            }

            this.callback.onNotFound();
        }
        catch (Throwable t)
        {
            this.callback.onError(t);
        }
    }

    private @Nullable BlockPos searchRingBand(int cx, int cz, int innerRadius, int outerRadius)
    {
        int spacing = outerRadius > COARSE_THRESHOLD ? COARSE_SPACING : SAMPLE_SPACING;
        int minX = cx - outerRadius;
        int maxX = cx + outerRadius;
        int minZ = cz - outerRadius;
        int maxZ = cz + outerRadius;

        for (int x = minX; x <= maxX; x += spacing)
        {
            for (int z = minZ; z <= maxZ; z += spacing)
            {
                if (this.cancelled) return null;

                int dx = Math.abs(x - cx);
                int dz = Math.abs(z - cz);
                int chebyshev = Math.max(dx, dz);

                if (chebyshev <= innerRadius) continue;

                if (checkPoint(x, z))
                {
                    return new BlockPos(x, 64, z);
                }
            }
        }
        return null;
    }

    private boolean checkPoint(int x, int z)
    {
        return this.pointMatcher.test(x, z);
    }

    public interface Callback
    {
        void onProgress(int currentDistance, int maxDistance);

        void onFound(BlockPos pos);

        void onNotFound();

        void onCancelled();

        void onError(Throwable t);
    }
}
