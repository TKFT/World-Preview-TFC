package com.rustysnail.world.preview.tfc.mixin;

import com.rustysnail.world.preview.tfc.backend.search.FeaturePlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlacedFeature.class)
public abstract class MixinPlacedFeature
{
    @Inject(method = "place", at = @At("RETURN"))
    private void worldpreview$onPlaceReturn(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        if (FeaturePlacementTracker.isRecording()) return;

        if (cir.getReturnValue())
        {
            Registry<PlacedFeature> reg = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            Optional<ResourceKey<PlacedFeature>> key = reg.getResourceKey((PlacedFeature) (Object) this);
            if (key.isEmpty()) return;

            FeaturePlacementTracker.onFeaturePlaced(level, key.get().location(), pos);
        }
    }
}
