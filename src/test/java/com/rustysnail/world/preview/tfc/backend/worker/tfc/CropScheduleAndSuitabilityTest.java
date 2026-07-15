package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

import static org.junit.jupiter.api.Assertions.*;

class CropScheduleAndSuitabilityTest
{
    @Test
    void mapScheduleUsesFourMidpointSamplesPerMonthAndIsCached()
    {
        AnnualClimateSchedule schedule = AnnualClimateSchedule.standard();

        assertEquals(48, schedule.samplesPerYear());
        assertSame(schedule, AnnualClimateSchedule.forSamples(48));
        assertEquals(0.5f / 48f, schedule.fractions[0], 1e-7f);
        assertEquals(47.5f / 48f, schedule.fractions[47], 1e-7f);
        assertEquals(0.125f, schedule.fractions[0] * 12f, 1e-6f);
        assertEquals(0.875f, schedule.fractions[3] * 12f, 1e-6f);
    }

    @Test
    void dailySchedulesFollowEightAndSixteenDayCalendars()
    {
        assertEquals(96, AnnualClimateSchedule.daily(8).samplesPerYear());
        assertEquals(192, AnnualClimateSchedule.daily(16).samplesPerYear());
        assertSame(AnnualClimateSchedule.daily(16), AnnualClimateSchedule.daily(16));

        AnnualClimateSchedule extreme = AnnualClimateSchedule.daily(Integer.MAX_VALUE);
        assertEquals(AnnualClimateSchedule.MAX_DETAILED_SAMPLES, extreme.samplesPerYear());
    }

    @Test
    void durationThresholdsScaleWithCalendarNotFixedSampleCounts()
    {
        CropCalendarSettings eight = CropCalendarSettings.build(8, 1f);
        CropCalendarSettings sixteen = CropCalendarSettings.build(16, 1f);

        assertEquals(2d, eight.daysPerSample(48));
        assertEquals(4d, sixteen.daysPerSample(48));
        assertEquals(1d, eight.daysPerSample(96));
        assertEquals(1d, sixteen.daysPerSample(192));
        assertEquals(24d, eight.requiredGrowthDays());
        assertEquals(24d, sixteen.requiredGrowthDays());

        assertEquals(TFCCropSuitability.CROP_POOR,
            TFCCropSuitability.classify(48, 5, 5, 1f, 48, eight));
        assertEquals(TFCCropSuitability.CROP_MARGINAL,
            TFCCropSuitability.classify(48, 6, 6, 1f, 48, eight));
        assertEquals(TFCCropSuitability.CROP_GOOD,
            TFCCropSuitability.classify(48, 12, 12, 1f, 48, eight));
        assertEquals(TFCCropSuitability.CROP_IDEAL,
            TFCCropSuitability.classify(48, 24, 20, 0.7f, 48, eight));

        assertEquals(TFCCropSuitability.CROP_MARGINAL,
            TFCCropSuitability.classify(48, 3, 3, 1f, 48, sixteen));
        assertEquals(TFCCropSuitability.CROP_GOOD,
            TFCCropSuitability.classify(48, 6, 6, 1f, 48, sixteen));
    }

    @Test
    void extremeGrowthModifierCannotBecomeIdealByThresholdClamping()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1000f);
        assertEquals(TFCCropSuitability.CROP_POOR,
            TFCCropSuitability.classify(48, 48, 48, 1f, 48, calendar));
    }

    @Test
    void circularRunsWorkBeyondThirtyTwoSamples()
    {
        assertEquals(0, TFCCropSuitability.finishCircularRun(0, 0, 0, 0, 48));
        assertEquals(48, TFCCropSuitability.finishCircularRun(48, 48, 48, 48, 48));
        assertEquals(5, TFCCropSuitability.finishCircularRun(5, 3, 2, 3, 48));
        assertEquals(40, TFCCropSuitability.finishCircularRun(40, 20, 20, 20, 96));
    }

    @Test
    void mapUsesFortyEightSamplesAndHoverUsesOneSamplePerDay()
    {
        TFCCropRegistry.Entry crop = new TFCCropRegistry.Entry(
            ResourceLocation.fromNamespaceAndPath("test", "crop/permissive"), null,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            0f, 0f, 0f, false, "Permissive");
        TFCPreviewClimateSampler climate = new TFCPreviewClimateSampler(1234L, 20f);
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f);

        short map = TFCCropSuitability.evaluateMapValue(
            crop, climate, ChunkData.EMPTY, 0, 0, 63,
            TFCCropSuitability.CropWaterMode.RAIN_FED,
            AnnualClimateSchedule.standard(), calendar);
        assertEquals(TFCCropSuitability.CROP_IDEAL, map);

        TFCCropSuitability.CropSuitabilityResult detail = TFCCropSuitability.evaluateDetailed(
            crop, climate, ChunkData.EMPTY, 0, 0, 63,
            TFCCropSuitability.CropWaterMode.RAIN_FED,
            AnnualClimateSchedule.daily(8), calendar);
        assertEquals(96, detail.samplesPerYear());
        assertEquals(96, detail.coreValidSamples());
        assertEquals(96, detail.wiggleValidSamples());
        assertEquals(96, detail.longestCoreRun());
        assertEquals(1d, detail.daysPerSample());
    }
}
