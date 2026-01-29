package caeruleustait.world.preview.backend;

import caeruleustait.world.preview.backend.worker.SampleUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Background task that searches for a specific biome by expanding outward
 * from a center position in square rings. Supports cancellation and progress reporting.
 */
public class BiomeSearchTask implements Runnable {

    public interface Callback {
        void onProgress(int currentDistance, int maxDistance);
        void onFound(BlockPos pos);
        void onNotFound();
        void onCancelled();
        void onError(Throwable t);
    }

    private static final int RING_STEP = 2000;
    private static final int MAX_DISTANCE = 50_000;
    private static final int SAMPLE_SPACING = 16;
    private static final int COARSE_SPACING = 32;
    private static final int COARSE_THRESHOLD = 20_000;

    private final SampleUtils sampleUtils;
    private final ResourceKey<Biome> targetBiome;
    private final BlockPos searchCenter;
    private final Callback callback;
    private volatile boolean cancelled = false;

    public BiomeSearchTask(
            SampleUtils sampleUtils,
            ResourceKey<Biome> targetBiome,
            BlockPos searchCenter,
            Callback callback
    ) {
        this.sampleUtils = sampleUtils;
        this.targetBiome = targetBiome;
        this.searchCenter = searchCenter;
        this.callback = callback;
    }

    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void run() {
        try {
            int cx = this.searchCenter.getX();
            int cz = this.searchCenter.getZ();

            // Check center point first
            if (checkPoint(cx, cz)) {
                this.callback.onFound(new BlockPos(cx, 64, cz));
                return;
            }

            int prevRadius = 0;
            for (int dist = RING_STEP; dist <= MAX_DISTANCE; dist += RING_STEP) {
                if (this.cancelled) {
                    this.callback.onCancelled();
                    return;
                }
                this.callback.onProgress(dist, MAX_DISTANCE);

                BlockPos result = searchRingBand(cx, cz, prevRadius, dist);
                if (result != null) {
                    this.callback.onFound(result);
                    return;
                }
                prevRadius = dist;
            }

            this.callback.onNotFound();
        } catch (Throwable t) {
            this.callback.onError(t);
        }
    }

    private BlockPos searchRingBand(int cx, int cz, int innerRadius, int outerRadius) {
        int spacing = outerRadius > COARSE_THRESHOLD ? COARSE_SPACING : SAMPLE_SPACING;
        int minX = cx - outerRadius;
        int maxX = cx + outerRadius;
        int minZ = cz - outerRadius;
        int maxZ = cz + outerRadius;

        for (int x = minX; x <= maxX; x += spacing) {
            for (int z = minZ; z <= maxZ; z += spacing) {
                if (this.cancelled) return null;

                int dx = Math.abs(x - cx);
                int dz = Math.abs(z - cz);
                int chebyshev = Math.max(dx, dz);

                // Only sample points in the current ring band (skip already-searched interior)
                if (chebyshev <= innerRadius) continue;

                if (checkPoint(x, z)) {
                    return new BlockPos(x, 64, z);
                }
            }
        }
        return null;
    }

    private boolean checkPoint(int x, int z) {
        ResourceKey<Biome> found = this.sampleUtils.getBiomeKey(new BlockPos(x, 64, z));
        return found.equals(this.targetBiome);
    }
}
