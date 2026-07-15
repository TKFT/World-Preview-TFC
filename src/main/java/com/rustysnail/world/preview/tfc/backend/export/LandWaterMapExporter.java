package com.rustysnail.world.preview.tfc.backend.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Bounds;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Sampling;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset.Spec;

import net.minecraft.core.QuartPos;

public final class LandWaterMapExporter
{
    private static final int TILE_HEIGHT = 256;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private final int workerThreads;

    public LandWaterMapExporter(int workerThreads)
    {
        this.workerThreads = Math.clamp(workerThreads, 1, 8);
    }

    public Result export(
        Spec spec,
        Context context,
        QuartSampler sampler,
        BooleanSupplier cancelled,
        LongConsumer progress
    ) throws IOException
    {
        Bounds bounds = spec.bounds(context.centerX(), context.centerZ());
        Files.createDirectories(context.outputDirectory());

        String pngName = LandWaterExportNames.pngFilename(context.seedEntered(), spec.id(), context.centerX(), context.centerZ());
        String jsonName = LandWaterExportNames.metadataFilename(context.seedEntered(), spec.id(), context.centerX(), context.centerZ());
        Path png = context.outputDirectory().resolve(pngName);
        Path json = context.outputDirectory().resolve(jsonName);
        Path pngPart = partPath(png);
        Path jsonPart = partPath(json);
        Files.deleteIfExists(pngPart);
        Files.deleteIfExists(jsonPart);

        ExecutorService workers = Executors.newFixedThreadPool(this.workerThreads, new ExportThreadFactory());
        List<Future<Stripe>> stripes = new ArrayList<>((spec.imageHeight() + TILE_HEIGHT - 1) / TILE_HEIGHT);

        try
        {
            for (int startRow = 0; startRow < spec.imageHeight(); startRow += TILE_HEIGHT)
            {
                int row = startRow;
                int rows = Math.min(TILE_HEIGHT, spec.imageHeight() - row);
                stripes.add(workers.submit(() -> sampleStripe(spec, bounds, row, rows, sampler, cancelled, progress)));
            }

            try (BinaryIndexedPngWriter writer = new BinaryIndexedPngWriter(
                pngPart, spec.imageWidth(), spec.imageHeight(), context.landRgb(), context.waterRgb()))
            {
                for (Future<Stripe> future : stripes)
                {
                    checkCancelled(cancelled);
                    Stripe stripe = await(future);
                    writer.writeRows(stripe.filteredRows(), stripe.rowCount());
                }
                checkCancelled(cancelled);
                writer.finish();
            }

            LandWaterExportMetadata metadata = new LandWaterExportMetadata(
                context.seedEntered(),
                context.resolvedNumericSeed(),
                context.dimension(),
                context.centerX(),
                context.centerZ(),
                bounds,
                spec.blocksPerPixel(),
                spec.imageWidth(),
                spec.imageHeight(),
                context.exporterVersion(),
                Instant.now().toString(),
                LandWaterExportMetadata.CLASSIFICATION_MODE,
                context.tfcDetected(),
                context.tfcVersion(),
                context.tfcLargeBiomesVersion()
            );
            Files.writeString(jsonPart, GSON.toJson(metadata) + System.lineSeparator(), StandardCharsets.UTF_8);

            checkCancelled(cancelled);
            moveCompleteFile(jsonPart, json);
            checkCancelled(cancelled);
            moveCompleteFile(pngPart, png); // PNG is moved last and acts as the completion marker.
            return new Result(png, json, metadata);
        }
        catch (IOException | RuntimeException e)
        {
            Files.deleteIfExists(pngPart);
            Files.deleteIfExists(jsonPart);
            throw e;
        }
        finally
        {
            for (Future<Stripe> stripe : stripes)
            {
                stripe.cancel(true);
            }
            workers.shutdownNow();
            Files.deleteIfExists(pngPart);
            Files.deleteIfExists(jsonPart);
        }
    }

    static Path partPath(Path completePath)
    {
        return completePath.resolveSibling(completePath.getFileName() + ".part");
    }

    private static Stripe sampleStripe(
        Spec spec,
        Bounds bounds,
        int startRow,
        int rowCount,
        QuartSampler sampler,
        BooleanSupplier cancelled,
        LongConsumer progress
    )
    {
        int filteredRowSize = 1 + ((spec.imageWidth() + 7) >>> 3);
        byte[] rows = new byte[Math.multiplyExact(rowCount, filteredRowSize)];
        int samplesPerPixel = spec.sampling().samplesPerPixel();
        int minQuartX = QuartPos.fromBlock(bounds.minX());
        int minQuartZ = QuartPos.fromBlock(bounds.minZ());

        for (int localRow = 0; localRow < rowCount; localRow++)
        {
            checkCancelled(cancelled);
            int pixelZ = startRow + localRow;
            int rowOffset = localRow * filteredRowSize;

            int north = minQuartZ + (spec.sampling() == Sampling.QUART ? pixelZ : pixelZ * 2);
            int south = north + 1;
            for (int pixelX = 0; pixelX < spec.imageWidth(); pixelX++)
            {
                if ((pixelX & 255) == 0)
                {
                    checkCancelled(cancelled);
                }

                boolean water;
                if (spec.sampling() == Sampling.QUART)
                {
                    byte sample = sampler.sample(minQuartX + pixelX, north);
                    water = LandWaterAggregation.isWater(sample);
                }
                else
                {
                    int west = minQuartX + pixelX * 2;
                    water = LandWaterAggregation.aggregate2x2(
                        sampler.sample(west, north),
                        sampler.sample(west + 1, north),
                        sampler.sample(west, south),
                        sampler.sample(west + 1, south)
                    );
                }

                if (water)
                {
                    rows[rowOffset + 1 + (pixelX >>> 3)] |= (byte) (0x80 >>> (pixelX & 7));
                }
            }
            progress.accept((long) spec.imageWidth() * samplesPerPixel);
        }
        return new Stripe(rows, rowCount);
    }

    private static Stripe await(Future<Stripe> future) throws IOException
    {
        try
        {
            return future.get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new CancellationException("Land/water export interrupted");
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException cancellation)
            {
                throw cancellation;
            }
            if (cause instanceof RuntimeException runtime)
            {
                throw runtime;
            }
            if (cause instanceof Error error)
            {
                throw error;
            }
            throw new IOException("Land/water sampling failed", cause);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
        {
            throw new CancellationException("Land/water export cancelled");
        }
    }

    private static void moveCompleteFile(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    public interface QuartSampler
    {
        byte sample(int quartX, int quartZ);
    }

    public record Context(
        String seedEntered,
        long resolvedNumericSeed,
        String dimension,
        int centerX,
        int centerZ,
        Path outputDirectory,
        int landRgb,
        int waterRgb,
        String exporterVersion,
        boolean tfcDetected,
        String tfcVersion,
        String tfcLargeBiomesVersion
    )
    {
    }

    public record Result(Path png, Path metadataJson, LandWaterExportMetadata metadata)
    {
    }

    private record Stripe(byte[] filteredRows, int rowCount)
    {
    }

    private static final class ExportThreadFactory implements ThreadFactory
    {
        private static final AtomicInteger NEXT_ID = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task)
        {
            Thread thread = new Thread(task, "world-preview-land-water-sampler-" + NEXT_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
