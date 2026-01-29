package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.backend.color.PreviewData;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class StructStartWorkUnit extends WorkUnit {
   public StructStartWorkUnit(SampleUtils sampleUtils, ChunkPos pos, PreviewData previewData) {
      super(sampleUtils, pos, previewData, 0);
   }

   @Override
   protected List<WorkResult> doWork() {
      List<Pair<ResourceLocation, StructureStart>> res = this.sampleUtils.doStructures(this.chunkPos);
      return List.of(new WorkResult(this, 0, this.primarySection, List.of(), res));
   }

   @Override
   public long flags() {
      return 1L;
   }
}
