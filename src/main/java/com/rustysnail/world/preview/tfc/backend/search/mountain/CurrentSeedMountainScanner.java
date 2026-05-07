package com.rustysnail.world.preview.tfc.backend.search.mountain;

import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CurrentSeedMountainScanner
{
    public interface Callback
    {
        void onProgress(long samplesChecked, int bestHeight, int bestX, int bestZ, String phase);
        void onComplete(MountainSearchResult result);
        void onCancelled();
        void onError(Throwable t);
    }

    private final long seed;
    private final SampleUtils sampleUtils;
    private final MountainSearchConfig config;
    private final Callback callback;
    private volatile boolean cancelled = false;

    public CurrentSeedMountainScanner(long seed, SampleUtils sampleUtils, MountainSearchConfig config, Callback callback)
    {
        this.seed = seed;
        this.sampleUtils = sampleUtils;
        this.config = config;
        this.callback = callback;
    }

    public void cancel()
    {
        this.cancelled = true;
    }

    public void run()
    {
        long startMs = System.currentTimeMillis();
        long samplesChecked = 0;

        try
        {
            int cx = config.center().getX();
            int cz = config.center().getZ();
            int r = config.radius();
            int coarseStep = config.coarseStep();

            int bestHeight = Integer.MIN_VALUE;
            int bestX = cx;
            int bestZ = cz;

            // === COARSE PASS ===
            List<MountainCandidate> coarseCandidates = new ArrayList<>();

            outer:
            for (int x = cx - r; x <= cx + r; x += coarseStep)
            {
                for (int z = cz - r; z <= cz + r; z += coarseStep)
                {
                    if (cancelled) break outer;

                    int h = sampleUtils.doHeightSlow(new BlockPos(x, 0, z));
                    samplesChecked++;

                    if (h > bestHeight)
                    {
                        bestHeight = h;
                        bestX = x;
                        bestZ = z;
                    }

                    addToTopN(coarseCandidates, new MountainCandidate(h, x, z), config.candidatesToRefine());

                    if (samplesChecked % 2048 == 0)
                    {
                        final long sc = samplesChecked;
                        final int bh = bestHeight, bx = bestX, bz = bestZ;
                        callback.onProgress(sc, bh, bx, bz, "coarse");
                    }
                }
            }

            if (cancelled)
            {
                callback.onCancelled();
                return;
            }

            int coarseBestHeight = bestHeight;

            // === REFINE PASS ===
            List<MountainCandidate> refinedCandidates = new ArrayList<>();
            LongOpenHashSet visitedRefine = new LongOpenHashSet();
            int refineRadius = config.refineRadius();
            int refineStep = config.refineStep();

            outer2:
            for (MountainCandidate candidate : coarseCandidates)
            {
                for (int x = candidate.x() - refineRadius; x <= candidate.x() + refineRadius; x += refineStep)
                {
                    for (int z = candidate.z() - refineRadius; z <= candidate.z() + refineRadius; z += refineStep)
                    {
                        if (cancelled) break outer2;
                        if (x < cx - r || x > cx + r || z < cz - r || z > cz + r) continue;

                        long key = BlockPos.asLong(x, 0, z);
                        if (!visitedRefine.add(key)) continue;

                        int h = sampleUtils.doHeightSlow(new BlockPos(x, 0, z));
                        samplesChecked++;

                        if (h > bestHeight)
                        {
                            bestHeight = h;
                            bestX = x;
                            bestZ = z;
                        }

                        addToTopN(refinedCandidates, new MountainCandidate(h, x, z), config.candidatesToRefine());

                        if (samplesChecked % 2048 == 0)
                        {
                            final long sc = samplesChecked;
                            final int bh = bestHeight, bx = bestX, bz = bestZ;
                            callback.onProgress(sc, bh, bx, bz, "refine");
                        }
                    }
                }
            }

            if (cancelled)
            {
                callback.onCancelled();
                return;
            }

            int refinedBestHeight = bestHeight;

            // === FINAL PASS ===
            List<MountainCandidate> finalSource = refinedCandidates.isEmpty() ? coarseCandidates : refinedCandidates;
            int finalCount = Math.min(3, finalSource.size());
            LongOpenHashSet visitedFinal = new LongOpenHashSet();
            int finalRadius = config.finalRadius();
            int finalStep = config.finalStep();

            outer3:
            for (int i = 0; i < finalCount; i++)
            {
                MountainCandidate candidate = finalSource.get(i);
                for (int x = candidate.x() - finalRadius; x <= candidate.x() + finalRadius; x += finalStep)
                {
                    for (int z = candidate.z() - finalRadius; z <= candidate.z() + finalRadius; z += finalStep)
                    {
                        if (cancelled) break outer3;

                        long key = BlockPos.asLong(x, 0, z);
                        if (!visitedFinal.add(key)) continue;

                        int h = sampleUtils.doHeightSlow(new BlockPos(x, 0, z));
                        samplesChecked++;

                        if (h > bestHeight)
                        {
                            bestHeight = h;
                            bestX = x;
                            bestZ = z;
                        }

                        if (samplesChecked % 2048 == 0)
                        {
                            final long sc = samplesChecked;
                            final int bh = bestHeight, bx = bestX, bz = bestZ;
                            callback.onProgress(sc, bh, bx, bz, "final");
                        }
                    }
                }
            }

            if (cancelled)
            {
                callback.onCancelled();
                return;
            }

            long elapsed = System.currentTimeMillis() - startMs;
            callback.onComplete(new MountainSearchResult(
                seed, bestHeight, bestX, bestZ, coarseBestHeight, refinedBestHeight, samplesChecked, elapsed
            ));
        }
        catch (Throwable t)
        {
            callback.onError(t);
        }
    }

    private static void addToTopN(List<MountainCandidate> list, MountainCandidate candidate, int n)
    {
        if (list.size() < n || candidate.height() > list.get(list.size() - 1).height())
        {
            list.add(candidate);
            list.sort(null); // uses MountainCandidate.compareTo — descending by height
            if (list.size() > n)
            {
                list.subList(n, list.size()).clear();
            }
        }
    }
}
