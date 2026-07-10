package com.rustysnail.world.preview.tfc.backend.worker.tfc;

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
 *       instead of calling {@code Helpers.triangle} 24 times.</li>
 * </ul>
 *
 * The arrays are package-private and must never be mutated; they are read directly by
 * {@link TFCCropSuitability} in the same package for hot-loop speed. The class exposes no public array
 * accessor, so the cached arrays cannot be handed out and mutated by callers.
 */
public final class AnnualClimateSchedule
{
    private final int samplesPerYear;
    final float[] fractions;
    final float[] monthFactors;
    final float[] rainTriangleFactors;

    /** Cached schedule for the standard 24-sample map (two samples per month). */
    private static final AnnualClimateSchedule DEFAULT_24 = build(TFCPreviewClimateSampler.SAMPLES_PER_YEAR);

    private AnnualClimateSchedule(int samplesPerYear, float[] fractions, float[] monthFactors, float[] rainTriangleFactors)
    {
        this.samplesPerYear = samplesPerYear;
        this.fractions = fractions;
        this.monthFactors = monthFactors;
        this.rainTriangleFactors = rainTriangleFactors;
    }

    public static AnnualClimateSchedule standard()
    {
        return DEFAULT_24;
    }

    public static AnnualClimateSchedule build(int samplesPerYear)
    {
        int n = Math.max(1, samplesPerYear);
        float[] fractions = new float[n];
        float[] monthFactors = new float[n];
        float[] rainTriangle = new float[n];
        for (int i = 0; i < n; i++)
        {
            float foy = (float) i / n;
            fractions[i] = foy;
            monthFactors[i] = TFCPreviewClimateSampler.monthFactorForFraction(foy);
            rainTriangle[i] = TFCPreviewClimateSampler.rainfallTriangleFactor(foy);
        }
        return new AnnualClimateSchedule(n, fractions, monthFactors, rainTriangle);
    }

    public int samplesPerYear()
    {
        return this.samplesPerYear;
    }
}
