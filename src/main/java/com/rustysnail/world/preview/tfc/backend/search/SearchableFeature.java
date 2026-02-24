package com.rustysnail.world.preview.tfc.backend.search;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;


public record SearchableFeature(
    ResourceLocation id,
    Component name,
    @Nullable FeatureTest test,
    boolean requiresProbe
)
{
    public SearchableFeature(ResourceLocation id, Component name, @Nullable FeatureTest test)
    {
        this(id, name, test, false);
    }

    public ResourceLocation getPlacedFeatureId()
    {
        return id;
    }
}
