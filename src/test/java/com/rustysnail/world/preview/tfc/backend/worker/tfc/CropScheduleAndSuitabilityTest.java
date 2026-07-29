package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.world.chunkdata.ChunkData;

import static org.junit.jupiter.api.Assertions.*;

class CropScheduleAndSuitabilityTest
{
    @Test
    void mapScheduleUsesFortyEightIntervalsAndDailySchedulesFollowTheCalendar()
    {
        AnnualClimateSchedule schedule = AnnualClimateSchedule.standard();

        assertEquals(48, schedule.samplesPerYear());
        assertSame(schedule, AnnualClimateSchedule.forSamples(48));
        assertEquals(96, AnnualClimateSchedule.daily(8).samplesPerYear());
        assertEquals(192, AnnualClimateSchedule.daily(16).samplesPerYear());
        assertSame(AnnualClimateSchedule.daily(16), AnnualClimateSchedule.daily(16));
        assertEquals(
            AnnualClimateSchedule.MAX_DETAILED_SAMPLES,
            AnnualClimateSchedule.daily(Integer.MAX_VALUE).samplesPerYear());
    }

    @Test
    void calendarAndModifiersProduceTheConfiguredTfcFormula()
    {
        CropCalendarSettings settings = CropCalendarSettings.build(8, 2f, 3f);

        assertEquals(96d, settings.daysPerYear());
        assertEquals(48d, settings.requiredGrowthDays());
        assertEquals(1d / 48d, settings.growthPerDay());
        assertEquals(3d, settings.localExpiryLimit());
        assertEquals(2d, settings.daysPerSample(48));
    }

    @Test
    void exactCompletedCyclesUseProductionCategories()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);

        assertResult(TFCCropSuitability.CROP_IMPOSSIBLE, 0,
            evaluate(calendar, CropHarvestBehavior.REPLANT, window(96, 0, 20)));
        assertResult(TFCCropSuitability.CROP_ONE_HARVEST, 1,
            evaluate(calendar, CropHarvestBehavior.REPLANT, window(96, 0, 24)));
        assertResult(TFCCropSuitability.CROP_TWO_HARVESTS, 2,
            evaluate(calendar, CropHarvestBehavior.REPLANT, window(96, 0, 48)));
        assertResult(TFCCropSuitability.CROP_THREE_HARVESTS, 3,
            evaluate(calendar, CropHarvestBehavior.REPLANT, window(96, 0, 72)));

        CropCalendarSettings longYear = CropCalendarSettings.build(16, 1f, 1f);
        assertEquals(
            TFCCropSuitability.CROP_FOUR_PLUS_HARVESTS,
            evaluate(longYear, CropHarvestBehavior.REPLANT, window(192, 0, 191)).category());
    }

    @Test
    void yearRoundRequiresEveryCoreIntervalRatherThanAHighHarvestCount()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);
        byte[] allCore = new byte[96];
        Arrays.fill(allCore, TFCCropSuitability.CORE);

        TFCCropSuitability.CropHarvestResult result =
            evaluate(calendar, CropHarvestBehavior.REPLANT, allCore);
        assertEquals(TFCCropSuitability.CROP_YEAR_ROUND, result.category());
        assertEquals(4, result.completedHarvests());
        assertTrue(result.yearRoundCoreGrowth());

        TFCCropSuitability.CropHarvestResult tooSlow = evaluate(
            CropCalendarSettings.build(8, 1000f, 1f),
            CropHarvestBehavior.REPLANT,
            allCore);
        assertEquals(0, tooSlow.completedHarvests());
        assertEquals(TFCCropSuitability.CROP_IMPOSSIBLE, tooSlow.category());
        assertTrue(tooSlow.yearRoundCoreGrowth());
    }

    @Test
    void lethalSplitDoesNotCombinePartialCrops()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);
        byte[] conditions = lethalYear(96);
        Arrays.fill(conditions, 0, 20, TFCCropSuitability.CORE);
        Arrays.fill(conditions, 40, 60, TFCCropSuitability.CORE);

        assertResult(
            TFCCropSuitability.CROP_IMPOSSIBLE, 0,
            evaluate(calendar, CropHarvestBehavior.REPLANT, conditions));
    }

    @Test
    void twoYearSimulationCarriesASeasonAcrossDecemberAndJanuary()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);
        byte[] conditions = lethalYear(96);
        Arrays.fill(conditions, 0, 12, TFCCropSuitability.CORE);
        Arrays.fill(conditions, 84, 96, TFCCropSuitability.CORE);

        TFCCropSuitability.CropHarvestResult result =
            evaluate(calendar, CropHarvestBehavior.REPLANT, conditions);
        assertResult(TFCCropSuitability.CROP_ONE_HARVEST, 1, result);
        assertEquals(24d, result.longestCoreWindowDays());
    }

    @Test
    void harvestBehaviorResetsIncreaseMatureProduction()
    {
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);
        byte[] allCore = new byte[96];
        Arrays.fill(allCore, TFCCropSuitability.CORE);

        int replant = evaluate(calendar, CropHarvestBehavior.REPLANT, allCore).completedHarvests();
        int pickable = evaluate(calendar, CropHarvestBehavior.PICKABLE, allCore).completedHarvests();
        int spreading = evaluate(calendar, CropHarvestBehavior.SPREADING, allCore).completedHarvests();

        assertEquals(4, replant);
        assertTrue(pickable > replant);
        assertTrue(spreading > pickable);
        assertEquals(0d, CropHarvestBehavior.REPLANT.resetGrowth());
        assertEquals(0.55d, CropHarvestBehavior.PICKABLE.resetGrowth());
        assertEquals(0.66d, CropHarvestBehavior.SPREADING.resetGrowth());
    }

    @Test
    void monthLengthGrowthAndExpiryModifiersChangeResults()
    {
        byte[] eightDayYear = new byte[96];
        Arrays.fill(eightDayYear, TFCCropSuitability.CORE);
        byte[] sixteenDayYear = new byte[192];
        Arrays.fill(sixteenDayYear, TFCCropSuitability.CORE);

        assertEquals(4, evaluate(
            CropCalendarSettings.build(8, 1f, 1f),
            CropHarvestBehavior.REPLANT, eightDayYear).completedHarvests());
        assertEquals(8, evaluate(
            CropCalendarSettings.build(16, 1f, 1f),
            CropHarvestBehavior.REPLANT, sixteenDayYear).completedHarvests());
        assertEquals(2, evaluate(
            CropCalendarSettings.build(8, 2f, 1f),
            CropHarvestBehavior.REPLANT, eightDayYear).completedHarvests());

        byte[] expirySequence = lethalYear(96);
        Arrays.fill(expirySequence, 0, 12, TFCCropSuitability.CORE);
        Arrays.fill(expirySequence, 12, 42, TFCCropSuitability.HEALTHY_ONLY);
        Arrays.fill(expirySequence, 42, 54, TFCCropSuitability.CORE);
        assertEquals(1, evaluate(
            CropCalendarSettings.build(8, 1f, 1f),
            CropHarvestBehavior.REPLANT, expirySequence).completedHarvests());
        assertEquals(0, evaluate(
            CropCalendarSettings.build(8, 1f, 0.5f),
            CropHarvestBehavior.REPLANT, expirySequence).completedHarvests());
    }

    @Test
    void realMapAndDetailedPathsUseNewSemanticsAndFloodedHydration()
    {
        TFCCropRegistry.Entry crop = new TFCCropRegistry.Entry(
            ResourceLocation.fromNamespaceAndPath("test", "crop/permissive"), null,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            0f, 0f, 0f, true, CropHarvestBehavior.REPLANT, "Permissive");
        TFCPreviewClimateSampler climate = new TFCPreviewClimateSampler(1234L, 20f);
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);

        short map = TFCCropSuitability.evaluateMapValue(
            crop, climate, ChunkData.EMPTY, 0, 0, 63,
            TFCCropSuitability.CropWaterMode.RAIN_FED,
            AnnualClimateSchedule.standard(), calendar);
        assertEquals(TFCCropSuitability.CROP_YEAR_ROUND, map);
        assertEquals(100, TFCCropSuitability.hydrationFor(
            0f, TFCCropSuitability.CropWaterMode.RAIN_FED, crop.flooded()));

        TFCCropSuitability.CropHarvestResult detail = TFCCropSuitability.evaluateDetailed(
            crop, climate, ChunkData.EMPTY, 0, 0, 63,
            TFCCropSuitability.CropWaterMode.RAIN_FED,
            AnnualClimateSchedule.daily(8), calendar);
        assertEquals(96, detail.samplesPerYear());
        assertEquals(96d, detail.activeGrowthDays());
        assertEquals(96d, detail.longestCoreWindowDays());
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
    void mapHotPathThroughputSample()
    {
        TFCCropRegistry.Entry crop = new TFCCropRegistry.Entry(
            ResourceLocation.fromNamespaceAndPath("test", "crop/permissive"), null,
            new ClimateRange(0, 100, 0, -100f, 100f, 0f),
            0f, 0f, 0f, false, CropHarvestBehavior.REPLANT, "Permissive");
        TFCPreviewClimateSampler climate = new TFCPreviewClimateSampler(1234L, 20f);
        CropCalendarSettings calendar = CropCalendarSettings.build(8, 1f, 1f);
        AnnualClimateSchedule schedule = AnnualClimateSchedule.standard();

        for (int i = 0; i < 2_000; i++)
        {
            TFCCropSuitability.evaluateMapValue(
                crop, climate, ChunkData.EMPTY, i, i, 63,
                TFCCropSuitability.CropWaterMode.RAIN_FED, schedule, calendar);
        }
        int iterations = 20_000;
        int checksum = 0;
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++)
        {
            checksum += TFCCropSuitability.evaluateMapValue(
                crop, climate, ChunkData.EMPTY, i & 15, -(i & 15), 63,
                TFCCropSuitability.CropWaterMode.RAIN_FED, schedule, calendar);
        }
        long elapsed = System.nanoTime() - started;
        assertEquals(iterations * TFCCropSuitability.CROP_YEAR_ROUND, checksum);
        System.out.printf(
            Locale.ROOT,
            "Annual map evaluator: %,d points in %.3f ms (%.1f ns/point)%n",
            iterations, elapsed / 1_000_000d, elapsed / (double) iterations);
    }

    private static TFCCropSuitability.CropHarvestResult evaluate(
        CropCalendarSettings calendar,
        CropHarvestBehavior behavior,
        byte[] conditions
    )
    {
        return TFCCropSuitability.evaluateConditions(conditions, behavior, calendar);
    }

    private static byte[] lethalYear(int days)
    {
        byte[] conditions = new byte[days];
        Arrays.fill(conditions, TFCCropSuitability.LETHAL);
        return conditions;
    }

    private static byte[] window(int days, int start, int end)
    {
        byte[] conditions = lethalYear(days);
        Arrays.fill(conditions, start, end, TFCCropSuitability.CORE);
        return conditions;
    }

    private static void assertResult(
        short category,
        int harvests,
        TFCCropSuitability.CropHarvestResult result
    )
    {
        assertEquals(category, result.category());
        assertEquals(harvests, result.completedHarvests());
    }
}
