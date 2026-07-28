package com.rustysnail.world.preview.tfc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.dries007.tfc.common.blocks.plant.fruit.SpreadingBushBlock;

@Mixin(SpreadingBushBlock.class)
public interface SpreadingBushBlockAccessor
{
    @Accessor("maxHeight")
    int worldPreviewTfc$getMaxHeight();
}
