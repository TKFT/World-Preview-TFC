package com.rustysnail.world.preview.tfc.client.gui.widgets;

import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.WorkManager;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCRegionWorkUnit;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;
import com.rustysnail.world.preview.tfc.client.WorldPreviewClient;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.PreviewDisplayDataProvider;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomesList;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
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
    private IconData[] structureIcons;
    private IconData[] featureIcons;
    private IconData playerIcon;
    private IconData spawnIcon;
    private IconData worldSpawnIcon;
    private ItemStack[] structureItems;
    private PreviewDisplayDataProvider.StructureRenderInfo[] structureRenderInfoMap;
    private boolean showFeatures = false;
    private Component coordinatesCopiedMsg = null;
    private Instant coordinatesCopiedTime = null;
    private int texWidth = 100;
    private int texHeight = 100;
    private short selectedBiomeId;
    private short selectedRockId;
    private boolean highlightCaves;
    private double totalDragX = 0.0;
    private double totalDragZ = 0.0;
    private int scaleBlockPos = 1;
    private StructHoverHelperCell[] hoverHelperGrid;
    private int hoverHelperGridWidth;
    private int hoverHelperGridHeight;
    private final Queue<Long> frametimes = new ArrayDeque<>();
    private boolean clicked = false;

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
            }
            else
            {
                Arrays.fill(this.workingVisibleBiomes, 0L);
                Arrays.fill(this.workingVisibleStructures, 0L);
                Arrays.fill(this.workingVisibleRocks, 0L);
                Arrays.stream(this.hoverHelperGrid).forEach(cell -> cell.entries.clear());
                List<RenderHelper> renderData = this.generateRenderData();
                this.updateTexture(renderData);
                this.previewTexture.upload();
                WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
                guiGraphics.enableScissor(xMin, yMin, xMax, yMax);
                Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, (float) winWidth, (float) winHeight, 0.0F, 1000.0F, 21000.0F);
                RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
                this.renderStructures(renderData, guiGraphics);
                this.renderFeatures(renderData, guiGraphics);
                this.renderPlayerAndSpawn(guiGraphics);
                matrix4f = new Matrix4f().setOrtho(0.0F, (float) (winWidth / guiScale), (float) (winHeight / guiScale), 0.0F, 1000.0F, 21000.0F);
                RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
                guiGraphics.disableScissor();
                double mouseX = this.minecraft.mouseHandler.xpos() * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                double mouseZ = this.minecraft.mouseHandler.ypos() * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                this.biomesChanged();
                this.updateTooltip(mouseX, mouseZ);
            }
        }

        guiGraphics.fill(xMin - 1, yMin - 1, xMax + 1, yMin, -10066330);
        guiGraphics.fill(xMax, yMin, xMax + 1, yMax, -10066330);
        guiGraphics.fill(xMin - 1, yMax, xMax + 1, yMax + 1, -10066330);
        guiGraphics.fill(xMin - 1, yMin, xMin, yMax, -10066330);
        if (this.coordinatesCopiedMsg != null)
        {
            guiGraphics.fill(xMin, yMax - 38, xMax, yMax - 19, -1442840576);
            guiGraphics.drawCenteredString(this.minecraft.font, this.coordinatesCopiedMsg, xMin + (xMax - xMin) / 2, yMax - 32, 16777215);
            if (Duration.between(this.coordinatesCopiedTime, Instant.now()).toSeconds() >= 8L)
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
        int cellX = Math.max(0, Math.min(this.hoverHelperGridWidth - 1, pos.x / PreviewSection.SIZE));
        int cellZ = Math.max(0, Math.min(this.hoverHelperGridHeight - 1, pos.z / PreviewSection.SIZE));
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
                PreviewSection.AccessData accessData = dataSection.calcQuartOffsetData(quartX, quartZ, maxQuartX, maxQuartZ);
                res.add(new RenderHelper(dataSection, structureSection, accessData, sectionStartTexX, sectionStartTexZ));
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
                            if (rawData >= 0)
                            {
                                color = this.selectedBiomeId < 0 && !this.highlightCaves ? this.colorMap[rawData] : this.colorMapGrayScale[rawData];
                                if (this.selectedBiomeId == rawData || this.highlightCaves && this.cavesMap[rawData])
                                {
                                    color = this.colorMap[rawData];
                                }

                                this.workingVisibleBiomes[rawData]++;
                            }
                            break;
                        case HEIGHTMAP:
                            if (rawData > -32768)
                            {
                                color = this.heightColorMap[rawData - this.dataProvider.yMin()];
                            }
                            break;
                        case TFC_TEMPERATURE:
                            if (rawData > -32768 && this.tfcTemperatureColorMap != null)
                            {
                                int quartXCoord = r.dataSection.quartX() + x;
                                int quartZCoord = r.dataSection.quartZ() + z;
                                short landWater = this.workManager.previewStorage().getRawData4(quartXCoord, 0, quartZCoord, RenderSettings.RenderMode.TFC_LAND_WATER.flag);
                                if (landWater == TFCRegionWorkUnit.LAND_WATER_OCEAN)
                                {
                                    color = 0xFFAA4422;  // Blue
                                }
                                else
                                {
                                    float normalized = (rawData + 32768.0F) / 65535.0F;
                                    int idx = Math.min(1023, Math.max(0, (int) (normalized * 1023.0F)));
                                    color = this.tfcTemperatureColorMap[idx];
                                }
                            }
                            break;
                        case TFC_RAINFALL:
                            if (rawData > -32768 && this.tfcRainfallColorMap != null)
                            {
                                int quartXCoord = r.dataSection.quartX() + x;
                                int quartZCoord = r.dataSection.quartZ() + z;
                                short landWater = this.workManager.previewStorage().getRawData4(quartXCoord, 0, quartZCoord, RenderSettings.RenderMode.TFC_LAND_WATER.flag);
                                if (landWater == TFCRegionWorkUnit.LAND_WATER_OCEAN)
                                {
                                    color = 0xFFAA4422;  // Blue
                                }
                                else
                                {
                                    float normalized = rawData / 32767.0F;
                                    int idx = Math.min(1023, Math.max(0, (int) (normalized * 1023.0F)));
                                    color = this.tfcRainfallColorMap[idx];
                                }
                            }
                            break;
                        case TFC_LAND_WATER:
                            if (rawData > -32768)
                            {
                                color = switch (rawData)
                                {
                                    case TFCRegionWorkUnit.LAND_WATER_OCEAN -> 0xFF8B0000;  // Ocean - Dark Blue
                                    case TFCRegionWorkUnit.LAND_WATER_LAND -> 0xFF44AA44;   // Land - Green
                                    case TFCRegionWorkUnit.LAND_WATER_SHORE -> 0xFF8CB4D2; // Shore - Tan
                                    case TFCRegionWorkUnit.LAND_WATER_LAKE -> 0xFFFF0000;   // Lake - Med Blue
                                    case TFCRegionWorkUnit.LAND_WATER_RIVER -> 0xFFFFD7AD;  // River - Light Blue
                                    default -> 0xFF000000;
                                };
                            }
                            break;
                        case TFC_ROCK_TOP:
                        case TFC_ROCK_MID:
                        case TFC_ROCK_BOT:
                            {
                                int quartXCoord = r.dataSection.quartX() + x;
                                int quartZCoord = r.dataSection.quartZ() + z;
                                short landWater = this.workManager.previewStorage().getRawData4(
                                    quartXCoord, 0, quartZCoord, RenderSettings.RenderMode.TFC_LAND_WATER.flag
                                );
                                if (landWater == TFCRegionWorkUnit.LAND_WATER_OCEAN)
                                {
                                    color = 0xFF8B0000;  // Ocean - Dark Blue
                                    break;
                                }
                            }
                            if (rawData >= 0 && rawData < TFCSampleUtils.ROCK_COLORS.length)
                            {
                                this.workingVisibleRocks[rawData]++;
                                int argbColor = TFCSampleUtils.getRockColor(rawData);
                                int alpha = (argbColor >> 24) & 0xFF;
                                int red = (argbColor >> 16) & 0xFF;
                                int green = (argbColor >> 8) & 0xFF;
                                int blue = argbColor & 0xFF;
                                RenderSettings.RenderMode currentMode = this.renderSettings.mode;
                                int layerShift = switch (currentMode)
                                {
                                    case TFC_ROCK_TOP -> 0;    // Full brightness
                                    case TFC_ROCK_MID -> 20;   // Slightly darker
                                    case TFC_ROCK_BOT -> 40;   // Darker
                                    default -> 0;
                                };
                                red = Math.max(0, red - layerShift);
                                green = Math.max(0, green - layerShift);
                                blue = Math.max(0, blue - layerShift);
                                color = this.applyRockSelectionTint(rawData, alpha, red, green, blue);
                            }
                            else if (rawData == -1)
                            {
                                color = 0xFF888888;
                            }
                            break;
                        case TFC_ROCK_TYPE:
                            if (rawData >= 0 && rawData < TFCSampleUtils.ROCK_TYPE_COLORS.length)
                            {
                                this.workingVisibleRocks[rawData]++;
                                int argbColor = TFCSampleUtils.getRockTypeColor(rawData);
                                int alpha = (argbColor >> 24) & 0xFF;
                                int red = (argbColor >> 16) & 0xFF;
                                int green = (argbColor >> 8) & 0xFF;
                                int blue = argbColor & 0xFF;
                                color = this.applyRockSelectionTint(rawData, alpha, red, green, blue);
                            }
                            break;
                        case TFC_KAOLINITE:
                            if (rawData > -32768)
                            {
                                color = switch (rawData)
                                {
                                    case 0 -> 0xFFFF0000; // water blue
                                    case 1 -> 0xFF00CC00; // land green
                                    case 2 -> 0xFFFF66FF; // kaolin pink
                                    default -> 0xFF000001; // background
                                };
                            }
                            break;
                        case TFC_HOTSPOT:
                            if (rawData > -32768)
                            {
                                if (rawData > 0)
                                {
                                    color = hotspotAgeToColor(rawData);
                                }
                                else
                                {
                                    int quartXCoord = r.dataSection.quartX() + x;
                                    int quartZCoord = r.dataSection.quartZ() + z;
                                    short landWater = this.workManager.previewStorage().getRawData4(quartXCoord, 0, quartZCoord, RenderSettings.RenderMode.TFC_LAND_WATER.flag);
                                    color = switch (landWater)
                                    {
                                        case TFCRegionWorkUnit.LAND_WATER_OCEAN -> 0xFF8B0000;  // Dark blue
                                        case TFCRegionWorkUnit.LAND_WATER_LAND -> 0xFF44AA44;   // Green
                                        case TFCRegionWorkUnit.LAND_WATER_SHORE -> 0xFF8CB4D2;  // Tan
                                        case TFCRegionWorkUnit.LAND_WATER_LAKE -> 0xFFFF0000;   // Blue
                                        case TFCRegionWorkUnit.LAND_WATER_RIVER -> 0xFF44AA44;  // Treat rivers as land
                                        default -> 0xFF000000;
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

    private void renderFeatures(List<RenderHelper> renderData, GuiGraphics guiGraphics)
    {
        if (this.featureIcons == null || this.featureIcons.length == 0 || this.structureRenderInfoMap == null)
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

                int structureIndex = this.dataProvider.placedFeatureStructureIndex(id);
                boolean mappedToStructure = structureIndex >= 0
                    && structureIndex < this.workingVisibleStructures.length
                    && structureIndex < this.structureRenderInfoMap.length;

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
                    if (mappedToStructure)
                    {
                        this.workingVisibleStructures[structureIndex]++;
                    }

                    boolean renderAsStructure = mappedToStructure
                        && !this.renderSettings.hideAllStructures
                        && this.structureRenderInfoMap[structureIndex].show();
                    boolean renderAsFeature = !mappedToStructure && this.showFeatures;
                    if (!renderAsStructure && !renderAsFeature)
                    {
                        continue;
                    }

                    int texStartX = texCenter.x - icon.getWidth() / 2;
                    int texStartZ = texCenter.z - icon.getHeight() / 2;
                    int rXMin = (int) (texStartX + this.getX() * guiScale);
                    int rZMin = (int) (texStartZ + this.getY() * guiScale);
                    int rXMax = rXMin + icon.getWidth();
                    int rZMax = rZMin + icon.getHeight();

                    WorldPreviewClient.renderTexture(iconTexture, rXMin, rZMin, rXMax, rZMax);

                    PreviewSection.PreviewStruct pseudoStruct = new PreviewSection.PreviewStruct(
                        feature.center(),
                        mappedToStructure ? (short) structureIndex : (short) -(id + 1),
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

        screenMinX = Math.max(visMinX, Math.min(visMaxX, screenMinX));
        screenMaxX = Math.max(visMinX, Math.min(visMaxX, screenMaxX));
        screenMinZ = Math.max(visMinZ, Math.min(visMaxZ, screenMinZ));
        screenMaxZ = Math.max(visMinZ, Math.min(visMaxZ, screenMaxZ));

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
        texCenter = new TextureCoordinate(Math.max(0, Math.min(this.texWidth, texCenter.x)), Math.max(0, Math.min(this.texHeight, texCenter.z)));
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
            float tfcTemp = tfcTempRaw > -32768 ? TFCSampleUtils.denormalizeTemperature(tfcTempRaw) : Float.NaN;
            float tfcRain = tfcRainRaw > -32768 ? TFCSampleUtils.denormalizeRainfall(tfcRainRaw) : Float.NaN;

            if (biome < 0)
            {
                return new HoverInfo(
                    xMin + xPos, center.getY(), zMin + zPos, null, height, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    tfcTemp, tfcRain, tfcLandWater, tfcRockTop, tfcRockMid, tfcRockBot, tfcRockType, tfcHotspotAge
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
                    tfcHotspotAge
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
                    NoiseRouterData.peaksAndValleys(Math.min(1.0F, Math.max(-1.0F, weirdness / 0.75F / 32767.0F))),
                    tfcTemp,
                    tfcRain,
                    tfcLandWater,
                    tfcRockTop,
                    tfcRockMid,
                    tfcRockBot,
                    tfcRockType,
                    tfcHotspotAge
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
        assert this.minecraft.screen != null;
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

                if (structId < 0)
                {
                    int featureId = -(structId + 1);
                    SearchableFeature feature = this.dataProvider.feature4Id(featureId);
                    name = feature != null ? feature.name().getString() : "Unknown Feature";
                }
                else
                {
                    name = this.dataProvider.structure4Id(structId).name();
                }

                if (this.config.showControls)
                {
                    this.setTooltipNow(
                        Tooltip.create(
                            Component.translatable(
                                "world_preview_tfc.preview-display.struct.tooltip.controls",
                                nameFormatter(name),
                                blockPosTemplate.formatted(structure.center().getX(), structure.center().getY(), structure.center().getZ()))
                        )
                    );
                }
                else
                {
                    this.setTooltipNow(
                        Tooltip.create(
                            Component.translatable(
                                "world_preview_tfc.preview-display.struct.tooltip",
                                nameFormatter(name),
                                blockPosTemplate.formatted(structure.center().getX(), structure.center().getY(), structure.center().getZ()))
                        )
                    );
                }
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
                                // Rivers are displayed as land in this mode
                                String terrainName = hoverInfo.tfcLandWater == TFCRegionWorkUnit.LAND_WATER_RIVER
                                    ? "Land" : hoverInfo.getTfcLandWaterName();
                                tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(terrainName));
                            }
                            if (hoverInfo.tfcHotspotAge > 0)
                            {
                                tfcInfo.append("\n§3Hotspot Age:§r §b%d§r".formatted((int) hoverInfo.tfcHotspotAge));
                            }
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
                if (hoverInfo == null || hoverInfo.entry == null)
                {
                    return;
                }

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

            this.renderSettings.setCenter(this.center());
            this.totalDragX = 0.0;
            this.totalDragZ = 0.0;
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
        int gray = Math.max(32, Math.min(224, (R + G + B) / 3));
        return 0xFF000000 | gray << 16 | gray << 8 | gray;
    }

    private int applyRockSelectionTint(short rawData, int alpha, int red, int green, int blue)
    {
        if (this.selectedRockId >= 0)
        {
            if (rawData != this.selectedRockId)
            {
                int gray = (red * 30 + green * 59 + blue * 11) / 100;
                red = gray;
                green = gray;
                blue = gray;
            }
            else
            {
                red = Math.min(255, red + 40);
                green = Math.min(255, green + 40);
                blue = Math.min(255, blue + 40);
            }
        }
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    public void setSelectedBiomeId(short biomeId)
    {
        this.selectedBiomeId = biomeId;
    }

    public void setSelectedRockId(short rockId)
    {
        this.selectedRockId = rockId;
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
        BiomesList.BiomeEntry entry,
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
        short tfcHotspotAge
    )
    {
        public String getTfcLandWaterName()
        {
            return switch (tfcLandWater)
            {
                case TFCRegionWorkUnit.LAND_WATER_OCEAN -> "Ocean";
                case TFCRegionWorkUnit.LAND_WATER_LAND -> "Land";
                case TFCRegionWorkUnit.LAND_WATER_SHORE -> "Shore";
                case TFCRegionWorkUnit.LAND_WATER_LAKE -> "Lake";
                case TFCRegionWorkUnit.LAND_WATER_RIVER -> "River";
                default -> "Unknown";
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
        PreviewSection dataSection, PreviewSection structureSection, PreviewSection.AccessData accessData, int sectionStartTexX, int sectionStartTexZ
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
