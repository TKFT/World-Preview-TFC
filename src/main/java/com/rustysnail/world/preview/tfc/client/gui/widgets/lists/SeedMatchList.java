package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import com.rustysnail.world.preview.tfc.backend.search.MatchResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SeedMatchList extends BaseObjectSelectionList<SeedMatchList.Entry>
{

    public SeedMatchList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 28);
    }

    public Entry createEntry(MatchResult result)
    {
        return new Entry(result);
    }

    @Override
    public void replaceEntries(@NotNull Collection<Entry> entryList)
    {
        Entry oldEntry = this.getSelected();
        super.replaceEntries(entryList);
        if (entryList.contains(oldEntry))
        {
            super.setSelected(oldEntry);
        }
        double maxScroll = Math.max(0.0, (super.getItemCount() * super.itemHeight - super.height));
        if (super.getScrollAmount() > maxScroll)
        {
            super.setScrollAmount(maxScroll);
        }
    }

    public void selectAndScrollToLast()
    {
        int count = this.getItemCount();
        if (count > 0)
        {
            Entry last = this.getEntry(count - 1);
            this.setSelected(last);
            this.centerScrollOn(last);
        }
    }

    public void selectAndScrollToResult(MatchResult target)
    {
        for (int i = 0; i < getItemCount(); i++)
        {
            Entry e = getEntry(i);
            if (e.result() == target || e.result().equals(target))
            {
                setSelected(e);
                centerScrollOn(e);
                return;
            }
        }
    }

    public class Entry extends BaseObjectSelectionList.Entry<Entry>
    {
        private final MatchResult result;
        private final String seedDisplay;
        private final String detailsLine;
        private final Tooltip tooltip;

        public Entry(MatchResult result)
        {
            this.result = result;

            this.seedDisplay = result.seedString();

            String biomeNames = result.foundBiomes().stream()
                .map(ResourceKey::location)
                .map(loc -> {
                    String path = loc.getPath();
                    int lastSlash = path.lastIndexOf('/');
                    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                })
                .collect(Collectors.joining(", "));
            String featureNames = result.foundFeatures().stream()
                .map(f -> f.name().getString())
                .collect(Collectors.joining(", "));

            String dt = result.detailText();
            if (dt != null && !dt.isBlank())
            {
                this.detailsLine = dt;
            }
            else
            {
                this.detailsLine = Stream.of(biomeNames, featureNames)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(" | "));
            }

            StringBuilder tipBuilder = new StringBuilder();
            tipBuilder.append("Seed: ").append(result.seedString());
            if (dt != null && !dt.isBlank())
            {
                tipBuilder.append("\n").append(dt);
            }
            if (!result.foundBiomes().isEmpty())
            {
                tipBuilder.append("\n\nBiomes: ").append(biomeNames);
            }
            if (!result.foundFeatures().isEmpty())
            {
                tipBuilder.append("\n\nFeatures: ").append(featureNames);
            }
            if (result.featureLocation() != null)
            {
                tipBuilder.append("\n\nLocation: X=").append(result.featureLocation().getX())
                    .append(" Z=").append(result.featureLocation().getZ());
            }
            this.tooltip = Tooltip.create(Component.literal(tipBuilder.toString()));
        }

        public MatchResult result()
        {
            return this.result;
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
            return Component.translatable("narrator.select", this.result.seedString());
        }

        private String trimToWidth(String text, int maxWidth)
        {
            if (maxWidth <= 0) return "";
            Font f = SeedMatchList.this.minecraft.font;
            if (f.width(text) <= maxWidth) return text;
            String ellipsis = "...";
            int ellipsisW = f.width(ellipsis);
            if (maxWidth <= ellipsisW) return "";
            return f.plainSubstrByWidth(text, maxWidth - ellipsisW) + ellipsis;
        }

        @Override
        public void render(@NotNull GuiGraphics gg, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick)
        {
            int maxWidth = Math.max(0, width - 10);
            String seedLine = trimToWidth(this.seedDisplay, maxWidth);
            gg.drawString(SeedMatchList.this.minecraft.font, seedLine, left + 4, top + 2, 0xFFFFFF);

            if (!this.detailsLine.isEmpty())
            {
                String detailLine = trimToWidth(this.detailsLine, maxWidth);
                if (!detailLine.isEmpty())
                {
                    gg.drawString(SeedMatchList.this.minecraft.font, detailLine, left + 4, top + 14, 0xAAAAAA);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (button == 0)
            {
                SeedMatchList.this.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
