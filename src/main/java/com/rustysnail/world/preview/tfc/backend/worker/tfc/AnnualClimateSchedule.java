package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AnnualClimateSchedule
{
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

    public static synchronized AnnualClimateSchedule forSamples(int samplesPerYear)
    {
        int n = Math.max(1, samplesPerYear);
        if (n == TFCPreviewClimateSampler.MAP_SAMPLES_PER_YEAR)
        {
            return MAP_SCHEDULE;
        }
        return CACHE.computeIfAbsent(n, AnnualClimateSchedule::create);
    }

    public static AnnualClimateSchedule daily(int daysInMonth)
    {
        long requested = (long) Math.max(1, daysInMonth) * TFCPreviewClimateSampler.MONTHS_PER_YEAR;
        return forSamples((int) Math.min(requested, MAX_DETAILED_SAMPLES));
    }

    private static AnnualClimateSchedule create(int samplesPerYear)
    {
        float[] fractions = new float[samplesPerYear];
        float[] monthFactors = new float[samplesPerYear];
        float[] rainTriangle = new float[samplesPerYear];
        for (int i = 0; i < samplesPerYear; i++)
        {
            float foy = (i + 0.5f) / samplesPerYear;
            fractions[i] = foy;
            monthFactors[i] = TFCPreviewClimateSampler.monthFactorForFraction(foy);
            rainTriangle[i] = TFCPreviewClimateSampler.rainfallTriangleFactor(foy);
        }
        return new AnnualClimateSchedule(samplesPerYear, fractions, monthFactors, rainTriangle);
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
