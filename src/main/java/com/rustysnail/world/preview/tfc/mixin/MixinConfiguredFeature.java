package com.rustysnail.world.preview.tfc.mixin;

import com.rustysnail.world.preview.tfc.backend.search.FeaturePlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredFeature.class)
public abstract class MixinConfiguredFeature
{
    @Inject(method = "place", at = @At("RETURN"))
    private void worldpreview$onConfiguredFeaturePlace(
        WorldGenLevel level,
        ChunkGenerator generator,
        RandomSource random,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    )
    {
        if (!FeaturePlacementTracker.isRecording() || !cir.getReturnValue())
        {
            return;
        }

        ResourceLocation placedFeatureId = FeaturePlacementTracker.getCurrentPlacedFeature();
        if (placedFeatureId == null)
        {
            return;
        }

        FeaturePlacementTracker.onFeaturePlaced(level, placedFeatureId, pos);
    }
}
