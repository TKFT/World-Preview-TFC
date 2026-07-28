package com.rustysnail.world.preview.tfc.mixin;

import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.plant.fruit.SeasonalPlantBlock;
import net.dries007.tfc.util.climate.ClimateRange;

@Mixin(SeasonalPlantBlock.class)
public interface SeasonalPlantBlockAccessor
{
    @Accessor("climateRange")
    Supplier<ClimateRange> worldPreviewTfc$getClimateRange();

    @Accessor("lifecycle")
    Lifecycle[] worldPreviewTfc$getLifecycle();
}
