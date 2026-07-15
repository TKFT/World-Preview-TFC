package com.rustysnail.world.preview.tfc.backend.export;

import net.minecraft.core.QuartPos;

public enum LandWaterExportPreset
{
    FIFTY_K(new Spec("50k", 50_000, 4, 12_500, 12_500, Sampling.QUART)),
    HUNDRED_K(new Spec("100k", 100_000, 8, 12_500, 12_500, Sampling.QUART_2X2));

    private final Spec spec;

    LandWaterExportPreset(Spec spec)
    {
        this.spec = spec;
    }

    public Spec spec()
    {
        return this.spec;
    }

    public enum Sampling
    {
        QUART(1),
        QUART_2X2(4);

        private final int samplesPerPixel;

        Sampling(int samplesPerPixel)
        {
            this.samplesPerPixel = samplesPerPixel;
        }

        public int samplesPerPixel()
        {
            return this.samplesPerPixel;
        }
    }

    public record Spec(
        String id,
        int coverageBlocks,
        int blocksPerPixel,
        int imageWidth,
        int imageHeight,
        Sampling sampling
    )
    {
        public Spec
        {
            if (id == null || id.isBlank() || coverageBlocks <= 0 || blocksPerPixel <= 0
                || imageWidth <= 0 || imageHeight <= 0 || sampling == null)
            {
                throw new IllegalArgumentException("Invalid land/water export specification");
            }
            if ((long) imageWidth * blocksPerPixel != coverageBlocks
                || (long) imageHeight * blocksPerPixel != coverageBlocks)
            {
                throw new IllegalArgumentException("Image dimensions do not exactly cover the requested blocks");
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

        public int sampleBlockX(Bounds bounds, int pixelX, int quartOffset)
        {
            return sampleBlock(bounds.minX, pixelX, quartOffset);
        }

        public int sampleBlockZ(Bounds bounds, int pixelZ, int quartOffset)
        {
            return sampleBlock(bounds.minZ, pixelZ, quartOffset);
        }

        public int sampleQuartX(Bounds bounds, int pixelX, int quartOffset)
        {
            return QuartPos.fromBlock(sampleBlockX(bounds, pixelX, quartOffset));
        }

        public int sampleQuartZ(Bounds bounds, int pixelZ, int quartOffset)
        {
            return QuartPos.fromBlock(sampleBlockZ(bounds, pixelZ, quartOffset));
        }

        public long samplingWork()
        {
            return (long) this.imageWidth * this.imageHeight * this.sampling.samplesPerPixel();
        }

        private int sampleBlock(int minimum, int pixel, int quartOffset)
        {
            if (pixel < 0 || pixel >= this.imageWidth)
            {
                throw new IndexOutOfBoundsException("Pixel outside export image: " + pixel);
            }
            int maximumOffset = this.sampling == Sampling.QUART_2X2 ? 1 : 0;
            if (quartOffset < 0 || quartOffset > maximumOffset)
            {
                throw new IndexOutOfBoundsException("Invalid quart offset: " + quartOffset);
            }
            return Math.toIntExact((long) minimum + (long) pixel * this.blocksPerPixel + 4L * quartOffset);
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
