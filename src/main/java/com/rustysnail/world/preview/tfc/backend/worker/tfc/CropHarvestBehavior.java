package com.rustysnail.world.preview.tfc.backend.worker.tfc;

/**
 * How a mature annual crop begins its next production cycle after harvest.
 */
public enum CropHarvestBehavior
{
    REPLANT(0d),
    PICKABLE(0.55d),
    SPREADING(0.66d);

    private final double resetGrowth;

    CropHarvestBehavior(double resetGrowth)
    {
        this.resetGrowth = resetGrowth;
    }

    public double resetGrowth()
    {
        return this.resetGrowth;
    }
}
