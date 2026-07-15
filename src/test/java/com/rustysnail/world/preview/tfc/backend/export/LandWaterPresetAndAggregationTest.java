package com.rustysnail.world.preview.tfc.backend.export;

import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Bounds;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Spec;

import net.minecraft.core.QuartPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandWaterPresetAndAggregationTest
{
    @Test
    void bothPresetsHaveRequestedOutputDimensions()
    {
        assertDimensions(LandWaterExportPreset.FIFTY_K.spec(), 50_000, 4);
        assertDimensions(LandWaterExportPreset.HUNDRED_K.spec(), 100_000, 8);
    }

    @Test
    void boundsAreExactAtZeroAndSignedCenters()
    {
        Spec spec = LandWaterExportPreset.FIFTY_K.spec();
        assertEquals(new Bounds(-25_000, 24_999, -25_000, 24_999), spec.bounds(0, 0));
        assertEquals(new Bounds(-24_877, 25_122, -25_456, 24_543), spec.bounds(123, -456));
        assertEquals(new Bounds(-25_123, 24_876, -24_544, 25_455), spec.bounds(-123, 456));
    }

    @Test
    void negativeQuartConversionUsesFloorDivisionWithoutPixelShift()
    {
        Spec spec = LandWaterExportPreset.FIFTY_K.spec();
        Bounds bounds = spec.bounds(-1, -1);
        assertEquals(-25_001, spec.sampleBlockX(bounds, 0, 0));
        assertEquals(Math.floorDiv(-25_001, 4), spec.sampleQuartX(bounds, 0, 0));
        assertEquals(QuartPos.fromBlock(-24_997), spec.sampleQuartX(bounds, 1, 0));
        assertEquals(spec.sampleQuartX(bounds, 0, 0) + 1, spec.sampleQuartX(bounds, 1, 0));
    }

    @Test
    void fourAndEightBlockSamplingCoordinatesAreExact()
    {
        Spec four = LandWaterExportPreset.FIFTY_K.spec();
        Bounds fourBounds = four.bounds(7, -9);
        assertEquals(4, four.sampleBlockX(fourBounds, 1, 0) - four.sampleBlockX(fourBounds, 0, 0));

        Spec eight = LandWaterExportPreset.HUNDRED_K.spec();
        Bounds eightBounds = eight.bounds(0, 0);
        assertEquals(8, eight.sampleBlockX(eightBounds, 1, 0) - eight.sampleBlockX(eightBounds, 0, 0));
        assertEquals(4, eight.sampleBlockX(eightBounds, 0, 1) - eight.sampleBlockX(eightBounds, 0, 0));
        assertEquals(1, eight.sampleQuartX(eightBounds, 0, 1) - eight.sampleQuartX(eightBounds, 0, 0));
        assertEquals(2, eight.sampleQuartX(eightBounds, 1, 0) - eight.sampleQuartX(eightBounds, 0, 0));
    }

    @Test
    void aggregationPreservesNarrowWaterThenUsesHalfWaterThreshold()
    {
        assertTrue(LandWaterAggregation.aggregate2x2(
            LandWaterSample.NARROW_WATER, LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND));
        assertTrue(LandWaterAggregation.aggregate2x2(
            LandWaterSample.WATER, LandWaterSample.WATER, LandWaterSample.LAND, LandWaterSample.LAND));
        assertFalse(LandWaterAggregation.aggregate2x2(
            LandWaterSample.WATER, LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND));
        assertFalse(LandWaterAggregation.aggregate2x2(
            LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND, LandWaterSample.LAND));
    }

    private static void assertDimensions(Spec spec, int coverage, int blocksPerPixel)
    {
        assertEquals(coverage, spec.coverageBlocks());
        assertEquals(blocksPerPixel, spec.blocksPerPixel());
        assertEquals(12_500, spec.imageWidth());
        assertEquals(12_500, spec.imageHeight());
        assertEquals(coverage, spec.bounds(0, 0).width());
        assertEquals(coverage, spec.bounds(0, 0).height());
    }
}
