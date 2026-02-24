package com.rustysnail.world.preview.tfc.mixin.client;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.screens.InGamePreviewScreen;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin
{
    @Shadow
    @Final
    private static int BUTTON_WIDTH_FULL;

    @Inject(
        method = "createPauseMenu",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z")
    )
    private void addWorldPreviewButton(CallbackInfo ci, @Local RowHelper rowHelper)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null)
        {
            if (WorldPreview.get().cfg().showInPauseMenu)
            {
                rowHelper.addChild(Button.builder(WorldPreviewComponents.TITLE_FULL, this::worldPreview_TFC$onPressWorldPreview).width(BUTTON_WIDTH_FULL).build(), 2);
            }
        }
    }

    @Unique
    private void worldPreview_TFC$onPressWorldPreview(Button btn)
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new InGamePreviewScreen());
    }
}
