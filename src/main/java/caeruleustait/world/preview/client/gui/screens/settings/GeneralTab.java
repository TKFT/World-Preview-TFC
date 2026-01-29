package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.widgets.SelectionSlider;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;

public class GeneralTab extends GridLayoutTab {
   public GeneralTab(Minecraft minecraft) {
      super(WorldPreviewComponents.SETTINGS_GENERAL_TITLE);
      WorldPreviewConfig cfg = WorldPreview.get().cfg();
      List<ThreadCount> threadCounts = new ArrayList<>(Runtime.getRuntime().availableProcessors());

      for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++) {
         threadCounts.add(new ThreadCount(i));
      }

      SelectionSlider<ThreadCount> threadsSlider = new SelectionSlider<>(
         0, 0, 320, 20, threadCounts, threadCounts.get(cfg.numThreads() - 1), x -> cfg.setNumThreads(x.value)
      );
      Checkbox cbBg = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_BG, minecraft.font)
         .selected(cfg.backgroundSampleVertChunk)
         .onValueChange((box, val) -> cfg.backgroundSampleVertChunk = val)
         .build();
      Checkbox cbFc = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_FC, minecraft.font)
         .selected(cfg.buildFullVertChunk)
         .onValueChange((box, val) -> cfg.buildFullVertChunk = val)
         .build();
      Checkbox cbStruct = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_STRUCT, minecraft.font)
         .selected(cfg.sampleStructures)
         .onValueChange((box, val) -> cfg.sampleStructures = val)
         .build();
      Checkbox cbHm = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_HEIGHTMAP, minecraft.font)
         .selected(cfg.sampleHeightmap)
         .onValueChange((box, val) -> cfg.sampleHeightmap = val)
         .build();
      Checkbox cbCtrl = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_CONTROLS, minecraft.font)
         .selected(cfg.showControls)
         .onValueChange((box, val) -> cfg.showControls = val)
         .build();
      Checkbox cbFt = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_FRAMETIME, minecraft.font)
         .selected(cfg.showFrameTime)
         .onValueChange((box, val) -> cfg.showFrameTime = val)
         .build();
      Checkbox cbPause = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_SHOW_IN_MENU, minecraft.font)
         .selected(cfg.showInPauseMenu)
         .onValueChange((box, val) -> cfg.showInPauseMenu = val)
         .build();
      Checkbox cbPlayer = Checkbox.builder(WorldPreviewComponents.SETTINGS_GENERAL_SHOW_PLAYER, minecraft.font)
         .selected(cfg.showPlayer)
         .onValueChange((box, val) -> cfg.showPlayer = val)
         .build();
      threadsSlider.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_THREADS_TOOLTIP));
      cbFc.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_FC_TOOLTIP));
      cbBg.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_BG_TOOLTIP));
      cbStruct.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_STRUCT_TOOLTIP));
      cbHm.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_HEIGHTMAP_TOOLTIP));
      cbCtrl.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_CONTROLS_TOOLTIP));
      cbFt.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_FRAMETIME_TOOLTIP));
      cbPause.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_SHOW_IN_MENU_TOOLTIP));
      cbPlayer.setTooltip(Tooltip.create(WorldPreviewComponents.SETTINGS_GENERAL_SHOW_PLAYER_TOOLTIP));
      RowHelper rowHelper = this.layout.rowSpacing(4).createRowHelper(2);
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_GENERAL_HEAD, 16777215), 2);
      rowHelper.addChild(threadsSlider, 2);
      rowHelper.addChild(cbFc, 2);
      rowHelper.addChild(cbBg, 2);
      rowHelper.addChild(cbStruct, 1);
      rowHelper.addChild(cbHm, 1);
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 200, 2, WGLabel.TextAlignment.CENTER, Component.literal(""), 16777215), 2);
      rowHelper.addChild(cbCtrl);
      rowHelper.addChild(cbFt);
      rowHelper.addChild(cbPause);
      rowHelper.addChild(cbPlayer);
   }

   public record ThreadCount(int value) implements SelectionSlider.SelectionValues {

      @Override
         public Component message() {
            return Component.translatable("world_preview.settings.general.threads", this.value);
         }
      }
}
