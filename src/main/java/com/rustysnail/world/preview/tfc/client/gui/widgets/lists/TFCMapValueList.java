package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import com.rustysnail.world.preview.tfc.WorldPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Scrollable side list of forest-type / tree-species categories, modeled on {@link BiomesList}
 * but backed by plain (id, name, color) entries so it needs no biome registry holders.
 * Reused for both TFC_FOREST_TYPE and TFC_TREE_SPECIES modes. Colors are normal ARGB and
 * rendered as swatches via {@link WorldPreview#nativeColor(int)}.
 */
public class TFCMapValueList extends BaseObjectSelectionList<TFCMapValueList.ValueEntry>
{
    private Consumer<ValueEntry> onValueSelected;

    public TFCMapValueList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public ValueEntry createEntry(short id, String name, int color)
    {
        return new ValueEntry(id, name, color);
    }

    public void setChangeListener(Consumer<ValueEntry> onValueSelected)
    {
        this.onValueSelected = onValueSelected;
    }

    public void setSelected(@Nullable ValueEntry entry)
    {
        this.setSelected(entry, false);
    }

    public void setSelected(@Nullable ValueEntry entry, boolean centerScroll)
    {
        super.setSelected(entry);
        if (centerScroll && entry != null)
        {
            super.centerScrollOn(entry);
        }
        if (this.onValueSelected != null)
        {
            this.onValueSelected.accept(entry);
        }
    }

    @Nullable
    public ValueEntry getEntryById(short id)
    {
        for (ValueEntry entry : this.children())
        {
            if (entry.id == id)
            {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void replaceEntries(@NotNull Collection<ValueEntry> entryList)
    {
        ValueEntry oldEntry = this.getSelected();
        super.replaceEntries(entryList);
        if (oldEntry != null && entryList.contains(oldEntry))
        {
            this.setSelected(oldEntry);
        }
        double maxScroll = Math.max(0.0, (super.getItemCount() * super.itemHeight - super.height));
        if (super.getScrollAmount() > maxScroll)
        {
            super.setScrollAmount(maxScroll);
        }
    }

    public class ValueEntry extends Entry<ValueEntry>
    {
        private final short id;
        private final String name;
        private final int color;
        private final Tooltip tooltip;

        public ValueEntry(short id, String name, int color)
        {
            this.id = id;
            this.name = name;
            this.color = color;
            this.tooltip = Tooltip.create(Component.literal(name));
        }

        public short id()
        {
            return this.id;
        }

        public String name()
        {
            return this.name;
        }

        public int color()
        {
            return this.color;
        }

        @Override
        public Tooltip tooltip()
        {
            return this.tooltip;
        }

        @NotNull
        public Component getNarration()
        {
            return Component.translatable("narrator.select", this.name);
        }

        public void render(@NotNull GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f)
        {
            guiGraphics.fill(k + 3, j + 1, k + 13, j + 11, WorldPreview.nativeColor(this.color));
            guiGraphics.drawString(TFCMapValueList.this.minecraft.font, this.name, k + 16, j + 2, 16777215);
        }

        public boolean mouseClicked(double d, double e, int i)
        {
            if (i != 0)
            {
                return false;
            }
            TFCMapValueList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            ValueEntry selected = TFCMapValueList.this.getSelected();
            boolean isSelected = selected != null && this.id == selected.id;
            if (isSelected)
            {
                TFCMapValueList.this.setSelected(null);
                return false;
            }
            return true;
        }
    }
}
