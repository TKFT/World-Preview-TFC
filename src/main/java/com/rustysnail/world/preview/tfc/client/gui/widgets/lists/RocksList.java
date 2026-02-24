package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
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

public class RocksList extends BaseObjectSelectionList<RocksList.RockEntry>
{

    private Consumer<RockEntry> onRockSelected;

    public RocksList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public void setSelected(@Nullable RockEntry entry)
    {
        this.setSelected(entry, false);
    }

    public void setSelected(@Nullable RockEntry entry, boolean centerScroll)
    {
        super.setSelected(entry);
        if (centerScroll)
        {
            assert entry != null;
            super.centerScrollOn(entry);
        }

        this.onRockSelected.accept(entry);
    }

    public void setRockChangeListener(Consumer<RockEntry> onRockSelected)
    {
        this.onRockSelected = onRockSelected;
    }

    @Override
    public void replaceEntries(@NotNull Collection<RockEntry> entryList)
    {
        RockEntry oldEntry = this.getSelected();
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

    public RockEntry createRockEntry(short rockId)
    {
        String name = TFCSampleUtils.getRockName(rockId);
        int color = TFCSampleUtils.getRockColor(rockId);
        return new RockEntry(rockId, name, color);
    }

    public RockEntry createRockTypeEntry(short rockTypeId)
    {
        String name = TFCSampleUtils.getRockTypeName(rockTypeId);
        int color = TFCSampleUtils.getRockTypeColor(rockTypeId);
        return new RockEntry(rockTypeId, name, color);
    }

    public class RockEntry extends Entry<RockEntry>
    {
        private final short id;
        private final String name;
        private final int color;
        private final Tooltip tooltip;

        public RockEntry(short id, String name, int color)
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
            guiGraphics.drawString(RocksList.this.minecraft.font, this.name, k + 16, j + 2, 16777215);
        }

        public boolean mouseClicked(double d, double e, int i)
        {
            if (i != 0)
            {
                return false;
            }
            RocksList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            RockEntry selected = RocksList.this.getSelected();
            boolean isSelected = selected != null && this.id == selected.id;
            if (isSelected)
            {
                RocksList.this.setSelected(null);
                return false;
            }
            return true;
        }
    }
}
