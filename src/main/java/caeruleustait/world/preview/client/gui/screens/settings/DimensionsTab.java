package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import caeruleustait.world.preview.client.gui.widgets.lists.BaseObjectSelectionList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

public class DimensionsTab implements Tab {
   private final RenderSettings renderSettings;
   private final WGLabel headLabel;
   private final DimensionList dimensionList;

   public DimensionsTab(Minecraft minecraft, List<ResourceLocation> levelStemKeys) {
      this.renderSettings = WorldPreview.get().renderSettings();
      this.headLabel = new WGLabel(minecraft.font, 0, 0, 256, 20, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_DIM_HEAD, -1);
      this.dimensionList = new DimensionList(minecraft, 256, 100, 0, 0);
      this.dimensionList.replaceEntries(levelStemKeys.stream().map(this.dimensionList::entryFactory).toList());
      this.dimensionList.select(this.renderSettings.dimension);
   }

   @NotNull
   public Component getTabTitle() {
      return WorldPreviewComponents.SETTINGS_DIM_TITLE;
   }

   public void visitChildren(Consumer<AbstractWidget> consumer) {
      consumer.accept(this.headLabel);
      consumer.accept(this.dimensionList);
   }

   public void doLayout(ScreenRectangle rectangle) {
      int width = Math.min(rectangle.width() - 8, 256);
      int center = rectangle.left() + rectangle.width() / 2;
      int left = center - width / 2;
      int top = rectangle.top() + 4;
      int bottom = rectangle.bottom() - 16;
      this.headLabel.setWidth(width);
      this.headLabel.setPosition(left, top);
      top += 24;
      this.dimensionList.setPosition(left, top);
      this.dimensionList.setSize(width, bottom - top);
   }

   public class DimensionList extends BaseObjectSelectionList<DimensionList.DimensionEntry> {
      public DimensionList(Minecraft minecraft, int width, int height, int x, int y) {
         super(minecraft, width, height, x, y, 16);
      }

      public DimensionEntry entryFactory(ResourceLocation dimensionKey) {
         return new DimensionEntry(dimensionKey);
      }

      public void select(ResourceLocation dimensionKey) {
         for (DimensionEntry entry : this.children()) {
            if (entry.dimensionKey.equals(dimensionKey)) {
               this.setSelected(entry);
               return;
            }
         }

         this.setSelected(null);
      }

      public class DimensionEntry extends Entry<DimensionEntry> {
         private final ResourceLocation dimensionKey;
         private final Component component;

         public DimensionEntry(ResourceLocation dimensionKey) {
            this.dimensionKey = dimensionKey;
            this.component = Component.literal(dimensionKey.toString());
         }

         @NotNull
         public Component getNarration() {
            return Component.literal("");
         }

         public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean bl, float partialTick) {
            guiGraphics.drawString(DimensionList.this.minecraft.font, this.component, left + 5, top + 2, 16777215);
         }

         public boolean mouseClicked(double d, double e, int i) {
            if (i != 0) {
               return false;
            } else {
               DimensionList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
               DimensionsTab.this.renderSettings.dimension = this.dimensionKey;
               return true;
            }
         }
      }
   }
}
