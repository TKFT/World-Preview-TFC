package com.rustysnail.world.preview.tfc.backend.export;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterMapExporter.Context;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterMapExporter.QuartSampler;
import org.jetbrains.annotations.Nullable;

public final class LandWaterExportController implements AutoCloseable
{
    private final LandWaterMapExporter exporter;
    private final ExecutorService coordinator;
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final AtomicLong completedWork = new AtomicLong();
    private volatile long totalWork;
    private volatile long startedNanos;
    private volatile long finishedNanos;
    private volatile Phase phase = Phase.IDLE;
    private volatile String preset = "";
    private volatile String error = "";
    @Nullable private volatile Path outputDirectory;

    public LandWaterExportController(int workerThreads)
    {
        this.exporter = new LandWaterMapExporter(workerThreads);
        this.coordinator = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "world-preview-land-water-export");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized boolean start(List<LandWaterExportPreset> presets, Context context, QuartSampler sampler)
    {
        if (this.phase.running() || presets == null || presets.isEmpty())
        {
            return false;
        }

        List<LandWaterExportPreset> batch = List.copyOf(presets);
        this.cancelRequested.set(false);
        this.completedWork.set(0L);
        this.totalWork = batch.stream().mapToLong(value -> value.spec().samplingWork()).sum();
        this.startedNanos = System.nanoTime();
        this.finishedNanos = 0L;
        this.phase = Phase.EXPORTING;
        this.preset = batch.size() == 1 ? batch.getFirst().spec().id() : "batch";
        this.error = "";
        this.outputDirectory = context.outputDirectory();
        this.coordinator.submit(() -> runBatch(batch, context, sampler));
        return true;
    }

    public synchronized void cancel()
    {
        if (!this.phase.running())
        {
            return;
        }
        this.cancelRequested.set(true);
        this.phase = Phase.CANCELLING;
    }

    public Status status()
    {
        long now = this.finishedNanos == 0L ? System.nanoTime() : this.finishedNanos;
        long elapsed = this.startedNanos == 0L ? 0L : Math.max(0L, now - this.startedNanos);
        long completed = Math.min(this.completedWork.get(), this.totalWork);
        long remaining = -1L;
        if (this.phase.running() && completed > 0L && completed < this.totalWork)
        {
            remaining = (long) ((double) elapsed * (this.totalWork - completed) / completed);
        }
        return new Status(this.phase, this.preset, completed, this.totalWork, elapsed, remaining, this.outputDirectory, this.error);
    }

    @Override
    public void close()
    {
        cancel();
        this.coordinator.shutdownNow();
    }

    private void runBatch(List<LandWaterExportPreset> presets, Context context, QuartSampler sampler)
    {
        try
        {
            for (LandWaterExportPreset value : presets)
            {
                this.preset = value.spec().id();
                this.phase = Phase.EXPORTING;
                this.exporter.export(value.spec(), context, sampler, this.cancelRequested::get, this.completedWork::addAndGet);
            }
            this.phase = Phase.COMPLETED;
        }
        catch (CancellationException e)
        {
            this.phase = Phase.CANCELLED;
        }
        catch (Throwable e)
        {
            this.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            this.phase = Phase.FAILED;
            WorldPreview.LOGGER.error("Land/water map export failed", e);
        }
        finally
        {
            this.finishedNanos = System.nanoTime();
        }
    }

    public enum Phase
    {
        IDLE,
        EXPORTING,
        CANCELLING,
        COMPLETED,
        CANCELLED,
        FAILED;

        public boolean running()
        {
            return this == EXPORTING || this == CANCELLING;
        }
    }

    public record Status(
        Phase phase,
        String preset,
        long completedWork,
        long totalWork,
        long elapsedNanos,
        long estimatedRemainingNanos,
        @Nullable Path outputDirectory,
        String error
    )
    {
        public double percentage()
        {
            return this.totalWork <= 0L ? 0D : 100D * this.completedWork / this.totalWork;
        }
    }
}
