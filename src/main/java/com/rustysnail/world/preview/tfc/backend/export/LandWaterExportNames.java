package com.rustysnail.world.preview.tfc.backend.export;

import java.text.Normalizer;

public final class LandWaterExportNames
{
    private static final int MAX_COMPONENT_LENGTH = 64;

    private LandWaterExportNames()
    {
    }

    public static String pngFilename(String seedEntered, String presetId, int centerX, int centerZ)
    {
        return "tfc_land_water_" + sanitizeComponent(seedEntered) + "_" + sanitizeComponent(presetId)
            + "_x" + centerX + "_z" + centerZ + ".png";
    }

    public static String metadataFilename(String seedEntered, String presetId, int centerX, int centerZ)
    {
        String png = pngFilename(seedEntered, presetId, centerX, centerZ);
        return png.substring(0, png.length() - 4) + ".json";
    }

    public static String sanitizeComponent(String value)
    {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), MAX_COMPONENT_LENGTH));
        boolean previousUnderscore = false;

        for (int offset = 0; offset < normalized.length() && result.length() < MAX_COMPONENT_LENGTH; )
        {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean allowed = Character.isLetterOrDigit(codePoint) || codePoint == '-' || codePoint == '_' || codePoint == '.';
            char output = allowed && codePoint <= Character.MAX_VALUE ? (char) codePoint : '_';
            if (output == '_')
            {
                if (previousUnderscore)
                {
                    continue;
                }
                previousUnderscore = true;
            }
            else
            {
                previousUnderscore = false;
            }
            result.append(output);
        }

        while (!result.isEmpty() && (result.charAt(0) == '.' || result.charAt(0) == '_'))
        {
            result.deleteCharAt(0);
        }
        while (!result.isEmpty() && (result.charAt(result.length() - 1) == '.' || result.charAt(result.length() - 1) == '_'))
        {
            result.deleteCharAt(result.length() - 1);
        }
        return result.isEmpty() ? "seed" : result.toString();
    }
}
