package com.rustysnail.world.preview.tfc.backend.search;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface FeatureTest
{
    boolean matches(FeatureQuery q);

    default @Nullable BlockPos findCenter(FeatureQuery q)
    {
        return null;
    }
}
