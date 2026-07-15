package com.rustysnail.world.preview.tfc.backend.worker.tfc;

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
    float cropGrowthModifier,
    double daysPerYear,
    double requiredGrowthDays,
    double poorCoreDays,
    double goodCoreDays,
    double idealCoreDays,
    double idealCoverageDays
)
{
    private static final int MONTHS_PER_YEAR = 12;

    /**
     * A representative crop needs ~24 days of valid conditions (scaled by the growth modifier).
     */
    private static final float BASE_REQUIRED_GROWTH_DAYS = 24f;

    public static CropCalendarSettings build(int daysInMonth, float cropGrowthModifier)
    {
        int safeDays = Math.max(1, daysInMonth);
        float safeModifier = Float.isFinite(cropGrowthModifier) && cropGrowthModifier > 0f ? cropGrowthModifier : 1f;

        double daysPerYear = (double) safeDays * MONTHS_PER_YEAR;
        double requiredGrowthDays = BASE_REQUIRED_GROWTH_DAYS * safeModifier;

        return new CropCalendarSettings(
            safeDays, safeModifier, daysPerYear, requiredGrowthDays,
            0.5f * requiredGrowthDays,
            requiredGrowthDays,
            (5d / 3d) * requiredGrowthDays,
            0.5f * daysPerYear);
    }

    public double daysPerSample(int samplesPerYear)
    {
        return this.daysPerYear / Math.max(1, samplesPerYear);
    }
}
