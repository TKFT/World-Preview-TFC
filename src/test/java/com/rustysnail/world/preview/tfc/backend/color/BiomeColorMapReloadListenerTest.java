package com.rustysnail.world.preview.tfc.backend.color;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BiomeColorMapReloadListenerTest
{
    @Test
    void parsesBundledTfcBiomeColorsWithoutOptionalNames() throws Exception
    {
        InputStream stream = BiomeColorMapReloadListenerTest.class
            .getResourceAsStream("/data/tfc/worldgen/biome_colors.json");
        assertNotNull(stream);

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            JsonElement json = JsonParser.parseReader(reader);
            Map<ResourceLocation, PreviewMappingData.ColorEntry> parsed =
                BiomeColorMapReloadListener.parseColorData(json, PreviewData.DataSource.RESOURCE);

            assertEquals(json.getAsJsonObject().size(), parsed.size());

            PreviewMappingData.ColorEntry ocean = parsed.get(ResourceLocation.parse("tfc:ocean"));
            assertNotNull(ocean);
            assertEquals(0x1C5596, ocean.color);
            assertNull(ocean.name);

            PreviewMappingData.ColorEntry riverValley =
                parsed.get(ResourceLocation.parse("tfc:river_valley"));
            assertNotNull(riverValley);
            assertEquals(0x4BFA46, riverValley.color);
            assertNull(riverValley.name);
        }
    }
}
