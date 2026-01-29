package caeruleustait.world.preview.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
//import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.resources.ResourceLocation;

public class ToggleButton extends OldStyleImageButton {
   public boolean selected;
   protected final int xDiff;

  /* public ToggleButton(int x, int y, int width, int height, int xTexStart, int yTexStart, ResourceLocation resourceLocation, OnPress onPress) {
      this(x, y, width, height, xTexStart, yTexStart, width, height, resourceLocation, 256, 256, onPress);
   }

   public ToggleButton(
      int x, int y, int width, int height, int xTexStart, int yTexStart, int xDiff, int yDiff, ResourceLocation resourceLocation, OnPress onPress
   ) {
      this(x, y, width, height, xTexStart, yTexStart, xDiff, yDiff, resourceLocation, 256, 256, onPress);
   }*/

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
   ) {
      super(x, y, width, height, xTexStart, yTexStart, yDiff, resourceLocation, texWidth, texHeight, onPress);
      this.xDiff = xDiff;
   }

   @Override
   public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      int x = this.xTexStart;
      int y = this.yTexStart;
      if (!this.selected) {
         x += this.xDiff;
      }

      if (!this.isActive()) {
         y += this.yDiffTex * 2;
      } else if (this.isHoveredOrFocused()) {
         y += this.yDiffTex;
      }

      guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
      RenderSystem.enableBlend();
      RenderSystem.enableDepthTest();
      guiGraphics.blit(this.texture, this.getX(), this.getY(), x, y, this.width, this.height, this.texWidth, this.texHeight);
   }

   public void onPress() {
      this.selected = !this.selected;
      super.onPress();
   }
}
