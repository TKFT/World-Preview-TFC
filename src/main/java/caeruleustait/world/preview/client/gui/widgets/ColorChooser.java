package caeruleustait.world.preview.client.gui.widgets;

import caeruleustait.world.preview.client.WorldPreviewClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class ColorChooser extends AbstractWidget {
   public static final int INITIAL_SV_SQUARE_SIZE = 128;
   public static final int INITIAL_H_BAR_WIDTH = 16;
   public static final int SEPARATOR = 10;
   public static final int INITIAL_FINAL_COLOR_HEIGHT = 20;
   private int svSquareSize;
   private int hBarWidth;
   private int finalColorHeight;
   private float hue = 0.0F;
   private float saturation = 0.0F;
   private float value = 0.0F;
   private int argbColor = -16777216;
   private int argbHueOnly = -16777216;
   private ColorUpdater updater;

   public ColorChooser(int x, int y) {
      super(x, y, 10, 10, CommonComponents.EMPTY);
      this.svSquareSize = 128;
      this.hBarWidth = 16;
      this.finalColorHeight = 20;
      this.recalculateSize();
   }

   private void recalculateSize() {
      this.width = this.svSquareSize + 10 + this.hBarWidth;
      this.height = this.svSquareSize + 10 + this.finalColorHeight;
   }

   public void setSquareSize(int squareSize) {
      float scalor = squareSize / 128.0F;
      this.svSquareSize = squareSize;
      this.hBarWidth = (int)(16.0F * scalor);
      this.finalColorHeight = (int)(20.0F * scalor);
      this.recalculateSize();
   }

   public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
      guiGraphics.fill(this.getX() - 2, this.getY() - 2, this.getX() + this.width + 2, this.getY() + this.height + 2, 1996488704);
      RenderSystem.setShader(() -> WorldPreviewClient.HSV_SHADER);
      Matrix4f posMatrix = guiGraphics.pose().last().pose();
      int leftX = this.getX();
      int topY = this.getY();
      int rightX = leftX + this.svSquareSize;
      int botY = topY + this.svSquareSize;
      BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buffer.addVertex(posMatrix, leftX, topY, 0.0F).setColor(this.hue, 0.0F, 1.0F, 1.0F);
      buffer.addVertex(posMatrix, leftX, botY, 0.0F).setColor(this.hue, 0.0F, 0.0F, 1.0F);
      buffer.addVertex(posMatrix, rightX, botY, 0.0F).setColor(this.hue, 1.0F, 0.0F, 1.0F);
      buffer.addVertex(posMatrix, rightX, topY, 0.0F).setColor(this.hue, 1.0F, 1.0F, 1.0F);
      MeshData data = buffer.build();

      try {
         if (data != null) {
            BufferUploader.drawWithShader(data);
         }
      } catch (Throwable var18) {
         if (data != null) {
            try {
               data.close();
            } catch (Throwable var17) {
               var18.addSuppressed(var17);
            }
         }

         throw var18;
      }

      if (data != null) {
         data.close();
      }

      int satX = leftX + Math.round(this.saturation * this.svSquareSize);
      int valY = topY + Math.round((1.0F - this.value) * this.svSquareSize);
      guiGraphics.fill(satX - 4, valY - 4, satX + 4, valY + 4, this.value > 0.3 ? -16777216 : -1);
      guiGraphics.fill(satX - 3, valY - 3, satX + 3, valY + 3, this.argbColor);
      RenderSystem.setShader(() -> WorldPreviewClient.HSV_SHADER);
      leftX = rightX + 10;
      rightX = leftX + this.hBarWidth;
      buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buffer.addVertex(posMatrix, leftX, topY, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
      buffer.addVertex(posMatrix, leftX, botY, 0.0F).setColor(0.0F, 1.0F, 1.0F, 1.0F);
      buffer.addVertex(posMatrix, rightX, botY, 0.0F).setColor(0.0F, 1.0F, 1.0F, 1.0F);
      buffer.addVertex(posMatrix, rightX, topY, 0.0F).setColor(1.0F, 1.0F, 1.0F, 1.0F);
      MeshData datax = buffer.build();

      try {
         if (datax != null) {
            BufferUploader.drawWithShader(datax);
         }
      } catch (Throwable var19) {
         if (datax != null) {
            try {
               datax.close();
            } catch (Throwable var16) {
               var19.addSuppressed(var16);
            }
         }

         throw var19;
      }

      if (datax != null) {
         datax.close();
      }

      int hueY = topY + Math.round((1.0F - this.hue) * this.svSquareSize);
      guiGraphics.fill(leftX - 2, hueY - 4, rightX + 2, hueY + 4, -16777216);
      guiGraphics.fill(leftX - 1, hueY - 3, rightX + 1, hueY + 3, this.argbHueOnly);
      guiGraphics.fill(this.getX(), botY + 10, this.getX() + this.width, this.getY() + this.height, this.argbColor);
   }

   public boolean mouseEvent(double mouseX, double mouseY, int button, boolean playSound) {
      if (this.active && this.visible && this.isValidClickButton(button) && this.isMouseOver(mouseX, mouseY)) {
         if (Minecraft.getInstance().screen != null) {
            Minecraft.getInstance().screen.setFocused(this);
         }

         double leftX = this.getX();
         double topY = this.getY();
         double rightX = leftX + this.svSquareSize;
         double botY = topY + this.svSquareSize;
         boolean updated = false;
         if (mouseX >= leftX && mouseX <= rightX && mouseY >= topY && mouseY <= botY) {
            if (playSound) {
               this.playDownSound(Minecraft.getInstance().getSoundManager());
            }

            this.value = 1.0F - (float)((mouseY - topY) / (botY - topY));
            this.saturation = (float)((mouseX - leftX) / (rightX - leftX));
            updated = true;
         }

         leftX = rightX + 10.0;
         rightX = leftX + this.hBarWidth;
         if (mouseX >= leftX && mouseX <= rightX && mouseY >= topY && mouseY <= botY) {
            if (playSound) {
               this.playDownSound(Minecraft.getInstance().getSoundManager());
            }

            this.hue = 1.0F - (float)((mouseY - topY) / (botY - topY));
            updated = true;
         }

         this.argbColor = Color.HSBtoRGB(this.hue, this.saturation, this.value);
         this.argbHueOnly = Color.HSBtoRGB(this.hue, 1.0F, 1.0F);
         if (updated) {
            this.runUpdater();
         }

         return updated;
      } else {
         return false;
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return this.mouseEvent(mouseX, mouseY, button, true);
   }

   protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
      this.mouseEvent(mouseX, mouseY, 0, false);
   }

   public void runUpdater() {
      if (this.updater != null) {
         this.updater.doUpdate((int)(this.hue * 360.0F), (int)(this.saturation * 100.0F), (int)(this.value * 100.0F));
      }
   }

   public void setUpdater(ColorUpdater updater) {
      this.updater = updater;
   }

   public void updateHSV(int h, int s, int v) {
      this.hue = h / 360.0F;
      this.saturation = s / 100.0F;
      this.value = v / 100.0F;
      this.argbColor = Color.HSBtoRGB(this.hue, this.saturation, this.value);
      this.argbHueOnly = Color.HSBtoRGB(this.hue, 1.0F, 1.0F);
      this.runUpdater();
   }

   public void updateRGB(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      float[] hsv = Color.RGBtoHSB(r, g, b, null);
      this.hue = hsv[0];
      this.saturation = hsv[1];
      this.value = hsv[2];
      this.argbColor = Color.HSBtoRGB(this.hue, this.saturation, this.value);
      this.argbHueOnly = Color.HSBtoRGB(this.hue, 1.0F, 1.0F);
      this.runUpdater();
   }

   public int colorRGB() {
      return this.argbColor & 16777215;
   }

   protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
   }

   public interface ColorUpdater {
      void doUpdate(int var1, int var2, int var3);
   }
}
