package com.rustysnail.world.preview.tfc.backend.worker.tfc;

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
