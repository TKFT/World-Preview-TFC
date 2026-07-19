package com.rustysnail.world.preview.tfc.client.gui.widgets;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.WorkManager;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropRegistry;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCCropSuitability;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCPreviewClimateSampler;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCRegionWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import com.rustysnail.world.preview.tfc.client.WorldPreviewClient;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.PreviewDisplayDataProvider;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomesList;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class PreviewDisplay extends AbstractWidget implements AutoCloseable
{
    private final Minecraft minecraft;
    private final PreviewDisplayDataProvider dataProvider;
    private final WorkManager workManager;
    private final RenderSettings renderSettings;
    private final WorldPreviewConfig config;
    private Short2LongMap visibleBiomes;
    private Short2LongMap visibleStructures;
    private Short2LongMap visibleRocks;
    private NativeImage previewImg;
    private DynamicTexture previewTexture;
    private long[] workingVisibleBiomes;
    private long[] workingVisibleStructures;
    private final long[] workingVisibleRocks;
    private int[] colorMap;
    private int[] colorMapGrayScale;
    private int[] heightColorMap;
    private int[] tfcTemperatureColorMap;
    private int[] tfcRainfallColorMap;
    private boolean[] cavesMap;
    // Precomputed NativeImage-order TFC palettes (normal + grayscale for selection dimming).
    private int[] forestTexPalette;
    private int[] forestTexPaletteGray;
    private int[] treeTexPalette;
    private int[] treeTexPaletteGray;
    private int[] soilTexPalette;
    private int[] soilTexPaletteGray;
    private int[] cropTexPalette;
    private int[] cropTexPaletteGray;
    private int[][] rockTexPalette;
    private int[][] rockTexPaletteGray;
    private int[][] rockTexPaletteBright;
    private int[] rockTypeTexPalette;
    private int[] rockTypeTexPaletteGray;
    private int[] rockTypeTexPaletteBright;
    private int tfcOceanTex;
    private int tfcOceanTexGray;
    private int tfcLakeTex;
    private int tfcLakeTexGray;
    private int tfcRiverTex;
    private int tfcRiverTexGray;
    private int tfcShoreTex;
    private int tfcLandTex;
    private int tfcUnknownTex;
    private int tfcInvalidTex;
    private int tfcInvalidTexGray;
    private boolean loggedInvalidBiomeId = false;
    private IconData[] structureIcons;
    private IconData[] featureIcons;
    private IconData playerIcon;
    private IconData spawnIcon;
    private IconData worldSpawnIcon;
    private ItemStack[] structureItems;
    private PreviewDisplayDataProvider.StructureRenderInfo[] structureRenderInfoMap;
    private boolean showFeatures = false;
    @Nullable private Component coordinatesCopiedMsg = null;
    @Nullable private Instant coordinatesCopiedTime = null;
    // Crop-hover detail debounce: only compute the detailed breakdown after the cursor has rested on
    // the same quart for CROP_HOVER_DETAIL_MS, so panning across quarts never triggers per-frame work.
    private static final long CROP_HOVER_DETAIL_MS = 90L;
    private int lastCropHoverQuartX = Integer.MIN_VALUE;
    private int lastCropHoverQuartZ = Integer.MIN_VALUE;
    private int lastCropHoverRevision = Integer.MIN_VALUE;
    private long cropHoverSinceMs = 0L;
    private int texWidth = 100;
    private int texHeight = 100;
    private short selectedBiomeId;
    private short selectedRockId;
    private short selectedTFCMapValue = Short.MIN_VALUE;
    private boolean highlightCaves;
    private double totalDragX = 0.0;
    private double totalDragZ = 0.0;
    private int scaleBlockPos = 1;
    private StructHoverHelperCell[] hoverHelperGrid;
    private int hoverHelperGridWidth;
    private int hoverHelperGridHeight;
    private final Queue<Long> frametimes = new ArrayDeque<>();
    private boolean clicked = false;
    private boolean loggedUnknownTreeSpecies = false;
    private long lastSpeciesChunkKey = Long.MIN_VALUE;
    private com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver.Result cachedTreeResult =
        com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver.Result.NONE;
    private boolean loggedInvalidHeight = false;

    // --- Texture dirty/revision tracking (rebuild the texture only when something actually changed) ---
    private static final long DRAG_REBUILD_THROTTLE_MS = 60L;
    @Nullable private List<RenderHelper> cachedRenderData = null;
    private long lastRenderedRevision = Long.MIN_VALUE;
    private long lastRenderedPaletteRevision = Long.MIN_VALUE;
    private long builtPaletteRevision = Long.MIN_VALUE;
    @Nullable private RenderSettings.RenderMode lastRenderedMode = null;
    private short lastRenderedBiomeId = Short.MIN_VALUE;
    private short lastRenderedRockId = Short.MIN_VALUE;
    private short lastRenderedTFCMapValue = Short.MIN_VALUE;
    private boolean lastRenderedHighlightCaves = false;
    private int lastRenderedTexWidth = -1;
    private int lastRenderedTexHeight = -1;
    private int lastRenderedQuartStride = -1;
    private long lastRenderedCenterX = Long.MIN_VALUE;
    private long lastRenderedCenterZ = Long.MIN_VALUE;
    private long lastRenderedCenterY = Long.MIN_VALUE;
    private long lastTextureBuildAtMs = 0L;
    private long textureBuildNanosTotal = 0L;
    private int textureBuildCount = 0;
    private long lastTextureStatsLogMs = 0L;

    public PreviewDisplay(Minecraft minecraft, PreviewDisplayDataProvider dataProvider, Component component)
    {
        super(0, 0, 100, 100, component);
        this.minecraft = minecraft;
        this.workManager = WorldPreview.get().workManager();
        this.dataProvider = dataProvider;
        this.visibleBiomes = new Short2LongOpenHashMap();
        this.visibleStructures = new Short2LongOpenHashMap();
        this.visibleRocks = new Short2LongOpenHashMap();
        this.workingVisibleRocks = new long[TFCSampleUtils.ROCK_NAMES.length];
        this.renderSettings = WorldPreview.get().renderSettings();
        this.config = WorldPreview.get().cfg();
        this.structureIcons = new IconData[0];
        this.resizeImage();
    }

    public void resizeImage()
    {
        this.closeDisplayTextures();
        this.previewImg = new NativeImage(Format.RGBA, this.texWidth, this.texHeight, true);
        this.previewTexture = new DynamicTexture(this.previewImg);
        this.scaleBlockPos = 4 / this.renderSettings.quartExpand() * this.renderSettings.quartStride();
        this.hoverHelperGridWidth = this.texWidth / PreviewSection.SIZE + 1;
        this.hoverHelperGridHeight = this.texHeight / PreviewSection.SIZE + 1;

        this.hoverHelperGrid = new StructHoverHelperCell[this.hoverHelperGridWidth * this.hoverHelperGridHeight];

        for (int i = 0; i < this.hoverHelperGrid.length; i++)
        {
            this.hoverHelperGrid[i] = new StructHoverHelperCell(new ArrayList<>());
        }

        // The cached render data references sections sized for the old texture; force a rebuild.
        this.cachedRenderData = null;
    }

    private void logTextureStatsPeriodically(long now)
    {
        if (now - this.lastTextureStatsLogMs >= 5000L && this.textureBuildCount > 0)
        {
            WorldPreview.LOGGER.debug("[Preview] texture rebuilds: {} in last {}s window, avg {} ms/rebuild",
                this.textureBuildCount, (now - this.lastTextureStatsLogMs) / 1000,
                (this.textureBuildNanosTotal / this.textureBuildCount) / 1_000_000.0);
            this.textureBuildCount = 0;
            this.textureBuildNanosTotal = 0L;
            this.lastTextureStatsLogMs = now;
        }
    }

    public void setSize(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.texWidth = this.width * (int) this.minecraft.getWindow().getGuiScale();
        this.texHeight = this.height * (int) this.minecraft.getWindow().getGuiScale();
        this.resizeImage();
    }

    public void reloadData()
    {
        this.closeIconTextures();
        PreviewData.BiomeData[] rawBiomeMap = this.dataProvider.previewData().biomeId2BiomeData();
        this.structureRenderInfoMap = this.dataProvider.renderStructureMap();
        this.structureItems = this.dataProvider.structureItems();
        this.structureIcons = Arrays.stream(this.dataProvider.structureIcons())
            .map(x -> new IconData(x, new DynamicTexture(x)))
            .toArray(IconData[]::new);
        NativeImage[] featureIconImages = this.dataProvider.featureIcons();
        if (featureIconImages != null)
        {
            this.featureIcons = Arrays.stream(featureIconImages)
                .map(x -> x != null ? new IconData(x, new DynamicTexture(x)) : null)
                .toArray(IconData[]::new);
            Arrays.stream(this.featureIcons).filter(Objects::nonNull).map(IconData::texture).forEach(DynamicTexture::upload);
        }
        else
        {
            this.featureIcons = new IconData[0];
        }
        this.playerIcon = new IconData(this.dataProvider.playerIcon(), new DynamicTexture(this.dataProvider.playerIcon()));
        this.spawnIcon = new IconData(this.dataProvider.spawnIcon(), new DynamicTexture(this.dataProvider.spawnIcon()));
        this.worldSpawnIcon = new IconData(this.dataProvider.worldSpawnIcon(), new DynamicTexture(this.dataProvider.worldSpawnIcon()));
        this.playerIcon.texture.upload();
        this.spawnIcon.texture.upload();
        this.worldSpawnIcon.texture.upload();
        Arrays.stream(this.structureIcons).map(IconData::texture).forEach(DynamicTexture::upload);

        try
        {
            this.heightColorMap = this.dataProvider.heightColorMap();
            this.tfcTemperatureColorMap = this.dataProvider.tfcTemperatureColorMap();
            this.tfcRainfallColorMap = this.dataProvider.tfcRainfallColorMap();
        }
        catch (Throwable e)
        {
            WorldPreview.LOGGER.error("Error initializing PreviewDisplay color maps", e);
        }

        this.workingVisibleBiomes = new long[rawBiomeMap.length];
        this.workingVisibleStructures = new long[this.structureIcons.length];
        this.colorMap = new int[rawBiomeMap.length];
        this.colorMapGrayScale = new int[rawBiomeMap.length];
        this.cavesMap = new boolean[rawBiomeMap.length];

        for (short i = 0; i < rawBiomeMap.length; i++)
        {
            this.colorMap[i] = textureColor(rawBiomeMap[i].color());
            this.colorMapGrayScale[i] = grayScale(this.colorMap[i]);
            this.cavesMap[i] = rawBiomeMap[i].isCave();
        }

        this.buildTfcPalettes();
    }

    private static boolean needsLandWaterOverlay(RenderSettings.RenderMode mode)
    {
        return mode == RenderSettings.RenderMode.TFC_TEMPERATURE
            || mode == RenderSettings.RenderMode.TFC_RAINFALL
            || mode == RenderSettings.RenderMode.TFC_ROCK_TOP
            || mode == RenderSettings.RenderMode.TFC_ROCK_MID
            || mode == RenderSettings.RenderMode.TFC_ROCK_BOT
            || mode == RenderSettings.RenderMode.TFC_HOTSPOT;
    }

    private static boolean shouldOverlayRegionWater(short terrain)
    {
        return terrain != TFCRegionWorkUnit.LAND_WATER_LAND
            && terrain != TFCRegionWorkUnit.LAND_WATER_LAKE
            && terrain != TFCRegionWorkUnit.LAND_WATER_RIVER;
    }

    private static int packTex(int r, int g, int b)
    {
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /**
     * Rebuild the TFC palettes if the tree-species registry changed (cheap no-op otherwise).
     */
    private void maybeRebuildTfcPalettes()
    {
        long paletteRevision = WorldPreview.get().biomeColorMap().paletteRevision();
        if (this.treeTexPalette == null || this.treeTexPalette.length != TFCSampleUtils.treeSpeciesCount()
            || this.forestTexPalette == null || this.builtPaletteRevision != paletteRevision)
        {
            this.heightColorMap = this.dataProvider.heightColorMap();
            this.tfcTemperatureColorMap = this.dataProvider.tfcTemperatureColorMap();
            this.tfcRainfallColorMap = this.dataProvider.tfcRainfallColorMap();
            this.buildTfcPalettes();
            this.dataProvider.onColorPalettesChanged(paletteRevision);
        }
    }

    /**
     * Precomputes NativeImage-order color palettes for the categorical TFC maps, so updateTexture
     * needs no per-pixel textureColor(...) conversion or getXxxColor(...) lookup. Rebuilt on data
     * reload (rock colors / palette resources) and when the tree-species registry changes.
     */
    private void buildTfcPalettes()
    {
        int fc = TFCSampleUtils.forestTypeCount();
        this.forestTexPalette = new int[fc];
        this.forestTexPaletteGray = new int[fc];
        for (short i = 0; i < fc; i++)
        {
            int tex = textureColor(TFCSampleUtils.getForestTypeColor(i));
            this.forestTexPalette[i] = tex;
            this.forestTexPaletteGray[i] = grayScale(tex);
        }

        int tc = TFCSampleUtils.treeSpeciesCount();
        this.treeTexPalette = new int[tc];
        this.treeTexPaletteGray = new int[tc];
        for (short i = 0; i < tc; i++)
        {
            int tex = textureColor(TFCSampleUtils.getTreeSpeciesColor(i));
            this.treeTexPalette[i] = tex;
            this.treeTexPaletteGray[i] = grayScale(tex);
        }

        int sc = TFCSampleUtils.soilTypeCount();
        this.soilTexPalette = new int[sc];
        this.soilTexPaletteGray = new int[sc];
        for (short i = 0; i < sc; i++)
        {
            int tex = textureColor(TFCSampleUtils.getSoilTypeColor(i));
            this.soilTexPalette[i] = tex;
            this.soilTexPaletteGray[i] = grayScale(tex);
        }

        int cc = TFCCropSuitability.suitabilityCount();
        this.cropTexPalette = new int[cc];
        this.cropTexPaletteGray = new int[cc];
        for (short i = 0; i < cc; i++)
        {
            int tex = textureColor(TFCCropSuitability.getSuitabilityColor(i));
            this.cropTexPalette[i] = tex;
            this.cropTexPaletteGray[i] = grayScale(tex);
        }

        this.tfcOceanTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_OCEAN, TFCSampleUtils.COLOR_WATER));
        this.tfcOceanTexGray = grayScale(this.tfcOceanTex);
        this.tfcLakeTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_LAKE, TFCSampleUtils.COLOR_WATER));
        this.tfcLakeTexGray = grayScale(this.tfcLakeTex);
        this.tfcRiverTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_RIVER, TFCSampleUtils.COLOR_WATER));
        this.tfcRiverTexGray = grayScale(this.tfcRiverTex);
        this.tfcShoreTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_SHORE, 0xFFD2B48C));
        this.tfcLandTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_LAND, 0xFF44AA44));
        this.tfcUnknownTex = textureColor(TFCSampleUtils.getWaterColor(TFCSampleUtils.WATER_UNKNOWN, TFCSampleUtils.COLOR_INVALID));
        this.tfcInvalidTex = textureColor(TFCCropSuitability.getSuitabilityColor(TFCSampleUtils.VALUE_INVALID));
        this.tfcInvalidTexGray = grayScale(this.tfcInvalidTex);

        int rc = TFCSampleUtils.ROCK_COLORS.length;
        this.rockTexPalette = new int[3][rc];
        this.rockTexPaletteGray = new int[3][rc];
        this.rockTexPaletteBright = new int[3][rc];
        final int[] layerShift = {0, 20, 40}; // top / mid / bot
        for (int layer = 0; layer < 3; layer++)
        {
            int shift = layerShift[layer];
            for (short id = 0; id < rc; id++)
            {
                int argb = TFCSampleUtils.getRockColor(id);
                int r = Math.max(0, ((argb >> 16) & 0xFF) - shift);
                int g = Math.max(0, ((argb >> 8) & 0xFF) - shift);
                int b = Math.max(0, (argb & 0xFF) - shift);
                this.rockTexPalette[layer][id] = packTex(r, g, b);
                int gray = (r * 30 + g * 59 + b * 11) / 100;
                this.rockTexPaletteGray[layer][id] = packTex(gray, gray, gray);
                this.rockTexPaletteBright[layer][id] = packTex(Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40));
            }
        }

        int rtc = TFCSampleUtils.ROCK_TYPE_COLORS.length;
        this.rockTypeTexPalette = new int[rtc];
        this.rockTypeTexPaletteGray = new int[rtc];
        this.rockTypeTexPaletteBright = new int[rtc];
        for (short id = 0; id < rtc; id++)
        {
            int argb = TFCSampleUtils.getRockTypeColor(id);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            this.rockTypeTexPalette[id] = packTex(r, g, b);
            int gray = (r * 30 + g * 59 + b * 11) / 100;
            this.rockTypeTexPaletteGray[id] = packTex(gray, gray, gray);
            this.rockTypeTexPaletteBright[id] = packTex(Math.min(255, r + 40), Math.min(255, g + 40), Math.min(255, b + 40));
        }
        this.builtPaletteRevision = WorldPreview.get().biomeColorMap().paletteRevision();
    }

    private void closeIconTextures()
    {
        if (this.structureIcons != null)
        {
            Arrays.stream(this.structureIcons).forEach(IconData::close);
        }

        if (this.featureIcons != null)
        {
            Arrays.stream(this.featureIcons).filter(Objects::nonNull).forEach(IconData::close);
        }

        if (this.playerIcon != null)
        {
            this.playerIcon.texture.close();
        }

        if (this.spawnIcon != null)
        {
            this.spawnIcon.texture.close();
        }

        if (this.worldSpawnIcon != null)
        {
            this.worldSpawnIcon.texture.close();
        }
    }

    private void closeDisplayTextures()
    {
        if (this.previewTexture != null)
        {
            this.previewTexture.close();
        }

        if (this.previewImg != null)
        {
            this.previewImg.close();
        }
    }

    @Override
    public void close()
    {
        this.closeIconTextures();
        this.closeDisplayTextures();
    }

    public void resetDragOffset()
    {
        this.totalDragX = 0.0;
        this.totalDragZ = 0.0;
    }

    public BlockPos center()
    {
        return this.totalDragX == 0.0 && this.totalDragZ == 0.0
            ? this.renderSettings.center()
            : new BlockPos(
            (int) (this.renderSettings.center().getX() + this.totalDragX),
            this.renderSettings.center().getY(),
            (int) (this.renderSettings.center().getZ() + this.totalDragZ)
        );
    }

    public void renderWidget(@NotNull GuiGraphics guiGraphics, int x, int y, float f)
    {
        int xMin = this.getX();
        int yMin = this.getY();
        int xMax = xMin + this.width;
        int yMax = yMin + this.height;
        double winWidth = this.minecraft.getWindow().getWidth();
        double winHeight = this.minecraft.getWindow().getHeight();
        double guiScale = this.minecraft.getWindow().getGuiScale();
        Instant renderStart = Instant.now();
        this.queueGeneration();
        synchronized (this.dataProvider)
        {
            if (this.dataProvider.setupFailed())
            {
                this.previewImg.fillRect(0, 0, this.texWidth, this.texHeight, -16777216);
                this.previewTexture.upload();
                WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
                List<MutableComponent> lines = WorldPreviewComponents.MSG_ERROR_SETUP_FAILED.getString().lines().map(Component::literal).toList();
                int centerX = this.getX() + this.width / 2;
                int centerY = this.getY() + this.height / 2 - lines.size() / 2 * (9 + 4);

                for (int i = 0; i < lines.size(); i++)
                {
                    Component line = lines.get(i);
                    int offsetY = i * (9 + 4);
                    guiGraphics.drawCenteredString(this.minecraft.font, line, centerX, centerY + offsetY, 16777215);
                }
            }
            else if (this.dataProvider.isUpdating())
            {
                this.previewImg.fillRect(0, 0, this.texWidth, this.texHeight, -16777216);
                this.previewTexture.upload();
                WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
                int centerX = this.getX() + this.width / 2;
                int centerY = this.getY() + this.height / 2;
                guiGraphics.drawCenteredString(this.minecraft.font, WorldPreviewComponents.MSG_PREVIEW_SETUP_LOADING, centerX, centerY, 16777215);
                this.cachedRenderData = null;
            }
            else
            {
                BlockPos center = this.center();
                long revision = this.workManager.dataRevision();
                long paletteRevision = WorldPreview.get().biomeColorMap().paletteRevision();
                long now = System.currentTimeMillis();
                boolean stateChanged =
                    revision != this.lastRenderedRevision
                        || paletteRevision != this.lastRenderedPaletteRevision
                        || this.renderSettings.mode != this.lastRenderedMode
                        || this.selectedBiomeId != this.lastRenderedBiomeId
                        || this.selectedRockId != this.lastRenderedRockId
                        || this.selectedTFCMapValue != this.lastRenderedTFCMapValue
                        || this.highlightCaves != this.lastRenderedHighlightCaves
                        || this.texWidth != this.lastRenderedTexWidth
                        || this.texHeight != this.lastRenderedTexHeight
                        || this.renderSettings.quartStride() != this.lastRenderedQuartStride
                        || center.getX() != this.lastRenderedCenterX
                        || center.getZ() != this.lastRenderedCenterZ
                        || center.getY() != this.lastRenderedCenterY;
                boolean throttled = this.clicked && (now - this.lastTextureBuildAtMs) < DRAG_REBUILD_THROTTLE_MS;
                boolean rebuild = this.cachedRenderData == null || (stateChanged && !throttled);

                if (rebuild)
                {
                    Arrays.fill(this.workingVisibleBiomes, 0L);
                    Arrays.fill(this.workingVisibleRocks, 0L);
                    long buildStart = System.nanoTime();
                    List<RenderHelper> renderData = this.generateRenderData();
                    this.updateTexture(renderData);
                    this.previewTexture.upload();
                    this.cachedRenderData = renderData;
                    this.textureBuildNanosTotal += System.nanoTime() - buildStart;
                    this.textureBuildCount++;
                    this.lastTextureBuildAtMs = now;
                    this.lastRenderedRevision = revision;
                    this.lastRenderedPaletteRevision = paletteRevision;
                    this.lastRenderedMode = this.renderSettings.mode;
                    this.lastRenderedBiomeId = this.selectedBiomeId;
                    this.lastRenderedRockId = this.selectedRockId;
                    this.lastRenderedTFCMapValue = this.selectedTFCMapValue;
                    this.lastRenderedHighlightCaves = this.highlightCaves;
                    this.lastRenderedTexWidth = this.texWidth;
                    this.lastRenderedTexHeight = this.texHeight;
                    this.lastRenderedQuartStride = this.renderSettings.quartStride();
                    this.lastRenderedCenterX = center.getX();
                    this.lastRenderedCenterZ = center.getZ();
                    this.lastRenderedCenterY = center.getY();
                    this.logTextureStatsPeriodically(now);
                }

                // Overlays are re-drawn every frame; they use the same render data the current
                // texture was built from, so their positions stay aligned with the texture.
                List<RenderHelper> renderData = this.cachedRenderData;
                Arrays.fill(this.workingVisibleStructures, 0L);
                Arrays.stream(this.hoverHelperGrid).forEach(cell -> cell.entries.clear());

                WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
                guiGraphics.enableScissor(xMin, yMin, xMax, yMax);
                Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, (float) winWidth, (float) winHeight, 0.0F, 1000.0F, 21000.0F);
                RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
                this.renderStructures(renderData, guiGraphics);
                this.renderFeatures(renderData);
                this.renderPlayerAndSpawn(guiGraphics);
                matrix4f = new Matrix4f().setOrtho(0.0F, (float) (winWidth / guiScale), (float) (winHeight / guiScale), 0.0F, 1000.0F, 21000.0F);
                RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
                guiGraphics.disableScissor();
                double mouseX = this.minecraft.mouseHandler.xpos() * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                double mouseZ = this.minecraft.mouseHandler.ypos() * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                if (rebuild)
                {
                    this.biomesChanged();
                }
                this.updateTooltip(mouseX, mouseZ);
            }
        }

        guiGraphics.fill(xMin - 1, yMin - 1, xMax + 1, yMin, -10066330);
        guiGraphics.fill(xMax, yMin, xMax + 1, yMax, -10066330);
        guiGraphics.fill(xMin - 1, yMax, xMax + 1, yMax + 1, -10066330);
        guiGraphics.fill(xMin - 1, yMin, xMin, yMax, -10066330);
        Component coordinatesCopiedMsg = this.coordinatesCopiedMsg;
        Instant coordinatesCopiedTime = this.coordinatesCopiedTime;
        if (coordinatesCopiedMsg != null && coordinatesCopiedTime != null)
        {
            guiGraphics.fill(xMin, yMax - 38, xMax, yMax - 19, -1442840576);
            guiGraphics.drawCenteredString(this.minecraft.font, coordinatesCopiedMsg, xMin + (xMax - xMin) / 2, yMax - 32, 16777215);
            if (Duration.between(coordinatesCopiedTime, Instant.now()).toSeconds() >= 8L)
            {
                this.coordinatesCopiedMsg = null;
                this.coordinatesCopiedTime = null;
            }
        }

        Instant renderEnd = Instant.now();
        this.frametimes.add(Duration.between(renderStart, renderEnd).abs().toMillis());

        while (this.frametimes.size() > 30)
        {
            this.frametimes.poll();
        }

        long sum = this.frametimes.stream().reduce(0L, Long::sum);
        if (this.config.showFrameTime)
        {
            guiGraphics.drawString(this.minecraft.font, sum / this.frametimes.size() + " ms", 5, 5, 16777215);
        }
    }

    private TextureCoordinate blockToTexture(BlockPos blockPos)
    {
        BlockPos center = this.center();
        int xMin = center.getX() - this.texWidth * this.scaleBlockPos / 2 - 1;
        int zMin = center.getZ() - this.texHeight * this.scaleBlockPos / 2 - 1;
        return new TextureCoordinate((blockPos.getX() - xMin) / 4 * 4 / this.scaleBlockPos, (blockPos.getZ() - zMin) / 4 * 4 / this.scaleBlockPos);
    }

    private void putHoverStructEntry(TextureCoordinate pos, StructHoverHelperEntry entry)
    {
        int cellX = Math.clamp(pos.x / PreviewSection.SIZE, 0, this.hoverHelperGridWidth - 1);
        int cellZ = Math.clamp(pos.z / PreviewSection.SIZE, 0, this.hoverHelperGridHeight - 1);
        this.hoverHelperGrid[cellX * this.hoverHelperGridHeight + cellZ].entries.add(entry);
    }

    private void queueGeneration()
    {
        BlockPos center = this.center();
        int xMin = center.getX() - this.texWidth * this.scaleBlockPos / 2 - 1;
        int xMax = center.getX() + this.texWidth * this.scaleBlockPos / 2 + 1;
        int zMin = center.getZ() - this.texHeight * this.scaleBlockPos / 2 - 1;
        int zMax = center.getZ() + this.texHeight * this.scaleBlockPos / 2 + 1;
        int y = this.renderSettings.mode != null && this.renderSettings.mode.useY ? center.getY() : 0;
        this.workManager.queueRange(new BlockPos(xMin, y, zMin), new BlockPos(xMax, y, zMax));
    }

    private List<RenderHelper> generateRenderData()
    {
        BlockPos center = this.center();
        int xMin = center.getX() - this.texWidth * this.scaleBlockPos / 2 - 1;
        int zMin = center.getZ() - this.texHeight * this.scaleBlockPos / 2 - 1;
        int quartExpand = this.renderSettings.quartExpand();
        int quartStride = this.renderSettings.quartStride();
        int quartsInWidth = this.texWidth / quartExpand * quartStride;
        int quartsInHeight = this.texHeight / quartExpand * quartStride;
        int minQuartX = QuartPos.fromBlock(xMin);
        int minQuartZ = QuartPos.fromBlock(zMin);
        int maxQuartX = minQuartX + quartsInWidth;
        int maxQuartZ = minQuartZ + quartsInHeight;
        int quartX = minQuartX;
        int quartY = QuartPos.fromBlock(center.getY());
        int quartZ = minQuartZ;
        int sectionStartTexX = 0;
        int sectionStartTexZ = 0;

        // Modes that tint water (ocean) read the land/water section; attach it once per section so
        // updateTexture never does a per-pixel storage lookup for the water mask.
        boolean needsLandWater = needsLandWaterOverlay(this.renderSettings.mode);
        List<RenderHelper> res = new ArrayList<>((quartsInWidth / PreviewSection.SIZE + 2) * (quartsInHeight / PreviewSection.SIZE + 2));
        PreviewStorage storage = this.workManager.previewStorage();
        synchronized (storage)
        {
            while (true)
            {
                long flag = this.renderSettings.mode.flag;
                int useY = this.renderSettings.mode.useY ? quartY : 0;
                PreviewSection dataSection = storage.section4(quartX, useY, quartZ, flag);
                PreviewSection structureSection = storage.section4(quartX, 0, quartZ, 1L);
                PreviewSection landWaterSection = needsLandWater
                    ? storage.section4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_LAND_WATER.flag)
                    : null;
                PreviewSection.AccessData accessData = dataSection.calcQuartOffsetData(quartX, quartZ, maxQuartX, maxQuartZ);
                res.add(new RenderHelper(dataSection, structureSection, landWaterSection, accessData, sectionStartTexX, sectionStartTexZ));
                if (accessData.continueX())
                {
                    int quartDiffX = accessData.maxX() - accessData.minX();
                    quartX += quartDiffX;
                    sectionStartTexX += quartDiffX * quartExpand / quartStride;
                }
                else
                {
                    if (!accessData.continueZ())
                    {
                        return res;
                    }

                    int quartDiffZ = accessData.maxZ() - accessData.minZ();
                    quartX = minQuartX;
                    quartZ += quartDiffZ;
                    sectionStartTexZ += quartDiffZ * quartExpand / quartStride;
                    sectionStartTexX = 0;
                }
            }
        }
    }

    private void updateTexture(List<RenderHelper> renderData)
    {
        int texX;
        int texZ;
        int quartExpand = this.renderSettings.quartExpand();
        int quartStride = this.renderSettings.quartStride();
        this.maybeRebuildTfcPalettes();
        // Rock layer index (0 top / 1 mid / 2 bot), constant for the whole texture pass.
        final int rockLayer = switch (this.renderSettings.mode)
        {
            case TFC_ROCK_MID -> 1;
            case TFC_ROCK_BOT -> 2;
            default -> 0;
        };

        for (RenderHelper r : renderData)
        {
            texX = r.sectionStartTexX;

            for (int x = r.accessData.minX(); x < r.accessData.maxX(); x += quartStride)
            {
                texZ = r.sectionStartTexZ;

                for (int z = r.accessData.minZ(); z < r.accessData.maxZ(); z += quartStride)
                {
                    short rawData = r.dataSection.get(x, z);
                    int color = -16777216;
                    switch (this.renderSettings.mode)
                    {
                        case BIOMES:
                            if (rawData >= 0 && rawData < this.colorMap.length)
                            {
                                color = this.selectedBiomeId < 0 && !this.highlightCaves ? this.colorMap[rawData] : this.colorMapGrayScale[rawData];
                                if (this.selectedBiomeId == rawData || this.highlightCaves && this.cavesMap[rawData])
                                {
                                    color = this.colorMap[rawData];
                                }
                                this.workingVisibleBiomes[rawData]++;
                            }
                            else if (rawData >= 0)
                            {
                                if (!this.loggedInvalidBiomeId)
                                {
                                    WorldPreview.LOGGER.warn("Invalid biome id {} out of range (colorMap length {})", rawData, this.colorMap.length);
                                    this.loggedInvalidBiomeId = true;
                                }
                                color = 0xFFFF00FF;
                            }
                            break;
                        case HEIGHTMAP:
                            if (rawData > -32768 && this.heightColorMap != null)
                            {
                                int idx = rawData - this.dataProvider.yMin();
                                if (idx >= 0 && idx < this.heightColorMap.length)
                                {
                                    color = this.heightColorMap[idx];
                                }
                                else if (!this.loggedInvalidHeight)
                                {
                                    WorldPreview.LOGGER.warn("Invalid height value {} (index {} out of heightColorMap length {})",
                                        rawData, idx, this.heightColorMap.length);
                                    this.loggedInvalidHeight = true;
                                }
                            }
                            break;
                        case TFC_TEMPERATURE:
                            if (rawData > -32768 && this.tfcTemperatureColorMap != null)
                            {
                                short terrain = r.landWaterSection != null ? r.landWaterSection.get(x, z) : TFCRegionWorkUnit.LAND_WATER_LAND;
                                if (shouldOverlayRegionWater(terrain))
                                {
                                    color = this.regionWaterTexture(terrain);
                                }
                                else
                                {
                                    float normalized = (rawData + 32768.0F) / 65535.0F;
                                    int idx = Math.clamp((int) (normalized * 1023.0F), 0, 1023);
                                    color = this.tfcTemperatureColorMap[idx];
                                }
                            }
                            break;
                        case TFC_RAINFALL:
                            if (rawData > -32768 && this.tfcRainfallColorMap != null)
                            {
                                short terrain = r.landWaterSection != null ? r.landWaterSection.get(x, z) : TFCRegionWorkUnit.LAND_WATER_LAND;
                                if (shouldOverlayRegionWater(terrain))
                                {
                                    color = this.regionWaterTexture(terrain);
                                }
                                else
                                {
                                    float normalized = rawData / 32767.0F;
                                    int idx = Math.clamp((int) (normalized * 1023.0F), 0, 1023);
                                    color = this.tfcRainfallColorMap[idx];
                                }
                            }
                            break;
                        case TFC_LAND_WATER:
                            if (rawData > -32768)
                            {
                                color = switch (rawData)
                                {
                                    case TFCRegionWorkUnit.LAND_WATER_OCEAN -> this.tfcOceanTex;
                                    case TFCRegionWorkUnit.LAND_WATER_LAND -> this.tfcLandTex;
                                    case TFCRegionWorkUnit.LAND_WATER_SHORE -> this.tfcShoreTex;
                                    case TFCRegionWorkUnit.LAND_WATER_LAKE -> this.tfcLakeTex;
                                    case TFCRegionWorkUnit.LAND_WATER_RIVER -> this.tfcRiverTex;
                                    default -> this.tfcUnknownTex;
                                };
                            }
                            break;
                        case TFC_ROCK_TOP:
                        case TFC_ROCK_MID:
                        case TFC_ROCK_BOT:
                            short terrain = r.landWaterSection != null ? r.landWaterSection.get(x, z) : TFCRegionWorkUnit.LAND_WATER_LAND;
                            if (shouldOverlayRegionWater(terrain))
                            {
                                color = this.regionWaterTexture(terrain);
                            }
                            else if (rawData >= 0 && rawData < this.rockTexPalette[rockLayer].length)
                            {
                                this.workingVisibleRocks[rawData]++;
                                if (this.selectedRockId < 0)
                                {
                                    color = this.rockTexPalette[rockLayer][rawData];
                                }
                                else if (rawData == this.selectedRockId)
                                {
                                    color = this.rockTexPaletteBright[rockLayer][rawData];
                                }
                                else
                                {
                                    color = this.rockTexPaletteGray[rockLayer][rawData];
                                }
                            }
                            else if (rawData == -1)
                            {
                                color = 0xFF888888;
                            }
                            break;
                        case TFC_ROCK_TYPE:
                            if (rawData >= 0 && rawData < this.rockTypeTexPalette.length)
                            {
                                this.workingVisibleRocks[rawData]++;
                                if (this.selectedRockId < 0)
                                {
                                    color = this.rockTypeTexPalette[rawData];
                                }
                                else if (rawData == this.selectedRockId)
                                {
                                    color = this.rockTypeTexPaletteBright[rawData];
                                }
                                else
                                {
                                    color = this.rockTypeTexPaletteGray[rawData];
                                }
                            }
                            break;
                        case TFC_KAOLINITE:
                            if (rawData > -32768)
                            {
                                color = switch (rawData)
                                {
                                    case 0 -> this.tfcOceanTex;
                                    case 1 -> this.tfcLandTex;
                                    case 2 -> 0xFFFF66FF; // kaolin pink
                                    default -> 0xFF000001; // background
                                };
                            }
                            break;
                        case TFC_FOREST_TYPE:
                        {
                            boolean hi = this.selectedTFCMapValue == Short.MIN_VALUE
                                || TFCSampleUtils.canonicalMapValue(rawData) == this.selectedTFCMapValue;
                            if (TFCSampleUtils.isWaterValue(rawData))
                            {
                                color = this.waterTexture(rawData, hi);
                            }
                            else if (rawData >= 0 && rawData < this.forestTexPalette.length)
                            {
                                color = hi ? this.forestTexPalette[rawData] : this.forestTexPaletteGray[rawData];
                            }
                            else
                            {
                                color = hi ? this.tfcInvalidTex : this.tfcInvalidTexGray;
                            }
                            break;
                        }
                        case TFC_TREE_SPECIES:
                        {
                            boolean hi = this.selectedTFCMapValue == Short.MIN_VALUE
                                || TFCSampleUtils.canonicalMapValue(rawData) == this.selectedTFCMapValue;
                            if (TFCSampleUtils.isWaterValue(rawData))
                            {
                                color = this.waterTexture(rawData, hi);
                            }
                            else if (rawData >= 0 && rawData < this.treeTexPalette.length)
                            {
                                color = hi ? this.treeTexPalette[rawData] : this.treeTexPaletteGray[rawData];
                            }
                            else
                            {
                                color = hi ? this.tfcInvalidTex : this.tfcInvalidTexGray;
                            }
                            break;
                        }
                        case TFC_SOIL_TYPE:
                        {
                            boolean hi = this.selectedTFCMapValue == Short.MIN_VALUE
                                || TFCSampleUtils.canonicalMapValue(rawData) == this.selectedTFCMapValue;
                            if (TFCSampleUtils.isWaterValue(rawData))
                            {
                                color = this.waterTexture(rawData, hi);
                            }
                            else if (rawData >= 0 && rawData < this.soilTexPalette.length)
                            {
                                color = hi ? this.soilTexPalette[rawData] : this.soilTexPaletteGray[rawData];
                            }
                            else
                            {
                                color = hi ? this.tfcInvalidTex : this.tfcInvalidTexGray;
                            }
                            break;
                        }
                        case TFC_CROP_SUITABILITY:
                        {
                            boolean hi = this.selectedTFCMapValue == Short.MIN_VALUE
                                || TFCSampleUtils.canonicalMapValue(rawData) == this.selectedTFCMapValue;
                            if (TFCSampleUtils.isWaterValue(rawData))
                            {
                                color = this.waterTexture(rawData, hi);
                            }
                            else if (rawData >= 0 && rawData < this.cropTexPalette.length)
                            {
                                color = hi ? this.cropTexPalette[rawData] : this.cropTexPaletteGray[rawData];
                            }
                            else
                            {
                                color = hi ? this.tfcInvalidTex : this.tfcInvalidTexGray;
                            }
                            break;
                        }
                        case TFC_HOTSPOT:
                            if (rawData > -32768)
                            {
                                if (rawData > 0)
                                {
                                    color = hotspotAgeToColor(rawData);
                                }
                                else
                                {
                                    short landWater = r.landWaterSection != null ? r.landWaterSection.get(x, z) : TFCRegionWorkUnit.LAND_WATER_LAND;
                                    color = switch (landWater)
                                    {
                                        case TFCRegionWorkUnit.LAND_WATER_OCEAN -> this.tfcOceanTex;
                                        case TFCRegionWorkUnit.LAND_WATER_LAND,
                                             TFCRegionWorkUnit.LAND_WATER_LAKE,
                                             TFCRegionWorkUnit.LAND_WATER_RIVER -> this.tfcLandTex;
                                        case TFCRegionWorkUnit.LAND_WATER_SHORE -> this.tfcShoreTex;
                                        default -> this.tfcUnknownTex;
                                    };
                                }
                            }
                            break;

                    }

                    if (quartExpand > 1)
                    {
                        this.previewImg.fillRect(texX, texZ, Math.min(this.texWidth - texX, quartExpand), Math.min(this.texHeight - texZ, quartExpand), color);
                    }
                    else
                    {
                        this.previewImg.setPixelRGBA(texX, texZ, color);
                    }

                    texZ += quartExpand;
                }

                texX += quartExpand;
            }
        }
    }

    private void renderStructures(List<RenderHelper> renderData, GuiGraphics guiGraphics)
    {
        if (this.config.sampleStructures)
        {
            double guiScale = this.minecraft.getWindow().getGuiScale();

            for (RenderHelper r : renderData)
            {
                for (PreviewSection.PreviewStruct structure : r.structureSection.structures())
                {
                    short id = structure.structureId();
                    if (id < 0 || id >= this.structureIcons.length)
                    {
                        continue; // stale/invalid structure id from cached data
                    }
                    TextureCoordinate texCenter = this.blockToTexture(structure.center());
                    IconData iconData = this.structureIcons[id];
                    NativeImage icon = iconData.img;
                    DynamicTexture iconTexture = iconData.texture;
                    ItemStack item = this.structureItems[id];
                    int xMin = -(icon.getWidth() / 2);
                    int xMax = icon.getWidth() / 2 + 1 + this.texWidth;
                    int zMin = -(icon.getHeight() / 2);
                    int zMax = icon.getHeight() / 2 + 1 + this.texHeight;
                    if (texCenter.x >= xMin && texCenter.z >= zMin && texCenter.x <= xMax && texCenter.z <= zMax)
                    {
                        this.workingVisibleStructures[id]++;
                        if (this.structureRenderInfoMap[id].show() && !this.renderSettings.hideAllStructures)
                        {
                            int texStartX = texCenter.x - icon.getWidth() / 2;
                            int texStartZ = texCenter.z - icon.getHeight() / 2;
                            int rXMin = (int) (texStartX + this.getX() * guiScale);
                            int rZMin = (int) (texStartZ + this.getY() * guiScale);
                            int rXMax = rXMin + icon.getWidth();
                            int rZMax = rZMin + icon.getHeight();
                            if (item != null)
                            {
                                guiGraphics.renderItem(item, rXMin, rZMin);
                            }
                            else
                            {
                                WorldPreviewClient.renderTexture(iconTexture, rXMin, rZMin, rXMax, rZMax);
                            }

                            this.putHoverStructEntry(
                                texCenter,
                                new StructHoverHelperEntry(
                                    new BoundingBox(texStartX, 0, texStartZ, texStartX + icon.getWidth(), 0, texStartZ + icon.getHeight()), structure
                                )
                            );
                        }
                    }
                }
            }
        }
    }

    private void renderFeatures(List<RenderHelper> renderData)
    {
        if (!this.showFeatures || this.featureIcons == null || this.featureIcons.length == 0)
        {
            return;
        }

        double guiScale = this.minecraft.getWindow().getGuiScale();

        for (RenderHelper r : renderData)
        {
            List<PreviewSection.PreviewFeature> features;
            try
            {
                features = r.structureSection.features();
            }
            catch (Exception e)
            {
                continue;
            }
            for (PreviewSection.PreviewFeature feature : features)
            {
                short id = feature.featureTypeId();
                if (id < 0 || id >= this.featureIcons.length || this.featureIcons[id] == null)
                {
                    continue;
                }

                TextureCoordinate texCenter = this.blockToTexture(feature.center());
                IconData iconData = this.featureIcons[id];
                NativeImage icon = iconData.img;
                DynamicTexture iconTexture = iconData.texture;

                int xMin = -(icon.getWidth() / 2);
                int xMax = icon.getWidth() / 2 + 1 + this.texWidth;
                int zMin = -(icon.getHeight() / 2);
                int zMax = icon.getHeight() / 2 + 1 + this.texHeight;

                if (texCenter.x >= xMin && texCenter.z >= zMin && texCenter.x <= xMax && texCenter.z <= zMax)
                {
                    int texStartX = texCenter.x - icon.getWidth() / 2;
                    int texStartZ = texCenter.z - icon.getHeight() / 2;
                    int rXMin = (int) (texStartX + this.getX() * guiScale);
                    int rZMin = (int) (texStartZ + this.getY() * guiScale);
                    int rXMax = rXMin + icon.getWidth();
                    int rZMax = rZMin + icon.getHeight();

                    WorldPreviewClient.renderTexture(iconTexture, rXMin, rZMin, rXMax, rZMax);

                    PreviewSection.PreviewStruct pseudoStruct = new PreviewSection.PreviewStruct(
                        feature.center(),
                        (short) -(id + 1),
                        new BoundingBox(
                            feature.center().getX() - 8, 0, feature.center().getZ() - 8,
                            feature.center().getX() + 8, 0, feature.center().getZ() + 8
                        )
                    );
                    this.putHoverStructEntry(
                        texCenter,
                        new StructHoverHelperEntry(
                            new BoundingBox(texStartX, 0, texStartZ, texStartX + icon.getWidth(), 0, texStartZ + icon.getHeight()),
                            pseudoStruct
                        )
                    );
                }
            }
        }
    }

    private void renderPlayerAndSpawn(GuiGraphics guiGraphics)
    {
        BlockPos worldSpawn = this.dataProvider.getWorldSpawnPos();
        int spawnDistance = this.dataProvider.getWorldSpawnDistance();

        if (worldSpawn != null && spawnDistance > 0)
        {
            this.renderSpawnAreaBox(guiGraphics, worldSpawn, spawnDistance);
        }

        if (worldSpawn != null)
        {
            this.renderStickyIcon(this.worldSpawnIcon, worldSpawn);
        }

        if (this.config.showPlayer)
        {
            PreviewDisplayDataProvider.PlayerData playerData = this.dataProvider.getPlayerData(this.minecraft.getUser().getProfileId());
            if (playerData.currentPos() != null)
            {
                this.renderStickyIcon(this.playerIcon, playerData.currentPos());
            }

            if (playerData.spawnPos() != null)
            {
                this.renderStickyIcon(this.spawnIcon, playerData.spawnPos());
            }
        }
    }

    private void renderSpawnAreaBox(GuiGraphics guiGraphics, BlockPos center, int distance)
    {
        double guiScale = this.minecraft.getWindow().getGuiScale();

        int minX = center.getX() - distance;
        int maxX = center.getX() + distance;
        int minZ = center.getZ() - distance;
        int maxZ = center.getZ() + distance;

        TextureCoordinate topLeft = this.blockToTexture(new BlockPos(minX, 0, minZ));
        TextureCoordinate topRight = this.blockToTexture(new BlockPos(maxX, 0, minZ));
        TextureCoordinate bottomLeft = this.blockToTexture(new BlockPos(minX, 0, maxZ));

        int screenMinX = (int) (topLeft.x + this.getX() * guiScale);
        int screenMaxX = (int) (topRight.x + this.getX() * guiScale);
        int screenMinZ = (int) (topLeft.z + this.getY() * guiScale);
        int screenMaxZ = (int) (bottomLeft.z + this.getY() * guiScale);

        int visMinX = (int) (this.getX() * guiScale);
        int visMaxX = (int) ((this.getX() + this.width) * guiScale);
        int visMinZ = (int) (this.getY() * guiScale);
        int visMaxZ = (int) ((this.getY() + this.height) * guiScale);

        screenMinX = Math.clamp(screenMinX, visMinX, visMaxX);
        screenMaxX = Math.clamp(screenMaxX, visMinX, visMaxX);
        screenMinZ = Math.clamp(screenMinZ, visMinZ, visMaxZ);
        screenMaxZ = Math.clamp(screenMaxZ, visMinZ, visMaxZ);

        int borderColor = 0xAAFFAA00;
        int lineWidth = 2;

        guiGraphics.fill(screenMinX, screenMinZ, screenMaxX, screenMinZ + lineWidth, borderColor);
        guiGraphics.fill(screenMinX, screenMaxZ - lineWidth, screenMaxX, screenMaxZ, borderColor);
        guiGraphics.fill(screenMinX, screenMinZ, screenMinX + lineWidth, screenMaxZ, borderColor);
        guiGraphics.fill(screenMaxX - lineWidth, screenMinZ, screenMaxX, screenMaxZ, borderColor);
    }

    private void renderStickyIcon(IconData iconData, BlockPos pos)
    {
        double guiScale = this.minecraft.getWindow().getGuiScale();
        NativeImage icon = iconData.img;
        TextureCoordinate texCenter = this.blockToTexture(pos);
        texCenter = new TextureCoordinate(Math.clamp(texCenter.x, 0, this.texWidth), Math.clamp(texCenter.z, 0, this.texHeight));
        int texStartX = texCenter.x - icon.getWidth();
        int texStartZ = texCenter.z - icon.getHeight();
        int rXMin = (int) (texStartX + this.getX() * guiScale);
        int rZMin = (int) (texStartZ + this.getY() * guiScale);
        int rXMax = rXMin + icon.getWidth() * 2;
        int rZMax = rZMin + icon.getHeight() * 2;
        WorldPreviewClient.renderTexture(iconData.texture, rXMin, rZMin, rXMax, rZMax);
    }

    private void biomesChanged()
    {
        Short2LongMap tempBiomesSet = new Short2LongOpenHashMap(this.workingVisibleBiomes.length);
        Short2LongMap tempStructuresSet = new Short2LongOpenHashMap(this.workingVisibleStructures.length);

        for (short i = 0; i < this.workingVisibleBiomes.length; i++)
        {
            if (this.workingVisibleBiomes[i] > 0L)
            {
                tempBiomesSet.put(i, this.workingVisibleBiomes[i]);
            }
        }

        for (short ix = 0; ix < this.workingVisibleStructures.length; ix++)
        {
            if (this.workingVisibleStructures[ix] > 0L)
            {
                tempStructuresSet.put(ix, this.workingVisibleStructures[ix]);
            }
        }

        if (!tempBiomesSet.equals(this.visibleBiomes))
        {
            this.dataProvider.onVisibleBiomesChanged(tempBiomesSet);
        }

        if (!tempStructuresSet.equals(this.visibleStructures))
        {
            this.dataProvider.onVisibleStructuresChanged(tempStructuresSet);
        }

        Short2LongMap tempRocksSet = new Short2LongOpenHashMap(this.workingVisibleRocks.length);
        for (short ir = 0; ir < this.workingVisibleRocks.length; ir++)
        {
            if (this.workingVisibleRocks[ir] > 0L)
            {
                tempRocksSet.put(ir, this.workingVisibleRocks[ir]);
            }
        }
        if (!tempRocksSet.equals(this.visibleRocks))
        {
            this.dataProvider.onVisibleRocksChanged(tempRocksSet);
        }

        this.visibleBiomes = tempBiomesSet;
        this.visibleStructures = tempStructuresSet;
        this.visibleRocks = tempRocksSet;
    }

    @Nullable
    private HoverInfo hoveredBiome(double mouseX, double mouseY)
    {
        if (this.isHovered && this.workManager.previewStorage() != null)
        {
            int guiScale = (int) this.minecraft.getWindow().getGuiScale();
            BlockPos center = this.center();
            int xMin = center.getX() - this.texWidth / 2 * this.scaleBlockPos - 1;
            int zMin = center.getZ() - this.texHeight / 2 * this.scaleBlockPos - 1;
            int xPos = (int) ((mouseX - this.getX()) * guiScale * this.scaleBlockPos);
            int zPos = (int) ((mouseY - this.getY()) * guiScale * this.scaleBlockPos);
            int quartX = QuartPos.fromBlock(xMin + xPos);
            int quartZ = QuartPos.fromBlock(zMin + zPos);
            short biome = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 0L);
            short height = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 2L);

            short tfcTempRaw = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_TEMPERATURE.flag);
            short tfcRainRaw = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_RAINFALL.flag);
            short tfcLandWater = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_LAND_WATER.flag);
            short tfcRockTop = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_ROCK_TOP.flag);
            short tfcRockMid = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_ROCK_MID.flag);
            short tfcRockBot = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_ROCK_BOT.flag);
            short tfcRockType = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_ROCK_TYPE.flag);
            short tfcHotspotAge = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_HOTSPOT.flag);
            short tfcForestType = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_FOREST_TYPE.flag);
            short tfcTreeSpecies = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, RenderSettings.RenderMode.TFC_TREE_SPECIES.flag);
            float tfcTemp = tfcTempRaw > -32768 ? TFCSampleUtils.denormalizeTemperature(tfcTempRaw) : Float.NaN;
            float tfcRain = tfcRainRaw > -32768 ? TFCSampleUtils.denormalizeRainfall(tfcRainRaw) : Float.NaN;

            if (biome < 0)
            {
                return new HoverInfo(
                    xMin + xPos, center.getY(), zMin + zPos, null, height, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    tfcTemp, tfcRain, tfcLandWater, tfcRockTop, tfcRockMid, tfcRockBot, tfcRockType, tfcHotspotAge, tfcForestType, tfcTreeSpecies
                );
            }
            else
            {
                short temperature = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 9L);
                short humidity = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 10L);
                short continentalness = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 11L);
                short erosion = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 12L);
                short depth = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 13L);
                short weirdness = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 14L);
                return temperature == -32768 && humidity == -32768 && continentalness == -32768 && erosion == -32768 && depth == -32768 && weirdness == -32768
                    ? new HoverInfo(
                    xMin + xPos,
                    center.getY(),
                    zMin + zPos,
                    this.dataProvider.biome4Id(biome),
                    height,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    tfcTemp,
                    tfcRain,
                    tfcLandWater,
                    tfcRockTop,
                    tfcRockMid,
                    tfcRockBot,
                    tfcRockType,
                    tfcHotspotAge,
                    tfcForestType,
                    tfcTreeSpecies
                )
                    : new HoverInfo(
                    xMin + xPos,
                    center.getY(),
                    zMin + zPos,
                    this.dataProvider.biome4Id(biome),
                    height,
                    temperature / 1.0 / 32767.0,
                    humidity / 1.0 / 32767.0,
                    continentalness / 0.5 / 32767.0,
                    erosion / 1.0 / 32767.0,
                    depth / 0.5 / 32767.0,
                    weirdness / 0.75 / 32767.0,
                    NoiseRouterData.peaksAndValleys(Math.clamp(weirdness / 0.75F / 32767.0F, -1.0F, 1.0F)),
                    tfcTemp,
                    tfcRain,
                    tfcLandWater,
                    tfcRockTop,
                    tfcRockMid,
                    tfcRockBot,
                    tfcRockType,
                    tfcHotspotAge,
                    tfcForestType,
                    tfcTreeSpecies
                );
            }
        }
        else
        {
            return null;
        }
    }

    private List<StructHoverHelperEntry> hoveredStructures(double mouseX, double mouseY)
    {
        if (!this.isHovered)
        {
            return List.of();
        }
        else
        {
            int guiScale = (int) this.minecraft.getWindow().getGuiScale();
            int xTexPos = (int) (mouseX - this.getX()) * guiScale;
            int zTexPos = (int) (mouseY - this.getY()) * guiScale;
            int xGridPos = xTexPos / PreviewSection.SIZE;
            int zGridPos = zTexPos / PreviewSection.SIZE;
            List<StructHoverHelperEntry> res = new ArrayList<>();

            for (int x = xGridPos - 1; x <= xGridPos + 1; x++)
            {
                for (int z = zGridPos - 1; z <= zGridPos + 1; z++)
                {
                    if (x >= 0 && x < this.hoverHelperGridWidth && z >= 0 && z < this.hoverHelperGridHeight)
                    {
                        StructHoverHelperCell cell = this.hoverHelperGrid[x * this.hoverHelperGridHeight + z];

                        for (StructHoverHelperEntry entry : cell.entries)
                        {
                            if (entry.boundingBox.isInside(xTexPos, 0, zTexPos))
                            {
                                res.add(entry);
                            }
                        }
                    }
                }
            }

            return res;
        }
    }

    private static String nameFormatter(String s)
    {
        int idx = s.indexOf(':');
        return idx < 0 ? "§e" + s + "§r" : String.format("§5§o%s§r§5:%s§r", s.substring(0, idx), s.substring(idx + 1));
    }

    private void setTooltipNow(Tooltip tooltip)
    {
        if (this.minecraft.screen == null)
        {
            return;
        }
        this.minecraft.screen.setTooltipForNextRenderPass(tooltip, DefaultTooltipPositioner.INSTANCE, true);
    }

    private void updateTooltip(double mouseX, double mouseY)
    {
        HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
        List<StructHoverHelperEntry> structuresInfos = this.hoveredStructures(mouseX, mouseY);
        if (hoverInfo != null || !structuresInfos.isEmpty())
        {
            String blockPosTemplate = "§3X=§b%d§r §3Y=§b%d§r §3Z=§b%d§r";
            if (!structuresInfos.isEmpty())
            {
                PreviewSection.PreviewStruct structure = structuresInfos.getFirst().structure;
                short structId = structure.structureId();
                String name;
                Component tooltip;

                if (structId < 0)
                {
                    int featureId = -(structId + 1);
                    SearchableFeature feature = this.dataProvider.feature4Id(featureId);
                    name = feature != null ? feature.name().getString() : "Unknown Feature";
                    Component variant = this.dataProvider.featureVariantName(featureId, structure.center());
                    String tooltipKey = variant != null
                        ? "world_preview_tfc.preview-display.feature.variant.tooltip"
                        : "world_preview_tfc.preview-display.feature.tooltip";
                    if (this.config.showControls)
                    {
                        tooltipKey += ".controls";
                    }
                    String center = blockPosTemplate.formatted(
                        structure.center().getX(), structure.center().getY(), structure.center().getZ());
                    tooltip = variant != null
                        ? Component.translatable(tooltipKey, nameFormatter(name), variant, center)
                        : Component.translatable(tooltipKey, nameFormatter(name), center);
                }
                else
                {
                    var structEntry = this.dataProvider.structure4Id(structId);
                    name = structEntry != null ? structEntry.name() : "Unknown Structure";
                    String tooltipKey = this.config.showControls
                        ? "world_preview_tfc.preview-display.struct.tooltip.controls"
                        : "world_preview_tfc.preview-display.struct.tooltip";
                    tooltip = Component.translatable(
                        tooltipKey,
                        nameFormatter(name),
                        blockPosTemplate.formatted(structure.center().getX(), structure.center().getY(), structure.center().getZ()));
                }
                this.setTooltipNow(Tooltip.create(tooltip));
            }
            else
            {
                RenderSettings.RenderMode currentMode = this.renderSettings.mode;
                boolean isTFCMode = currentMode.isTFC();

                if (isTFCMode)
                {
                    String blockPosTemplateNoY = "§3X=§b%d§r §3Z=§b%d§r";
                    StringBuilder tfcInfo = new StringBuilder();
                    tfcInfo.append(blockPosTemplateNoY.formatted(hoverInfo.blockX, hoverInfo.blockZ));

                    switch (currentMode)
                    {
                        case TFC_TEMPERATURE:
                        case TFC_RAINFALL:
                            if (!Float.isNaN(hoverInfo.tfcTemperature))
                            {
                                tfcInfo.append("\n§3Temperature:§r §b%.1f°C§r".formatted(hoverInfo.tfcTemperature));
                            }
                            if (!Float.isNaN(hoverInfo.tfcRainfall))
                            {
                                tfcInfo.append("\n§3Rainfall:§r §b%.0fmm§r".formatted(hoverInfo.tfcRainfall));
                            }
                            if (hoverInfo.tfcLandWater > -32768)
                            {
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                            }
                            break;
                        case TFC_LAND_WATER:
                            if (hoverInfo.tfcLandWater > -32768)
                            {
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                            }
                            if (!Float.isNaN(hoverInfo.tfcTemperature))
                            {
                                tfcInfo.append("\n§3Temperature:§r §b%.1f°C§r".formatted(hoverInfo.tfcTemperature));
                            }
                            if (!Float.isNaN(hoverInfo.tfcRainfall))
                            {
                                tfcInfo.append("\n§3Rainfall:§r §b%.0fmm§r".formatted(hoverInfo.tfcRainfall));
                            }
                            break;
                        case TFC_ROCK_TOP:
                        case TFC_ROCK_MID:
                        case TFC_ROCK_BOT:
                            String layerName = switch (currentMode)
                            {
                                case TFC_ROCK_TOP -> "Surface";
                                case TFC_ROCK_MID -> "Middle";
                                case TFC_ROCK_BOT -> "Bottom";
                                default -> "";
                            };
                            short rockId = switch (currentMode)
                            {
                                case TFC_ROCK_TOP -> hoverInfo.tfcRockTop;
                                case TFC_ROCK_MID -> hoverInfo.tfcRockMid;
                                case TFC_ROCK_BOT -> hoverInfo.tfcRockBot;
                                default -> (short) -1;
                            };
                            if (rockId >= 0)
                            {
                                tfcInfo.append("\n§3%s Rock:§r §b%s§r".formatted(layerName, hoverInfo.getTfcRockName(rockId)));
                            }
                            if (hoverInfo.tfcRockType >= 0)
                            {
                                tfcInfo.append("\n§3Rock Type:§r §b%s§r".formatted(hoverInfo.getTfcRockTypeName()));
                            }
                            if (hoverInfo.tfcLandWater > -32768)
                            {
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                            }
                            break;
                        case TFC_ROCK_TYPE:
                            if (hoverInfo.tfcRockType >= 0)
                            {
                                tfcInfo.append("\n§3Rock Type:§r §b%s§r".formatted(hoverInfo.getTfcRockTypeName()));
                            }
                            if (hoverInfo.tfcLandWater > -32768)
                            {
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                            }
                            break;
                        case TFC_HOTSPOT:
                            if (hoverInfo.tfcLandWater > -32768)
                            {
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                            }
                            if (hoverInfo.tfcHotspotAge > 0)
                            {
                                tfcInfo.append("\n§3Hotspot Age:§r §b%d§r".formatted((int) hoverInfo.tfcHotspotAge));
                            }
                            break;
                        case TFC_FOREST_TYPE:
                            if (hoverInfo.entry != null)
                                tfcInfo.append("\n§3Biome:§r §b%s§r".formatted(hoverInfo.entry.name()));
                            if (TFCSampleUtils.isWaterValue(hoverInfo.tfcForestType))
                            {
                                tfcInfo.append("\n§7Forest Type: None — %s§r".formatted(
                                    TFCSampleUtils.getWaterTypeName(hoverInfo.tfcForestType).toLowerCase()));
                            }
                            else if (hoverInfo.tfcForestType >= 0 && hoverInfo.tfcForestType < TFCSampleUtils.forestTypeCount())
                            {
                                tfcInfo.append("\n§3Forest Type:§r §b%s§r".formatted(hoverInfo.getTfcForestTypeName()));
                                String densityLabel = TFCSampleUtils.getForestDensityLabel(hoverInfo.tfcForestType);
                                if (densityLabel != null)
                                    tfcInfo.append("\n§3Density:§r §b%s§r".formatted(densityLabel));
                            }
                            else
                            {
                                tfcInfo.append("\n§3Forest Type:§r §bUnknown§r");
                            }
                            break;
                        case TFC_TREE_SPECIES:
                            if (hoverInfo.entry != null)
                                tfcInfo.append("\n§3Biome:§r §b%s§r".formatted(hoverInfo.entry.name()));
                            if (TFCSampleUtils.isWaterValue(hoverInfo.tfcTreeSpecies))
                            {
                                tfcInfo.append("\n§7Trees: None — %s§r".formatted(
                                    TFCSampleUtils.getWaterTypeName(hoverInfo.tfcTreeSpecies).toLowerCase()));
                            }
                            else
                            {
                                // Forest type first (matches the biome-map ordering), then tree details.
                                if (hoverInfo.tfcForestType >= 0 && hoverInfo.tfcForestType < TFCSampleUtils.forestTypeCount())
                                {
                                    tfcInfo.append("\n§3Forest Type:§r §b%s§r".formatted(hoverInfo.getTfcForestTypeName()));
                                    String densityLabel = TFCSampleUtils.getForestDensityLabel(hoverInfo.tfcForestType);
                                    if (densityLabel != null)
                                        tfcInfo.append("\n§3Density:§r §b%s§r".formatted(densityLabel));
                                }
                                else
                                {
                                    tfcInfo.append("\n§3Forest Type:§r §bUnknown§r");
                                }

                                String dominantName = hoverInfo.tfcTreeSpecies >= 0 && hoverInfo.tfcTreeSpecies < TFCSampleUtils.treeSpeciesCount()
                                    ? treeSpeciesDisplayName(hoverInfo.tfcTreeSpecies) : "None";
                                tfcInfo.append("\n§3Most Likely Tree:§r §b%s§r".formatted(dominantName));

                                var treeResult = resolvedTreeAt(hoverInfo.blockX, hoverInfo.blockZ);
                                tfcInfo.append("\n§3Possible Trees:§r §b%s§r".formatted(formatPossibleTrees(treeResult.possibleIds())));

                                if (treeResult.sourceConfig() != null)
                                {
                                    tfcInfo.append("\n§3Source:§r §b%s§r".formatted(treeResult.sourceConfig()));
                                }
                            }
                            break;
                        case TFC_SOIL_TYPE:
                            if (hoverInfo.entry != null)
                                tfcInfo.append("\n§3Biome:§r §b%s§r".formatted(hoverInfo.entry.name()));
                            short soil = this.readSoilRawAt(hoverInfo.blockX, hoverInfo.blockZ);
                            if (TFCSampleUtils.isWaterValue(soil))
                            {
                                tfcInfo.append("\n§7Soil Type: None — %s§r".formatted(
                                    TFCSampleUtils.getWaterTypeName(soil).toLowerCase()));
                            }
                            else if (TFCSampleUtils.isSoilTypeValue(soil))
                            {
                                tfcInfo.append("\n§3Soil Type:§r §b%s§r".formatted(TFCSampleUtils.getSoilTypeName(soil)));
                            }
                            else
                            {
                                tfcInfo.append("\n§3Soil Type:§r §bUnknown / No Soil§r");
                            }
                            break;
                        case TFC_CROP_SUITABILITY:
                            this.appendCropTooltip(tfcInfo, hoverInfo);
                            break;
                        default:
                            break;
                    }
                    this.setTooltipNow(Tooltip.create(Component.literal(tfcInfo.toString())));
                }
                else
                {
                    String blockPosTemplateXZ = "§3X=§b%d§r §3Z=§b%d§r";
                    String height = hoverInfo.height > -32768 ? String.format("§b%d§r", hoverInfo.height) : "§7<N/A>§r";
                    String tfcClimate = getTfcClimate(hoverInfo);

                    String formatted = blockPosTemplateXZ.formatted(hoverInfo.blockX, hoverInfo.blockZ);
                    if (this.config.showControls)
                    {
                        this.setTooltipNow(
                            Tooltip.create(
                                Component.translatable(
                                    "world_preview_tfc.preview-display.tooltip.controls",
                                    nameFormatter(hoverInfo.entry == null ? "<N/A>" : hoverInfo.entry.name()),
                                    formatted,
                                    height,
                                    tfcClimate)
                            )
                        );
                    }
                    else
                    {
                        this.setTooltipNow(
                            Tooltip.create(
                                Component.translatable(
                                    "world_preview_tfc.preview-display.tooltip",
                                    nameFormatter(hoverInfo.entry == null ? "<N/A>" : hoverInfo.entry.name()),
                                    formatted,
                                    height,
                                    tfcClimate)
                            )
                        );
                    }
                }
            }
        }
    }

    private static @NotNull String getTfcClimate(HoverInfo hoverInfo)
    {
        String tfcClimate = "";
        if (!Float.isNaN(hoverInfo.tfcTemperature) || !Float.isNaN(hoverInfo.tfcRainfall))
        {
            String tfcTempStr = !Float.isNaN(hoverInfo.tfcTemperature)
                ? String.format("§b%.1f°C§r", hoverInfo.tfcTemperature)
                : "§7N/A§r";
            String tfcRainStr = !Float.isNaN(hoverInfo.tfcRainfall)
                ? String.format("§b%.0fmm§r", hoverInfo.tfcRainfall)
                : "§7N/A§r";
            tfcClimate = "\n\n§6TFC Climate§r\n§3Temp:§r " + tfcTempStr + "  §3Rain:§r " + tfcRainStr;
        }
        return tfcClimate;
    }

    /**
     * Registry display name for a species id, or "Unknown Tree #id" (logged once) if not registered.
     */
    private String treeSpeciesDisplayName(short id)
    {
        if (id >= 0 && id < TFCSampleUtils.treeSpeciesCount())
        {
            return TFCSampleUtils.getTreeSpeciesName(id);
        }
        if (!this.loggedUnknownTreeSpecies)
        {
            this.loggedUnknownTreeSpecies = true;
            WorldPreview.LOGGER.warn("Tree species id {} not in runtime registry (count {})", id, TFCSampleUtils.treeSpeciesCount());
        }
        return "Unknown Tree #" + id;
    }

    /**
     * Comma-joined species names, capped at 5 with a "+N more" suffix to keep the tooltip short.
     */
    private String formatPossibleTrees(List<Short> ids)
    {
        if (ids.isEmpty())
        {
            return "None";
        }
        int shown = Math.min(5, ids.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(treeSpeciesDisplayName(ids.get(i)));
        }
        int more = ids.size() - shown;
        if (more > 0)
        {
            sb.append(" +").append(more).append(" more");
        }
        return sb.toString();
    }

    private com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver.Result resolvedTreeAt(int blockX, int blockZ)
    {
        // Cache per quart position: the dominant/possible set now varies within a chunk (elevation
        // and per-point climate), so a per-chunk cache key would be wrong.
        long quartKey = ((long) (blockX >> 2) << 32) | ((blockZ >> 2) & 0xFFFFFFFFL);
        if (quartKey == this.lastSpeciesChunkKey) return this.cachedTreeResult;
        this.lastSpeciesChunkKey = quartKey;
        try
        {
            this.cachedTreeResult = this.workManager.resolveTreeAt(blockX, blockZ);
        }
        catch (Exception e)
        {
            this.cachedTreeResult = com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCTreeResolver.Result.NONE;
        }
        return this.cachedTreeResult;
    }

    public void playDownSound(@NotNull SoundManager handler)
    {
    }

    public void onClick(double mouseX, double mouseY, int button)
    {
        if (this.minecraft.screen != null)
        {
            this.minecraft.screen.setFocused(this);
        }

        this.clicked = true;
    }

    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY)
    {
        double guiScale = this.minecraft.getWindow().getGuiScale();
        this.totalDragX = this.totalDragX - dragX * guiScale * this.scaleBlockPos;
        this.totalDragZ = this.totalDragZ - dragY * guiScale * this.scaleBlockPos;
    }

    public void onRelease(double mouseX, double mouseY)
    {
        if (this.clicked)
        {
            this.clicked = false;
            if (Math.abs(this.totalDragX) <= 4.0 && Math.abs(this.totalDragZ) <= 4.0)
            {
                HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
                if (hoverInfo != null)
                {
                    switch (this.renderSettings.mode)
                    {
                        case BIOMES:
                            if (hoverInfo.entry != null)
                            {
                                super.playDownSound(this.minecraft.getSoundManager());
                                if (this.selectedBiomeId == hoverInfo.entry.id())
                                {
                                    this.dataProvider.onBiomeVisuallySelected(null);
                                }
                                else
                                {
                                    this.dataProvider.onBiomeVisuallySelected(hoverInfo.entry);
                                }
                            }
                            break;
                        case TFC_FOREST_TYPE:
                            this.handleTFCMapValueClick(RenderSettings.RenderMode.TFC_FOREST_TYPE, hoverInfo.tfcForestType);
                            break;
                        case TFC_TREE_SPECIES:
                            this.handleTFCMapValueClick(RenderSettings.RenderMode.TFC_TREE_SPECIES, hoverInfo.tfcTreeSpecies);
                            break;
                        case TFC_SOIL_TYPE:
                            this.handleTFCMapValueClick(RenderSettings.RenderMode.TFC_SOIL_TYPE, this.readSoilRawAt(hoverInfo.blockX, hoverInfo.blockZ));
                            break;
                        case TFC_CROP_SUITABILITY:
                            this.handleTFCMapValueClick(RenderSettings.RenderMode.TFC_CROP_SUITABILITY, this.readCropRawAt(hoverInfo.blockX, hoverInfo.blockZ));
                            break;
                        default:
                            break;
                    }
                }
            }

            this.renderSettings.setCenter(this.center());
            this.totalDragX = 0.0;
            this.totalDragZ = 0.0;
        }
    }

    /**
     * Reads the stored soil-type value for a block position (soil embeds its own water values).
     */
    private short readSoilRawAt(int blockX, int blockZ)
    {
        return this.workManager.previewStorage().getRawData4(
            QuartPos.fromBlock(blockX), 0, QuartPos.fromBlock(blockZ),
            RenderSettings.RenderMode.TFC_SOIL_TYPE.flag);
    }

    /**
     * Reads the stored crop-suitability value for a block position (embeds its own water values).
     */
    private short readCropRawAt(int blockX, int blockZ)
    {
        return this.workManager.previewStorage().getRawData4(
            QuartPos.fromBlock(blockX), 0, QuartPos.fromBlock(blockZ),
            RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag);
    }

    /**
     * Builds the crop-suitability hover text. Basic lines (crop, stored suitability category, water
     * mode) show immediately from the map's stored value - no computation. Detailed climate lines are
     * only requested after the cursor has settled on the same quart for {@link #CROP_HOVER_DETAIL_MS};
     * the WorkManager then serves them from a bounded LRU cache, so the same quart is never recomputed
     * every frame.
     */
    private void appendCropTooltip(StringBuilder tfcInfo, HoverInfo hoverInfo)
    {
        var cropId = this.workManager.selectedCropId();
        TFCCropRegistry.Entry entry = cropId != null ? this.workManager.cropRegistry().get(cropId) : null;
        if (entry == null)
        {
            tfcInfo.append("\n§3Crop:§r §bNone selected§r");
            return;
        }
        tfcInfo.append("\n§3Crop:§r §b%s§r".formatted(entry.displayName()));

        short raw = this.readCropRawAt(hoverInfo.blockX, hoverInfo.blockZ);
        if (TFCSampleUtils.isWaterValue(raw))
        {
            tfcInfo.append("\n§7Suitability: None — open water§r");
            return;
        }

        String waterMode = this.workManager.cropWaterMode() == TFCCropSuitability.CropWaterMode.IRRIGATED ? "Irrigated" : "Rain-Fed";

        // Basic lines from the stored map value (always available, no compute).
        if (TFCCropSuitability.isSuitabilityValue(raw))
        {
            tfcInfo.append("\n§3Suitability:§r §b%s§r".formatted(TFCCropSuitability.getSuitabilityName(raw)));
        }
        else
        {
            tfcInfo.append("\n§3Suitability:§r §bNo Data§r");
        }
        tfcInfo.append("\n§3Water Mode:§r §b%s§r".formatted(waterMode));
        if (entry.flooded())
        {
            tfcInfo.append("\n§3Special Requirement:§r §bFlooded farmland§r");
        }

        // Debounce: only compute detail once the cursor has rested on this quart.
        int qx = hoverInfo.blockX >> 2;
        int qz = hoverInfo.blockZ >> 2;
        int hoverRevision = this.workManager.cropRevision();
        long now = Util.getMillis();
        if (qx != this.lastCropHoverQuartX || qz != this.lastCropHoverQuartZ
            || hoverRevision != this.lastCropHoverRevision)
        {
            this.lastCropHoverQuartX = qx;
            this.lastCropHoverQuartZ = qz;
            this.lastCropHoverRevision = hoverRevision;
            this.cropHoverSinceMs = now;
        }
        boolean detailReady = (now - this.cropHoverSinceMs) >= CROP_HOVER_DETAIL_MS;

        final var formatted = "\n§3Nutrients:§r §bN %.1f, P %.1f, K %.1f§r".formatted(entry.nitrogen(), entry.phosphorus(), entry.potassium());
        if (!detailReady)
        {
            if (entry.hasClimateData())
            {
                var cr = entry.climateRange();
                tfcInfo.append("\n§3Core Range:§r §b%s, %d–%d hydration§r".formatted(cropTempRange(cr), cr.minHydration(), cr.maxHydration()));
                tfcInfo.append(formatted);
            }
            tfcInfo.append("\n§8Hold to show growing details…§r");
            return;
        }

        TFCCropSuitability.CropSuitabilityResult result = this.workManager.requestCropDetailsAt(hoverInfo.blockX, hoverInfo.blockZ);
        if (result == null)
        {
            if (entry.hasClimateData())
            {
                var cr = entry.climateRange();
                tfcInfo.append("\n§3Core Range:§r §b%s, %d–%d hydration§r".formatted(cropTempRange(cr), cr.minHydration(), cr.maxHydration()));
            }
            tfcInfo.append(formatted);
            tfcInfo.append("\n§8Calculating daily growing details…§r");
            return;
        }
        if (TFCCropSuitability.isSuitabilityValue(result.suitability()))
        {
            tfcInfo.append("\n§3Growing Window:§r §b~%d days§r".formatted(result.growingWindowDays()));
            tfcInfo.append("\n§3Temperature:§r §b%s§r".formatted(cropAxisStatus(result.tooColdSamples(), result.tooHotSamples(), result.samplesPerYear(), "Cold", "Hot")));
            tfcInfo.append("\n§3Hydration:§r §b%s§r".formatted(cropAxisStatus(result.tooDrySamples(), result.tooWetSamples(), result.samplesPerYear(), "Dry", "Wet")));
            tfcInfo.append("\n§3Limiting Factor:§r §b%s§r".formatted(cropLimitingName(result.limitingFactor())));
        }

        if (entry.hasClimateData())
        {
            var cr = entry.climateRange();
            tfcInfo.append("\n§3Core Range:§r §b%s, %d–%d hydration§r".formatted(cropTempRange(cr), cr.minHydration(), cr.maxHydration()));
        }
        tfcInfo.append(formatted);
        if (result.daysInMonth() > 0)
        {
            tfcInfo.append("\n§8Calendar: %d days/month§r".formatted(result.daysInMonth()));
        }
    }

    private static String cropAxisStatus(int lowCount, int highCount, int samplesPerYear, String lowWord, String highWord)
    {
        int n = samplesPerYear > 0 ? samplesPerYear : TFCPreviewClimateSampler.SAMPLES_PER_YEAR;
        if (lowCount == 0 && highCount == 0) return "Suitable";
        if (lowCount >= highCount) return lowCount > n / 2 ? "Too " + lowWord : "Marginal (" + lowWord.toLowerCase() + ")";
        return highCount > n / 2 ? "Too " + highWord : "Marginal (" + highWord.toLowerCase() + ")";
    }

    private static String cropLimitingName(TFCCropSuitability.LimitingFactor lf)
    {
        return switch (lf)
        {
            case NONE -> "None";
            case TOO_COLD, TOO_HOT -> "Temperature";
            case TOO_DRY, TOO_WET -> "Hydration";
            case SHORT_SEASON -> "Season Length";
            case NO_DATA -> "No Data";
            case WATER -> "Water";
        };
    }

    private static String cropTempRange(net.dries007.tfc.util.climate.ClimateRange cr)
    {
        float min = cr.minTemperature();
        float max = cr.maxTemperature();
        boolean noMin = Float.isInfinite(min);
        boolean noMax = Float.isInfinite(max);
        if (noMin && noMax) return "any temp";
        if (noMin) return "≤%.0f°C".formatted(max);
        if (noMax) return "≥%.0f°C".formatted(min);
        return "%.0f–%.0f°C".formatted(min, max);
    }

    private void handleTFCMapValueClick(RenderSettings.RenderMode mode, short rawValue)
    {
        if (rawValue == TFCSampleUtils.VALUE_INVALID)
        {
            return;
        }
        short value;
        if (TFCSampleUtils.isWaterValue(rawValue))
        {
            value = TFCSampleUtils.VALUE_WATER;
        }
        else
        {
            int count = switch (mode)
            {
                case TFC_FOREST_TYPE -> TFCSampleUtils.forestTypeCount();
                case TFC_TREE_SPECIES -> TFCSampleUtils.treeSpeciesCount();
                case TFC_SOIL_TYPE -> TFCSampleUtils.soilTypeCount();
                case TFC_CROP_SUITABILITY -> TFCCropSuitability.suitabilityCount();
                default -> 0;
            };
            if (rawValue < 0 || rawValue >= count)
            {
                return;
            }
            value = rawValue;
        }

        super.playDownSound(this.minecraft.getSoundManager());
        if (this.selectedTFCMapValue == value)
        {
            this.dataProvider.onTFCMapValueVisuallySelected(mode, Short.MIN_VALUE);
        }
        else
        {
            this.dataProvider.onTFCMapValueVisuallySelected(mode, value);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.clicked(mouseX, mouseY) && button == 1)
        {
            this.playDownSound(this.minecraft.getSoundManager());
            HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
            if (hoverInfo != null)
            {
                String coordinates = String.format("%s %s %s", hoverInfo.blockX, hoverInfo.height == -32768 ? "~" : hoverInfo.height, hoverInfo.blockZ);
                this.minecraft.keyboardHandler.setClipboard(coordinates);
                this.coordinatesCopiedTime = Instant.now();
                this.coordinatesCopiedMsg = Component.translatable("world_preview_tfc.preview-display.coordinates.copied", coordinates);
            }
            return true;
        }
        else
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public void setOverlayMessage(@Nullable Component message)
    {
        this.coordinatesCopiedMsg = message;
        this.coordinatesCopiedTime = message != null ? Instant.now() : null;
    }

    private int waterTexture(short value, boolean highlighted)
    {
        return switch (value)
        {
            case TFCSampleUtils.VALUE_WATER_LAKE -> highlighted ? this.tfcLakeTex : this.tfcLakeTexGray;
            case TFCSampleUtils.VALUE_WATER_RIVER -> highlighted ? this.tfcRiverTex : this.tfcRiverTexGray;
            default -> highlighted ? this.tfcOceanTex : this.tfcOceanTexGray;
        };
    }

    private int regionWaterTexture(short value)
    {
        return switch (value)
        {
            case TFCRegionWorkUnit.LAND_WATER_OCEAN -> this.tfcOceanTex;
            case TFCRegionWorkUnit.LAND_WATER_SHORE -> this.tfcShoreTex;
            case TFCRegionWorkUnit.LAND_WATER_LAKE -> this.tfcLakeTex;
            case TFCRegionWorkUnit.LAND_WATER_RIVER -> this.tfcRiverTex;
            case TFCRegionWorkUnit.LAND_WATER_LAND -> this.tfcLandTex;
            default -> this.tfcUnknownTex;
        };
    }

    private static int hotspotAgeToColor(short age)
    {
        return switch (age)
        {
            case 1 -> 0xFF0000FF; // Red
            case 2 -> 0xFF0080FF; // Orange
            case 3 -> 0xFF00FFFF; // Yellow
            case 4 -> 0xFF8000FF; // Purple
            default -> 0xFF9900FF;
        };
    }

    private static int textureColor(int orig)
    {
        int R = orig >> 16 & 0xFF;
        int G = orig >> 8 & 0xFF;
        int B = orig & 0xFF;
        return R | G << 8 | B << 16 | 0xFF000000;
    }

    private static int grayScale(int orig)
    {
        int R = orig >> 16 & 0xFF;
        int G = orig >> 8 & 0xFF;
        int B = orig & 0xFF;
        int gray = Math.clamp((R + G + B) / 3, 32, 224);
        return 0xFF000000 | gray << 16 | gray << 8 | gray;
    }

    public void setSelectedBiomeId(short biomeId)
    {
        this.selectedBiomeId = biomeId;
    }

    public void setSelectedRockId(short rockId)
    {
        this.selectedRockId = rockId;
    }

    public void setSelectedTFCMapValue(short value)
    {
        this.selectedTFCMapValue = value;
    }

    public void setHighlightCaves(boolean highlightCaves)
    {
        this.highlightCaves = highlightCaves;
    }

    public void setShowFeatures(boolean showFeatures)
    {
        this.showFeatures = showFeatures;
    }

    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput)
    {
    }

    private record HoverInfo(
        int blockX,
        int blockY,
        int blockZ,
        @Nullable BiomesList.BiomeEntry entry,
        short height,
        double temperature,
        double humidity,
        double continentalness,
        double erosion,
        double depth,
        double weirdness,
        double pv,
        float tfcTemperature,
        float tfcRainfall,
        short tfcLandWater,
        short tfcRockTop,
        short tfcRockMid,
        short tfcRockBot,
        short tfcRockType,
        short tfcHotspotAge,
        short tfcForestType,
        short tfcTreeSpecies
    )
    {
        public String getTfcForestTypeName()
        {
            return TFCSampleUtils.getForestTypeName(tfcForestType);
        }

        public String getTfcTreeSpeciesName()
        {
            return TFCSampleUtils.getTreeSpeciesName(tfcTreeSpecies);
        }

        public String getTfcLandWaterName()
        {
            return switch (tfcLandWater)
            {
                case TFCRegionWorkUnit.LAND_WATER_OCEAN -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_OCEAN, "Ocean");
                case TFCRegionWorkUnit.LAND_WATER_LAND -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_LAND, "Land");
                case TFCRegionWorkUnit.LAND_WATER_SHORE -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_SHORE, "Shore");
                case TFCRegionWorkUnit.LAND_WATER_LAKE -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_LAKE, "Lake");
                case TFCRegionWorkUnit.LAND_WATER_RIVER -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_RIVER, "River");
                default -> TFCSampleUtils.getWaterName(TFCSampleUtils.WATER_UNKNOWN, "Unknown");
            };
        }

        public String getTfcRockName(short rockId)
        {
            return TFCSampleUtils.getRockName(rockId);
        }

        public String getTfcRockTypeName()
        {
            return TFCSampleUtils.getRockTypeName(tfcRockType);
        }
    }

    private record IconData(@NotNull NativeImage img, @NotNull DynamicTexture texture)
    {
        public void close()
        {
            this.texture.close();
            this.img.close();
        }
    }

    private record RenderHelper(
        PreviewSection dataSection, PreviewSection structureSection, @Nullable PreviewSection landWaterSection,
        PreviewSection.AccessData accessData, int sectionStartTexX, int sectionStartTexZ
    )
    {
    }

    private record StructHoverHelperCell(List<StructHoverHelperEntry> entries)
    {
    }

    private record StructHoverHelperEntry(BoundingBox boundingBox, PreviewSection.PreviewStruct structure)
    {
    }

    private record TextureCoordinate(int x, int z)
    {
    }

}
