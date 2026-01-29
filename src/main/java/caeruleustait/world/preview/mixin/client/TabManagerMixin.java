package caeruleustait.world.preview.mixin.client;

import caeruleustait.world.preview.client.gui.screens.PreviewTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabManager.class)
public abstract class TabManagerMixin {
   @Shadow
   @Nullable
   public abstract Tab getCurrentTab();

   @Inject(method = "setCurrentTab", at = @At("HEAD"))
   private void onTabChange(Tab tab, boolean bl, CallbackInfo ci) {
      if (tab != this.getCurrentTab()) {
         if (tab instanceof PreviewTab previewTab) {
            previewTab.mainScreenWidget().start();
         } else if (this.getCurrentTab() instanceof PreviewTab previewTab) {
            previewTab.mainScreenWidget().stop();
         }
      }
   }
}
