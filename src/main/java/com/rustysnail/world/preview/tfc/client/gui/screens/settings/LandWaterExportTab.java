package com.rustysnail.world.preview.tfc.client.gui.screens.settings;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportController;
import com.rustysnail.world.preview.tfc.backend.export.LandWaterExportPreset;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.screens.PreviewContainer;
import com.rustysnail.world.preview.tfc.client.gui.widgets.WGLabel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class LandWaterExportTab extends GridLayoutTab
{
    private static final int LINE_WIDTH = 360;
    private static final int HALF_WIDTH = (LINE_WIDTH - 4) / 2;

    private final PreviewContainer previewContainer;
    private final EditBox centerX;
    private final EditBox centerZ;
    private final Button export50k;
    private final Button export100k;
    private final Button exportBoth;
    private final Button cancel;
    private final WGLabel status;
    private final WGLabel timing;
    private final WGLabel output;
    @Nullable private String localError;

    public LandWaterExportTab(Minecraft minecraft, PreviewContainer previewContainer)
    {
        super(WorldPreviewComponents.LAND_WATER_EXPORT_TITLE);
        this.previewContainer = previewContainer;

        this.centerX = coordinateBox(minecraft, WorldPreviewComponents.LAND_WATER_EXPORT_CENTER_X);
        this.centerZ = coordinateBox(minecraft, WorldPreviewComponents.LAND_WATER_EXPORT_CENTER_Z);
        this.export50k = Button.builder(WorldPreviewComponents.LAND_WATER_EXPORT_50K,
            button -> start(List.of(LandWaterExportPreset.FIFTY_K))).width(HALF_WIDTH).build();
        this.export100k = Button.builder(WorldPreviewComponents.LAND_WATER_EXPORT_100K,
            button -> start(List.of(LandWaterExportPreset.HUNDRED_K))).width(HALF_WIDTH).build();
        this.exportBoth = Button.builder(WorldPreviewComponents.LAND_WATER_EXPORT_BOTH,
            button -> start(List.of(LandWaterExportPreset.FIFTY_K, LandWaterExportPreset.HUNDRED_K))).width(LINE_WIDTH).build();
        this.cancel = Button.builder(WorldPreviewComponents.LAND_WATER_EXPORT_CANCEL,
            button -> this.previewContainer.cancelLandWaterExport()).width(LINE_WIDTH).build();
        this.status = label(minecraft, WorldPreviewComponents.LAND_WATER_EXPORT_IDLE);
        this.timing = label(minecraft, Component.empty());
        this.output = label(minecraft, Component.empty());

        RowHelper rows = this.layout.rowSpacing(7).columnSpacing(4).createRowHelper(2);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, LINE_WIDTH, 20, WGLabel.TextAlignment.CENTER,
            WorldPreviewComponents.LAND_WATER_EXPORT_HEAD, 0xFFFFFF), 2);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, HALF_WIDTH, 12, WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.LAND_WATER_EXPORT_CENTER_X, 0xAAAAAA));
        rows.addChild(new WGLabel(minecraft.font, 0, 0, HALF_WIDTH, 12, WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.LAND_WATER_EXPORT_CENTER_Z, 0xAAAAAA));
        rows.addChild(this.centerX);
        rows.addChild(this.centerZ);
        rows.addChild(new WGLabel(minecraft.font, 0, 0, LINE_WIDTH, 24, WGLabel.TextAlignment.CENTER,
            WorldPreviewComponents.LAND_WATER_EXPORT_DESC, 0xAAAAAA), 2);
        rows.addChild(this.export50k);
        rows.addChild(this.export100k);
        rows.addChild(this.exportBoth, 2);
        rows.addChild(this.cancel, 2);
        rows.addChild(this.status, 2);
        rows.addChild(this.timing, 2);
        rows.addChild(this.output, 2);
        updateStatus();
    }

    public void tick()
    {
        updateStatus();
    }

    private void start(List<LandWaterExportPreset> presets)
    {
        try
        {
            int x = Integer.parseInt(this.centerX.getValue());
            int z = Integer.parseInt(this.centerZ.getValue());
            this.localError = this.previewContainer.startLandWaterExport(presets, x, z);
        }
        catch (NumberFormatException e)
        {
            this.localError = "Center X and Z must be whole block coordinates.";
        }
        updateStatus();
    }

    private void updateStatus()
    {
        LandWaterExportController.Status current = this.previewContainer.landWaterExportStatus();
        boolean running = current.phase().running();
        boolean available = this.previewContainer.workManager().isTFCEnabled() && !this.previewContainer.isUpdating();
        this.centerX.active = !running;
        this.centerZ.active = !running;
        this.export50k.active = available && !running;
        this.export100k.active = available && !running;
        this.exportBoth.active = available && !running;
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
            case IDLE -> this.status.setText(available
                ? WorldPreviewComponents.LAND_WATER_EXPORT_IDLE
                : WorldPreviewComponents.LAND_WATER_EXPORT_UNAVAILABLE);
            case EXPORTING -> this.status.setText(Component.translatable(
                "world_preview_tfc.export.land_water.progress", current.preset(), String.format("%.1f", current.percentage())));
            case CANCELLING -> this.status.setText(WorldPreviewComponents.LAND_WATER_EXPORT_CANCELLING);
            case COMPLETED -> this.status.setText(WorldPreviewComponents.LAND_WATER_EXPORT_COMPLETE);
            case CANCELLED -> this.status.setText(WorldPreviewComponents.LAND_WATER_EXPORT_CANCELLED);
            case FAILED -> this.status.setText(Component.translatable("world_preview_tfc.export.land_water.failed", current.error()));
        }

        if (current.phase() != LandWaterExportController.Phase.IDLE)
        {
            String elapsed = formatDuration(current.elapsedNanos());
            String eta = current.estimatedRemainingNanos() < 0L ? "--:--" : formatDuration(current.estimatedRemainingNanos());
            this.timing.setText(Component.translatable("world_preview_tfc.export.land_water.timing", elapsed, eta));
        }
        else
        {
            this.timing.setText(Component.empty());
        }

        Path outputDirectory = current.outputDirectory();
        Component outputText;
        if (outputDirectory == null)
        {
            outputText = Component.translatable("world_preview_tfc.export.land_water.output.not_set");
            this.output.setTooltip(null);
        }
        else
        {
            String outputPath = outputDirectory.toAbsolutePath().normalize().toString();
            outputText = Component.literal(outputPath);
            this.output.setTooltip(Tooltip.create(Component.literal(outputPath)));
        }
        this.output.setText(Component.translatable("world_preview_tfc.export.land_water.output", outputText));
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
