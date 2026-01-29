package caeruleustait.world.preview.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Palette.class)
public interface StructureTemplatePaletteAccessor {
   @Accessor
   Map<Block, List<StructureBlockInfo>> getCache();

   @Accessor
   @Mutable
   void setCache(Map<Block, List<StructureBlockInfo>> var1);
}
