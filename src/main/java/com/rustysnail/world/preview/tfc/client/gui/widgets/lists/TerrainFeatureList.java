package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TerrainFeatureList extends BaseObjectSelectionList<TerrainFeatureList.Entry>
{

    public TerrainFeatureList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public Entry createEntry(SearchableFeature feature)
    {
        return new Entry(feature);
    }

    public Set<SearchableFeature> getCheckedFeatures()
    {
        Set<SearchableFeature> result = new HashSet<>();
        for (int i = 0; i < this.getItemCount(); i++)
        {
            Entry entry = this.getEntry(i);
            if (entry.checked)
            {
                result.add(entry.feature);
            }
        }
        return result;
    }

    public void clearChecks()
    {
        for (int i = 0; i < this.getItemCount(); i++)
        {
            this.getEntry(i).checked = false;
        }
    }

    @Override
    public void replaceEntries(@NotNull Collection<Entry> entryList)
    {
        super.replaceEntries(entryList);
        double maxScroll = Math.max(0.0, (super.getItemCount() * super.itemHeight - super.height));
        if (super.getScrollAmount() > maxScroll)
        {
            super.setScrollAmount(maxScroll);
        }
    }

    public class Entry extends BaseObjectSelectionList.Entry<Entry>
    {
        private final SearchableFeature feature;
        private final String name;
        private boolean checked = false;
        private boolean enabled = true;
        private final Tooltip tooltip;

        public Entry(SearchableFeature feature)
        {
            this.feature = feature;
            this.name = feature.name().getString();

            String tipText = feature.id().toString();
            this.tooltip = Tooltip.create(Component.literal(tipText));
        }

        public SearchableFeature feature()
        {
            return this.feature;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
            if (!enabled)
            {
                this.checked = false;
            }
        }

        @Override
        @Nullable
        public Tooltip tooltip()
        {
            return this.tooltip;
        }

        @NotNull
        @Override
        public Component getNarration()
        {
            return Component.translatable("narrator.select", this.name);
        }

        @Override
        public void render(@NotNull GuiGraphics gg, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick)
        {
            int textColor = this.enabled ? 0xFFFFFF : 0x777777;

            int cbX = left + 2;
            int cbY = top + 1;
            BaseObjectSelectionList.renderCheckbox(gg, cbX, cbY, this.checked, this.enabled);

            gg.drawString(TerrainFeatureList.this.minecraft.font, this.name, left + 15, top + 2, textColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (button == 0)
            {
                if (!this.enabled)
                {
                    return false;
                }
                this.checked = !this.checked;
                return true;
            }
            return false;
        }
    }

    public int entryCount()
    {
        return this.children().size();
    }

    @org.jetbrains.annotations.Nullable
    public Entry entryAt(int index)
    {
        if (index < 0 || index >= this.children().size()) return null;
        return this.children().get(index);
    }

}
