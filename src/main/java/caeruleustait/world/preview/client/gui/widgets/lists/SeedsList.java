package caeruleustait.world.preview.client.gui.widgets.lists;

import caeruleustait.world.preview.client.gui.screens.PreviewContainer;
import caeruleustait.world.preview.client.gui.widgets.OldStyleImageButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

public class SeedsList extends BaseObjectSelectionList<SeedsList.SeedEntry> {
   private final PreviewContainer previewContainer;
   private final boolean seedCanChange;

   public SeedsList(Minecraft minecraft, PreviewContainer previewContainer) {
      super(minecraft, 100, 100, 0, 0, 24);
      this.previewContainer = previewContainer;
      this.seedCanChange = this.previewContainer.dataProvider().seedIsEditable();
   }

   public SeedEntry createEntry(String seed) {
      return new SeedEntry(this, seed);
   }

   public class SeedEntry extends Entry<SeedEntry> {
      public final SeedsList seedsList;
      public final String seed;
      public final Button deleteButton;

      public SeedEntry(SeedsList seedsList, String seed) {
         this.seedsList = seedsList;
         this.seed = seed;
         this.deleteButton = new OldStyleImageButton(0, 0, 20, 20, 40, 20, 20, PreviewContainer.BUTTONS_TEXTURE, 400, 60, this::deleteEntry);
         this.deleteButton.active = SeedsList.this.seedCanChange;
      }

      private void deleteEntry(Button btn) {
         this.seedsList.previewContainer.deleteSeed(this.seed);
      }

      @NotNull
      public Component getNarration() {
         return Component.translatable("narrator.select", this.seed);
      }

      public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean bl, float partialTick) {
         guiGraphics.drawString(this.seedsList.minecraft.font, this.seed, left + 4, top + 6, SeedsList.this.seedCanChange ? 16777215 : 10066329);
         this.deleteButton.setPosition(this.seedsList.getRowRight() - 22, top);
         this.deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      public boolean mouseClicked(double d, double e, int i) {
         if (!SeedsList.this.seedCanChange) {
            return true;
         } else {
            if (this.deleteButton.isHovered()) {
               this.deleteButton.mouseClicked(d, e, i);
            }

            if (i == 0 && d < this.seedsList.getRowRight() - 22) {
               this.seedsList.setSelected(this);
               this.seedsList.previewContainer.setSeed(this.seed);
               SeedsList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
               return true;
            } else {
               return false;
            }
         }
      }
   }
}
