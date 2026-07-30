package com.rustysnail.world.preview.tfc.client.gui.widgets;

import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCRegionWorkUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewDisplayTerrainOverlayTest
{
    @Test
    void onlyOceanUsesTheSharedTerrainOverlay()
    {
        assertTrue(PreviewDisplay.shouldOverlayOcean(TFCRegionWorkUnit.LAND_WATER_OCEAN));
        assertFalse(PreviewDisplay.shouldOverlayOcean(TFCRegionWorkUnit.LAND_WATER_LAND));
        assertFalse(PreviewDisplay.shouldOverlayOcean(TFCRegionWorkUnit.LAND_WATER_SHORE));
        assertFalse(PreviewDisplay.shouldOverlayOcean(TFCRegionWorkUnit.LAND_WATER_LAKE));
        assertFalse(PreviewDisplay.shouldOverlayOcean(TFCRegionWorkUnit.LAND_WATER_RIVER));
    }
}
