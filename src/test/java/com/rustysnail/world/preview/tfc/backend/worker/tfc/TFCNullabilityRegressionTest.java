package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TFCNullabilityRegressionTest
{
    @Test
    void nullModeProducesAFeatureOnlyPlan()
    {
        TFCWorkPlan plan = assertDoesNotThrow(() -> TFCWorkPlan.forMode(null, true));

        assertNull(plan.mode());
        assertTrue(plan.features());
        assertFalse(plan.temperature());
        assertFalse(plan.landWater());
    }

    @Test
    void nullRegionPointUsesInvalidLandWaterFallback()
    {
        assertEquals(Short.MIN_VALUE, TFCRegionWorkUnit.classifyLandWater(null, false));
    }
}
