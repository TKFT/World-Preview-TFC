package com.rustysnail.world.preview.tfc.backend.export;

import java.util.Locale;

public final class MapExportNames
{
    private MapExportNames()
    {
    }

    public static String pngFilename(String seed, String preset, MapExportLayer layer, int centerX, int centerZ)
    {
        return base(seed, preset, layer, centerX, centerZ) + ".png";
    }

    public static String metadataFilename(String seed, String preset, MapExportLayer layer, int centerX, int centerZ)
    {
        return base(seed, preset, layer, centerX, centerZ) + ".json";
    }

    static String sanitizeSeed(String value)
    {
        String normalized = value == null ? "seed" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), 48));
        boolean separator = false;
        for (int i = 0; i < normalized.length() && result.length() < 48; i++)
        {
            char character = normalized.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_')
            {
                result.append(character);
                separator = false;
            }
            else if (!separator && !result.isEmpty())
            {
                result.append('_');
                separator = true;
            }
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '_')
        {
            result.setLength(result.length() - 1);
        }
        return result.isEmpty() ? "seed" : result.toString();
    }

    private static String base(String seed, String preset, MapExportLayer layer, int centerX, int centerZ)
    {
        return "tfc_map_" + sanitizeSeed(seed) + '_' + preset + '_' + layer.id()
            + "_x" + centerX + "_z" + centerZ;
    }
}
