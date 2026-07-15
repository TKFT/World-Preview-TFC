package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The crop selection captured for a batch of {@link TFCRegionWorkUnit}s when queued for the
 * {@link com.rustysnail.world.preview.tfc.RenderSettings.RenderMode#TFC_CROP_SUITABILITY} map.
 *
 * <p>Each unit holds the {@link #revision} that was current when it was queued. If the user selects
 * a different crop or water mode, the WorkManager bumps its crop revision, so a still-running unit
 * created for the old crop reports {@link #isStale()} and discards its results instead of writing
 * stale suitability into flag-16 storage.
 */
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
