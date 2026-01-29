package caeruleustait.world.preview.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Aquifer.NoiseBasedAquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NoiseBasedAquifer.class)
public abstract class NoiseBasedAquiferMixin {
   @Final
   @Shadow
   private NoiseChunk noiseChunk;

   @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 2)
   private int fixMaxPosX(int k) {
      return k + this.invokeGridX(((NoiseChunkAccessor)this.noiseChunk).getCellCountXZ() * this.worldPreview_TFC$cellWidth());
   }

   @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 5)
   private int fixMaxPosZ(int k) {
      return k + this.invokeGridZ(((NoiseChunkAccessor)this.noiseChunk).getCellCountXZ() * this.worldPreview_TFC$cellWidth());
   }

   @Invoker
   abstract int invokeGridX(int var1);

   @Invoker
   abstract int invokeGridZ(int var1);

   @Unique
   private int worldPreview_TFC$cellWidth() {
      return ((NoiseChunkAccessor)this.noiseChunk).getNoiseSettings().getCellWidth();
   }
}
