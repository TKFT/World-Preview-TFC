package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.widgets.SelectionSlider;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;

public class SamplingTab extends GridLayoutTab {
   public SamplingTab(Minecraft minecraft) {
      super(WorldPreviewComponents.SETTINGS_SAMPLE_TITLE);
      RowHelper rowHelper = this.layout.rowSpacing(8).createRowHelper(1);
      RenderSettings renderSettings = WorldPreview.get().renderSettings();
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 320, 20, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_SAMPLE_HEAD, 16777215));
      rowHelper.addChild(
         new WGLabel(minecraft.font, 0, 0, 320, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_SAMPLE_PIXELS_TITLE_1, 13421772)
      );
      rowHelper.addChild(
         new WGLabel(minecraft.font, 0, 0, 320, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_SAMPLE_PIXELS_TITLE_2, 13421772)
      );
      rowHelper.addChild(
         new SelectionSlider<PixelsPerChunk>(
            0,
            0,
            320,
            20,
            List.of(
               PixelsPerChunk.NUM_1,
               PixelsPerChunk.NUM_2,
               PixelsPerChunk.NUM_4,
               PixelsPerChunk.NUM_8,
               PixelsPerChunk.NUM_16
            ),
            PixelsPerChunk.of(renderSettings.pixelsPerChunk()),
            x -> renderSettings.setPixelsPerChunk(x.value)
         )
      );
      rowHelper.addChild(new WGLabel(minecraft.font, 0, 0, 200, 10, WGLabel.TextAlignment.CENTER, Component.literal(""), 16777215));
      rowHelper.addChild(
         new WGLabel(minecraft.font, 0, 0, 320, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_SAMPLE_SAMPLE_TITLE_1, 13421772)
      );
      rowHelper.addChild(
         new WGLabel(minecraft.font, 0, 0, 320, 10, WGLabel.TextAlignment.CENTER, WorldPreviewComponents.SETTINGS_SAMPLE_SAMPLE_TITLE_2, 13421772)
      );
      rowHelper.addChild(
         new SelectionSlider<GUISamplesType>(
            0,
            0,
            320,
            20,
            List.of(GUISamplesType.AUTO, GUISamplesType.FULL, GUISamplesType.QUARTER, GUISamplesType.SINGLE),
            GUISamplesType.of(renderSettings.samplerType),
            x -> renderSettings.samplerType = x.samplerType
         )
      );
   }

   public enum GUISamplesType implements SelectionSlider.SelectionValues {
      AUTO(RenderSettings.SamplerType.AUTO),
      FULL(RenderSettings.SamplerType.FULL),
      QUARTER(RenderSettings.SamplerType.QUARTER),
      SINGLE(RenderSettings.SamplerType.SINGLE);

      public final RenderSettings.SamplerType samplerType;

      public static GUISamplesType of(RenderSettings.SamplerType typ) {
         return valueOf(typ.name());
      }

      @Override
      public Component message() {
         return Component.translatable("world_preview.settings.sample.sampler.name." + this.name());
      }

      GUISamplesType(RenderSettings.SamplerType samplerType) {
         this.samplerType = samplerType;
      }
   }

   public enum PixelsPerChunk implements SelectionSlider.SelectionValues {
      NUM_16(16),
      NUM_8(8),
      NUM_4(4),
      NUM_2(2),
      NUM_1(1);

      public final int value;

      PixelsPerChunk(int value) {
         this.value = value;
      }

      public static PixelsPerChunk of(int num) {
         return valueOf("NUM_" + num);
      }

      @Override
      public Component message() {
         return Component.translatable("world_preview.settings.sample.numChunk.name." + this.name());
      }
   }
}
