package caeruleustait.world.preview.backend.stubs;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyAquifer implements Aquifer {
   @Nullable
   public BlockState computeSubstance(@NotNull FunctionContext context, double substance) {
      return substance > 0.0 ? null : Blocks.AIR.defaultBlockState();
   }

   public boolean shouldScheduleFluidUpdate() {
      return false;
   }
}
