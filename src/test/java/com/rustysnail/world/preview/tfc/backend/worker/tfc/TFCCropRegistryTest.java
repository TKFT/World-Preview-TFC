package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import net.dries007.tfc.util.climate.ClimateRange;

import static org.junit.jupiter.api.Assertions.*;

class TFCCropRegistryTest
{
    private static final ClimateRange QUINOA = new ClimateRange(20, 80, 5, -5f, 30f, 2f);

    @Test
    void exactResourceIdWins()
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("addon", "crop/quinoa");
        assertSame(QUINOA, TFCCropRegistry.resolveResourceClimate(id, Map.of(id, QUINOA)));
    }

    @Test
    void uniqueNormalizedFallbackStaysWithinNamespace()
    {
        ResourceLocation climateId = ResourceLocation.fromNamespaceAndPath("addon", "crop/quinoa");
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("addon", "plants/quinoa_crop");
        ResourceLocation foreign = ResourceLocation.fromNamespaceAndPath("other", "crop/quinoa");

        assertSame(QUINOA, TFCCropRegistry.resolveResourceClimate(blockId, Map.of(climateId, QUINOA, foreign, QUINOA)));
        assertEquals("quinoa", TFCCropRegistry.normalizeCropName(blockId.getPath()));
    }

    @Test
    void ambiguousNormalizedFallbackAndMissingDataReturnNull()
    {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("addon", "plants/quinoa_crop");
        Map<ResourceLocation, ClimateRange> candidates = new LinkedHashMap<>();
        candidates.put(ResourceLocation.fromNamespaceAndPath("addon", "crop/quinoa"), QUINOA);
        candidates.put(ResourceLocation.fromNamespaceAndPath("addon", "crop/crop_quinoa"), QUINOA);

        assertNull(TFCCropRegistry.resolveResourceClimate(blockId, candidates));
        assertNull(TFCCropRegistry.resolveResourceClimate(
            ResourceLocation.fromNamespaceAndPath("addon", "plants/unknown_crop"), candidates));
    }

    @Test
    void climateCodecParsesEveryCurrentFieldAndDefaults()
    {
        ClimateRange full = ClimateRange.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
            {"min_hydration":11,"max_hydration":77,"hydration_wiggle_range":4,
             "min_temperature":-3.5,"max_temperature":28.25,"temperature_wiggle_range":1.5}
            """)).getOrThrow();
        assertEquals(11, full.minHydration());
        assertEquals(77, full.maxHydration());
        assertEquals(4, full.hydrationWiggleRange());
        assertEquals(-3.5f, full.minTemperature());
        assertEquals(28.25f, full.maxTemperature());
        assertEquals(1.5f, full.temperatureWiggleRange());

        ClimateRange defaults = ClimateRange.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        assertEquals(0, defaults.minHydration());
        assertEquals(100, defaults.maxHydration());
        assertEquals(0, defaults.hydrationWiggleRange());
        assertEquals(Float.NEGATIVE_INFINITY, defaults.minTemperature());
        assertEquals(Float.POSITIVE_INFINITY, defaults.maxTemperature());
        assertEquals(0f, defaults.temperatureWiggleRange());
    }
}
