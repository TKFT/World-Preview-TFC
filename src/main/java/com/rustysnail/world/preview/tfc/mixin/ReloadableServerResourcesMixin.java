package com.rustysnail.world.preview.tfc.mixin;

import com.rustysnail.world.preview.tfc.backend.color.BiomeColorMapReloadListener;
import com.rustysnail.world.preview.tfc.backend.color.ColormapReloadListener;
import com.rustysnail.world.preview.tfc.backend.color.HeightmapPresetReloadListener;
import com.rustysnail.world.preview.tfc.backend.color.RockColorReloadListener;
import com.rustysnail.world.preview.tfc.backend.color.RockTypeColorReloadListener;
import com.rustysnail.world.preview.tfc.backend.color.StructureMapReloadListener;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin
{
    @Inject(method = "listeners", at = @At("RETURN"), cancellable = true)
    private void modifyReloadList(CallbackInfoReturnable<List<PreparableReloadListener>> cir)
    {
        List<PreparableReloadListener> listeners = new ArrayList<>(cir.getReturnValue());
        listeners.add(new BiomeColorMapReloadListener());
        listeners.add(new StructureMapReloadListener());
        listeners.add(new HeightmapPresetReloadListener());
        listeners.add(new ColormapReloadListener());
        listeners.add(new RockColorReloadListener());
        listeners.add(new RockTypeColorReloadListener());
        cir.setReturnValue(listeners);
    }
}
