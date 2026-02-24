package com.rustysnail.world.preview.tfc.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PreviewCacheLoadingScreen extends Screen
{
    protected PreviewCacheLoadingScreen(Component component)
    {
        super(component);
    }

    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.title, this.width / 2, this.height / 2, 16777215);
    }
}
