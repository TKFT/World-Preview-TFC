package caeruleustait.world.preview.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
   @Shadow
   @Final
   private EditBox seedEdit;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void addSeedUpdateListener(CreateWorldScreen createWorldScreen, CallbackInfo ci) {
      WorldCreationUiState uiState = createWorldScreen.getUiState();
      uiState.addListener(worldCreationUiState -> {
         String currentSeed = worldCreationUiState.getSeed();
         if (!currentSeed.equals(this.seedEdit.getValue())) {
            this.seedEdit.setValue(currentSeed);
         }
      });
   }
}
