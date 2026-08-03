package com.rustysnail.world.preview.tfc.backend.export;

import net.minecraft.core.QuartPos;

public enum MapExportPreset
{
    FIFTY_K(new Spec("50k", 50_000, 4, 12_500, 12_500, 1)),
    HUNDRED_K(new Spec("100k", 100_000, 8, 12_500, 12_500, 2)),
    TWO_HUNDRED_K(new Spec("200k", 200_000, 16, 12_500, 12_500, 4));

    private final Spec spec;

    MapExportPreset(Spec spec)
    {
        this.spec = spec;
    }

    public Spec spec()
    {
        return this.spec;
    }

    public record Spec(
        String id,
        int coverageBlocks,
        int blocksPerPixel,
        int imageWidth,
        int imageHeight,
        int quartSamplesPerAxis
    )
    {
        public Spec
        {
            if (id == null || id.isBlank() || coverageBlocks <= 0 || blocksPerPixel <= 0
                || imageWidth <= 0 || imageHeight <= 0 || quartSamplesPerAxis <= 0)
            {
                throw new IllegalArgumentException("Invalid map export specification");
            }
            if ((long) imageWidth * blocksPerPixel != coverageBlocks
                || (long) imageHeight * blocksPerPixel != coverageBlocks
                || blocksPerPixel != quartSamplesPerAxis * 4)
            {
                throw new IllegalArgumentException("Map export geometry must cover every quart exactly");
            }
        }

        public Bounds bounds(int centerX, int centerZ)
        {
            int half = this.coverageBlocks / 2;
            int minX = Math.toIntExact((long) centerX - half);
            int minZ = Math.toIntExact((long) centerZ - half);
            int maxX = Math.toIntExact((long) minX + this.coverageBlocks - 1L);
            int maxZ = Math.toIntExact((long) minZ + this.coverageBlocks - 1L);
            return new Bounds(minX, maxX, minZ, maxZ);
        }

        public int minimumQuartX(Bounds bounds)
        {
            return QuartPos.fromBlock(bounds.minX());
        }

        public int minimumQuartZ(Bounds bounds)
        {
            return QuartPos.fromBlock(bounds.minZ());
        }

        public long samplingWork()
        {
            return (long) this.imageWidth * this.imageHeight * this.quartSamplesPerAxis * this.quartSamplesPerAxis;
        }
    }

    public record Bounds(int minX, int maxX, int minZ, int maxZ)
    {
        public int width()
        {
            return Math.toIntExact((long) this.maxX - this.minX + 1L);
        }

        public int height()
        {
            return Math.toIntExact((long) this.maxZ - this.minZ + 1L);
        }
    }
}
