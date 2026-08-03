package com.rustysnail.world.preview.tfc.backend.export;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class IndexedPngWriterTest
{
    @TempDir
    Path tempDirectory;

    @Test
    void writesEightBitIndexedRowsAndDimensions() throws Exception
    {
        Path png = this.tempDirectory.resolve("indexed.png");
        int[] palette = {0x112233, 0xAABBCC, 0x010203};
        try (IndexedPngWriter writer = new IndexedPngWriter(png, 7, 5, palette))
        {
            byte[] rows = new byte[(7 + 1) * 5];
            for (int row = 0; row < 5; row++)
            {
                rows[row * 8] = 0;
                for (int x = 0; x < 7; x++)
                {
                    rows[row * 8 + 1 + x] = (byte) ((row + x) % palette.length);
                }
            }
            writer.writeRows(rows, 5);
            writer.finish();
        }

        byte[] bytes = Files.readAllBytes(png);
        assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
            java.util.Arrays.copyOf(bytes, 8));
        try (DataInputStream input = new DataInputStream(Files.newInputStream(png)))
        {
            input.skipNBytes(8);
            assertEquals(13, input.readInt());
            assertEquals("IHDR", new String(input.readNBytes(4), java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals(7, input.readInt());
            assertEquals(5, input.readInt());
            assertEquals(8, input.readUnsignedByte());
            assertEquals(3, input.readUnsignedByte());
        }

        BufferedImage image = ImageIO.read(png.toFile());
        assertEquals(7, image.getWidth());
        assertEquals(5, image.getHeight());
        assertEquals(0xAABBCC, image.getRGB(1, 0) & 0xFFFFFF);
    }

    @Test
    void acceptsPaletteSizesOneThrough256() throws Exception
    {
        for (int size = 1; size <= 256; size++)
        {
            Path png = this.tempDirectory.resolve("palette-" + size + ".png");
            int[] palette = new int[size];
            for (int i = 0; i < size; i++)
            {
                palette[i] = i * 0x010101;
            }
            try (IndexedPngWriter writer = new IndexedPngWriter(png, 1, 1, palette))
            {
                writer.writeRows(new byte[] {0, 0}, 1);
                writer.finish();
            }
            assertEquals(size * 3, paletteChunkLength(png));
        }
    }

    private static int paletteChunkLength(Path png) throws Exception
    {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(png)))
        {
            input.skipNBytes(8);
            while (true)
            {
                int length = input.readInt();
                String type = new String(input.readNBytes(4), java.nio.charset.StandardCharsets.US_ASCII);
                if (type.equals("PLTE"))
                {
                    return length;
                }
                input.skipNBytes(length + 4L);
            }
        }
    }
}
