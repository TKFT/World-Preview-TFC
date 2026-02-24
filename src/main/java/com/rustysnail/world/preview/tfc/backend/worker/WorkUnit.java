package com.rustysnail.world.preview.tfc.backend.worker;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.WorkManager;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

public abstract class WorkUnit
{
    protected final WorkManager workManager = WorldPreview.get().workManager();
    protected final SampleUtils sampleUtils;
    protected final PreviewStorage storage;
    protected final PreviewSection primarySection;
    protected final ChunkPos chunkPos;
    protected final PreviewData previewData;
    protected final int y;
    private boolean isCanceled;

    protected WorkUnit(SampleUtils sampleUtils, ChunkPos chunkPos, PreviewData previewData, int y)
    {
        this.sampleUtils = sampleUtils;
        this.storage = this.workManager.previewStorage();
        this.primarySection = this.storage.section4(chunkPos, y, this.flags());
        this.chunkPos = chunkPos;
        this.previewData = previewData;
        this.y = y;
    }

    public short biomeIdFrom(ResourceKey<Biome> resourceKey)
    {
        return this.previewData.biome2Id().getShort(resourceKey.location().toString());
    }

    protected abstract List<WorkResult> doWork();

    public abstract long flags();

    public boolean isCompleted()
    {
        return this.primarySection.isCompleted(this.chunkPos);
    }

    public void markCompleted()
    {
        this.primarySection.markCompleted(this.chunkPos);
    }

    public List<WorkResult> work()
    {
        try
        {
            return this.doWork();
        }
        catch (Throwable e)
        {
            WorldPreview.LOGGER.error("Error in work unit for chunk {}", this.chunkPos, e);
            throw e;
        }
    }

    public ChunkPos chunk()
    {
        return this.chunkPos;
    }

    public int y()
    {
        return this.y;
    }

    public void cancel()
    {
        this.isCanceled = true;
    }

    public boolean isCanceled()
    {
        return this.isCanceled;
    }
}
