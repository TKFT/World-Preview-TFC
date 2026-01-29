package caeruleustait.world.preview.client.gui.widgets.lists;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.client.WorldPreviewClient;
import caeruleustait.world.preview.client.gui.screens.PreviewContainer;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder.Reference;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BiomesList extends BaseObjectSelectionList<BiomesList.BiomeEntry> {
   private Consumer<BiomeEntry> onBiomeSelected;
   private final boolean allowDeselecting;
   private final PreviewContainer previewContainer;

   public BiomesList(PreviewContainer previewContainer, Minecraft minecraft, int width, int height, int x, int y, boolean allowDeselecting) {
      super(minecraft, width, height, x, y, 16);
      this.allowDeselecting = allowDeselecting;
      this.previewContainer = previewContainer;
   }

   public BiomeEntry createEntry(
      Reference<Biome> entry,
      short id,
      int color,
      int initialColor,
      boolean isCave,
      boolean initialIsCave,
      String explicitName,
      PreviewData.DataSource dataSource
   ) {
      return new BiomeEntry(entry, id, color, initialColor, isCave, initialIsCave, explicitName, dataSource);
   }

   public void setSelected(@Nullable BiomesList.BiomeEntry entry) {
      this.setSelected(entry, false);
   }

   public void setSelected(@Nullable BiomesList.BiomeEntry entry, boolean centerScroll) {
      super.setSelected(entry);
      if (centerScroll) {
          assert entry != null;
          super.centerScrollOn(entry);
      }

      this.onBiomeSelected.accept(entry);
   }

   public void setBiomeChangeListener(Consumer<BiomeEntry> onBiomeSelected) {
      this.onBiomeSelected = onBiomeSelected;
   }

   @Override
   public void replaceEntries(@NotNull Collection<BiomeEntry> entryList) {
      BiomeEntry oldEntry = this.getSelected();
      super.replaceEntries(entryList);
      if (entryList.contains(oldEntry)) {
         this.setSelected(oldEntry);
      }

      double maxScroll = Math.max(0.0, (super.getItemCount() * super.itemHeight - super.height));
      if (super.getScrollAmount() > maxScroll) {
         super.setScrollAmount(maxScroll);
      }
   }

   public class BiomeEntry extends Entry<BiomeEntry> {
      private final short id;
      private final String name;
      private int color;
      private boolean isCave;
      private final int initialColor;
      private final boolean initialIsCave;
      private final Reference<Biome> entry;
      private PreviewData.DataSource dataSource;
      private final Tooltip tooltip;
      private final PreviewData.DataSource initialDataSource;
      private final boolean isPrimaryNamespace;

      public BiomeEntry(
         Reference<Biome> entry,
         short id,
         int color,
         int initialColor,
         boolean isCave,
         boolean initialIsCave,
         String explicitName,
         PreviewData.DataSource dataSource
      ) {
         this.entry = entry;
         this.id = id;
         this.color = color;
         this.initialColor = initialColor;
         this.isCave = isCave;
         this.initialIsCave = initialIsCave;
         this.dataSource = dataSource;
         this.initialDataSource = dataSource;
         ResourceLocation resourceLocation = entry.key().location();
         String langKey = resourceLocation.toLanguageKey("biome");
         if (Language.getInstance().has(langKey)) {
            this.name = Component.translatable(langKey).getString();
         } else if (explicitName != null && !explicitName.isBlank()) {
            this.name = explicitName;
         } else {
            this.name = WorldPreviewClient.toTitleCase(resourceLocation.getPath().replace("_", " "));
         }

         this.isPrimaryNamespace = resourceLocation.getNamespace().equals("minecraft");
         String tag = "§5§o" + resourceLocation.getNamespace() + "§r\n§9" + resourceLocation.getPath() + "§r";
         this.tooltip = Tooltip.create(Component.literal(this.name + "\n\n" + tag));
      }

      public String name() {
         return this.name;
      }

      public Component statusComponent() {
         return Component.translatable("world_preview.settings.biomes.source." + this.dataSource.name());
      }

      public Reference<Biome> entry() {
         return this.entry;
      }

      public short id() {
         return this.id;
      }

      public int color() {
         return this.color;
      }

      public boolean isCave() {
         return this.isCave;
      }

      public PreviewData.DataSource dataSource() {
         return this.dataSource;
      }

      public PreviewContainer previewTab() {
         return BiomesList.this.previewContainer;
      }

      @Override
      public Tooltip tooltip() {
         return this.tooltip;
      }

      public void reset() {
         this.color = this.initialColor;
         this.isCave = this.initialIsCave;
         this.dataSource = this.initialDataSource == PreviewData.DataSource.CONFIG ? PreviewData.DataSource.RESOURCE : this.initialDataSource;
      }

      public void changeColor(int newColor) {
         this.color = newColor & 16777215;
         this.dataSource = PreviewData.DataSource.CONFIG;
      }

      public void setCave(boolean cave) {
         this.isCave = cave;
      }

      @NotNull
      public Component getNarration() {
         return Component.translatable("narrator.select", this.name);
      }

      public void render(@NotNull GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f) {
         guiGraphics.fill(k + 3, j + 1, k + 13, j + 11, WorldPreview.nativeColor(this.color));
         String formatName = this.isPrimaryNamespace ? this.name : "§o" + this.name;
         guiGraphics.drawString(BiomesList.this.minecraft.font, formatName, k + 16, j + 2, 16777215);
      }

      public boolean mouseClicked(double d, double e, int i) {
         if (i != 0) {
            return false;
         } else {
            BiomesList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            BiomeEntry selected = BiomesList.this.getSelected();
            boolean isSelected = selected != null && this.id == selected.id;
            if (isSelected && BiomesList.this.allowDeselecting) {
               BiomesList.this.setSelected(null);
               return false;
            } else {
               return true;
            }
         }
      }
   }
}
