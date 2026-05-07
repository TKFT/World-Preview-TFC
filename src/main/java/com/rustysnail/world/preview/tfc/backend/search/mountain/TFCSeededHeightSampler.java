package com.rustysnail.world.preview.tfc.backend.search.mountain;

import com.rustysnail.world.preview.tfc.backend.search.HeightSampler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import net.dries007.tfc.world.ChunkHeightFiller;
import net.dries007.tfc.world.TFCChunkGenerator;

/**
 * Height-only sampler backed by a freshly seeded TFCChunkGenerator clone.
 * Not thread-safe — create one instance per thread/scan.
 */
public class TFCSeededHeightSampler implements HeightSampler, AutoCloseable
{
    private final long seed;
    private final TFCChunkGenerator generator;
    private final Long2ObjectMap<ChunkHeightFiller> fillerCache;

    public TFCSeededHeightSampler(long seed, TFCChunkGenerator generator)
    {
        this.seed = seed;
        this.generator = generator;
        this.fillerCache = new Long2ObjectOpenHashMap<>();
    }

    public long seed()
    {
        return seed;
    }

    @Override
    public int surfaceY(int blockX, int blockZ)
    {
        ChunkPos chunkPos = new ChunkPos(
            SectionPos.blockToSectionCoord(blockX),
            SectionPos.blockToSectionCoord(blockZ)
        );
        long key = chunkPos.toLong();
        ChunkHeightFiller filler = fillerCache.get(key);
        if (filler == null)
        {
            filler = generator.createHeightFillerForChunk(chunkPos);
            fillerCache.put(key, filler);
        }
        return (int) filler.sampleHeight(blockX, blockZ);
    }

    @Override
    public void close()
    {
        fillerCache.clear();
    }
}
