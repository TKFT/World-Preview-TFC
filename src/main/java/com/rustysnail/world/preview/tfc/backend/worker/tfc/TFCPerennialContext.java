package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record TFCPerennialContext(
    @Nullable ResourceLocation perennialId,
    @Nullable TFCPerennialRegistry.PerennialEntry perennial,
    TFCPerennialSuitability.PerennialWaterMode waterMode,
    TFCPerennialSuitability.ProductionProfile production,
    int revision,
    IntSupplier currentRevision
)
{
    public boolean isStale()
    {
        return this.currentRevision.getAsInt() != this.revision;
    }
}
