package com.rustysnail.world.preview.tfc.client.gui.screens.settings;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import com.rustysnail.world.preview.tfc.backend.export.MapExportController;
import com.rustysnail.world.preview.tfc.backend.export.MapExportLayer;
import com.rustysnail.world.preview.tfc.backend.export.MapExportPreset;
import com.rustysnail.world.preview.tfc.client.gui.screens.PreviewContainer;
import com.rustysnail.world.preview.tfc.client.gui.widgets.WGLabel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MapExportTab extends GridLayoutTab
{
    private static final int LINE_WIDTH = 360;
    private static final int HALF_WIDTH = (LINE_WIDTH - 4) / 2;
    private static final int THIRD_WIDTH = (LINE_WIDTH - 8) / 3;

    private final PreviewContainer previewContainer;
    private final EnumMap<MapExportLayer, Checkbox> layerBoxes = new EnumMap<>(MapExportLayer.class);
    private final EditBox centerX;
    private final EditBox centerZ;
    private final Button export50k;
    private final Button export100k;
    private final Button export200k;
    private final Button exportAllSizes;
    private final Button cancel;
    private final WGLabel status;
    private final WGLabel timing;
    private final WGLabel output;
    @Nullable private String localError;

    public MapExportTab(Minecraft minecraft, PreviewContainer previewContainer)
    {
        super(Component.translatable("world_preview_tfc.export.map.title"));
        this.previewContainer = previewContainer;
        this.centerX = coordinateBox(minecraft, Component.translatable("world_preview_tfc.export.map.center_x"));
        this.centerZ = coordinateBox(minecraft, Component.translatable("world_preview_tfc.export.map.center_z"));

        for (MapExportLayer layer : MapExportLayer.values())
        {
            Checkbox checkbox = Checkbox.builder(Component.literal(layer.displayName()), minecraft.font)
                .selected(true)
                .build();
            this.layerBoxes.put(layer, checkbox);
        }

        this.export50k = Button.builder(Component.translatable("world_preview_tfc.export.map.50k"),
            button -> start(List.of(MapExportPreset.FIFTY_K))).width(THIRD_WIDTH).build();
        this.export100k = Button.builder(Component.translatable("world_preview_tfc.export.map.100k"),
            button -> start(List.of(MapExportPreset.HUNDRED_K))).width(THIRD_WIDTH).build();
        this.export200k = Button.builder(Component.translatable("world_preview_tfc.export.map.200k"),
            button -> start(List.of(MapExportPreset.TWO_HUNDRED_K))).width(THIRD_WIDTH).build();
        this.exportAllSizes = Button.builder(Component.translatable("world_preview_tfc.export.map.all_sizes"),
            button -> start(List.of(
                MapExportPreset.FIFTY_K,
                MapExportPreset.HUNDRED_K,
                MapExportPreset.TWO_HUNDRED_K
            ))).width(LINE_WIDTH).build();
        this.cancel = Button.builder(Component.translatable("world_preview_tfc.export.map.cancel"),
            button -> this.previewContainer.cancelMapExport()).width(LINE_WIDTH).build();
        this.status = label(minecraft, Component.translatable("world_preview_tfc.export.map.idle"));
        this.timing = label(minecraft, Component.empty());
        this.output = label(minecraft, Component.empty());

        RowHelper rows = this.layout.rowSpacing(4).columnSpacing(4).createRowHelper(6);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, LINE_WIDTH, 18, WGLabel.TextAlignment.CENTER,
            Component.translatable("world_preview_tfc.export.map.head"), 0xFFFFFF), 6);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, HALF_WIDTH, 12, WGLabel.TextAlignment.LEFT,
            Component.translatable("world_preview_tfc.export.map.center_x"), 0xAAAAAA), 3);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, HALF_WIDTH, 12, WGLabel.TextAlignment.LEFT,
            Component.translatable("world_preview_tfc.export.map.center_z"), 0xAAAAAA), 3);
        rows.addChild(this.centerX, 3);
        rows.addChild(this.centerZ, 3);
        for (Checkbox checkbox : this.layerBoxes.values())
        {
            rows.addChild(checkbox, 3);
        }
        rows.addChild(this.export50k, 2);
        rows.addChild(this.export100k, 2);
        rows.addChild(this.export200k, 2);
        rows.addChild(this.exportAllSizes, 6);
        rows.addChild(this.cancel, 6);
        rows.addChild(this.status, 6);
        rows.addChild(this.timing, 6);
        rows.addChild(this.output, 6);
        updateStatus();
    }

    public void tick()
    {
        updateStatus();
    }

    private void start(List<MapExportPreset> presets)
    {
        List<MapExportLayer> selected = new ArrayList<>();
        this.layerBoxes.forEach((layer, box) -> {
            if (box.selected())
            {
                selected.add(layer);
            }
        });
        if (selected.isEmpty())
        {
            this.localError = "Select at least one map layer.";
            updateStatus();
            return;
        }

        try
        {
            int x = Integer.parseInt(this.centerX.getValue());
            int z = Integer.parseInt(this.centerZ.getValue());
            this.localError = this.previewContainer.startMapExport(selected, presets, x, z);
        }
        catch (NumberFormatException e)
        {
            this.localError = "Center X and Z must be whole block coordinates.";
        }
        updateStatus();
    }

    private void updateStatus()
    {
        MapExportController.Status current = this.previewContainer.mapExportStatus();
        boolean running = current.phase().running();
        boolean available = this.previewContainer.workManager().isTFCEnabled() && !this.previewContainer.isUpdating();
        this.centerX.active = !running;
        this.centerZ.active = !running;
        this.layerBoxes.values().forEach(box -> box.active = !running);
        this.export50k.active = available && !running;
        this.export100k.active = available && !running;
        this.export200k.active = available && !running;
        this.exportAllSizes.active = available && !running;
        this.cancel.active = running;

        if (this.localError != null && !this.localError.isBlank())
        {
            this.status.setText(Component.literal(this.localError));
            this.timing.setText(Component.empty());
            this.output.setText(Component.empty());
            return;
        }

        switch (current.phase())
        {
            case IDLE -> this.status.setText(Component.translatable(available
                ? "world_preview_tfc.export.map.idle"
                : "world_preview_tfc.export.map.unavailable"));
            case EXPORTING -> this.status.setText(Component.translatable(
                "world_preview_tfc.export.map.progress",
                current.layer(), current.preset(), String.format("%.1f", current.percentage())));
            case CANCELLING -> this.status.setText(Component.translatable("world_preview_tfc.export.map.cancelling"));
            case COMPLETED -> this.status.setText(Component.translatable("world_preview_tfc.export.map.complete"));
            case CANCELLED -> this.status.setText(Component.translatable("world_preview_tfc.export.map.cancelled"));
            case FAILED -> this.status.setText(Component.translatable(
                "world_preview_tfc.export.map.failed", current.error()));
        }

        if (current.phase() != MapExportController.Phase.IDLE)
        {
            String elapsed = formatDuration(current.elapsedNanos());
            String eta = current.estimatedRemainingNanos() < 0L ? "--:--"
                : formatDuration(current.estimatedRemainingNanos());
            this.timing.setText(Component.translatable("world_preview_tfc.export.map.timing", elapsed, eta));
        }
        else
        {
            this.timing.setText(Component.empty());
        }

        Path outputDirectory = current.outputDirectory();
        Component outputText;
        if (outputDirectory == null)
        {
            outputText = Component.translatable("world_preview_tfc.export.map.output.not_set");
            this.output.setTooltip(null);
        }
        else
        {
            String outputPath = outputDirectory.toAbsolutePath().normalize().toString();
            outputText = Component.literal(outputPath);
            this.output.setTooltip(Tooltip.create(Component.literal(outputPath)));
        }
        this.output.setText(Component.translatable("world_preview_tfc.export.map.output", outputText));
    }

    private static EditBox coordinateBox(Minecraft minecraft, Component label)
    {
        EditBox box = new EditBox(minecraft.font, 0, 0, HALF_WIDTH, 20, label);
        box.setValue("0");
        box.setMaxLength(11);
        box.setFilter(value -> value.isEmpty() || value.equals("-") || value.matches("-?\\d+"));
        return box;
    }

    private static WGLabel label(Minecraft minecraft, Component text)
    {
        return new WGLabel(minecraft.font, 0, 0, LINE_WIDTH, 16, WGLabel.TextAlignment.CENTER, text, 0xFFFFFF);
    }

    private static String formatDuration(long nanos)
    {
        long seconds = Math.max(0L, Duration.ofNanos(nanos).toSeconds());
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainingSeconds = seconds % 60L;
        return hours > 0L
            ? String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
            : String.format("%02d:%02d", minutes, remainingSeconds);
    }
}
