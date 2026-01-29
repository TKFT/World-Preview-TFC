package caeruleustait.world.preview.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PreviewCacheLoadingScreen extends Screen {
   protected PreviewCacheLoadingScreen(Component component) {
      super(component);
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      int var10003 = this.width / 2;
      int var10004 = this.height / 2;
      guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.title, var10003, var10004, 16777215);
   }
}
