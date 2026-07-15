package com.rustysnail.world.preview.tfc.backend.worker;

import java.util.ArrayList;
import java.util.List;
import com.mojang.datafixers.util.Pair;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class WorkBatch
{
    private static PreviewSection getPreviewSection(WorkResult workResult)
    {
        PreviewSection section = workResult.section();

        final int baseQuartX = section.quartX();
        final int baseQuartZ = section.quartZ();
        for (WorkResult.BlockResult x : workResult.results())
        {
            final int localX = x.quartX() - baseQuartX;
            final int localZ = x.quartZ() - baseQuartZ;
            if (localX < 0 || localX >= PreviewSection.SIZE || localZ < 0 || localZ >= PreviewSection.SIZE)
            {
                continue;
            }
            section.set(localX, localZ, x.value());
        }
        return section;
    }

    public final List<WorkUnit> workUnits;
    private final Object completedSynchro;
    private final PreviewData previewData;
    private volatile boolean isCanceled = false;

    public WorkBatch(List<WorkUnit> workUnits, Object completedSynchro, PreviewData previewData)
    {
        this.workUnits = workUnits;
        this.completedSynchro = completedSynchro;
        this.previewData = previewData;
    }

    public boolean isCanceled()
    {
        return this.isCanceled;
    }

    public void cancel()
    {
        this.isCanceled = true;
        this.workUnits.forEach(WorkUnit::cancel);
    }

    public void process()
    {
        try
        {
            if (this.isCanceled())
            {
                return;
            }

            List<WorkResult> res = new ArrayList<>();

            for (WorkUnit unit : this.workUnits)
            {
                res.addAll(unit.work());
                if (this.isCanceled() || !unit.isResultValid())
                {
                    return;
                }
            }

            if (this.workUnits.stream().anyMatch(unit -> !unit.isResultValid()))
            {
                return;
            }

            boolean applied = this.applyChunkResult(res);

            if (applied && !this.isCanceled()
                && this.workUnits.stream().allMatch(WorkUnit::isResultValid))
            {
                synchronized (this.completedSynchro)
                {
                    this.workUnits.forEach(WorkUnit::markCompleted);
                }
                WorldPreview.get().workManager().bumpDataRevision();
            }
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.error("Error processing work batch", e);
        }
    }

    private boolean applyChunkResult(List<WorkResult> workResultList)
    {
        try
        {
            for (WorkResult workResult : workResultList)
            {
                if (workResult == null)
                {
                    continue;
                }
                if (this.isCanceled() || !workResult.workUnit().isResultValid())
                {
                    return false;
                }

                PreviewSection section = getPreviewSection(workResult);

                for (Pair<ResourceLocation, StructureStart> x : workResult.structures())
                {
                    StructureStart structureStart = x.getSecond();
                    short id = this.previewData.struct2Id().getShort((x.getFirst()).toString());
                    section.addStructure(new PreviewSection.PreviewStruct(structureStart.getBoundingBox().getCenter(), id, structureStart.getBoundingBox()));
                }
            }
            return true;
        }
        catch (Throwable e)
        {
            WorldPreview.LOGGER.error("Error applying chunk result", e);
            return false;
        }
    }
}
