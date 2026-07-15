package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, cached per-sample annual schedule for the crop-suitability climate loop. The values
 * depend only on the sample count (they are fractions of the year), not on the calendar month length,
 * so a single instance is shared by every crop work unit and hover computation instead of allocating
 * fresh arrays per unit.
 *
 * <p>For each of {@link #samplesPerYear} samples it precomputes:
 * <ul>
 *   <li>{@code monthFactors[i]} - the interpolated {@link net.dries007.tfc.util.calendar.Month}
 *       temperature factor (drives seasonal temperature);</li>
 *   <li>{@code rainTriangleFactors[i]} - the normalized seasonal-rainfall triangle value in [-1,1]
 *       for phase {@code fractionOfYear + 0.75}, so a point computes rainfall as
 *       {@code rainAverage * (1 + rainVariance * rainTriangleFactors[i])} with a single multiply-add
 *       instead of calling {@code Helpers.triangle} for every annual sample.</li>
 * </ul>
 *
 * The arrays are package-private and must never be mutated; they are read directly by
 * {@link TFCCropSuitability} in the same package for hot-loop speed. The class exposes no public array
 * accessor, so the cached arrays cannot be handed out and mutated by callers.
 */
public final class AnnualClimateSchedule
{
    /**
     * Avoid pathological calendar settings turning one hover into billions of samples.
     */
    public static final int MAX_DETAILED_SAMPLES = 4096;
    private static final int MAX_CACHED_SCHEDULES = 16;
    private static final Map<Integer, AnnualClimateSchedule> CACHE = new LinkedHashMap<>(16, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, AnnualClimateSchedule> eldest)
        {
            return size() > MAX_CACHED_SCHEDULES;
        }
    };
    private static final AnnualClimateSchedule MAP_SCHEDULE = create(TFCPreviewClimateSampler.MAP_SAMPLES_PER_YEAR);

    public static AnnualClimateSchedule standard()
    {
        return MAP_SCHEDULE;
    }

    /**
     * Returns the single immutable schedule cached for this sample count.
     */
    public static synchronized AnnualClimateSchedule forSamples(int samplesPerYear)
    {
        int n = Math.max(1, samplesPerYear);
        if (n == TFCPreviewClimateSampler.MAP_SAMPLES_PER_YEAR)
        {
            return MAP_SCHEDULE;
        }
        return CACHE.computeIfAbsent(n, AnnualClimateSchedule::create);
    }

    /**
     * One sample per configured calendar day for normal calendars. Extremely large month lengths are
     * capped so malformed/server-test configs cannot monopolize the hover executor; days-per-sample
     * still uses the real year length, preserving duration-based classification.
     */
    public static AnnualClimateSchedule daily(int daysInMonth)
    {
        long requested = (long) Math.max(1, daysInMonth) * TFCPreviewClimateSampler.MONTHS_PER_YEAR;
        return forSamples((int) Math.min(requested, MAX_DETAILED_SAMPLES));
    }

    private static AnnualClimateSchedule create(int samplesPerYear)
    {
        int n = samplesPerYear;
        float[] fractions = new float[n];
        float[] monthFactors = new float[n];
        float[] rainTriangle = new float[n];
        for (int i = 0; i < n; i++)
        {
            // Midpoints provide four representative samples within every month at n=48 and one
            // representative sample at the middle of each calendar day for daily hover schedules.
            float foy = (i + 0.5f) / n;
            fractions[i] = foy;
            monthFactors[i] = TFCPreviewClimateSampler.monthFactorForFraction(foy);
            rainTriangle[i] = TFCPreviewClimateSampler.rainfallTriangleFactor(foy);
        }
        return new AnnualClimateSchedule(n, fractions, monthFactors, rainTriangle);
    }

    final float[] fractions;
    final float[] monthFactors;
    final float[] rainTriangleFactors;
    private final int samplesPerYear;

    private AnnualClimateSchedule(int samplesPerYear, float[] fractions, float[] monthFactors, float[] rainTriangleFactors)
    {
        this.samplesPerYear = samplesPerYear;
        this.fractions = fractions;
        this.monthFactors = monthFactors;
        this.rainTriangleFactors = rainTriangleFactors;
    }

    public int samplesPerYear()
    {
        return this.samplesPerYear;
    }
}
