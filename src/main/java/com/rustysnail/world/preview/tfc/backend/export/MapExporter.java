package com.rustysnail.world.preview.tfc.backend.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Bounds;
import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset.Spec;
import org.jetbrains.annotations.Nullable;

public final class MapExporter
{
    static final int STRIPE_HEIGHT = 64;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private final int workerThreads;
    private volatile int maxInFlightObserved;

    public MapExporter(int workerThreads)
    {
        this.workerThreads = Math.clamp(workerThreads, 1, 8);
    }

    public Result export(
        Spec spec,
        MapExportPlan plan,
        Context context,
        BooleanSupplier cancelled,
        LongConsumer progress
    ) throws IOException
    {
        Bounds bounds = spec.bounds(context.centerX(), context.centerZ());
        Files.createDirectories(context.outputDirectory());
        this.maxInFlightObserved = 0;

        String pngName = MapExportNames.pngFilename(
            context.seedEntered(), spec.id(), plan.layer(), context.centerX(), context.centerZ());
        String jsonName = MapExportNames.metadataFilename(
            context.seedEntered(), spec.id(), plan.layer(), context.centerX(), context.centerZ());
        Path png = context.outputDirectory().resolve(pngName);
        Path json = context.outputDirectory().resolve(jsonName);
        Path pngPart = partPath(png);
        Path jsonPart = partPath(json);
        Files.deleteIfExists(pngPart);
        Files.deleteIfExists(jsonPart);

        ExecutorService workers = Executors.newFixedThreadPool(this.workerThreads, new ExportThreadFactory());
        Deque<Future<Stripe>> inFlight = new ArrayDeque<>(this.workerThreads);

        try
        {
            int nextRow = 0;
            while (nextRow < spec.imageHeight() && inFlight.size() < this.workerThreads)
            {
                nextRow = submitStripe(inFlight, workers, spec, bounds, plan, nextRow, cancelled, progress);
            }

            try (IndexedPngWriter writer = new IndexedPngWriter(
                pngPart, spec.imageWidth(), spec.imageHeight(), plan.paletteRgbUnsafe()))
            {
                while (!inFlight.isEmpty())
                {
                    checkCancelled(cancelled);
                    Stripe stripe = await(inFlight.removeFirst());
                    writer.writeRows(stripe.filteredRows(), stripe.rowCount());
                    if (nextRow < spec.imageHeight())
                    {
                        nextRow = submitStripe(inFlight, workers, spec, bounds, plan, nextRow, cancelled, progress);
                    }
                }
                checkCancelled(cancelled);
                writer.finish();
            }

            MapExportMetadata metadata = new MapExportMetadata(
                context.seedEntered(),
                context.resolvedNumericSeed(),
                context.dimension(),
                context.centerX(),
                context.centerZ(),
                bounds,
                plan.layer().id(),
                spec.id(),
                spec.blocksPerPixel(),
                spec.quartSamplesPerAxis(),
                spec.imageWidth(),
                spec.imageHeight(),
                plan.paletteRgbUnsafe().length,
                plan.paletteMode(),
                plan.continentCellWaterShading(),
                plan.continentCellWaterShading() ? 16 : 0,
                context.effectiveTemperatureScale(),
                context.effectiveRainfallScale(),
                context.exporterVersion(),
                Instant.now().toString(),
                context.tfcDetected(),
                context.tfcVersion(),
                context.tfcLargeBiomesVersion()
            );
            Files.writeString(jsonPart, GSON.toJson(metadata) + System.lineSeparator(), StandardCharsets.UTF_8);

            checkCancelled(cancelled);
            moveCompleteFile(jsonPart, json);
            checkCancelled(cancelled);
            moveCompleteFile(pngPart, png);
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
            for (Future<Stripe> future : inFlight)
            {
                future.cancel(true);
            }
            workers.shutdownNow();
            Files.deleteIfExists(pngPart);
            Files.deleteIfExists(jsonPart);
        }
    }

    int maxInFlightObserved()
    {
        return this.maxInFlightObserved;
    }

    static Path partPath(Path completePath)
    {
        return completePath.resolveSibling(completePath.getFileName() + ".part");
    }

    private int submitStripe(
        Deque<Future<Stripe>> inFlight,
        ExecutorService workers,
        Spec spec,
        Bounds bounds,
        MapExportPlan plan,
        int startRow,
        BooleanSupplier cancelled,
        LongConsumer progress
    )
    {
        int rows = Math.min(STRIPE_HEIGHT, spec.imageHeight() - startRow);
        inFlight.addLast(workers.submit(
            () -> sampleStripe(spec, bounds, plan, startRow, rows, cancelled, progress)));
        this.maxInFlightObserved = Math.max(this.maxInFlightObserved, inFlight.size());
        return startRow + rows;
    }

    private static Stripe sampleStripe(
        Spec spec,
        Bounds bounds,
        MapExportPlan plan,
        int startRow,
        int rowCount,
        BooleanSupplier cancelled,
        LongConsumer progress
    )
    {
        int filteredRowSize = spec.imageWidth() + 1;
        byte[] rows = new byte[Math.multiplyExact(rowCount, filteredRowSize)];
        int samplesPerAxis = spec.quartSamplesPerAxis();
        int samplesPerPixel = samplesPerAxis * samplesPerAxis;
        int minimumQuartX = spec.minimumQuartX(bounds);
        int minimumQuartZ = spec.minimumQuartZ(bounds);

        for (int localRow = 0; localRow < rowCount; localRow++)
        {
            checkCancelled(cancelled);
            int pixelZ = startRow + localRow;
            int north = minimumQuartZ + pixelZ * samplesPerAxis;
            int rowOffset = localRow * filteredRowSize;

            for (int pixelX = 0; pixelX < spec.imageWidth(); pixelX++)
            {
                if ((pixelX & 255) == 0)
                {
                    checkCancelled(cancelled);
                }
                int west = minimumQuartX + pixelX * samplesPerAxis;
                int paletteIndex = plan.sampler().sample(west, north, samplesPerAxis);
                if ((paletteIndex & ~0xFF) != 0 || paletteIndex >= plan.paletteRgbUnsafe().length)
                {
                    throw new IllegalStateException(
                        plan.layer().id() + " sampler returned invalid palette index " + paletteIndex);
                }
                rows[rowOffset + 1 + pixelX] = (byte) paletteIndex;
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
            throw new CancellationException("Map export interrupted");
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
            throw new IOException("Map sampling failed", cause);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
        {
            throw new CancellationException("Map export cancelled");
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

    public record Context(
        String seedEntered,
        long resolvedNumericSeed,
        String dimension,
        int centerX,
        int centerZ,
        Path outputDirectory,
        double effectiveTemperatureScale,
        double effectiveRainfallScale,
        String exporterVersion,
        boolean tfcDetected,
        @Nullable String tfcVersion,
        @Nullable String tfcLargeBiomesVersion
    )
    {
    }

    public record Result(Path png, Path metadataJson, MapExportMetadata metadata)
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
            Thread thread = new Thread(task, "world-preview-map-sampler-" + NEXT_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
