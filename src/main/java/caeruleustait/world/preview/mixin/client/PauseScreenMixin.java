package caeruleustait.world.preview.mixin.client;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.screens.InGamePreviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
   @Shadow
   @Final
   private static int BUTTON_WIDTH_FULL;

   @Inject(
      method = "createPauseMenu",
      at = @At(value = "INVOKE", shift = Shift.BEFORE, target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z"),
      locals = LocalCapture.CAPTURE_FAILHARD
   )
   private void addWorldPreviewButton(CallbackInfo ci, GridLayout gridLayout, RowHelper rowHelper) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.getSingleplayerServer() != null) {
         if (WorldPreview.get().cfg().showInPauseMenu) {
            rowHelper.addChild(Button.builder(WorldPreviewComponents.TITLE_FULL, this::onPressWorldPreview).width(BUTTON_WIDTH_FULL).build(), 2);
         }
      }
   }

   @Unique
   private void onPressWorldPreview(Button btn) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.setScreen(new InGamePreviewScreen());
   }
}
