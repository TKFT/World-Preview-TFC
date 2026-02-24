package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import java.util.Collection;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseObjectSelectionList<E extends BaseObjectSelectionList.Entry<E>> extends ObjectSelectionList<E>
{
    private static final int CHECKBOX_SIZE = 10;
    private static final int CHECKBOX_BORDER_ENABLED = 0xFFAAAAAA;
    private static final int CHECKBOX_BORDER_DISABLED = 0xFF555555;
    private static final int CHECKBOX_FILL = 0xFF000000;
    private static final int CHECKBOX_CHECK = 0xFF55FF55;

    protected BaseObjectSelectionList(Minecraft minecraft, int width, int height, int x, int y, int itemHeight)
    {
        super(minecraft, width, height, y, itemHeight);
        this.setX(x);
    }

    protected static void renderCheckbox(GuiGraphics gg, int x, int y, boolean checked)
    {
        renderCheckbox(gg, x, y, checked, true);
    }

    protected static void renderCheckbox(GuiGraphics gg, int x, int y, boolean checked, boolean enabled)
    {
        int borderColor = enabled ? CHECKBOX_BORDER_ENABLED : CHECKBOX_BORDER_DISABLED;
        gg.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, borderColor);
        gg.fill(x + 1, y + 1, x + CHECKBOX_SIZE - 1, y + CHECKBOX_SIZE - 1, CHECKBOX_FILL);
        if (checked)
        {
            gg.fill(x + 2, y + 4, x + 4, y + 8, CHECKBOX_CHECK);
            gg.fill(x + 4, y + 5, x + 6, y + 9, CHECKBOX_CHECK);
            gg.fill(x + 6, y + 2, x + 8, y + 7, CHECKBOX_CHECK);
        }
    }

    public int getRowLeft()
    {
        return this.getX();
    }

    public int getRowRight()
    {
        return this.getX() + this.width - 6;
    }

    public int getRowWidth()
    {
        return this.width - 6;
    }

    protected int getScrollbarPosition()
    {
        return this.getRowRight();
    }

    protected void renderSelection(GuiGraphics guiGraphics, int rowTop, int rowWidth, int innerHeight, int boxBorderColor, int boxInnerColor)
    {
        int left = this.getRowLeft();
        int right = this.getRowRight();
        guiGraphics.fill(left, rowTop - 2, right, rowTop + innerHeight + 2, boxBorderColor);
        guiGraphics.fill(left + 1, rowTop - 1, right - 1, rowTop + innerHeight + 1, boxInnerColor);
    }

    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        E hovered = this.getHovered();
        if (hovered != null && hovered.tooltip() != null && this.minecraft.screen != null)
        {
            this.minecraft.screen.setTooltipForNextRenderPass(Objects.requireNonNull(hovered.tooltip()), DefaultTooltipPositioner.INSTANCE, true);
        }
    }

    public void replaceEntries(@NotNull Collection<E> entryList)
    {
        super.replaceEntries(entryList);
    }

    public abstract static class Entry<E extends Entry<E>> extends ObjectSelectionList.Entry<E>
    {
        @Nullable
        public Tooltip tooltip()
        {
            return null;
        }
    }
}
