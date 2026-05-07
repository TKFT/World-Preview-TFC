package com.rustysnail.world.preview.tfc.backend.search.mountain;

import com.rustysnail.world.preview.tfc.WorldPreview;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public class MountainSeedSearchEngine implements Runnable
{
    public interface Callback
    {
        void onProgress(int testedSeeds, int maxSeeds, long currentSeed, String phase, int bestHeight, int bestX, int bestZ);
        void onSeedComplete(int testedSeeds, int maxSeeds, MountainSearchResult seedResult, MountainSearchResult currentBest);
        void onComplete(List<MountainSearchResult> topResults, int testedSeeds);
        void onCancelled(List<MountainSearchResult> topResults, int testedSeeds);
        void onError(Throwable t);
    }

    private final ChunkGenerator activeGenerator;
    private final MountainSeedSearchConfig config;
    private final Callback callback;

    private volatile boolean cancelled = false;
    @Nullable private volatile MountainPeakScanner activeScanner;
    private final List<MountainSearchResult> topResults = new ArrayList<>();

    public MountainSeedSearchEngine(ChunkGenerator activeGenerator, MountainSeedSearchConfig config, Callback callback)
    {
        this.activeGenerator = activeGenerator;
        this.config = config;
        this.callback = callback;
    }

    public void cancel()
    {
        this.cancelled = true;
        MountainPeakScanner scanner = this.activeScanner;
        if (scanner != null) scanner.cancel();
    }

    @Override
    public void run()
    {
        try
        {
            SplittableRandom rng = new SplittableRandom(config.randomSalt());
            int maxSeeds = config.maxSeeds();
            int globalBestHeight = Integer.MIN_VALUE;

            for (int i = 0; i < maxSeeds; i++)
            {
                if (cancelled)
                {
                    callback.onCancelled(List.copyOf(topResults), i);
                    return;
                }

                final long seed = rng.nextLong();
                final int seedNum = i + 1;

                try (TFCSeededHeightSampler sampler = TFCSeededHeightSamplerFactory.create(activeGenerator, seed))
                {
                    MountainSearchResult[] resultHolder = {null};
                    boolean[] innerCancelled = {false};
                    Throwable[] errorHolder = {null};

                    MountainPeakScanner scanner = new MountainPeakScanner(seed, sampler, config.scanConfig(),
                        new MountainPeakScanner.Callback()
                        {
                            @Override
                            public void onProgress(long sc, int bh, int bx, int bz, String phase)
                            {
                                callback.onProgress(seedNum, maxSeeds, seed, phase, bh, bx, bz);
                            }

                            @Override
                            public void onComplete(MountainSearchResult result)
                            {
                                resultHolder[0] = result;
                            }

                            @Override
                            public void onCancelled()
                            {
                                innerCancelled[0] = true;
                            }

                            @Override
                            public void onError(Throwable t)
                            {
                                errorHolder[0] = t;
                            }
                        }
                    );

                    activeScanner = scanner;
                    scanner.run();
                    activeScanner = null;

                    if (innerCancelled[0] || cancelled)
                    {
                        callback.onCancelled(List.copyOf(topResults), i);
                        return;
                    }

                    if (errorHolder[0] != null)
                    {
                        WorldPreview.LOGGER.warn("Error scanning seed {}: skipping", seed, errorHolder[0]);
                        continue;
                    }

                    MountainSearchResult result = resultHolder[0];
                    if (result == null) continue;

                    addToTopResults(result);

                    if (result.height() > globalBestHeight)
                    {
                        globalBestHeight = result.height();
                        WorldPreview.LOGGER.info("New mountain seed best: seed={}, y={}, x={}, z={}, tested={}/{}",
                            seed, result.height(), result.x(), result.z(), seedNum, maxSeeds);
                    }

                    MountainSearchResult best = topResults.get(0);
                    callback.onSeedComplete(seedNum, maxSeeds, result, best);
                }
                catch (Throwable t)
                {
                    if (cancelled)
                    {
                        callback.onCancelled(List.copyOf(topResults), i);
                        return;
                    }
                    WorldPreview.LOGGER.warn("Failed to process seed {}: skipping", seed, t);
                }
            }

            callback.onComplete(List.copyOf(topResults), maxSeeds);
        }
        catch (Throwable t)
        {
            callback.onError(t);
        }
    }

    private void addToTopResults(MountainSearchResult result)
    {
        topResults.add(result);
        topResults.sort((a, b) -> Integer.compare(b.height(), a.height()));
        if (topResults.size() > config.topResults())
        {
            topResults.subList(config.topResults(), topResults.size()).clear();
        }
    }
}
