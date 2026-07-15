package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.OverworldClimateModel;

public class TFCPreviewClimateSampler extends OverworldClimateModel
{
    public static final int MAP_SAMPLES_PER_YEAR = 48;
    public static final int SAMPLES_PER_YEAR = MAP_SAMPLES_PER_YEAR;
    public static final int MONTHS_PER_YEAR = 12;
    private static final long CLIMATE_SEED_SALT = 719283741234L;
    private static final Month[] MONTHS = Month.values();

    public static float monthFactorForFraction(float fractionOfYear)
    {
        float monthPos = fractionOfYear * MONTHS_PER_YEAR;
        int monthIdx = Mth.floor(monthPos) % MONTHS_PER_YEAR;
        if (monthIdx < 0) monthIdx += MONTHS_PER_YEAR;
        float delta = monthPos - Mth.floor(monthPos);
        Month m = MONTHS[monthIdx];
        return Mth.lerp(delta, m.getTemperatureModifier(), m.next().getTemperatureModifier());
    }

    public static float rainfallTriangleFactor(float fractionOfYear)
    {
        return Helpers.triangle(1f, 0f, 1f, fractionOfYear + 0.75f);
    }

    public TFCPreviewClimateSampler(long worldSeed, float temperatureScale)
    {
        super(LinearCongruentialGenerator.next(worldSeed, CLIMATE_SEED_SALT), temperatureScale);
    }

    public float previewTemperature(int z, int surfaceY, float avgSeaLevelTemp, float monthFactor)
    {
        return getAverageMonthlyTemperature(z, surfaceY, avgSeaLevelTemp, monthFactor, false);
    }

    public float previewSeasonalRainfall(float rainAverage, float rainVariance, float fractionOfYear)
    {
        return rainVariance == 0f
            ? rainAverage
            : Helpers.triangle(rainVariance * rainAverage, rainAverage, 1f, fractionOfYear + 0.75f);
    }

    public float temperatureBase(int surfaceY, float avgSeaLevelTemp)
    {
        return adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, 0f, 0f);
    }

    public float temperatureSlope(int z, int surfaceY, float avgSeaLevelTemp)
    {
        float latitude = calculateMonthlyTemperature(z, 1f); // z-only latitude term (mf = 1)
        return adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, latitude, 0f)
            - adjustTemperatureByElevation(surfaceY, avgSeaLevelTemp, 0f, 0f);
    }
}
