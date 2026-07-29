package com.rustysnail.world.preview.tfc.backend.worker.tfc;

public record CropCalendarSettings(
    int daysInMonth,
    float cropGrowthModifier,
    float cropExpiryModifier,
    double daysPerYear,
    double requiredGrowthDays,
    double growthPerDay,
    double localExpiryLimit
)
{
    private static final int MONTHS_PER_YEAR = 12;
    private static final float BASE_REQUIRED_GROWTH_DAYS = 24f;

    public static CropCalendarSettings build(int daysInMonth, float cropGrowthModifier, float cropExpiryModifier)
    {
        int safeDays = Math.max(1, daysInMonth);
        float safeGrowth = positiveOrDefault(cropGrowthModifier);
        float safeExpiry = positiveOrDefault(cropExpiryModifier);

        double daysPerYear = (double) safeDays * MONTHS_PER_YEAR;
        double requiredGrowthDays = BASE_REQUIRED_GROWTH_DAYS * safeGrowth;
        double growthPerDay = 1d / requiredGrowthDays;
        double localExpiryLimit =
            net.dries007.tfc.common.blocks.crop.CropHelpers.EXPIRY_LIMIT * safeExpiry / safeGrowth;

        return new CropCalendarSettings(
            safeDays, safeGrowth, safeExpiry, daysPerYear, requiredGrowthDays,
            growthPerDay, localExpiryLimit);
    }

    public double daysPerSample(int samplesPerYear)
    {
        return this.daysPerYear / Math.max(1, samplesPerYear);
    }

    private static float positiveOrDefault(float value)
    {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }
}
