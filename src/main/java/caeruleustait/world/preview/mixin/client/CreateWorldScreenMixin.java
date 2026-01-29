package caeruleustait.world.preview.mixin.client;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.client.gui.screens.PreviewTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
   @Shadow
   @Nullable
   private TabNavigationBar tabNavigationBar;
   @Unique
   private PreviewTab worldPreview_TFC$previewTab;

   @Inject(
      method = "init",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;",
         shift = Shift.BEFORE
      ),
      slice = @Slice(
         from = @At("HEAD"),
         to = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"
         )
      )
   )
   private void appendPreviewTab(CallbackInfo ci) {
      this.worldPreview_TFC$previewTab = new PreviewTab((CreateWorldScreen)(Object)this, ((ScreenAccessor)this).getMinecraft());
      TabNavigationBar originalRaw = this.tabNavigationBar;
      TabNavigationBarAccessor original = (TabNavigationBarAccessor)originalRaw;
      this.tabNavigationBar = TabNavigationBar.builder(original.getTabManager(), original.getWidth())
         .addTabs(original.getTabs().toArray(new Tab[0]))
         .addTabs(new Tab[]{this.worldPreview_TFC$previewTab})
         .build();
   }

   @Inject(method = "popScreen", at = @At("HEAD"))
   private void saveConfigOnClose(CallbackInfo ci) {
      this.worldPreview_TFC$previewTab.close();
      WorldPreview.get().saveConfig();
   }

   @Inject(method = "onCreate", at = @At("HEAD"))
   private void saveConfigOnCreate(CallbackInfo ci) {
      this.worldPreview_TFC$previewTab.close();
      WorldPreview.get().saveConfig();
   }
}
