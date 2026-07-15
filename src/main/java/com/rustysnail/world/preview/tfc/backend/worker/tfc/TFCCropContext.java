package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record TFCCropContext(
    @Nullable ResourceLocation cropId,
    @Nullable TFCCropRegistry.Entry crop,
    TFCCropSuitability.CropWaterMode waterMode,
    CropCalendarSettings calendar,
    AnnualClimateSchedule schedule,
    int revision,
    IntSupplier currentRevision
)
{
    public boolean isStale()
    {
        return this.currentRevision.getAsInt() != this.revision;
    }
}
