package caeruleustait.world.preview.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer.FluidPicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
   @Accessor
   Supplier<FluidPicker> getGlobalFluidPicker();
}
