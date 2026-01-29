package caeruleustait.world.preview.client.gui.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class WGLabel extends AbstractWidget {
   private final Font font;
   private Component component;
   private final int color;
   private final TextAlignment alignment;
   private int textWidth;
   private int startX;
   private int startY;

   public WGLabel(Font _font, int _x, int _y, int _width, int _height, TextAlignment _alignment, Component _component, int _color) {
      super(_x, _y, _width, _height, _component);
      this.font = _font;
      this.component = _component;
      this.color = _color;
      this.alignment = _alignment;
      this.update();
   }

   public void update() {
      this.textWidth = this.font.width(this.component.getVisualOrderText());
      this.startY = this.getY() + this.height / 2 - 9 / 2;

      this.startX = switch (this.alignment) {
         case LEFT -> this.getX();
         case CENTER -> this.getX() + this.width / 2 - this.textWidth / 2;
         case RIGHT -> this.getX() + this.width - this.textWidth;
      };
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }

   public void setX(int x) {
      super.setX(x);
      this.update();
   }

   public void setY(int i) {
      super.setY(i);
      this.update();
   }

   public void setWidth(int width) {
      super.setWidth(width);
      this.update();
   }

   public void setText(Component _component) {
      this.component = _component;
      this.update();
   }

   public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
      guiGraphics.drawString(this.font, this.component, this.startX, this.startY, this.color);
   }

   protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
   }

   public enum TextAlignment {
      LEFT,
      CENTER,
      RIGHT
   }
}
