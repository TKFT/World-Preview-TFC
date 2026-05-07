package com.rustysnail.world.preview.tfc.backend.search.mountain;

import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import net.minecraft.core.BlockPos;

/** @deprecated Use {@link MountainPeakScanner} with a {@link com.rustysnail.world.preview.tfc.backend.search.HeightSampler} lambda directly. */
@Deprecated
public class CurrentSeedMountainScanner extends MountainPeakScanner
{
    public CurrentSeedMountainScanner(long seed, SampleUtils sampleUtils, MountainSearchConfig config, Callback callback)
    {
        super(seed, (x, z) -> sampleUtils.doHeightSlow(new BlockPos(x, 0, z)), config, callback);
    }
}
