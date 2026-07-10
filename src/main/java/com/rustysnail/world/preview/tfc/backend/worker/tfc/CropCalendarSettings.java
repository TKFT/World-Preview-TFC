package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.minecraft.util.Mth;

/**
 * The calendar / crop-growth assumptions a crop-suitability result was generated under. TFC lets an
 * existing world change its month length (via the {@code /time} command) and configure a global crop
 * growth modifier, so the suitability thresholds (how many valid samples make a growing window)
 * cannot be hardcoded to the default 8-day month - they derive from the live calendar.
 *
 * <p>Built via {@link #build}; immutable and value-equal, so a work unit can capture the exact
 * assumptions it was generated for (see {@link TFCCropContext}) and the WorkManager can detect a
 * change by simple {@code equals} comparison.
 */
public record CropCalendarSettings(
    int daysInMonth,
    int samplesPerYear,
    float daysPerSample,
    float cropGrowthModifier,
    float requiredGrowthDays,
    int poorCoreSamples,
    int goodCoreSamples,
    int idealCoreSamples,
    int idealCoverageSamples
)
{
    private static final int MONTHS_PER_YEAR = 12;

    /** A representative crop needs ~24 days of valid conditions (scaled by the growth modifier). */
    private static final float BASE_REQUIRED_GROWTH_DAYS = 24f;

    public static CropCalendarSettings build(int daysInMonth, int samplesPerYear, float cropGrowthModifier)
    {
        int safeDays = Math.max(1, daysInMonth);
        int safeSamples = Math.max(1, samplesPerYear);
        float safeModifier = cropGrowthModifier > 0f ? cropGrowthModifier : 1f;

        float daysPerYear = (float) safeDays * MONTHS_PER_YEAR;
        float daysPerSample = daysPerYear / safeSamples;
        float requiredGrowthDays = BASE_REQUIRED_GROWTH_DAYS * safeModifier;

        int poor = clampSamples(Mth.ceil(0.5f * requiredGrowthDays / daysPerSample), safeSamples);
        int good = clampSamples(Mth.ceil(requiredGrowthDays / daysPerSample), safeSamples);
        int ideal = clampSamples(Mth.ceil(1.6666667f * requiredGrowthDays / daysPerSample), safeSamples);
        int coverage = clampSamples(Mth.ceil(safeSamples * 0.5f), safeSamples);

        return new CropCalendarSettings(
            safeDays, safeSamples, daysPerSample, safeModifier, requiredGrowthDays,
            poor, good, ideal, coverage);
    }

    private static int clampSamples(int value, int samplesPerYear)
    {
        return Mth.clamp(value, 1, samplesPerYear);
    }

    public int growingWindowDays(int longestCoreRun)
    {
        return Math.round(longestCoreRun * this.daysPerSample);
    }
}
