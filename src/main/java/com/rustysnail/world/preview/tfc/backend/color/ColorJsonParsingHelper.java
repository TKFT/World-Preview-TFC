package com.rustysnail.world.preview.tfc.backend.color;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

final class ColorJsonParsingHelper
{
    private ColorJsonParsingHelper()
    {
    }

    @Nullable
    static String parseOptionalName(JsonObject raw)
    {
        JsonElement nameEl = raw.get("name");
        return nameEl == null ? null : nameEl.getAsString();
    }

    static int parsePackedRgbColor(JsonObject raw)
    {
        JsonElement colorEl = raw.get("color");
        if (colorEl != null)
        {
            return colorEl.getAsInt() & 0xFFFFFF;
        }

        JsonElement rEl = raw.get("r");
        JsonElement gEl = raw.get("g");
        JsonElement bEl = raw.get("b");
        if (rEl == null || gEl == null || bEl == null)
        {
            throw new IllegalStateException("No color was provided!");
        }

        int r = rEl.getAsInt() & 0xFF;
        int g = gEl.getAsInt() & 0xFF;
        int b = bEl.getAsInt() & 0xFF;
        return r << 16 | g << 8 | b;
    }
}
