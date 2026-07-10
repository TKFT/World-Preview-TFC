package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Scrollable side list used to pick which crop the {@code TFC_CROP_SUITABILITY} map is evaluated for.
 * Entries are plain (crop id, display name); the list carries no crop colours because the map colours
 * represent suitability, not crop identity (see the task spec).
 */
public class TFCCropList extends BaseObjectSelectionList<TFCCropList.CropEntry>
{
    private Consumer<CropEntry> onCropSelected;

    public TFCCropList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public CropEntry createEntry(ResourceLocation cropId, String name)
    {
        return new CropEntry(cropId, name);
    }

    public void setChangeListener(Consumer<CropEntry> onCropSelected)
    {
        this.onCropSelected = onCropSelected;
    }

    public void setSelected(@Nullable CropEntry entry, boolean centerScroll)
    {
        super.setSelected(entry);
        if (centerScroll && entry != null)
        {
            super.centerScrollOn(entry);
        }
        if (this.onCropSelected != null)
        {
            this.onCropSelected.accept(entry);
        }
    }

    @Nullable
    public CropEntry getEntryById(ResourceLocation cropId)
    {
        for (CropEntry entry : this.children())
        {
            if (entry.cropId.equals(cropId))
            {
                return entry;
            }
        }
        return null;
    }

    public class CropEntry extends Entry<CropEntry>
    {
        private final ResourceLocation cropId;
        private final String name;
        private final Tooltip tooltip;

        public CropEntry(ResourceLocation cropId, String name)
        {
            this.cropId = cropId;
            this.name = name;
            this.tooltip = Tooltip.create(Component.literal(cropId.toString()));
        }

        public ResourceLocation cropId()
        {
            return this.cropId;
        }

        public String name()
        {
            return this.name;
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
            guiGraphics.drawString(TFCCropList.this.minecraft.font, this.name, k + 4, j + 2, 16777215);
        }

        public boolean mouseClicked(double d, double e, int i)
        {
            if (i != 0)
            {
                return false;
            }
            TFCCropList.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            TFCCropList.this.setSelected(this, false);
            return true;
        }
    }

    @Override
    public void replaceEntries(@NotNull Collection<CropEntry> entryList)
    {
        super.replaceEntries(entryList);
    }
}
