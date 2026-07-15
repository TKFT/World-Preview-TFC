package com.rustysnail.world.preview.tfc.backend.storage;

import com.rustysnail.world.preview.tfc.RenderSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewBlockResolutionTest
{
    @Test
    void cropStorageAlwaysUsesFullQuartStride()
    {
        long crop = RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag;
        assertEquals(1, PreviewBlock.sectionQuartStride(crop, 1));
        assertEquals(1, PreviewBlock.sectionQuartStride(crop, 2));
        assertEquals(1, PreviewBlock.sectionQuartStride(crop, 4));
    }

    @Test
    void existingModesRetainVisualStride()
    {
        long soil = RenderSettings.RenderMode.TFC_SOIL_TYPE.flag;
        assertEquals(1, PreviewBlock.sectionQuartStride(soil, 1));
        assertEquals(2, PreviewBlock.sectionQuartStride(soil, 2));
        assertEquals(4, PreviewBlock.sectionQuartStride(soil, 4));
    }
}
