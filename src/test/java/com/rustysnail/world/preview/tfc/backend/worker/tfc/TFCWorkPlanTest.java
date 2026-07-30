package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.RenderSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TFCWorkPlanTest
{
    @Test
    void rockTypeDoesNotGenerateUnusedLandWaterData()
    {
        TFCWorkPlan plan = TFCWorkPlan.forMode(RenderSettings.RenderMode.TFC_ROCK_TYPE, false);

        assertTrue(plan.rocks());
        assertFalse(plan.landWater());
    }
}
