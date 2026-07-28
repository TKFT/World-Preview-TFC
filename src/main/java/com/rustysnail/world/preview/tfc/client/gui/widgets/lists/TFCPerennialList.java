package com.rustysnail.world.preview.tfc.client.gui.widgets.lists;

import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPerennialRegistry.PerennialType;

/**
 * Independent perennial selector. Type prefixes provide stable visual grouping without introducing
 * fake selectable header rows into the generic selection list.
 */
public class TFCPerennialList extends BaseObjectSelectionList<TFCPerennialList.PerennialEntry>
{
    @Nullable
    private Consumer<PerennialEntry> onPerennialSelected;

    public TFCPerennialList(Minecraft minecraft, int width, int height, int x, int y)
    {
        super(minecraft, width, height, x, y, 16);
    }

    public PerennialEntry createEntry(ResourceLocation id, String name, PerennialType type)
    {
        return new PerennialEntry(id, name, type);
    }

    public void setChangeListener(Consumer<PerennialEntry> listener)
    {
        this.onPerennialSelected = listener;
    }

    public void setSelected(@Nullable PerennialEntry entry, boolean centerScroll)
    {
        super.setSelected(entry);
        if (centerScroll && entry != null)
        {
            super.centerScrollOn(entry);
        }
        if (this.onPerennialSelected != null)
        {
            this.onPerennialSelected.accept(entry);
        }
    }

    @Nullable
    public PerennialEntry getEntryById(ResourceLocation id)
    {
        for (PerennialEntry entry : this.children())
        {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    @Override
    public void replaceEntries(@NotNull Collection<PerennialEntry> entries)
    {
        super.replaceEntries(entries);
    }

    public class PerennialEntry extends Entry<PerennialEntry>
    {
        private final ResourceLocation id;
        private final String name;
        private final String groupedName;
        private final Tooltip tooltip;

        PerennialEntry(ResourceLocation id, String name, PerennialType type)
        {
            this.id = id;
            this.name = name;
            this.groupedName = "[" + typeName(type) + "] " + name;
            this.tooltip = Tooltip.create(Component.literal(id.toString()));
        }

        public ResourceLocation id()
        {
            return this.id;
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

        @Override
        public Component getNarration()
        {
            return Component.translatable("narrator.select", this.groupedName);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (button != 0) return false;
            TFCPerennialList.this.minecraft.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            TFCPerennialList.this.setSelected(this, false);
            return true;
        }

        @Override
        public void render(
            @NotNull GuiGraphics graphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovered,
            float partialTick
        )
        {
            graphics.drawString(TFCPerennialList.this.minecraft.font, this.groupedName, left + 4, top + 2, 0xFFFFFF);
        }
    }

    private static String typeName(PerennialType type)
    {
        return switch (type)
        {
            case FRUIT_TREE -> "Fruit Tree";
            case BANANA -> "Banana";
            case STATIONARY_BERRY -> "Stationary Berry";
            case SPREADING_BERRY -> "Spreading Berry";
            case WATERLOGGED_BERRY -> "Waterlogged Berry";
            case ADDON_PERENNIAL -> "Addon Perennial";
        };
    }
}
