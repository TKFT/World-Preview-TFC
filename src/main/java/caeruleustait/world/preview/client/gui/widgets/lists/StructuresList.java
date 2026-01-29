package caeruleustait.world.preview.client.gui.widgets.lists;

import caeruleustait.world.preview.client.WorldPreviewClient;
import caeruleustait.world.preview.client.gui.PreviewDisplayDataProvider;
import caeruleustait.world.preview.client.gui.screens.PreviewContainer;
import caeruleustait.world.preview.client.gui.widgets.ToggleButton;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Collection;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StructuresList extends BaseObjectSelectionList<StructuresList.StructureEntry> {
   public StructuresList(Minecraft minecraft, int width, int height, int x, int y) {
      super(minecraft, width, height, x, y, 24);
   }

   public StructureEntry createEntry(
      short id, ResourceLocation resourceLocation, NativeImage icon, Item item, String name, boolean show, boolean showByDefault
   ) {
      return new StructureEntry(id, resourceLocation, icon, item, name, show, showByDefault);
   }

   @Override
   public void replaceEntries(@NotNull Collection<StructureEntry> entryList) {
      super.replaceEntries(entryList);
      double maxScroll = Math.max(0.0, (super.getItemCount() * super.itemHeight - super.height));
      if (super.getScrollAmount() > maxScroll) {
         super.setScrollAmount(maxScroll);
      }
   }

   public class StructureEntry extends Entry<StructureEntry> implements PreviewDisplayDataProvider.StructureRenderInfo {
      private final short id;
       private final Item item;
      private final ItemStack itemStack;
      private final DynamicTexture iconTexture;
      private final int iconWidth;
      private final int iconHeight;
      private final String name;
      private final Tooltip tooltip;
      private final boolean showByDefault;
      private final boolean isPrimaryNamespace;
      private boolean show;
      public final ToggleButton toggleVisible;

      public StructureEntry(
         short id,
         @NotNull ResourceLocation resourceLocation,
         @NotNull NativeImage icon,
         @Nullable Item item,
         String name,
         boolean show,
         boolean showByDefault
      ) {
         this.id = id;
         this.item = item;
         this.itemStack = this.item == null ? null : new ItemStack(this.item, 1);
         this.iconTexture = new DynamicTexture(icon);
         this.iconWidth = icon.getWidth();
         this.iconHeight = icon.getHeight();
         this.showByDefault = showByDefault;
         this.show = show;
         this.toggleVisible = new ToggleButton(0, 0, 20, 20, 140, 20, 20, 20, PreviewContainer.BUTTONS_TEXTURE, 400, 60, this::toggleVisible);
         this.iconTexture.upload();
         this.toggleVisible.selected = show;
         this.isPrimaryNamespace = resourceLocation.getNamespace().equals("minecraft");
         if (!Objects.equals(resourceLocation.toString(), name) && name != null) {
            this.name = name;
         } else {
            this.name = WorldPreviewClient.toTitleCase(resourceLocation.getPath().replace("_", " "));
         }

         String tag = "§5§o" + resourceLocation.getNamespace() + "§r\n§9" + resourceLocation.getPath() + "§r";
         this.tooltip = Tooltip.create(Component.literal(this.name + "\n\n" + tag));
      }

      public void reset() {
         this.show = this.showByDefault;
         this.toggleVisible.selected = this.show;
      }

      private void toggleVisible(Button btn) {
         this.show = this.toggleVisible.selected;
      }

      public void setVisible(boolean show) {
         this.show = show;
      }

      @Override
      public Tooltip tooltip() {
         return this.tooltip;
      }

      @NotNull
      public Component getNarration() {
         return Component.empty();
      }

      public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean bl, float partialTick) {
         int xMin = left + 2;
         int yMin = top + 2;
         int xMax = xMin + this.iconWidth;
         int yMax = yMin + this.iconHeight;
         if (this.item != null) {
            guiGraphics.renderItem(this.itemStack, xMin, yMin);
         } else {
            WorldPreviewClient.renderTexture(this.iconTexture, xMin, yMin, xMax, yMax);
         }

         String formatName = this.isPrimaryNamespace ? this.name : "§o" + this.name;
         guiGraphics.drawString(StructuresList.this.minecraft.font, formatName, left + 16 + 4, top + 6, 16777215);
         this.toggleVisible.setPosition(StructuresList.this.getRowRight() - 22, top);
         this.toggleVisible.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         StructuresList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         if (this.toggleVisible.isMouseOver(mouseX, mouseY)) {
            this.toggleVisible.onClick(mouseX, mouseY);
         }

         return true;
      }

      public String name() {
         return this.name;
      }

      public boolean showByDefault() {
         return this.showByDefault;
      }

      @Override
      public boolean show() {
         return this.show;
      }

      public short id() {
         return this.id;
      }

      public Item item() {
         return this.item;
      }

      public ItemStack itemStack() {
         return this.itemStack;
      }
   }
}
