package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.storage.PreviewSection;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class WorkBatch {
    public final List<WorkUnit> workUnits;
    private final Object completedSynchro;
    private final PreviewData previewData;
    private boolean isCanceled = false;

    public WorkBatch(List<WorkUnit> workUnits, Object completedSynchro, PreviewData previewData) {
        this.workUnits = workUnits;
        this.completedSynchro = completedSynchro;
        this.previewData = previewData;
    }

    public boolean isCanceled() {
        return this.isCanceled;
    }

    public void cancel() {
        this.isCanceled = true;
        this.workUnits.forEach(WorkUnit::cancel);
    }

    public void process() {
        try {
            if (this.isCanceled()) {
                return;
            }

            List<WorkResult> res = new ArrayList<>();

            for (WorkUnit unit : this.workUnits) {
                res.addAll(unit.work());
                if (this.isCanceled()) {
                    return;
                }
            }

            synchronized (this.completedSynchro) {
                this.workUnits.forEach(WorkUnit::markCompleted);
            }

            this.applyChunkResult(res);
        } catch (Exception var6) {
            WorldPreview.LOGGER.error("Error processing work batch", var6);
        }
    }

    private void applyChunkResult(List<WorkResult> workResultList) {
        try {
            for (WorkResult workResult : workResultList) {
                if (workResult == null) {
                    return;
                }

                PreviewSection section = workResult.section();

                // Map absolute quart-space coords into this section's local coordinates.
                // Some samplers (eg QuarterQuartSampler) expand by +1 and can produce boundary values
                // that land exactly on the next section (eg localX == 64). Those must be ignored.
                final int baseQuartX = section.quartX();
                final int baseQuartZ = section.quartZ();
                if (workResult.results() != null) {
                    for (WorkResult.BlockResult x : workResult.results()) {
                        final int localX = x.quartX() - baseQuartX;
                        final int localZ = x.quartZ() - baseQuartZ;
                        if (localX < 0 || localX >= PreviewSection.SIZE || localZ < 0 || localZ >= PreviewSection.SIZE) {
                            continue;
                        }
                        section.set(localX, localZ, x.value());
                    }
                }

                for (Pair<ResourceLocation, StructureStart> x : workResult.structures()) {
                    StructureStart structureStart = x.getSecond();
                    short id = this.previewData.struct2Id().getShort((x.getFirst()).toString());
                    section.addStructure(new PreviewSection.PreviewStruct(structureStart.getBoundingBox().getCenter(), id, structureStart.getBoundingBox()));
                }
            }
        } catch (Throwable var13) {
            WorldPreview.LOGGER.error("Error applying chunk result", var13);
        }
    }
}
