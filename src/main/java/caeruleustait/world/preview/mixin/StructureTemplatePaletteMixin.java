package caeruleustait.world.preview.mixin;

import caeruleustait.world.preview.WorldPreview;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Palette.class)
public abstract class StructureTemplatePaletteMixin {
   @Inject(method = "<init>", at = @At("TAIL"))
   private void threadSafeCache(List<StructureBlockInfo> list, CallbackInfo ci) {
      if (WorldPreview.get() != null && WorldPreview.get().workManager().isSetup()) {
         ((StructureTemplatePaletteAccessor)this).setCache(new ConcurrentHashMap<>());
      }
   }
}
