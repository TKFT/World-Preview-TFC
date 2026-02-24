package com.rustysnail.world.preview.tfc.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ToggleButton extends OldStyleImageButton
{
    public boolean selected;
    protected final int xDiff;

    public ToggleButton(
        int x,
        int y,
        int width,
        int height,
        int xTexStart,
        int yTexStart,
        int xDiff,
        int yDiff,
        ResourceLocation resourceLocation,
        int texWidth,
        int texHeight,
        OnPress onPress
    )
    {
        super(x, y, width, height, xTexStart, yTexStart, yDiff, resourceLocation, texWidth, texHeight, onPress);
        this.xDiff = xDiff;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        int x = this.xTexStart;
        if (!this.selected)
        {
            x += this.xDiff;
        }
        renderAtTexCoords(guiGraphics, x, this.yTexStart);
    }

    public void onPress()
    {
        this.selected = !this.selected;
        super.onPress();
    }
}
