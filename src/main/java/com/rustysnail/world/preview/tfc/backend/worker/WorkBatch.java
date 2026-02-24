package com.rustysnail.world.preview.tfc.backend.worker;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.NotNull;

public class WorkBatch
{
    public final List<WorkUnit> workUnits;
    private final Object completedSynchro;
    private final PreviewData previewData;
    private boolean isCanceled = false;

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
                if (this.isCanceled())
                {
                    return;
                }
            }

            synchronized (this.completedSynchro)
            {
                this.workUnits.forEach(WorkUnit::markCompleted);
            }

            this.applyChunkResult(res);
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.error("Error processing work batch", e);
        }
    }

    private void applyChunkResult(List<WorkResult> workResultList)
    {
        try
        {
            for (WorkResult workResult : workResultList)
            {
                if (workResult == null)
                {
                    return;
                }

                PreviewSection section = getPreviewSection(workResult);

                for (Pair<ResourceLocation, StructureStart> x : workResult.structures())
                {
                    StructureStart structureStart = x.getSecond();
                    short id = this.previewData.struct2Id().getShort((x.getFirst()).toString());
                    section.addStructure(new PreviewSection.PreviewStruct(structureStart.getBoundingBox().getCenter(), id, structureStart.getBoundingBox()));
                }
            }
        }
        catch (Throwable e)
        {
            WorldPreview.LOGGER.error("Error applying chunk result", e);
        }
    }

    private static @NotNull PreviewSection getPreviewSection(WorkResult workResult)
    {
        PreviewSection section = workResult.section();

        final int baseQuartX = section.quartX();
        final int baseQuartZ = section.quartZ();
        if (workResult.results() != null)
        {
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
        }
        return section;
    }
}
