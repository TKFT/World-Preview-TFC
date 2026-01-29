package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.screens.PreviewContainer;
import caeruleustait.world.preview.client.gui.widgets.ColorChooser;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import caeruleustait.world.preview.client.gui.widgets.lists.BiomesList;
import caeruleustait.world.preview.mixin.client.CheckboxAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import org.jetbrains.annotations.NotNull;

public class BiomesTab implements Tab {
   private final PreviewContainer previewContainer;
   private final Minecraft minecraft;
   private final GridLayout layout = new GridLayout();
   private final BiomesList biomesList;
   private final CycleButton<BiomeListFilter> filterCycleButton;
   private final List<AbstractWidget> toRender = new ArrayList<>();
   private final ColorChooser colorChooser;
   private BiomesList.BiomeEntry selectedEntry;
   private boolean blockUpdates = false;
   private final Button resetBtn;
   private final Button applyBtn;
   private final Checkbox isCaveCB;

   public BiomesTab(Minecraft _minecraft, PreviewContainer _previewTab) {
      this.previewContainer = _previewTab;
      this.minecraft = _minecraft;
      Font font = this.minecraft.font;
      this.biomesList = new BiomesList(this.previewContainer, this.minecraft, 100, 100, 0, 0, false);
      this.toRender.add(this.biomesList);
      this.colorChooser = new ColorChooser(0, 0);
      this.toRender.add(this.colorChooser);
      int EDIT_WIDTH = 36;
      int LABEL_WIDTH = 75;
      int COLUMN_SPACING = 8;
      int FULL_WIDTH = 120;
      this.filterCycleButton = CycleButton.builder(BiomeListFilter::toComponent)
         .withValues(BiomeListFilter.values())
         .withInitialValue(BiomeListFilter.DIMENSION)
         .create(0, 0, 120, 20, WorldPreviewComponents.COLOR_LIST_FILTER, (b, v) -> this.biomesList.replaceEntries(v.apply(this.previewContainer.allBiomes())));
      this.toRender.add(this.filterCycleButton);
      WGLabel statusLabel = new WGLabel(font, 0, 0, 120, 20, WGLabel.TextAlignment.CENTER, Component.literal(""), -5592406);
      EditBox hueBox = new EditBox(font, 0, 0, 36, 20, WorldPreviewComponents.COLOR_HUE);
      EditBox satBox = new EditBox(font, 0, 0, 36, 20, WorldPreviewComponents.COLOR_SAT);
      EditBox valBox = new EditBox(font, 0, 0, 36, 20, WorldPreviewComponents.COLOR_VAL);
      this.isCaveCB = Checkbox.builder(WorldPreviewComponents.COLOR_CAVE, this.minecraft.font)
         .selected(false)
         .onValueChange((x, y) -> this.updateStatus())
         .build();
      this.resetBtn = Button.builder(WorldPreviewComponents.COLOR_RESET, x -> {
         this.selectedEntry.reset();
         ((CheckboxAccessor)this.isCaveCB).setSelected(this.selectedEntry.isCave());
         this.colorChooser.updateRGB(this.selectedEntry.color());
         statusLabel.setText(this.selectedEntry.statusComponent());
      }).width(120).build();
      this.applyBtn = Button.builder(WorldPreviewComponents.COLOR_APPLY, x -> {
         this.selectedEntry.changeColor(this.colorChooser.colorRGB());
         this.selectedEntry.setCave(this.isCaveCB.selected());
         this.colorChooser.updateRGB(this.selectedEntry.color());
         statusLabel.setText(this.selectedEntry.statusComponent());
      }).width(120).build();
      hueBox.setFilter(x -> this.validateMaxInt(x, 360));
      satBox.setFilter(x -> this.validateMaxInt(x, 100));
      valBox.setFilter(x -> this.validateMaxInt(x, 100));
      Consumer<String> hsvConsumer = x -> {
         if (!this.blockUpdates) {
            this.colorChooser.updateHSV(this.intOrZero(hueBox.getValue()), this.intOrZero(satBox.getValue()), this.intOrZero(valBox.getValue()));
            this.colorChooser.runUpdater();
         }
      };
      hueBox.setResponder(hsvConsumer);
      satBox.setResponder(hsvConsumer);
      valBox.setResponder(hsvConsumer);
      this.colorChooser.setUpdater((h, s, v) -> {
         try {
            this.blockUpdates = true;
            this.updateIfChanged(hueBox, h);
            this.updateIfChanged(satBox, s);
            this.updateIfChanged(valBox, v);
            this.updateStatus();
         } finally {
            this.blockUpdates = false;
         }
      });
      this.biomesList.setBiomeChangeListener(biomeEntry -> {
         this.selectedEntry = biomeEntry;
         if (this.selectedEntry != null) {
            this.colorChooser.updateRGB(this.selectedEntry.color());
            ((CheckboxAccessor)this.isCaveCB).setSelected(this.selectedEntry.isCave());
            statusLabel.setText(this.selectedEntry.statusComponent());
         }
      });
      this.biomesList.setSelected(this.previewContainer.allBiomes().isEmpty() ? null : this.previewContainer.allBiomes().getFirst());
      this.layout.rowSpacing(4).columnSpacing(8);
      RowHelper rowHelper = this.layout.createRowHelper(2);
      rowHelper.addChild(new WGLabel(font, 0, 0, 75, 20, WGLabel.TextAlignment.LEFT, WorldPreviewComponents.COLOR_HUE, -1));
      rowHelper.addChild(hueBox);
      rowHelper.addChild(new WGLabel(font, 0, 0, 75, 20, WGLabel.TextAlignment.LEFT, WorldPreviewComponents.COLOR_SAT, -1));
      rowHelper.addChild(satBox);
      rowHelper.addChild(new WGLabel(font, 0, 0, 75, 20, WGLabel.TextAlignment.LEFT, WorldPreviewComponents.COLOR_VAL, -1));
      rowHelper.addChild(valBox);
      rowHelper.addChild(new WGLabel(font, 0, 0, 75, 10, WGLabel.TextAlignment.LEFT, Component.literal(""), 16777215));
      rowHelper.addChild(this.isCaveCB, 2);
      rowHelper.addChild(this.resetBtn, 2);
      rowHelper.addChild(this.applyBtn, 2);
      rowHelper.addChild(new WGLabel(font, 0, 0, 75, 10, WGLabel.TextAlignment.LEFT, Component.literal(""), 16777215));
      rowHelper.addChild(statusLabel, 2);
   }

   private void updateStatus() {
      if (this.selectedEntry != null) {
         this.resetBtn.active = this.selectedEntry.color() != this.colorChooser.colorRGB()
            || this.selectedEntry.isCave() != this.isCaveCB.selected()
            || this.selectedEntry.dataSource() == PreviewData.DataSource.CONFIG;
         this.applyBtn.active = this.selectedEntry.color() != this.colorChooser.colorRGB() || this.selectedEntry.isCave() != this.isCaveCB.selected();
         this.isCaveCB.active = true;
      } else {
         this.resetBtn.active = false;
         this.applyBtn.active = false;
         this.isCaveCB.active = false;
      }
   }

   @NotNull
   public Component getTabTitle() {
      return WorldPreviewComponents.SETTINGS_BIOMES_TITLE;
   }

   public void visitChildren(Consumer<AbstractWidget> consumer) {
      this.toRender.forEach(consumer);
      this.layout.visitWidgets(consumer);
   }

   public void doLayout(ScreenRectangle screenRectangle) {
      int leftWidth = screenRectangle.width() / 3;
      int left = screenRectangle.left() + 3;
      int top = screenRectangle.top() + 2;
      int bottom = screenRectangle.bottom() - 8;
      this.filterCycleButton.setPosition(left, top);
      this.filterCycleButton.setWidth(leftWidth);
      int listTop = top + 20 + 4;
      this.biomesList.setPosition(left, listTop);
      this.biomesList.setSize(leftWidth, bottom - listTop - 4);
      this.biomesList.replaceEntries(this.filterCycleButton.getValue().apply(this.previewContainer.allBiomes()));
      this.colorChooser.setSquareSize(screenRectangle.width() / 4);
      this.colorChooser.setPosition(left + leftWidth + 8, top + (bottom - top) / 2 - this.colorChooser.getHeight() / 2);
      this.layout.arrangeElements();
      left = this.colorChooser.getX() + this.colorChooser.getWidth();
      ScreenRectangle controlRectangle = new ScreenRectangle(left, top + 2, screenRectangle.right() - left + 16, bottom - top - 2);
      FrameLayout.alignInRectangle(this.layout, controlRectangle, 0.5F, 0.5F);
   }

   private boolean validateMaxInt(String in, int max) {
      if (in.isBlank()) {
         return true;
      } else {
         try {
            int i = Integer.parseInt(in);
            return i >= 0 && i <= max;
         } catch (NumberFormatException var4) {
            return false;
         }
      }
   }

   private int intOrZero(String src) {
      return src.isBlank() ? 0 : Integer.parseInt(src);
   }

   private void updateIfChanged(EditBox box, int value) {
      String strValue = String.valueOf(value);
      if (!box.getValue().equals(strValue)) {
         box.setValue(strValue);
      }
   }

   public enum BiomeListFilter {
      DIMENSION(
         x -> {
            LevelStem levelStem = x.previewTab().levelStemRegistry().get(WorldPreview.get().renderSettings().dimension);
            if (levelStem == null) {
               return true;
            } else {
               Set<ResourceLocation> supportedBiomes = levelStem.generator()
                  .getBiomeSource()
                  .possibleBiomes()
                  .stream()
                  .map(Holder::unwrapKey)
                  .map(Optional::orElseThrow)
                  .map(ResourceKey::location)
                  .collect(Collectors.toSet());
               return supportedBiomes.contains(x.entry().key().location());
            }
         }
      ),
      ALL(x -> true),
      MISSING(x -> x.dataSource() == PreviewData.DataSource.MISSING),
      CUSTOM(x -> x.dataSource() == PreviewData.DataSource.CONFIG),
      MISSING_CUSTOM(x -> x.dataSource() == PreviewData.DataSource.MISSING || x.dataSource() == PreviewData.DataSource.CONFIG),
      DATA_PACK(x -> x.dataSource() == PreviewData.DataSource.RESOURCE),
      DATA_PACK_CUSTOM(x -> x.dataSource() == PreviewData.DataSource.RESOURCE || x.dataSource() == PreviewData.DataSource.CONFIG);

      private final Predicate<BiomesList.BiomeEntry> filterFn;

      BiomeListFilter(Predicate<BiomesList.BiomeEntry> filterFn) {
         this.filterFn = filterFn;
      }

      public List<BiomesList.BiomeEntry> apply(List<BiomesList.BiomeEntry> orig) {
         return orig.stream().filter(this.filterFn).toList();
      }

      public static Component toComponent(BiomeListFilter x) {
         return Component.translatable("world_preview.settings.biomes.filter." + x.name());
      }
   }
}
