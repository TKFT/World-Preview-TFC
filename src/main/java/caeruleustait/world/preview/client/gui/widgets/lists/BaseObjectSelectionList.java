package caeruleustait.world.preview.client.gui.widgets.lists;

import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseObjectSelectionList<E extends BaseObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
   protected BaseObjectSelectionList(Minecraft minecraft, int width, int height, int x, int y, int itemHeight) {
      super(minecraft, width, height, y, itemHeight);
   }

   public int getRowLeft() {
      return this.getX();
   }

   public int getRowRight() {
      return this.getX() + this.width - 6;
   }

   public int getRowWidth() {
      return this.width - 6;
   }

   protected int getScrollbarPosition() {
      return this.getRowRight();
   }

   protected void renderSelection(GuiGraphics guiGraphics, int rowTop, int rowWidth, int innerHeight, int boxBorderColor, int boxInnerColor) {
      int left = this.getRowLeft();
      int right = this.getRowRight();
      guiGraphics.fill(left, rowTop - 2, right, rowTop + innerHeight + 2, boxBorderColor);
      guiGraphics.fill(left + 1, rowTop - 1, right - 1, rowTop + innerHeight + 1, boxInnerColor);
   }

   public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
      E hovered = this.getHovered();
      if (hovered != null && hovered.tooltip() != null && this.minecraft.screen != null) {
         this.minecraft.screen.setTooltipForNextRenderPass(hovered.tooltip(), DefaultTooltipPositioner.INSTANCE, true);
      }
   }

   public void replaceEntries(@NotNull Collection<E> entryList) {
      super.replaceEntries(entryList);
   }

   public abstract static class Entry<E extends Entry<E>> extends ObjectSelectionList.Entry<E> {
      @Nullable
      public Tooltip tooltip() {
         return null;
      }
   }
}
