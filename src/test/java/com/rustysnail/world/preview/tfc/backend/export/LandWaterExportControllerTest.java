package com.rustysnail.world.preview.tfc.backend.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LandWaterExportControllerTest
{
    @Test
    void initialStatusAllowsMissingOutputDirectory()
    {
        try (LandWaterExportController controller = new LandWaterExportController(1))
        {
            LandWaterExportController.Status status = controller.status();

            assertEquals(LandWaterExportController.Phase.IDLE, status.phase());
            assertNull(status.outputDirectory());
        }
    }
}
