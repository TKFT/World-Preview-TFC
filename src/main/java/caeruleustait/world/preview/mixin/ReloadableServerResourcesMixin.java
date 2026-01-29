package caeruleustait.world.preview.mixin;

import caeruleustait.world.preview.backend.color.BiomeColorMapReloadListener;
import caeruleustait.world.preview.backend.color.ColormapReloadListener;
import caeruleustait.world.preview.backend.color.HeightmapPresetReloadListener;
import caeruleustait.world.preview.backend.color.RockColorReloadListener;
import caeruleustait.world.preview.backend.color.RockTypeColorReloadListener;
import caeruleustait.world.preview.backend.color.StructureMapReloadListener;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
   @Inject(method = "listeners", at = @At("RETURN"), cancellable = true)
   private void modifyReloadList(CallbackInfoReturnable<List<PreparableReloadListener>> cir) {
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
