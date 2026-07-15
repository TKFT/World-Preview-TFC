package com.rustysnail.world.preview.tfc.backend.color;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Immutable colors and optional display names for one categorical preview layer. */
public final class CategoricalColorPalette
{
    private final Map<ResourceLocation, Entry> entries;

    public CategoricalColorPalette(Map<ResourceLocation, Entry> entries)
    {
        this.entries = Map.copyOf(entries);
    }

    public Map<ResourceLocation, Entry> entries()
    {
        return this.entries;
    }

    @Nullable
    public Entry get(ResourceLocation valueId)
    {
        return this.entries.get(valueId);
    }

    public record Entry(int rgb, @Nullable String name)
    {
        public Entry
        {
            if (rgb < 0 || rgb > 0xFFFFFF)
            {
                throw new IllegalArgumentException("RGB color must be between 0x000000 and 0xFFFFFF");
            }
        }

        public int argb()
        {
            return 0xFF000000 | this.rgb;
        }
    }
}
