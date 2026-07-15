package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuartSurfaceHeightsTest
{
    @Test
    void constantTerrainRemainsConstant()
    {
        QuartSurfaceHeights heights = new QuartSurfaceHeights(72, 72, 72, 72);
        for (int x = 0; x < 16; x += 4)
        {
            for (int z = 0; z < 16; z += 4)
            {
                assertEquals(72, heights.interpolatedSurfaceY(x, z));
            }
        }
    }

    @Test
    void steepTerrainUsesAllFourQuadrantsAndBilinearInterior()
    {
        QuartSurfaceHeights heights = new QuartSurfaceHeights(0, 80, 160, 240);

        assertEquals(0, heights.interpolatedSurfaceY(0, 0));
        assertEquals(80, heights.interpolatedSurfaceY(12, 0));
        assertEquals(160, heights.interpolatedSurfaceY(0, 12));
        assertEquals(240, heights.interpolatedSurfaceY(12, 12));
        assertEquals(60, heights.interpolatedSurfaceY(4, 4));
        assertEquals(180, heights.interpolatedSurfaceY(8, 8));
    }

    @Test
    void negativeChunksUseTheSameLocalQuartCoordinates()
    {
        QuartSurfaceHeights heights = new QuartSurfaceHeights(10, 20, 30, 40);
        assertEquals(heights.interpolatedSurfaceY(0, 0), heights.interpolatedSurfaceY(-16, -16));
        assertEquals(heights.interpolatedSurfaceY(12, 12), heights.interpolatedSurfaceY(-4, -4));
    }
}
