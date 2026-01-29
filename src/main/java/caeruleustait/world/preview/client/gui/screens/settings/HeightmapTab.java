package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.color.ColorMap;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.client.WorldPreviewClient;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import caeruleustait.world.preview.client.gui.widgets.lists.BaseObjectSelectionList;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

public class HeightmapTab implements Tab {
   private final WorldPreviewConfig cfg;
   private final WGLabel disabledWarning;
   private final WGLabel presetsHead;
   private final HeightPresetList heightPresetList;
   private final EditBox minYBox;
   private final EditBox maxYBox;
   private final WGLabel minYLabel;
   private final WGLabel maxYLabel;
   private final Checkbox visualYRange;
   private final WGLabel colormapHead;
   private final ColormapList colormapList;
   private final List<AbstractWidget> toRender = new ArrayList<>();

   public HeightmapTab(Minecraft minecraft, PreviewData previewData) {
      this.cfg = WorldPreview.get().cfg();
      Font font = minecraft.font;
      this.disabledWarning = new WGLabel(font, 0, 0, 100, 20, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_HEIGHTMAP_DISABLED, 16777215);
      this.toRender.add(this.disabledWarning);
      this.presetsHead = new WGLabel(font, 0, 0, 100, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_HEIGHTMAP_PRESETS, 16777215);
      this.colormapHead = new WGLabel(font, 0, 0, 100, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_HEIGHTMAP_COLORMAP, 16777215);
      this.toRender.add(this.presetsHead);
      this.toRender.add(this.colormapHead);
      this.minYBox = new EditBox(font, 0, 0, 100, 20, WorldPreviewComponents.SETTINGS_HEIGHTMAP_MIN_Y);
      this.maxYBox = new EditBox(font, 0, 0, 100, 20, WorldPreviewComponents.SETTINGS_HEIGHTMAP_MAX_Y);
      this.minYBox.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_HEIGHTMAP_MIN_Y_TOOLTIP));
      this.maxYBox.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_HEIGHTMAP_MAX_Y_TOOLTIP));
      this.minYBox.setFilter(HeightmapTab::isInteger);
      this.maxYBox.setFilter(HeightmapTab::isInteger);
      this.minYBox.setValue(String.valueOf(this.cfg.heightmapMinY));
      this.maxYBox.setValue(String.valueOf(this.cfg.heightmapMaxY));
      this.minYBox.setResponder(x -> this.cfg.heightmapMinY = x.isBlank() ? 0 : Integer.parseInt(x));
      this.maxYBox.setResponder(x -> this.cfg.heightmapMaxY = x.isBlank() ? 0 : Integer.parseInt(x));
      this.toRender.add(this.minYBox);
      this.toRender.add(this.maxYBox);
      this.minYLabel = new WGLabel(font, 0, 0, 64, 20, WGLabel.TextAlignment.LEFT, WorldPreviewComponents.SETTINGS_HEIGHTMAP_MIN_Y, 16777215);
      this.maxYLabel = new WGLabel(font, 0, 0, 64, 20, WGLabel.TextAlignment.LEFT, WorldPreviewComponents.SETTINGS_HEIGHTMAP_MAX_Y, 16777215);
      this.minYLabel.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_HEIGHTMAP_MIN_Y_TOOLTIP));
      this.maxYLabel.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_HEIGHTMAP_MAX_Y_TOOLTIP));
      this.toRender.add(this.minYLabel);
      this.toRender.add(this.maxYLabel);
      this.visualYRange = Checkbox.builder(WorldPreviewComponents.SETTINGS_HEIGHTMAP_VISUAL, minecraft.font)
         .selected(this.cfg.onlySampleInVisualRange)
         .onValueChange((box, val) -> this.cfg.onlySampleInVisualRange = val)
         .tooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_HEIGHTMAP_VISUAL_TOOLTIP))
         .build();
      this.toRender.add(this.visualYRange);
      this.heightPresetList = new HeightPresetList(minecraft, 100, 100, 0, 0);
      this.heightPresetList
         .replaceEntries(
            previewData.heightmapPresets().stream().map(entry -> this.heightPresetList.createEntry(entry.name(), entry.minY(), entry.maxY(), x -> {
               this.cfg.heightmapMinY = x.minY;
               this.cfg.heightmapMaxY = x.maxY;
               this.minYBox.setValue(String.valueOf(x.minY));
               this.maxYBox.setValue(String.valueOf(x.maxY));
            })).toList()
         );
      this.toRender.add(this.heightPresetList);
      this.colormapList = new ColormapList(minecraft, 100, 100, 0, 0);
      Map<String, ColormapList.ColormapEntry> colormaps = previewData.colorMaps()
         .values()
         .stream()
         .map(colorMap -> this.colormapList.createEntry(colorMap, x -> this.cfg.colorMap = x.colorMap.key().toString()))
         .collect(Collectors.toMap(x -> x.colorMap.key().toString(), x -> x));
      this.colormapList.replaceEntries(colormaps.values().stream().sorted(Comparator.comparing(x -> x.name)).toList());
      this.colormapList.setSelected(colormaps.get(this.cfg.colorMap));
      this.toRender.add(this.colormapList);
   }

   private static boolean isInteger(String s) {
      if (s.isBlank()) {
         return true;
      } else {
         try {
            Integer.parseInt(s);
            return true;
         } catch (NumberFormatException var2) {
            return false;
         }
      }
   }

   @NotNull
   public Component getTabTitle() {
      return WorldPreviewComponents.SETTINGS_HEIGHTMAP_TITLE;
   }

   public void visitChildren(@NotNull Consumer<AbstractWidget> consumer) {
      this.toRender.forEach(consumer);
   }

   public void doLayout(ScreenRectangle screenRectangle) {
      int center = screenRectangle.width() / 2;
      int leftL = screenRectangle.left() + 3;
      int leftR = center + 3;
      int topL = screenRectangle.top() + 2;
      int topR = topL;
      int bottomL = screenRectangle.bottom() - 36;
      int secWidth = screenRectangle.width() / 2 - leftL - 4;
      if (this.cfg.sampleHeightmap) {
         this.disabledWarning.visible = false;
      } else {
         this.disabledWarning.visible = true;
         this.disabledWarning.setPosition(screenRectangle.left(), topL);
         this.disabledWarning.setWidth(screenRectangle.width());
         topL += 24;
         topR = topL;
      }

      this.visualYRange.setPosition(leftL, bottomL);
      this.visualYRange.setWidth(secWidth);
      int var13 = bottomL - 24;
      this.maxYLabel.setPosition(leftL, var13);
      this.maxYLabel.setWidth(100);
      this.maxYBox.setPosition(leftL + 100, var13);
      this.maxYBox.setWidth(secWidth - 100);
      int var14 = var13 - 24;
      this.minYLabel.setPosition(leftL, var14);
      this.minYLabel.setWidth(100);
      this.minYBox.setPosition(leftL + 100, var14);
      this.minYBox.setWidth(secWidth - 100);
      this.presetsHead.setPosition(leftL, topL);
      this.presetsHead.setWidth(secWidth);
      topL += 14;
      this.heightPresetList.setPosition(leftL, topL);
      this.heightPresetList.setSize(secWidth, var14 - topL - 4);
      this.colormapHead.setPosition(leftR, topR);
      this.colormapHead.setWidth(secWidth);
      topR += 14;
      this.colormapList.setPosition(leftR, topR);
      this.colormapList.setSize(secWidth, bottomL - topR + 20);
   }

   public static class ColormapList extends BaseObjectSelectionList<ColormapList.ColormapEntry> {
      public ColormapList(Minecraft minecraft, int width, int height, int x, int y) {
         super(minecraft, width, height, x, y, 32);
      }

      public ColormapEntry createEntry(ColorMap colorMap, Consumer<ColormapEntry> onClick) {
         return new ColormapEntry(colorMap, onClick);
      }

      public class ColormapEntry extends Entry<ColormapEntry> {
         public final String name;
         public final ColorMap colorMap;
         private final Consumer<ColormapEntry> onClick;
         private final DynamicTexture colormapTexture;

         public ColormapEntry(ColorMap colorMap, Consumer<ColormapEntry> onClick) {
            this.name = colorMap.name();
            this.colorMap = colorMap;
            this.onClick = onClick;
            NativeImage colormapImg = new NativeImage(Format.RGBA, 1024, 1, true);
            this.colormapTexture = new DynamicTexture(colormapImg);

            for (int i = 0; i < 1024; i++) {
               colormapImg.setPixelRGBA(i, 0, colorMap.getARGB(i / 1024.0F));
            }

            this.colormapTexture.upload();
         }

         @NotNull
         public Component getNarration() {
            return Component.empty();
         }

         public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean bl, float partialTick) {
            guiGraphics.drawString(ColormapList.this.minecraft.font, this.name, left + 4, top + 2, 16777215);
            int xMin = left + 5;
            int yMin = top + 14;
            int xMax = left + width - 5;
            int yMax = top + height - 3;
            WorldPreviewClient.renderTexture(this.colormapTexture, xMin, yMin, xMax, yMax);
            guiGraphics.fill(xMin - 1, yMin - 1, xMax + 1, yMin, -6710887);
            guiGraphics.fill(xMax, yMin, xMax + 1, yMax, -6710887);
            guiGraphics.fill(xMin - 1, yMax, xMax + 1, yMax + 1, -6710887);
            guiGraphics.fill(xMin - 1, yMin, xMin, yMax, -6710887);
         }

         public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ColormapList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onClick.accept(this);
            return true;
         }
      }
   }

   public static class HeightPresetList extends BaseObjectSelectionList<HeightPresetList.HeightPresetEntry> {
      public HeightPresetList(Minecraft minecraft, int width, int height, int x, int y) {
         super(minecraft, width, height, x, y, 16);
      }

      public HeightPresetEntry createEntry(
         String name, int minY, int maxY, Consumer<HeightPresetEntry> onClick
      ) {
         return new HeightPresetEntry(name, minY, maxY, onClick);
      }

      public class HeightPresetEntry extends Entry<HeightPresetEntry> {
         public final String name;
         public final int minY;
         public final int maxY;
         private final Consumer<HeightPresetEntry> onClick;
         private final String displayString;

         public HeightPresetEntry(String name, int minY, int maxY, Consumer<HeightPresetEntry> onClick) {
            this.name = name;
            this.minY = minY;
            this.maxY = maxY;
            this.onClick = onClick;
            this.displayString = String.format("%s: §3y=§b%d§r§3-§b%d§r", this.name, this.minY, this.maxY);
         }

         @NotNull
         public Component getNarration() {
            return Component.empty();
         }

         public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean bl, float partialTick) {
            guiGraphics.drawString(HeightPresetList.this.minecraft.font, this.displayString, left + 4, top + 2, 16777215);
         }

         public boolean mouseClicked(double mouseX, double mouseY, int button) {
            HeightPresetList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onClick.accept(this);
            return true;
         }
      }
   }
}
