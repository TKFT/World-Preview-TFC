package com.rustysnail.world.preview.tfc.mixin;

import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeSaplingBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.util.climate.ClimateRange;

@Mixin(FruitTreeSaplingBlock.class)
public interface FruitTreeSaplingBlockAccessor
{
    @Accessor("climateRange")
    Supplier<ClimateRange> worldPreviewTfc$getClimateRange();

    @Accessor("stages")
    Lifecycle[] worldPreviewTfc$getStages();

    @Accessor("ticksToGrow")
    Supplier<Integer> worldPreviewTfc$getTicksToGrow();
}
