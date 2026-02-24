package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import com.rustysnail.world.preview.tfc.client.WorldPreviewClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BiomeCheckboxList extends BaseObjectSelectionList<BiomeCheckboxList.Entry>
{

    @Nullable
    private Runnable onChanged;

    public void setOnChanged(@Nullable Runnable onChanged)
    {
        this.onChanged = onChanged;
    }

    public BiomeCheckboxList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public Entry createEntry(Holder.Reference<Biome> biomeRef, int color)
    {
        return new Entry(biomeRef, color);
    }

    public Set<ResourceKey<Biome>> getCheckedBiomes()
    {
        Set<ResourceKey<Biome>> result = new HashSet<>();
        for (int i = 0; i < this.getItemCount(); i++)
        {
            Entry entry = this.getEntry(i);
            if (entry.checked)
            {
                result.add(entry.biomeRef.key());
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
        private final Holder.Reference<Biome> biomeRef;
        private final String name;
        private final int color;
        private boolean checked = false;
        private final Tooltip tooltip;

        public Entry(Holder.Reference<Biome> biomeRef, int color)
        {
            this.biomeRef = biomeRef;
            this.color = color;

            ResourceLocation loc = biomeRef.key().location();
            String langKey = loc.toLanguageKey("biome");
            if (Language.getInstance().has(langKey))
            {
                this.name = Component.translatable(langKey).getString();
            }
            else
            {
                this.name = WorldPreviewClient.toTitleCase(loc.getPath().replace("_", " "));
            }

            String tag = "§5§o" + loc.getNamespace() + "§r\n§9" + loc.getPath() + "§r";
            this.tooltip = Tooltip.create(Component.literal(this.name + "\n\n" + tag));
        }

        public String name()
        {
            return this.name;
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
            int cbX = left + 2;
            int cbY = top + 1;
            BaseObjectSelectionList.renderCheckbox(gg, cbX, cbY, this.checked);

            int swatchColor = 0xFF000000 | this.color;
            gg.fill(left + 15, top + 1, left + 25, top + 11, swatchColor);

            gg.drawString(BiomeCheckboxList.this.minecraft.font, this.name, left + 28, top + 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (button == 0)
            {
                this.checked = !this.checked;

                if (BiomeCheckboxList.this.onChanged != null)
                {
                    BiomeCheckboxList.this.onChanged.run();
                }
                return true;
            }
            return false;
        }
    }
}
