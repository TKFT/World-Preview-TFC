package com.rustysnail.world.preview.tfc.backend.color;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoricalColorPaletteTest
{
    private static ResourceLocation id(String namespace, String path)
    {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void mergeAcceptsPackedAndChannelColorsAndHigherPriorityValuesWin()
    {
        Map<ResourceLocation, CategoricalColorPalette.Entry> entries = new HashMap<>();
        ResourceLocation paletteId = id("test", "trees");
        CategoricalColorPaletteReloadListener.mergeJson(entries, paletteId, JsonParser.parseString("""
            {
              "tfc:oak": {"r": 1, "g": 2, "b": 3, "name": "Oak"},
              "addon:tree": {"color": 1056816}
            }
            """), "low pack");
        CategoricalColorPaletteReloadListener.mergeJson(entries, paletteId, JsonParser.parseString("""
            {"tfc:oak": {"color": 66051, "name": "Overridden Oak"}}
            """), "high pack");

        assertEquals(0x010203, entries.get(id("tfc", "oak")).rgb());
        assertEquals("Overridden Oak", entries.get(id("tfc", "oak")).name());
        assertEquals(0x102030, entries.get(id("addon", "tree")).rgb());
    }

    @Test
    void malformedEntryDoesNotDiscardValidSiblings()
    {
        Map<ResourceLocation, CategoricalColorPalette.Entry> entries = new HashMap<>();
        CategoricalColorPaletteReloadListener.mergeJson(entries, id("test", "palette"), JsonParser.parseString("""
            {
              "test:bad": {"r": 300, "g": 0, "b": 0},
              "test:good": {"r": 10, "g": 20, "b": 30, "name": "Good"}
            }
            """), "test resource");

        assertFalse(entries.containsKey(id("test", "bad")));
        assertEquals(0x0A141E, entries.get(id("test", "good")).rgb());
    }

    @Test
    void replacingPalettesChangesLookupsAndOnlyAdvancesPaletteRevision()
    {
        PreviewMappingData mapping = new PreviewMappingData();
        ResourceLocation value = id("addon", "tree");
        mapping.replaceCategoricalPalettes(Map.of(
            TFCColorPalettes.TREE_SPECIES,
            new CategoricalColorPalette(Map.of(value, new CategoricalColorPalette.Entry(0x123456, "First")))
        ));
        long firstRevision = mapping.paletteRevision();
        assertEquals(0xFF123456, mapping.getCategoricalColor(TFCColorPalettes.TREE_SPECIES, value, 0));

        mapping.replaceCategoricalPalettes(Map.of(
            TFCColorPalettes.TREE_SPECIES,
            new CategoricalColorPalette(Map.of(value, new CategoricalColorPalette.Entry(0xABCDEF, "Second")))
        ));
        assertEquals(firstRevision + 1, mapping.paletteRevision());
        assertEquals(0xFFABCDEF, mapping.getCategoricalColor(TFCColorPalettes.TREE_SPECIES, value, 0));
        assertEquals("Second", mapping.getCategoricalName(TFCColorPalettes.TREE_SPECIES, value));
    }

    @Test
    void configuredGradientCanSelectAnyLoadedColormap()
    {
        PreviewMappingData mapping = new PreviewMappingData();
        ResourceLocation fallbackId = id("world_preview_tfc", "tfc_temperature");
        ResourceLocation customId = id("addon", "temperature");
        ColorMap fallback = colorMap(fallbackId, "Fallback");
        ColorMap custom = colorMap(customId, "Custom");
        mapping.replaceColorMaps(Map.of(fallbackId, fallback, customId, custom));

        assertSame(custom, mapping.resolveColorMap(customId.toString(), fallbackId, "temperature"));
        assertSame(fallback, mapping.resolveColorMap("missing:temperature", fallbackId, "temperature"));
    }

    @Test
    void bundledDefaultsContainEveryCorePalette()
    {
        for (String palette : List.of("forest_types", "tree_species", "soil_types", "water", "suitability", "rock_types", "rocks"))
        {
            String path = "/data/world_preview_tfc/world_preview_tfc/colors/" + palette + ".json";
            try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(path));
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
            {
                JsonElement json = JsonParser.parseReader(reader);
                assertTrue(json.isJsonObject());
                assertTrue(json.getAsJsonObject().size() > 0);
            }
            catch (Exception e)
            {
                fail("Could not read " + path, e);
            }
        }
    }

    @Test
    void legacyAndGenericRockDefaultsCanMergeIntoOnePalette()
    {
        Map<ResourceLocation, CategoricalColorPalette.Entry> entries = new HashMap<>();
        CategoricalColorPaletteReloadListener.mergeJson(entries, TFCColorPalettes.ROCKS,
            readResource("/data/tfc/worldgen/rock_colors.json"), "legacy rock resource");
        assertEquals(0x4A4655, entries.get(id("tfc", "granite")).rgb());

        CategoricalColorPaletteReloadListener.mergeJson(entries, TFCColorPalettes.ROCKS,
            readResource("/data/world_preview_tfc/world_preview_tfc/colors/rocks.json"), "generic rock resource");
        assertEquals("Granite", entries.get(id("tfc", "granite")).name());
        assertEquals(20, entries.size());
    }

    private static JsonElement readResource(String path)
    {
        try (var stream = Objects.requireNonNull(CategoricalColorPaletteTest.class.getResourceAsStream(path));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(reader);
        }
        catch (Exception e)
        {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    private static ColorMap colorMap(ResourceLocation id, String name)
    {
        return new ColorMap(id, new ColorMap.RawColorMap(name, List.of(
            List.of(0f, 0f, 0f), List.of(1f, 1f, 1f)
        )));
    }
}
