package com.rustysnail.world.preview.tfc.client.gui.screens.settings;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.screens.PreviewContainer;
import com.rustysnail.world.preview.tfc.client.gui.widgets.SelectionSlider;
import com.rustysnail.world.preview.tfc.client.gui.widgets.WGLabel;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.Settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab for TerraFirmaCraft world generation options.
 */
public class TFCTab extends GridLayoutTab
{

    private static final int DISTANCE_STEP = 100;           // 0.1 km step size
    private static final int SPAWN_RADIUS_MIN = 100;        // 0.1 km minimum
    private static final int SPAWN_RADIUS_MAX = 20000;      // 20.0 km maximum
    private static final int CENTER_MIN = -20000;           // -20.0 km
    private static final int CENTER_MAX = 20000;            // +20.0 km
    private static final int SCALE_MAX_STEPS = 400;         // 0 to 40.0 km in 0.1 km steps
    private static final int PERCENT_STEPS = 100;           // -100% to +100%
    private static final int CONSTANT_STEPS = 10;           // -1.0 to +1.0 in 0.1 steps

    private final PreviewContainer previewContainer;
    private final ChunkGeneratorExtension tfcExtension;
    private final Runnable onApplyClose;
    private final boolean readOnly;

    private int temperatureScale;
    private float temperatureConstant;
    private int rainfallScale;
    private float rainfallConstant;
    private float continentalness;
    private boolean flatBedrock;
    private boolean finiteContinents;
    private int spawnDistance;
    private int spawnCenterX;
    private int spawnCenterZ;
    private float grassDensity;

    private final SelectionSlider<ScaleValue> spawnRadiusSlider;
    private final SelectionSlider<ScaleValue> spawnCenterXSlider;
    private final SelectionSlider<ScaleValue> spawnCenterZSlider;
    private final SelectionSlider<ScaleValue> tempScaleSlider;
    private final SelectionSlider<ConstantValue> tempConstSlider;
    private final SelectionSlider<ScaleValue> rainScaleSlider;
    private final SelectionSlider<ConstantValue> rainConstSlider;
    private final SelectionSlider<PercentValue> continentalnessSlider;
    private final SelectionSlider<PercentValue> grassDensitySlider;
    private final Checkbox cbFlatBedrock;
    private final Checkbox cbFiniteContinents;
    private final Button applyButton;

    private final WGLabel headerLabel;
    private final WGLabel labelFlatBedrock;
    private final WGLabel labelSpawnDistance;
    private final WGLabel labelSpawnCenterX;
    private final WGLabel labelSpawnCenterZ;
    private final WGLabel labelTempScale;
    private final WGLabel labelRainScale;
    private final WGLabel labelTempConst;
    private final WGLabel labelRainConst;
    private final WGLabel labelContinentalness;
    private final WGLabel labelGrassDensity;
    private final WGLabel labelFiniteContinents;
    private final WGLabel labelSpacer;

    private final RockLayerSettings rockLayerSettings;

    public TFCTab(Minecraft minecraft, PreviewContainer previewContainer, ChunkGeneratorExtension tfcExtension, Runnable onApplyClose, boolean readOnly)
    {
        super(WorldPreviewComponents.TFC_SETTINGS_TITLE);
        this.previewContainer = previewContainer;
        this.tfcExtension = tfcExtension;
        this.onApplyClose = onApplyClose;
        this.readOnly = readOnly;

        Settings settings = tfcExtension.settings();
        this.temperatureScale = settings.temperatureScale();
        this.temperatureConstant = settings.temperatureConstant();
        this.rainfallScale = settings.rainfallScale();
        this.rainfallConstant = settings.rainfallConstant();
        this.continentalness = settings.continentalness();
        this.flatBedrock = settings.flatBedrock();
        this.finiteContinents = settings.finiteContinents();
        this.spawnDistance = settings.spawnDistance();
        this.spawnCenterX = settings.spawnCenterX();
        this.spawnCenterZ = settings.spawnCenterZ();
        this.grassDensity = settings.grassDensity();
        this.rockLayerSettings = settings.rockLayerSettings();

        int LINE_WIDTH = 320;
        int HALF_WIDTH = (LINE_WIDTH - 4) / 2;

        List<ScaleValue> spawnRadiusValues = new ArrayList<>();
        for (int v = SPAWN_RADIUS_MIN; v <= SPAWN_RADIUS_MAX; v += DISTANCE_STEP)
        {
            spawnRadiusValues.add(new ScaleValue(v));
        }

        this.spawnDistance = Math.max(SPAWN_RADIUS_MIN, Math.min(SPAWN_RADIUS_MAX, this.spawnDistance));
        int spawnRadiusIdx = Math.round((this.spawnDistance - SPAWN_RADIUS_MIN) / (float) DISTANCE_STEP);
        spawnRadiusIdx = Math.max(0, Math.min(spawnRadiusValues.size() - 1, spawnRadiusIdx));

        this.spawnRadiusSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, spawnRadiusValues,
            spawnRadiusValues.get(spawnRadiusIdx),
            val -> this.spawnDistance = val.value
        );
        this.spawnRadiusSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_SPAWN_DISTANCE_TOOLTIP));


        List<ScaleValue> spawnCenterValues = new ArrayList<>();
        for (int v = CENTER_MIN; v <= CENTER_MAX; v += DISTANCE_STEP)
        {
            spawnCenterValues.add(new ScaleValue(v));
        }

        // X
        this.spawnCenterX = Math.max(CENTER_MIN, Math.min(CENTER_MAX, this.spawnCenterX));
        int centerXIdx = Math.round((this.spawnCenterX - CENTER_MIN) / (float) DISTANCE_STEP);
        centerXIdx = Math.max(0, Math.min(spawnCenterValues.size() - 1, centerXIdx));

        this.spawnCenterXSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, spawnCenterValues,
            spawnCenterValues.get(centerXIdx),
            val -> this.spawnCenterX = val.value
        );
        this.spawnCenterXSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_SPAWN_CENTER_X_TOOLTIP));

        // Z
        this.spawnCenterZ = Math.max(CENTER_MIN, Math.min(CENTER_MAX, this.spawnCenterZ));
        int centerZIdx = Math.round((this.spawnCenterZ - CENTER_MIN) / (float) DISTANCE_STEP);
        centerZIdx = Math.max(0, Math.min(spawnCenterValues.size() - 1, centerZIdx));

        this.spawnCenterZSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, spawnCenterValues,
            spawnCenterValues.get(centerZIdx),
            val -> this.spawnCenterZ = val.value
        );
        this.spawnCenterZSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_SPAWN_CENTER_Z_TOOLTIP));


        List<ScaleValue> tempScaleValues = new ArrayList<>();
        for (int i = 0; i <= SCALE_MAX_STEPS; i++)
        {
            tempScaleValues.add(new ScaleValue(i * DISTANCE_STEP));
        }

        int tempScaleIdx = Math.round(this.temperatureScale / (float) DISTANCE_STEP);
        tempScaleIdx = Math.max(0, Math.min(SCALE_MAX_STEPS, tempScaleIdx));

        this.tempScaleSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, tempScaleValues,
            tempScaleValues.get(tempScaleIdx),
            val -> this.temperatureScale = val.value
        );
        this.tempScaleSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_TEMP_SCALE_TOOLTIP));

        List<ConstantValue> tempConstValues = new ArrayList<>();
        for (int i = -CONSTANT_STEPS; i <= CONSTANT_STEPS; i++)
        {
            tempConstValues.add(new ConstantValue(i / (float) CONSTANT_STEPS));
        }
        int tempConstIdx = Math.round((this.temperatureConstant + 1.0f) * CONSTANT_STEPS);
        this.tempConstSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, tempConstValues,
            tempConstValues.get(Math.max(0, Math.min(2 * CONSTANT_STEPS, tempConstIdx))),
            val -> this.temperatureConstant = val.value
        );
        this.tempConstSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_TEMP_CONST_TOOLTIP));

        List<ScaleValue> rainScaleValues = new ArrayList<>();
        for (int i = 0; i <= SCALE_MAX_STEPS; i++)
        {
            rainScaleValues.add(new ScaleValue(i * DISTANCE_STEP));
        }

        int rainScaleIdx = Math.round(this.rainfallScale / (float) DISTANCE_STEP);
        rainScaleIdx = Math.max(0, Math.min(SCALE_MAX_STEPS, rainScaleIdx));

        this.rainScaleSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, rainScaleValues,
            rainScaleValues.get(rainScaleIdx),
            val -> this.rainfallScale = val.value
        );
        this.rainScaleSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_RAIN_SCALE_TOOLTIP));

        List<ConstantValue> rainConstValues = new ArrayList<>();
        for (int i = -CONSTANT_STEPS; i <= CONSTANT_STEPS; i++)
        {
            rainConstValues.add(new ConstantValue(i / (float) CONSTANT_STEPS));
        }
        int rainConstIdx = Math.round((this.rainfallConstant + 1.0f) * CONSTANT_STEPS);
        this.rainConstSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15, rainConstValues,
            rainConstValues.get(Math.max(0, Math.min(2 * CONSTANT_STEPS, rainConstIdx))),
            val -> this.rainfallConstant = val.value
        );
        this.rainConstSlider.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_RAIN_CONST_TOOLTIP));

        List<PercentValue> continentalnessValues = new ArrayList<>();
        for (int i = -PERCENT_STEPS; i <= PERCENT_STEPS; i++)
        {
            continentalnessValues.add(new PercentValue(i / (float) PERCENT_STEPS));
        }

        int contIdx = Math.round((this.continentalness + 1.0f) * PERCENT_STEPS);

        this.continentalnessSlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15,
            continentalnessValues,
            continentalnessValues.get(Math.max(0, Math.min(2 * PERCENT_STEPS, contIdx))),
            val -> this.continentalness = val.value
        );

        this.continentalnessSlider.setTooltip(
            Tooltip.create(WorldPreviewComponents.TFC_CONTINENTALNESS_TOOLTIP)
        );

        List<PercentValue> grassDensityValues = new ArrayList<>();
        for (int i = -PERCENT_STEPS; i <= PERCENT_STEPS; i++)
        {
            grassDensityValues.add(new PercentValue(i / (float) PERCENT_STEPS));
        }

        int grassIdx = Math.round((this.grassDensity + 1.0f) * PERCENT_STEPS);

        this.grassDensitySlider = new SelectionSlider<>(
            0, 0, HALF_WIDTH, 15,
            grassDensityValues,
            grassDensityValues.get(Math.max(0, Math.min(2 * PERCENT_STEPS, grassIdx))),
            val -> this.grassDensity = val.value
        );

        this.grassDensitySlider.setTooltip(
            Tooltip.create(WorldPreviewComponents.TFC_GRASS_DENSITY_TOOLTIP)
        );

        this.cbFlatBedrock = Checkbox.builder(
                WorldPreviewComponents.TFC_FLAT_BEDROCK, minecraft.font)
            .selected(this.flatBedrock)
            .onValueChange((box, val) -> this.flatBedrock = val)
            .build();
        this.cbFlatBedrock.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_FLAT_BEDROCK_TOOLTIP));

        this.cbFiniteContinents = Checkbox.builder(
                WorldPreviewComponents.TFC_FINITE_CONTINENTS, minecraft.font)
            .selected(this.finiteContinents)
            .onValueChange((box, val) -> this.finiteContinents = val)
            .build();
        this.cbFiniteContinents.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_FINITE_CONTINENTS_TOOLTIP));

        this.applyButton = Button.builder(
            WorldPreviewComponents.TFC_APPLY,
            btn -> this.applySettings()
        ).width(LINE_WIDTH).build();
        this.applyButton.setTooltip(Tooltip.create(WorldPreviewComponents.TFC_APPLY_TOOLTIP));

        int HALF = (LINE_WIDTH - 4) / 2;

        RowHelper rowHelper = this.layout
            .rowSpacing(6)
            .columnSpacing(4)
            .createRowHelper(2);

        this.headerLabel = new WGLabel(
            minecraft.font, 0, 0, LINE_WIDTH, 10,
            WGLabel.TextAlignment.CENTER,
            WorldPreviewComponents.TFC_SETTINGS_HEAD,
            0xFFFFFF
        );
        rowHelper.addChild(this.headerLabel, 2);

        this.labelFlatBedrock = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_FLAT_BEDROCK,
            0xAAAAAA
        );
        this.labelSpawnDistance = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_SPAWN_DISTANCE,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelFlatBedrock);
        rowHelper.addChild(this.labelSpawnDistance);

        rowHelper.addChild(this.cbFlatBedrock);
        rowHelper.addChild(this.spawnRadiusSlider);

        this.labelSpawnCenterX = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_SPAWN_CENTER_X,
            0xAAAAAA
        );
        this.labelSpawnCenterZ = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_SPAWN_CENTER_Z,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelSpawnCenterX);
        rowHelper.addChild(this.labelSpawnCenterZ);

        rowHelper.addChild(this.spawnCenterXSlider);
        rowHelper.addChild(this.spawnCenterZSlider);

        this.labelTempScale = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_TEMP_SCALE,
            0xAAAAAA
        );
        this.labelRainScale = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_RAIN_SCALE,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelTempScale);
        rowHelper.addChild(this.labelRainScale);

        rowHelper.addChild(this.tempScaleSlider);
        rowHelper.addChild(this.rainScaleSlider);

        this.labelTempConst = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_TEMP_CONST,
            0xAAAAAA
        );
        this.labelRainConst = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_RAIN_CONST,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelTempConst);
        rowHelper.addChild(this.labelRainConst);

        rowHelper.addChild(this.tempConstSlider);
        rowHelper.addChild(this.rainConstSlider);

        this.labelContinentalness = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_CONTINENTALNESS,
            0xAAAAAA
        );
        this.labelGrassDensity = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_GRASS_DENSITY,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelContinentalness);
        rowHelper.addChild(this.labelGrassDensity);

        rowHelper.addChild(this.continentalnessSlider);
        rowHelper.addChild(this.grassDensitySlider);

        this.labelFiniteContinents = new WGLabel(
            minecraft.font, 0, 0, HALF_WIDTH, 5,
            WGLabel.TextAlignment.LEFT,
            WorldPreviewComponents.TFC_FINITE_CONTINENTS,
            0xAAAAAA
        );
        rowHelper.addChild(this.labelFiniteContinents);

        rowHelper.addChild(this.cbFiniteContinents);
        this.labelSpacer = new WGLabel(
            minecraft.font, 0, 0, HALF, 5,
            WGLabel.TextAlignment.LEFT,
            Component.literal(""),
            0
        );
        rowHelper.addChild(this.labelSpacer);

        rowHelper.addChild(this.applyButton, 2);

        if (readOnly)
        {
            this.spawnRadiusSlider.active = false;
            this.spawnCenterXSlider.active = false;
            this.spawnCenterZSlider.active = false;
            this.tempScaleSlider.active = false;
            this.tempConstSlider.active = false;
            this.rainScaleSlider.active = false;
            this.rainConstSlider.active = false;
            this.continentalnessSlider.active = false;
            this.grassDensitySlider.active = false;
            this.cbFlatBedrock.active = false;
            this.cbFiniteContinents.active = false;
            this.applyButton.active = false;
            this.applyButton.visible = false;
        }

    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle)
    {
        final int OUTER_PAD = 16;
        final int MAX_WIDTH = 360;

        int lineWidth = Math.max(220, Math.min(MAX_WIDTH, screenRectangle.width() - OUTER_PAD));
        int halfWidth = Math.max(100, (lineWidth - 4) / 2);

        this.layout.rowSpacing(screenRectangle.height() < 260 ? 3 : 6);

        this.headerLabel.setWidth(lineWidth);
        this.labelFlatBedrock.setWidth(halfWidth);
        this.labelSpawnDistance.setWidth(halfWidth);
        this.labelSpawnCenterX.setWidth(halfWidth);
        this.labelSpawnCenterZ.setWidth(halfWidth);
        this.labelTempScale.setWidth(halfWidth);
        this.labelRainScale.setWidth(halfWidth);
        this.labelTempConst.setWidth(halfWidth);
        this.labelRainConst.setWidth(halfWidth);
        this.labelContinentalness.setWidth(halfWidth);
        this.labelGrassDensity.setWidth(halfWidth);
        this.labelFiniteContinents.setWidth(halfWidth);
        this.labelSpacer.setWidth(halfWidth);

        this.spawnRadiusSlider.setWidth(halfWidth);
        this.spawnCenterXSlider.setWidth(halfWidth);
        this.spawnCenterZSlider.setWidth(halfWidth);
        this.tempScaleSlider.setWidth(halfWidth);
        this.rainScaleSlider.setWidth(halfWidth);
        this.tempConstSlider.setWidth(halfWidth);
        this.rainConstSlider.setWidth(halfWidth);
        this.continentalnessSlider.setWidth(halfWidth);
        this.grassDensitySlider.setWidth(halfWidth);
        this.cbFlatBedrock.setWidth(halfWidth);
        this.cbFiniteContinents.setWidth(halfWidth);
        this.applyButton.setWidth(lineWidth);

        this.layout.arrangeElements();
        FrameLayout.alignInRectangle(this.layout, screenRectangle, 0.5F, 0.0F);
    }

    private void applySettings()
    {
        if (this.readOnly) return;

        Settings newSettings = new Settings(
            this.flatBedrock,
            this.spawnDistance,
            this.spawnCenterX,
            this.spawnCenterZ,
            this.temperatureScale,
            this.temperatureConstant,
            this.rainfallScale,
            this.rainfallConstant,
            this.rockLayerSettings,
            this.continentalness,
            this.grassDensity,
            this.finiteContinents
        );

        try
        {
            this.tfcExtension.applySettings(old -> newSettings);
        }
        catch (Throwable t)
        {
            WorldPreview.LOGGER.warn("Failed to sync TFC settings to vanilla world creation context", t);
        }

        this.previewContainer.workManager().setTFCSettingsOverride(newSettings);

        this.previewContainer.workManager().onTFCSettingsChanged();

        if (this.onApplyClose != null)
        {
            this.onApplyClose.run();
        }
    }

    public record ScaleValue(int value) implements SelectionSlider.SelectionValues
    {

        @Override
        public Component message()
        {
            if (value == 0) return Component.literal("0.0 km");
            return Component.literal(String.format("%+.1f km", value / 1000.0));
        }
    }

    public record ConstantValue(float value) implements SelectionSlider.SelectionValues
    {

        @Override
        public Component message()
        {
            return Component.literal(String.format("%.1f", value));
        }
    }

    public record PercentValue(float value) implements SelectionSlider.SelectionValues
    {

        @Override
        public Component message()
        {
            return Component.literal(String.format("%.0f%%", value * 100));
        }
    }
}
