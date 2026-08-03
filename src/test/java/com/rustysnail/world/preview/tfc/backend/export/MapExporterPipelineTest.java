package com.rustysnail.world.preview.tfc.backend.export;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Spec;
import com.rustysnail.world.preview.tfc.backend.export.MapExporter.Context;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MapExporterPipelineTest
{
    @TempDir
    Path tempDirectory;

    @Test
    void boundsInFlightStripesAndWritesInRowOrder() throws Exception
    {
        Spec spec = new Spec("test", 520, 4, 130, 130, 1);
        MapExportPlan plan = new MapExportPlan(
            MapExportLayer.TERRAIN, new int[] {0x000000, 0xFFFFFF}, "test", false,
            (west, north, axis) -> Math.floorMod(north, 2));
        MapExporter exporter = new MapExporter(2);

        MapExporter.Result result = exporter.export(
            spec, plan, context(), () -> false, ignored -> {});

        assertTrue(Files.exists(result.png()));
        assertTrue(Files.exists(result.metadataJson()));
        assertTrue(exporter.maxInFlightObserved() <= 2);
        assertEquals(2, exporter.maxInFlightObserved());
        BufferedImage image = ImageIO.read(result.png().toFile());
        assertEquals(0x000000, image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0xFFFFFF, image.getRGB(0, 65) & 0xFFFFFF);
        assertEquals(0x000000, image.getRGB(0, 128) & 0xFFFFFF);
    }

    @Test
    void cancellationRemovesPartAndFinalFiles()
    {
        Spec spec = new Spec("cancel", 512, 4, 128, 128, 1);
        AtomicInteger calls = new AtomicInteger();
        MapExportPlan plan = new MapExportPlan(
            MapExportLayer.LAND_WATER, new int[] {0x000000, 0xFFFFFF}, "test", false,
            (west, north, axis) -> {
                calls.incrementAndGet();
                return 0;
            });
        MapExporter exporter = new MapExporter(2);

        assertThrows(CancellationException.class, () -> exporter.export(
            spec, plan, context(), () -> calls.get() >= 32, ignored -> {}));

        Path png = this.tempDirectory.resolve(
            MapExportNames.pngFilename("test seed", "cancel", MapExportLayer.LAND_WATER, -10, 20));
        Path json = this.tempDirectory.resolve(
            MapExportNames.metadataFilename("test seed", "cancel", MapExportLayer.LAND_WATER, -10, 20));
        assertFalse(Files.exists(png));
        assertFalse(Files.exists(json));
        assertFalse(Files.exists(MapExporter.partPath(png)));
        assertFalse(Files.exists(MapExporter.partPath(json)));
    }

    private Context context()
    {
        return new Context(
            "test seed", 42L, "minecraft:overworld", -10, 20, this.tempDirectory,
            20_000D, 20_000D, "test", true, "4.2.5", "test"
        );
    }
}
