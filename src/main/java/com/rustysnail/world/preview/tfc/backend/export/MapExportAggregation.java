package com.rustysnail.world.preview.tfc.backend.export;

public final class MapExportAggregation
{
    private MapExportAggregation()
    {
    }

    public static int majority(int[] values, int length)
    {
        int bestValue = values[0];
        int bestCount = 0;
        for (int i = 0; i < length; i++)
        {
            int candidate = values[i];
            int count = 0;
            for (int j = 0; j < length; j++)
            {
                if (values[j] == candidate)
                {
                    count++;
                }
            }
            if (count > bestCount)
            {
                bestValue = candidate;
                bestCount = count;
            }
        }
        return bestValue;
    }

    public static int averageIndex(double sum, int sampleCount, double minimum, double maximum)
    {
        if (sampleCount <= 0 || maximum <= minimum)
        {
            throw new IllegalArgumentException("Invalid continuous aggregation range");
        }
        double normalized = Math.clamp((sum / sampleCount - minimum) / (maximum - minimum), 0D, 1D);
        return Math.min(255, (int) Math.floor(normalized * 256D));
    }

    public static int waterShadeIndex(int waterBaseIndex, int shadeBucket)
    {
        return waterBaseIndex + Math.clamp(shadeBucket, 0, 15);
    }
}
