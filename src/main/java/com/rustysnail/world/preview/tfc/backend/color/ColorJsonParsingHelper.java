package com.rustysnail.world.preview.tfc.backend.color;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

final class ColorJsonParsingHelper
{
    @Nullable
    static String parseOptionalName(JsonObject raw)
    {
        JsonElement nameEl = raw.get("name");
        if (nameEl == null)
        {
            return null;
        }
        if (!nameEl.isJsonPrimitive() || !nameEl.getAsJsonPrimitive().isString())
        {
            throw new IllegalArgumentException("name must be a string");
        }
        return nameEl.getAsString();
    }

    static int parsePackedRgbColor(JsonObject raw)
    {
        JsonElement colorEl = raw.get("color");
        if (colorEl != null)
        {
            int color = colorEl.getAsInt();
            if (color < 0 || color > 0xFFFFFF)
            {
                throw new IllegalArgumentException("color must be between 0 and 16777215");
            }
            return color;
        }

        JsonElement rEl = raw.get("r");
        JsonElement gEl = raw.get("g");
        JsonElement bEl = raw.get("b");
        if (rEl == null || gEl == null || bEl == null)
        {
            throw new IllegalStateException("No color was provided!");
        }

        int r = parseChannel(rEl, "r");
        int g = parseChannel(gEl, "g");
        int b = parseChannel(bEl, "b");
        return r << 16 | g << 8 | b;
    }

    private static int parseChannel(JsonElement element, String name)
    {
        int value = element.getAsInt();
        if (value < 0 || value > 255)
        {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
        return value;
    }

    private ColorJsonParsingHelper()
    {
    }
}
