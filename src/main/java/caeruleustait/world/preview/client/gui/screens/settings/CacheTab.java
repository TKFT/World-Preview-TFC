package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.storage.PreviewStorageCacheManager;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;

public class CacheTab extends GridLayoutTab {
   private final PreviewStorageCacheManager cacheManager;

   public CacheTab(Minecraft minecraft, PreviewStorageCacheManager cacheManager) {
      super(WorldPreviewComponents.SETTINGS_CACHE_TITLE);
      this.cacheManager = cacheManager;
      WorldPreviewConfig cfg = WorldPreview.get().cfg();
      int LINE_WIDTH = 320;
      Checkbox cbGameEnable = Checkbox.builder(WorldPreviewComponents.SETTINGS_CACHE_G_ENABLE, minecraft.font)
         .selected(cfg.cacheInGame)
         .onValueChange((box, val) -> cfg.cacheInGame = val)
         .build();
      Checkbox cbNewEnable = Checkbox.builder(WorldPreviewComponents.SETTINGS_CACHE_N_ENABLE, minecraft.font)
         .selected(cfg.cacheInNew)
         .onValueChange((box, val) -> cfg.cacheInNew = val)
         .build();
      Checkbox cbCompressEnable = Checkbox.builder(WorldPreviewComponents.SETTINGS_CACHE_COMPRESSION, minecraft.font)
         .selected(cfg.enableCompression)
         .onValueChange((box, val) -> cfg.enableCompression = val)
         .build();
      cbCompressEnable.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_CACHE_COMPRESSION_TOOLTIP));
      Button btnClear = Button.builder(WorldPreviewComponents.SETTINGS_CACHE_CLEAR, this::onClearCache)
         .tooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_CACHE_CLEAR_TOOLTIP))
         .width(320)
         .build();
      RowHelper rowHelper = this.layout.rowSpacing(8).createRowHelper(1);
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_CACHE_DESC, 16777215));
      rowHelper.addChild(cbGameEnable);
      rowHelper.addChild(cbNewEnable);
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, Component.empty(), 16777215));
      rowHelper.addChild(btnClear);
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, Component.empty(), 16777215));
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, Component.empty(), 16777215));
      rowHelper.addChild(cbCompressEnable);
   }

   private void onClearCache(Button btn) {
      this.cacheManager.clearCache();
   }
}
