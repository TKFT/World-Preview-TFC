package com.rustysnail.world.preview.tfc.backend.export;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Sampling;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Spec;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterMapExporter.Context;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BinaryPngAndCancellationTest
{
    @TempDir
    Path tempDirectory;

    @Test
    void writesRequestedFullDimensionsWithExactlyTwoPaletteEntries() throws Exception
    {
        Path png = this.tempDirectory.resolve("two-color.png");
        try (BinaryIndexedPngWriter writer = new BinaryIndexedPngWriter(png, 12_500, 12_500, 0x8B9B65, 0x173F5F))
        {
            byte[] landRow = new byte[writer.filteredRowSize()];
            for (int row = 0; row < 12_500; row++)
            {
                writer.writeRows(landRow, 1);
            }
            writer.finish();
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(png.toFile()))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input);
                assertEquals(12_500, reader.getWidth(0));
                assertEquals(12_500, reader.getHeight(0));
            }
            finally
            {
                reader.dispose();
            }
        }

        Path palettePng = this.tempDirectory.resolve("two-color-palette.png");
        try (BinaryIndexedPngWriter writer = new BinaryIndexedPngWriter(palettePng, 8, 8, 0x8B9B65, 0x173F5F))
        {
            writer.writeRows(new byte[writer.filteredRowSize() * 8], 8);
            writer.finish();
        }
        BufferedImage paletteImage = ImageIO.read(palettePng.toFile());
        IndexColorModel colors = assertInstanceOf(IndexColorModel.class, paletteImage.getColorModel());
        assertEquals(2, colors.getMapSize());
    }

    @Test
    void cancellationRemovesAllIncompleteFiles() throws Exception
    {
        Spec spec = new Spec("test", 32, 4, 8, 8, Sampling.QUART);
        Context context = new Context(
            "cancel-seed", 99L, "minecraft:overworld", 0, 0, this.tempDirectory,
            0x8B9B65, 0x173F5F, "test", true, "test", null
        );
        AtomicInteger calls = new AtomicInteger();
        LandWaterMapExporter exporter = new LandWaterMapExporter(1);

        assertThrows(CancellationException.class, () -> exporter.export(
            spec,
            context,
            (quartX, quartZ) -> {
                calls.incrementAndGet();
                return LandWaterSample.LAND;
            },
            () -> calls.get() >= 8,
            ignored -> {}
        ));

        Path png = this.tempDirectory.resolve(LandWaterExportNames.pngFilename("cancel-seed", "test", 0, 0));
        Path json = this.tempDirectory.resolve(LandWaterExportNames.metadataFilename("cancel-seed", "test", 0, 0));
        assertFalse(Files.exists(png));
        assertFalse(Files.exists(json));
        assertFalse(Files.exists(LandWaterMapExporter.partPath(png)));
        assertFalse(Files.exists(LandWaterMapExporter.partPath(json)));
    }
}
