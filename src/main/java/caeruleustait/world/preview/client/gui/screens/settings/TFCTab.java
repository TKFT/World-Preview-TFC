package caeruleustait.world.preview.client.gui.screens.settings;

import caeruleustait.world.preview.client.gui.screens.PreviewContainer;
import caeruleustait.world.preview.client.gui.widgets.SelectionSlider;
import caeruleustait.world.preview.client.gui.widgets.WGLabel;
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
public class TFCTab extends GridLayoutTab {

    // TFC Configuration constants (distances in blocks, 100 = 0.1 km)
    private static final int DISTANCE_STEP = 100;           // 0.1 km step size
    private static final int SPAWN_RADIUS_MIN = 100;        // 0.1 km minimum
    private static final int SPAWN_RADIUS_MAX = 20000;      // 20.0 km maximum
    private static final int CENTER_MIN = -20000;           // -20.0 km
    private static final int CENTER_MAX = 20000;            // +20.0 km
    private static final int SCALE_MAX_STEPS = 400;         // 0 to 40.0 km in 0.1 km steps
    private static final int PERCENT_STEPS = 100;           // -100% to +100%
    private static final int CONSTANT_STEPS = 10;           // -1.0 to +1.0 in 0.1 steps

    private final PreviewContainer previewContainer;
    private final Runnable onApplyClose;

    // Current settings values
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

    // Widgets kept as fields so doLayout() can resize/reflow them when the window/gui scale changes.
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

    // Labels that need width adjustments on resize
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

    //Not Editable
    private final RockLayerSettings rockLayerSettings;

    public TFCTab(Minecraft minecraft, PreviewContainer previewContainer, ChunkGeneratorExtension tfcExtension, Runnable onApplyClose) {
        super(Component.translatable("world_preview.tfc.settings.title"));
        this.previewContainer = previewContainer;
        this.onApplyClose = onApplyClose;

        // Load current settings
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

        // Log initial TFC settings (info level for visibility)
        caeruleustait.world.preview.WorldPreview.LOGGER.info(
            "[TFCTab] Loading initial settings: spawnDistance={}, spawnCenterX={}, spawnCenterZ={}",
            this.spawnDistance, this.spawnCenterX, this.spawnCenterZ
        );

        int LINE_WIDTH = 320;
        int HALF_WIDTH = (LINE_WIDTH - 4) / 2;

        // Spawn Radius slider (0.1-20.0km in 0.1km steps)
        List<ScaleValue> spawnRadiusValues = new ArrayList<>();
        for (int v = SPAWN_RADIUS_MIN; v <= SPAWN_RADIUS_MAX; v += DISTANCE_STEP) {
            spawnRadiusValues.add(new ScaleValue(v, "world_preview.tfc.settings.spawn_distance"));
        }

        // Clamp existing value into range, then compute index
        this.spawnDistance = Math.max(SPAWN_RADIUS_MIN, Math.min(SPAWN_RADIUS_MAX, this.spawnDistance));
        int spawnRadiusIdx = Math.round((this.spawnDistance - SPAWN_RADIUS_MIN) / (float) DISTANCE_STEP);
        spawnRadiusIdx = Math.max(0, Math.min(spawnRadiusValues.size() - 1, spawnRadiusIdx));

        this.spawnRadiusSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, spawnRadiusValues,
                spawnRadiusValues.get(spawnRadiusIdx),
                val -> this.spawnDistance = val.value
        );
        this.spawnRadiusSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.spawn_distance.tooltip")));


        // Spawn Center sliders (-20.0 to +20.0km in 0.1km steps)
        List<ScaleValue> spawnCenterValues = new ArrayList<>();
        for (int v = CENTER_MIN; v <= CENTER_MAX; v += DISTANCE_STEP) {
            spawnCenterValues.add(new ScaleValue(v, "world_preview.tfc.settings.spawn_center"));
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
        this.spawnCenterXSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.spawn_center_x.tooltip")));

        // Z
        this.spawnCenterZ = Math.max(CENTER_MIN, Math.min(CENTER_MAX, this.spawnCenterZ));
        int centerZIdx = Math.round((this.spawnCenterZ - CENTER_MIN) / (float) DISTANCE_STEP);
        centerZIdx = Math.max(0, Math.min(spawnCenterValues.size() - 1, centerZIdx));

        this.spawnCenterZSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, spawnCenterValues,
                spawnCenterValues.get(centerZIdx),
                val -> this.spawnCenterZ = val.value
        );
        this.spawnCenterZSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.spawn_center_z.tooltip")));



        // Temperature Scale slider (0.0-40.0km in 0.1km steps)
        List<ScaleValue> tempScaleValues = new ArrayList<>();
        for (int i = 0; i <= SCALE_MAX_STEPS; i++) {
            tempScaleValues.add(new ScaleValue(i * DISTANCE_STEP, "world_preview.tfc.settings.temp_scale"));
        }

        // pick the closest index based on current value (in "meters"/blocks)
        int tempScaleIdx = Math.round(this.temperatureScale / (float) DISTANCE_STEP);
        tempScaleIdx = Math.max(0, Math.min(SCALE_MAX_STEPS, tempScaleIdx));

        this.tempScaleSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, tempScaleValues,
                tempScaleValues.get(tempScaleIdx),
                val -> this.temperatureScale = val.value
        );
        this.tempScaleSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.temp_scale.tooltip")));

        // Temperature Constant slider (-1.0 to 1.0 in 0.1 steps)
        List<ConstantValue> tempConstValues = new ArrayList<>();
        for (int i = -CONSTANT_STEPS; i <= CONSTANT_STEPS; i++) {
            tempConstValues.add(new ConstantValue(i / (float) CONSTANT_STEPS, "world_preview.tfc.settings.temp_const"));
        }
        int tempConstIdx = Math.round((this.temperatureConstant + 1.0f) * CONSTANT_STEPS);
        this.tempConstSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, tempConstValues,
                tempConstValues.get(Math.max(0, Math.min(2 * CONSTANT_STEPS, tempConstIdx))),
                val -> this.temperatureConstant = val.value
        );
        this.tempConstSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.temp_const.tooltip")));

        // Rainfall Scale slider (0.0-40.0km in 0.1km steps)
        List<ScaleValue> rainScaleValues = new ArrayList<>();
        for (int i = 0; i <= SCALE_MAX_STEPS; i++) {
            rainScaleValues.add(new ScaleValue(i * DISTANCE_STEP, "world_preview.tfc.settings.rain_scale"));
        }

        int rainScaleIdx = Math.round(this.rainfallScale / (float) DISTANCE_STEP);
        rainScaleIdx = Math.max(0, Math.min(SCALE_MAX_STEPS, rainScaleIdx));

        this.rainScaleSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, rainScaleValues,
                rainScaleValues.get(rainScaleIdx),
                val -> this.rainfallScale = val.value
        );
        this.rainScaleSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.rain_scale.tooltip")));


        // Rainfall Constant slider (-1.0 to 1.0 in 0.1 steps)
        List<ConstantValue> rainConstValues = new ArrayList<>();
        for (int i = -CONSTANT_STEPS; i <= CONSTANT_STEPS; i++) {
            rainConstValues.add(new ConstantValue(i / (float) CONSTANT_STEPS, "world_preview.tfc.settings.rain_const"));
        }
        int rainConstIdx = Math.round((this.rainfallConstant + 1.0f) * CONSTANT_STEPS);
        this.rainConstSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15, rainConstValues,
                rainConstValues.get(Math.max(0, Math.min(2 * CONSTANT_STEPS, rainConstIdx))),
                val -> this.rainfallConstant = val.value
        );
        this.rainConstSlider.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.rain_const.tooltip")));

        // Continentalness slider (-100% to 100% in 1% steps)
        List<PercentValue> continentalnessValues = new ArrayList<>();
        for (int i = -PERCENT_STEPS; i <= PERCENT_STEPS; i++) {
            continentalnessValues.add(new PercentValue(i / (float) PERCENT_STEPS, "world_preview.tfc.settings.continentalness"));
        }

        // map current value (-1..1) -> index (0..200), default 0% -> index 100
        int contIdx = Math.round((this.continentalness + 1.0f) * PERCENT_STEPS);

        this.continentalnessSlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15,
                continentalnessValues,
                continentalnessValues.get(Math.max(0, Math.min(2 * PERCENT_STEPS, contIdx))),
                val -> this.continentalness = val.value
        );

        this.continentalnessSlider.setTooltip(
                Tooltip.create(Component.translatable("world_preview.tfc.settings.continentalness.tooltip"))
        );

        // Grass Density slider (-100% to 100% in 1% steps)
        List<PercentValue> grassDensityValues = new ArrayList<>();
        for (int i = -PERCENT_STEPS; i <= PERCENT_STEPS; i++) {
            grassDensityValues.add(new PercentValue(i / (float) PERCENT_STEPS, "world_preview.tfc.settings.grass_density"));
        }

        int grassIdx = Math.round((this.grassDensity + 1.0f) * PERCENT_STEPS);

        this.grassDensitySlider = new SelectionSlider<>(
                0, 0, HALF_WIDTH, 15,
                grassDensityValues,
                grassDensityValues.get(Math.max(0, Math.min(2 * PERCENT_STEPS, grassIdx))),
                val -> this.grassDensity = val.value
        );

        this.grassDensitySlider.setTooltip(
                Tooltip.create(Component.translatable("world_preview.tfc.settings.grass_density.tooltip"))
        );


        // Flat Bedrock checkbox
        this.cbFlatBedrock = Checkbox.builder(
                        Component.translatable("world_preview.tfc.settings.flat_bedrock"), minecraft.font)
                .selected(this.flatBedrock)
                .onValueChange((box, val) -> this.flatBedrock = val)
                .build();
        this.cbFlatBedrock.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.flat_bedrock.tooltip")));

        // Finite Continents checkbox
        this.cbFiniteContinents = Checkbox.builder(
                        Component.translatable("world_preview.tfc.settings.finite_continents"), minecraft.font)
                .selected(this.finiteContinents)
                .onValueChange((box, val) -> this.finiteContinents = val)
                .build();
        this.cbFiniteContinents.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.finite_continents.tooltip")));

        // Apply button
        this.applyButton = Button.builder(
                Component.translatable("world_preview.tfc.settings.apply"),
                btn -> this.applySettings()
        ).width(LINE_WIDTH).build();
        this.applyButton.setTooltip(Tooltip.create(Component.translatable("world_preview.tfc.settings.apply.tooltip")));

        // Build layout
        // The default GridLayoutTab layout centers vertically; if the tab is taller than the available
        // space (smaller GUI scale / resolution), bottom controls like the Apply button can be clipped.
        // To keep everything visible, we pack the climate sliders into two columns.
        int HALF = (LINE_WIDTH - 4) / 2;

        RowHelper rowHelper = this.layout
                .rowSpacing(6)
                .columnSpacing(4)
                .createRowHelper(2);

        /* Header */
        this.headerLabel = new WGLabel(
                minecraft.font, 0, 0, LINE_WIDTH, 10,
                WGLabel.TextAlignment.CENTER,
                Component.translatable("world_preview.tfc.settings.head"),
                0xFFFFFF
        );
        rowHelper.addChild(this.headerLabel, 2);

        this.labelFlatBedrock = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.flat_bedrock"),
                0xAAAAAA
        );
        this.labelSpawnDistance = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.spawn_distance"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelFlatBedrock);
        rowHelper.addChild(this.labelSpawnDistance);

        /* Row 1 */
        rowHelper.addChild(this.cbFlatBedrock);
        rowHelper.addChild(this.spawnRadiusSlider);

        this.labelSpawnCenterX = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.spawn_center_x"),
                0xAAAAAA
        );
        this.labelSpawnCenterZ = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.spawn_center_z"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelSpawnCenterX);
        rowHelper.addChild(this.labelSpawnCenterZ);

        /* Row 2 */
        rowHelper.addChild(this.spawnCenterXSlider);
        rowHelper.addChild(this.spawnCenterZSlider);

        this.labelTempScale = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.temp_scale"),
                0xAAAAAA
        );
        this.labelRainScale = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.rain_scale"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelTempScale);
        rowHelper.addChild(this.labelRainScale);

        /* Row 3 */
        rowHelper.addChild(this.tempScaleSlider);
        rowHelper.addChild(this.rainScaleSlider);

        this.labelTempConst = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.temp_const"),
                0xAAAAAA
        );
        this.labelRainConst = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.rain_const"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelTempConst);
        rowHelper.addChild(this.labelRainConst);

        /* Row 4 */
        rowHelper.addChild(this.tempConstSlider);
        rowHelper.addChild(this.rainConstSlider);

        this.labelContinentalness = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.continentalness"),
                0xAAAAAA
        );
        this.labelGrassDensity = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.grass_density"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelContinentalness);
        rowHelper.addChild(this.labelGrassDensity);

        /* Row 5 */
        rowHelper.addChild(this.continentalnessSlider);
        rowHelper.addChild(this.grassDensitySlider);

        this.labelFiniteContinents = new WGLabel(
                minecraft.font, 0, 0, HALF_WIDTH, 5,
                WGLabel.TextAlignment.LEFT,
                Component.translatable("world_preview.tfc.settings.finite_continents"),
                0xAAAAAA
        );
        rowHelper.addChild(this.labelFiniteContinents);

        /* Row 6 */
        rowHelper.addChild(this.cbFiniteContinents);
        this.labelSpacer = new WGLabel(
                minecraft.font, 0, 0, HALF, 5,
                WGLabel.TextAlignment.LEFT,
                Component.literal(""),
                0
        );
        rowHelper.addChild(this.labelSpacer);

        /* Apply */
        rowHelper.addChild(this.applyButton, 2);

    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {
        // Responsive layout: recompute widths on every layout pass (window resize / GUI scale changes).
        // We keep a max width for readability, but shrink to fit smaller windows.
        final int OUTER_PAD = 16;
        final int MAX_WIDTH = 360;

        int lineWidth = Math.max(220, Math.min(MAX_WIDTH, screenRectangle.width() - OUTER_PAD));
        int halfWidth = Math.max(100, (lineWidth - 4) / 2);

        // Tighten vertical spacing on short screens so the Apply button is less likely to be clipped.
        this.layout.rowSpacing(screenRectangle.height() < 260 ? 3 : 6);

        // Labels
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

        // Widgets
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

        // Arrange, then top-align inside the tab area so bottom controls aren't clipped as often.
        this.layout.arrangeElements();
        FrameLayout.alignInRectangle(this.layout, screenRectangle, 0.5F, 0.0F);
    }

    private void applySettings() {
        // Log settings being applied (info level for visibility)
        caeruleustait.world.preview.WorldPreview.LOGGER.info(
            "[TFCTab] Applying settings: spawnDistance={}, spawnCenterX={}, spawnCenterZ={}",
            this.spawnDistance, this.spawnCenterX, this.spawnCenterZ
        );

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

        // Persist the override inside the preview WorkManager.
        // The preview chunk generator is rebuilt when the preview context changes (e.g. after closing this screen),
        // so we must re-apply these settings whenever that happens.
        this.previewContainer.workManager().setTFCSettingsOverride(newSettings);

        // Soft refresh: cancel in-flight sampling, rebuild the TFC samplers, and invalidate only the TFC layers.
        this.previewContainer.workManager().onTFCSettingsChanged();

        // Return to the preview screen (matches the user's expectation for an Apply button).
        if (this.onApplyClose != null) {
            this.onApplyClose.run();
        }
    }

    /**
     * Scale value for temperature/rainfall scale sliders (displayed as km).
     */
    public static class ScaleValue implements SelectionSlider.SelectionValues {
        public final int value;

        public ScaleValue(int value, String translationKey) {
            this.value = value;
        }

        @Override
        public Component message() {
            if (value == 0) return Component.literal("0.0 km");
            return Component.literal(String.format("%+.1f km", value / 1000.0));
        }
    }

    /**
     * Constant value for temperature/rainfall constant sliders.
     */
    public static class ConstantValue implements SelectionSlider.SelectionValues {
        public final float value;

        public ConstantValue(float value, String translationKey) {
            this.value = value;
        }

        @Override
        public Component message() {
            return Component.literal(String.format("%.1f", value));
        }
    }

    /**
     * Percentage value for continentalness slider.
     */
    public static class PercentValue implements SelectionSlider.SelectionValues {
        public final float value;

        public PercentValue(float value, String translationKey) {
            this.value = value;
        }

        @Override
        public Component message() {
            return Component.literal(String.format("%.0f%%", value * 100));
        }
    }
}
