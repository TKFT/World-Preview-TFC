package com.rustysnail.world.preview.tfc.backend.export;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.export.MapExporter.Context;
import org.jetbrains.annotations.Nullable;

public final class MapExportController implements AutoCloseable
{
    private final MapExporter exporter;
    private final ExecutorService coordinator;
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final AtomicLong completedWork = new AtomicLong();
    private volatile long totalWork;
    private volatile long startedNanos;
    private volatile long finishedNanos;
    private volatile Phase phase = Phase.IDLE;
    private volatile String layer = "";
    private volatile String preset = "";
    private volatile String error = "";
    @Nullable private volatile Path outputDirectory;

    public MapExportController(int workerThreads)
    {
        this.exporter = new MapExporter(workerThreads);
        this.coordinator = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "world-preview-map-export");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized boolean start(
        @Nullable List<MapExportPlan> plans,
        @Nullable List<MapExportPreset> presets,
        Context context
    )
    {
        if (this.phase.running() || plans == null || plans.isEmpty() || presets == null || presets.isEmpty())
        {
            return false;
        }

        List<MapExportPlan> layerBatch = plans.stream()
            .sorted(Comparator.comparingInt(plan -> plan.layer().ordinal()))
            .toList();
        List<MapExportPreset> presetBatch = presets.stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .toList();
        this.cancelRequested.set(false);
        this.completedWork.set(0L);
        long presetWork = presetBatch.stream().mapToLong(value -> value.spec().samplingWork()).sum();
        this.totalWork = Math.multiplyExact(presetWork, layerBatch.size());
        this.startedNanos = System.nanoTime();
        this.finishedNanos = 0L;
        this.phase = Phase.EXPORTING;
        this.layer = layerBatch.size() == 1 ? layerBatch.getFirst().layer().id() : "batch";
        this.preset = presetBatch.size() == 1 ? presetBatch.getFirst().spec().id() : "batch";
        this.error = "";
        this.outputDirectory = context.outputDirectory();
        this.coordinator.submit(() -> runBatch(layerBatch, presetBatch, context));
        return true;
    }

    public synchronized void cancel()
    {
        if (this.phase.running())
        {
            this.cancelRequested.set(true);
            this.phase = Phase.CANCELLING;
        }
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
        return new Status(
            this.phase, this.layer, this.preset, completed, this.totalWork, elapsed, remaining,
            this.outputDirectory, this.error);
    }

    @Override
    public void close()
    {
        cancel();
        this.coordinator.shutdownNow();
    }

    private void runBatch(List<MapExportPlan> plans, List<MapExportPreset> presets, Context context)
    {
        try
        {
            for (MapExportPlan plan : plans)
            {
                this.layer = plan.layer().id();
                for (MapExportPreset value : presets)
                {
                    this.preset = value.spec().id();
                    this.phase = Phase.EXPORTING;
                    this.exporter.export(
                        value.spec(), plan, context, this.cancelRequested::get, this.completedWork::addAndGet);
                }
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
            WorldPreview.LOGGER.error("Map export failed", e);
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
        String layer,
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
