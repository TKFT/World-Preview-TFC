package caeruleustait.world.preview.client.gui.widgets;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.WorkManager;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.storage.PreviewSection;
import caeruleustait.world.preview.backend.storage.PreviewStorage;
import caeruleustait.world.preview.backend.worker.tfc.TFCRegionWorkUnit;
import caeruleustait.world.preview.backend.worker.tfc.TFCSampleUtils;
import caeruleustait.world.preview.client.WorldPreviewClient;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.PreviewDisplayDataProvider;
import caeruleustait.world.preview.client.gui.widgets.lists.BiomesList;
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

public class PreviewDisplay extends AbstractWidget implements AutoCloseable {
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
   private IconData playerIcon;
   private IconData spawnIcon;
   private IconData worldSpawnIcon;
   private ItemStack[] structureItems;
   private PreviewDisplayDataProvider.StructureRenderInfo[] structureRenderInfoMap;
   //private final NativeImage dummyIcon;
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
   //private final int hoverHelperGridCellSize = 64;
   private int hoverHelperGridWidth;
   private int hoverHelperGridHeight;
   private final Queue<Long> frametimes = new ArrayDeque<>();
   private boolean clicked = false;

   public PreviewDisplay(Minecraft minecraft, PreviewDisplayDataProvider dataProvider, Component component) {
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
      //this.dummyIcon = new NativeImage(16, 16, true);
      this.structureIcons = new IconData[0];
      this.resizeImage();
   }

   public void resizeImage() {
      this.closeDisplayTextures();
      this.previewImg = new NativeImage(Format.RGBA, this.texWidth, this.texHeight, true);
      this.previewTexture = new DynamicTexture(this.previewImg);
      // Base scale (blocks-per-pixel) from quartExpand/quartStride.
      // Additional downsampling from chunksPerPixel lets the map cover larger areas.
      //this.scaleBlockPos = (4 / this.renderSettings.quartExpand() * this.renderSettings.quartStride()) * this.renderSettings.chunksPerPixel();
      this.hoverHelperGridWidth = this.texWidth / 64 + 1;
      this.hoverHelperGridHeight = this.texHeight / 64 + 1;

      this.scaleBlockPos = 4 / this.renderSettings.quartExpand() * this.renderSettings.quartStride();
      this.hoverHelperGridWidth = this.texWidth / PreviewSection.SIZE + 1;
      this.hoverHelperGridHeight = this.texHeight / PreviewSection.SIZE + 1;

      this.hoverHelperGrid = new StructHoverHelperCell[this.hoverHelperGridWidth * this.hoverHelperGridHeight];

      for (int i = 0; i < this.hoverHelperGrid.length; i++) {
         this.hoverHelperGrid[i] = new StructHoverHelperCell(new ArrayList<>());
      }
   }

   public void setSize(int width, int height) {
      this.width = width;
      this.height = height;
      this.texWidth = this.width * (int)this.minecraft.getWindow().getGuiScale();
      this.texHeight = this.height * (int)this.minecraft.getWindow().getGuiScale();
      this.resizeImage();
   }

   public void reloadData() {
      this.closeIconTextures();
      PreviewData.BiomeData[] rawBiomeMap = this.dataProvider.previewData().biomeId2BiomeData();
      this.structureRenderInfoMap = this.dataProvider.renderStructureMap();
      this.structureItems = this.dataProvider.structureItems();
      this.structureIcons = Arrays.stream(this.dataProvider.structureIcons())
         .map(x -> new IconData(x, new DynamicTexture(x)))
         .toArray(IconData[]::new);
      this.playerIcon = new IconData(this.dataProvider.playerIcon(), new DynamicTexture(this.dataProvider.playerIcon()));
      this.spawnIcon = new IconData(this.dataProvider.spawnIcon(), new DynamicTexture(this.dataProvider.spawnIcon()));
      this.worldSpawnIcon = new IconData(this.dataProvider.worldSpawnIcon(), new DynamicTexture(this.dataProvider.worldSpawnIcon()));
      this.playerIcon.texture.upload();
      this.spawnIcon.texture.upload();
      this.worldSpawnIcon.texture.upload();
      Arrays.stream(this.structureIcons).map(IconData::texture).forEach(DynamicTexture::upload);

      try {
         this.heightColorMap = this.dataProvider.heightColorMap();
         this.tfcTemperatureColorMap = this.dataProvider.tfcTemperatureColorMap();
         this.tfcRainfallColorMap = this.dataProvider.tfcRainfallColorMap();
      } catch (Throwable var3) {
         WorldPreview.LOGGER.error("Error initializing PreviewDisplay color maps", var3);
      }

      this.workingVisibleBiomes = new long[rawBiomeMap.length];
      this.workingVisibleStructures = new long[this.structureIcons.length];
      this.colorMap = new int[rawBiomeMap.length];
      this.colorMapGrayScale = new int[rawBiomeMap.length];
      this.cavesMap = new boolean[rawBiomeMap.length];

      for (short i = 0; i < rawBiomeMap.length; i++) {
         this.colorMap[i] = textureColor(rawBiomeMap[i].color());
         this.colorMapGrayScale[i] = grayScale(this.colorMap[i]);
         this.cavesMap[i] = rawBiomeMap[i].isCave();
      }
   }

   private void closeIconTextures() {
      if (this.structureIcons != null) {
         Arrays.stream(this.structureIcons).forEach(IconData::close);
      }

      if (this.playerIcon != null) {
         this.playerIcon.texture.close();
      }

      if (this.spawnIcon != null) {
         this.spawnIcon.texture.close();
      }

      if (this.worldSpawnIcon != null) {
         this.worldSpawnIcon.texture.close();
      }
   }

   private void closeDisplayTextures() {
      if (this.previewTexture != null) {
         this.previewTexture.close();
      }

      if (this.previewImg != null) {
         this.previewImg.close();
      }
   }

   @Override
   public void close() {
      this.closeIconTextures();
      this.closeDisplayTextures();
   }

   public BlockPos center() {
      return this.totalDragX == 0.0 && this.totalDragZ == 0.0
         ? this.renderSettings.center()
         : new BlockPos(
            (int)(this.renderSettings.center().getX() + this.totalDragX),
            this.renderSettings.center().getY(),
            (int)(this.renderSettings.center().getZ() + this.totalDragZ)
         );
   }

   public void renderWidget(@NotNull GuiGraphics guiGraphics, int x, int y, float f) {
      //int colorBorder = -10066330;
      int xMin = this.getX();
      int yMin = this.getY();
      int xMax = xMin + this.width;
      int yMax = yMin + this.height;
      double winWidth = this.minecraft.getWindow().getWidth();
      double winHeight = this.minecraft.getWindow().getHeight();
      double guiScale = this.minecraft.getWindow().getGuiScale();
      Instant renderStart = Instant.now();
      this.queueGeneration();
      synchronized (this.dataProvider) {
         if (this.dataProvider.setupFailed()) {
            this.previewImg.fillRect(0, 0, this.texWidth, this.texHeight, -16777216);
            this.previewTexture.upload();
            WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
            List<MutableComponent> lines = WorldPreviewComponents.MSG_ERROR_SETUP_FAILED.getString().lines().map(Component::literal).toList();
            int centerX = this.getX() + this.width / 2;
            int centerY = this.getY() + this.height / 2 - lines.size() / 2 * (9 + 4);

            for (int i = 0; i < lines.size(); i++) {
               Component line = lines.get(i);
               int offsetY = i * (9 + 4);
               guiGraphics.drawCenteredString(this.minecraft.font, line, centerX, centerY + offsetY, 16777215);
            }
         } else if (this.dataProvider.isUpdating()) {
            this.previewImg.fillRect(0, 0, this.texWidth, this.texHeight, -16777216);
            this.previewTexture.upload();
            WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
            int centerX = this.getX() + this.width / 2;
            int centerY = this.getY() + this.height / 2;
            guiGraphics.drawCenteredString(this.minecraft.font, WorldPreviewComponents.MSG_PREVIEW_SETUP_LOADING, centerX, centerY, 16777215);
         } else {
            Arrays.fill(this.workingVisibleBiomes, 0L);
            Arrays.fill(this.workingVisibleStructures, 0L);
            Arrays.fill(this.workingVisibleRocks, 0L);
            Arrays.stream(this.hoverHelperGrid).forEach(cell -> cell.entries.clear());
            List<RenderHelper> renderData = this.generateRenderData();
            this.updateTexture(renderData);
            this.previewTexture.upload();
            WorldPreviewClient.renderTexture(this.previewTexture, xMin, yMin, xMax, yMax);
            guiGraphics.enableScissor(xMin, yMin, xMax, yMax);
            Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, (float)winWidth, (float)winHeight, 0.0F, 1000.0F, 21000.0F);
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
            this.renderStructures(renderData, guiGraphics);
            this.renderPlayerAndSpawn(guiGraphics);
            matrix4f = new Matrix4f().setOrtho(0.0F, (float)(winWidth / guiScale), (float)(winHeight / guiScale), 0.0F, 1000.0F, 21000.0F);
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
      if (this.coordinatesCopiedMsg != null) {
         guiGraphics.fill(xMin, yMax - 38, xMax, yMax - 19, -1442840576);
         guiGraphics.drawCenteredString(this.minecraft.font, this.coordinatesCopiedMsg, xMin + (xMax - xMin) / 2, yMax - 32, 16777215);
         if (Duration.between(this.coordinatesCopiedTime, Instant.now()).toSeconds() >= 8L) {
            this.coordinatesCopiedMsg = null;
            this.coordinatesCopiedTime = null;
         }
      }

      Instant renderEnd = Instant.now();
      this.frametimes.add(Duration.between(renderStart, renderEnd).abs().toMillis());

      while (this.frametimes.size() > 30) {
         this.frametimes.poll();
      }

      long sum = this.frametimes.stream().reduce(0L, Long::sum);
      if (this.config.showFrameTime) {
         guiGraphics.drawString(this.minecraft.font, sum / this.frametimes.size() + " ms", 5, 5, 16777215);
      }
   }

   private TextureCoordinate blockToTexture(BlockPos blockPos) {
      BlockPos center = this.center();
      int xMin = center.getX() - this.texWidth * this.scaleBlockPos / 2 - 1;
      int zMin = center.getZ() - this.texHeight * this.scaleBlockPos / 2 - 1;
      return new TextureCoordinate((blockPos.getX() - xMin) / 4 * 4 / this.scaleBlockPos, (blockPos.getZ() - zMin) / 4 * 4 / this.scaleBlockPos);
   }

   private void putHoverStructEntry(TextureCoordinate pos, StructHoverHelperEntry entry) {
      int cellX = Math.max(0, Math.min(this.hoverHelperGridWidth - 1, pos.x / PreviewSection.SIZE));
      int cellZ = Math.max(0, Math.min(this.hoverHelperGridHeight - 1, pos.z / PreviewSection.SIZE));
      this.hoverHelperGrid[cellX * this.hoverHelperGridHeight + cellZ].entries.add(entry);
   }

   private void queueGeneration() {
      BlockPos center = this.center();
      int xMin = center.getX() - this.texWidth * this.scaleBlockPos / 2 - 1;
      int xMax = center.getX() + this.texWidth * this.scaleBlockPos / 2 + 1;
      int zMin = center.getZ() - this.texHeight * this.scaleBlockPos / 2 - 1;
      int zMax = center.getZ() + this.texHeight * this.scaleBlockPos / 2 + 1;
      // Keep queueing logic consistent with WorkManager: modes that don't use Y always sample at Y=0.
      int y = this.renderSettings.mode != null && this.renderSettings.mode.useY ? center.getY() : 0;
      this.workManager.queueRange(new BlockPos(xMin, y, zMin), new BlockPos(xMax, y, zMax));
   }

   private List<RenderHelper> generateRenderData() {
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
      synchronized (storage) {
         while (true) {
            long flag = this.renderSettings.mode.flag;
            int useY = this.renderSettings.mode.useY ? quartY : 0;
            PreviewSection dataSection = storage.section4(quartX, useY, quartZ, flag);
            PreviewSection structureSection = storage.section4(quartX, 0, quartZ, 1L);
            PreviewSection.AccessData accessData = dataSection.calcQuartOffsetData(quartX, quartZ, maxQuartX, maxQuartZ);
            res.add(new RenderHelper(dataSection, structureSection, accessData, sectionStartTexX, sectionStartTexZ));
            //res.add(new RenderHelper(dataSection, structureSection, accessData, sectionStartTexX, sectionStartTexZ, flag, useY));
             if (accessData.continueX()) {
               int quartDiffX = accessData.maxX() - accessData.minX();
               quartX += quartDiffX;
               sectionStartTexX += quartDiffX * quartExpand / quartStride;
            } else {
               if (!accessData.continueZ()) {
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

    private void updateTexture(List<RenderHelper> renderData) {
        int texX;
        int texZ;
        int quartExpand = this.renderSettings.quartExpand();
        int quartStride = this.renderSettings.quartStride();

        for (RenderHelper r : renderData) {
            texX = r.sectionStartTexX;

            for (int x = r.accessData.minX(); x < r.accessData.maxX(); x += quartStride) {
                texZ = r.sectionStartTexZ;

                for (int z = r.accessData.minZ(); z < r.accessData.maxZ(); z += quartStride) {
                    short rawData = r.dataSection.get(x, z);
                    int color = -16777216;
                    switch (this.renderSettings.mode) {
                        case BIOMES:
                            if (rawData >= 0) {
                                color = this.selectedBiomeId < 0 && !this.highlightCaves ? this.colorMap[rawData] : this.colorMapGrayScale[rawData];
                                if (this.selectedBiomeId == rawData || this.highlightCaves && this.cavesMap[rawData]) {
                                    color = this.colorMap[rawData];
                                }

                                this.workingVisibleBiomes[rawData]++;
                            }
                            break;
                        case HEIGHTMAP:
                            if (rawData > -32768) {
                                color = this.heightColorMap[rawData - this.dataProvider.yMin()];
                            }
                            break;
                        case TFC_TEMPERATURE:
                            if (rawData > -32768 && this.tfcTemperatureColorMap != null) {
                                // Check land/water status - show ocean in blue
                                int quartXCoord = r.dataSection.quartX() + x;
                                int quartZCoord = r.dataSection.quartZ() + z;
                                short landWater = this.workManager.previewStorage().getRawData4(quartXCoord, 0, quartZCoord, 7L);
                                if (landWater == TFCRegionWorkUnit.LAND_WATER_OCEAN) {
                                    // Ocean - render as blue
                                    color = 0xFFAA4422;  // Blue in ABGR format
                                } else {
                                    // Land or shore - use temperature colormap
                                    float normalized = (rawData + 32768.0F) / 65535.0F;
                                    int idx = Math.min(1023, Math.max(0, (int)(normalized * 1023.0F)));
                                    color = this.tfcTemperatureColorMap[idx];
                                }
                            }
                            break;
                        case TFC_RAINFALL:
                            if (rawData > -32768 && this.tfcRainfallColorMap != null) {
                                // Check land/water status - show ocean in blue
                                int quartXCoord = r.dataSection.quartX() + x;
                                int quartZCoord = r.dataSection.quartZ() + z;
                                short landWater = this.workManager.previewStorage().getRawData4(quartXCoord, 0, quartZCoord, 7L);
                                if (landWater == TFCRegionWorkUnit.LAND_WATER_OCEAN) {
                                    // Ocean - render as blue
                                    color = 0xFFAA4422;  // Blue in ABGR format
                                } else {
                                    // Land or shore - use rainfall colormap
                                    float normalized = rawData / 32767.0F;
                                    int idx = Math.min(1023, Math.max(0, (int)(normalized * 1023.0F)));
                                    color = this.tfcRainfallColorMap[idx];
                                }
                            }
                            break;
                        case TFC_LAND_WATER:
                            if (rawData > -32768) {
                                color = switch (rawData) {
                                    case TFCRegionWorkUnit.LAND_WATER_OCEAN -> 0xFF8B0000;  // Ocean - Dark Blue
                                    case TFCRegionWorkUnit.LAND_WATER_LAND -> 0xFF44AA44;   // Land - Green
                                    case TFCRegionWorkUnit.LAND_WATER_SHORE ->  0xFF8CB4D2; // Shore - Tan
                                    case TFCRegionWorkUnit.LAND_WATER_LAKE -> 0xFFFF0000;   // Lake - Med Blue
                                    case TFCRegionWorkUnit.LAND_WATER_RIVER -> 0xFFFFD7AD;  // River - Light Blue
                                    default -> 0xFF000000;
                                };
                            }
                            break;
                        case TFC_ROCK_TOP:
                        case TFC_ROCK_MID:
                        case TFC_ROCK_BOT:
                            if (rawData >= 0 && rawData < TFCSampleUtils.ROCK_COLORS.length) {
                                this.workingVisibleRocks[rawData]++;
                                // rawData is now a rock ID (0-19), get the color directly
                                int argbColor = TFCSampleUtils.getRockColor(rawData);
                                // Convert ARGB to ABGR for NativeImage
                                int alpha = (argbColor >> 24) & 0xFF;
                                int red = (argbColor >> 16) & 0xFF;
                                int green = (argbColor >> 8) & 0xFF;
                                int blue = argbColor & 0xFF;
                                // Apply layer darkening
                                RenderSettings.RenderMode currentMode = this.renderSettings.mode;
                                int layerShift = switch (currentMode) {
                                    case TFC_ROCK_TOP -> 0;    // Full brightness
                                    case TFC_ROCK_MID -> 20;   // Slightly darker
                                    case TFC_ROCK_BOT -> 40;   // Darker
                                    default -> 0;
                                };
                                red = Math.max(0, red - layerShift);
                                green = Math.max(0, green - layerShift);
                                blue = Math.max(0, blue - layerShift);
                                // Apply grayscale if a different rock is selected, brighten if selected
                                if (this.selectedRockId >= 0) {
                                    if (rawData != this.selectedRockId) {
                                        // Grayscale for non-selected rocks
                                        int gray = (red * 30 + green * 59 + blue * 11) / 100;
                                        red = gray;
                                        green = gray;
                                        blue = gray;
                                    } else {
                                        // Brighten the selected rock
                                        red = Math.min(255, red + 40);
                                        green = Math.min(255, green + 40);
                                        blue = Math.min(255, blue + 40);
                                    }
                                }
                                // ABGR format for NativeImage
                                color = (alpha << 24) | (blue << 16) | (green << 8) | red;
                            } else if (rawData == -1) {
                                // Unknown rock - gray
                                color = 0xFF888888;
                            }
                            break;
                        case TFC_ROCK_TYPE:
                            if (rawData >= 0 && rawData < TFCSampleUtils.ROCK_TYPE_COLORS.length) {
                                this.workingVisibleRocks[rawData]++;
                                int argbColor = TFCSampleUtils.getRockTypeColor(rawData);
                                // Convert ARGB to ABGR for NativeImage
                                int alpha = (argbColor >> 24) & 0xFF;
                                int red = (argbColor >> 16) & 0xFF;
                                int green = (argbColor >> 8) & 0xFF;
                                int blue = argbColor & 0xFF;
                                // Apply grayscale if a different rock type is selected, brighten if selected
                                if (this.selectedRockId >= 0) {
                                    if (rawData != this.selectedRockId) {
                                        // Grayscale for non-selected rock types
                                        int gray = (red * 30 + green * 59 + blue * 11) / 100;
                                        red = gray;
                                        green = gray;
                                        blue = gray;
                                    } else {
                                        // Brighten the selected rock type
                                        red = Math.min(255, red + 40);
                                        green = Math.min(255, green + 40);
                                        blue = Math.min(255, blue + 40);
                                    }
                                }
                                color = (alpha << 24) | (blue << 16) | (green << 8) | red;
                            }
                            break;
                        case TFC_KAOLINITE:
                            if (rawData > -32768) {
                                // Encoded by TFCRegionWorkUnit:
                                // 0 = Water (ocean/shore), 1 = Land (no kaolin), 2 = Kaolin
                                color = switch (rawData) {
                                    case 0 -> 0xFFFF0000; // water blue
                                    case 1 -> 0xFF00CC00; // land green
                                    case 2 -> 0xFFFF66FF; // kaolin pink
                                    default -> 0xFF000001; // background
                                };
                            }
                            break;

                    }

                    if (quartExpand > 1) {
                        this.previewImg.fillRect(texX, texZ, Math.min(this.texWidth - texX, quartExpand), Math.min(this.texHeight - texZ, quartExpand), color);
                    } else {
                        this.previewImg.setPixelRGBA(texX, texZ, color);
                    }

                    texZ += quartExpand;
                }

                texX += quartExpand;
            }
        }
    }

   private void renderStructures(List<RenderHelper> renderData, GuiGraphics guiGraphics) {
      if (this.config.sampleStructures) {
         double guiScale = this.minecraft.getWindow().getGuiScale();

         for (RenderHelper r : renderData) {
            for (PreviewSection.PreviewStruct structure : r.structureSection.structures()) {
               short id = structure.structureId();
               TextureCoordinate texCenter = this.blockToTexture(structure.center());
               IconData iconData = this.structureIcons[id];
               NativeImage icon = iconData.img;
               DynamicTexture iconTexture = iconData.texture;
               ItemStack item = this.structureItems[id];
               if (icon != null || item != null) {
                  /*if (icon == null) {
                     icon = this.dummyIcon;
                  }*/

                  int xMin = -(icon.getWidth() / 2);
                  int xMax = icon.getWidth() / 2 + 1 + this.texWidth;
                  int zMin = -(icon.getHeight() / 2);
                  int zMax = icon.getHeight() / 2 + 1 + this.texHeight;
                  if (texCenter.x >= xMin && texCenter.z >= zMin && texCenter.x <= xMax && texCenter.z <= zMax) {
                     this.workingVisibleStructures[id]++;
                     if (this.structureRenderInfoMap[id].show() && !this.renderSettings.hideAllStructures) {
                        int texStartX = texCenter.x - icon.getWidth() / 2;
                        int texStartZ = texCenter.z - icon.getHeight() / 2;
                        int rXMin = (int)(texStartX + this.getX() * guiScale);
                        int rZMin = (int)(texStartZ + this.getY() * guiScale);
                        int rXMax = rXMin + icon.getWidth();
                        int rZMax = rZMin + icon.getHeight();
                        if (item != null) {
                           guiGraphics.renderItem(item, rXMin, rZMin);
                        } else if (iconTexture != null) {
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
   }

   private void renderPlayerAndSpawn(GuiGraphics guiGraphics) {
      // Render world spawn area (box) and center point for TFC worlds
      BlockPos worldSpawn = this.dataProvider.getWorldSpawnPos();
      int spawnDistance = this.dataProvider.getWorldSpawnDistance();

      if (worldSpawn != null && spawnDistance > 0) {
         // Draw spawn area box
         this.renderSpawnAreaBox(guiGraphics, worldSpawn, spawnDistance);
      }

      // Render world spawn center icon
      if (worldSpawn != null) {
         this.renderStickyIcon(this.worldSpawnIcon, worldSpawn);
      }

      // Render player position and respawn point if enabled
      if (this.config.showPlayer) {
         PreviewDisplayDataProvider.PlayerData playerData = this.dataProvider.getPlayerData(this.minecraft.getUser().getProfileId());
         if (playerData.currentPos() != null) {
            this.renderStickyIcon(this.playerIcon, playerData.currentPos());
         }

         if (playerData.spawnPos() != null) {
            this.renderStickyIcon(this.spawnIcon, playerData.spawnPos());
         }
      }
   }

   private void renderSpawnAreaBox(GuiGraphics guiGraphics, BlockPos center, int distance) {
      double guiScale = this.minecraft.getWindow().getGuiScale();

      // Calculate the four corners of the spawn area in block coordinates
      int minX = center.getX() - distance;
      int maxX = center.getX() + distance;
      int minZ = center.getZ() - distance;
      int maxZ = center.getZ() + distance;

      // Convert to texture coordinates
      TextureCoordinate topLeft = this.blockToTexture(new BlockPos(minX, 0, minZ));
      TextureCoordinate topRight = this.blockToTexture(new BlockPos(maxX, 0, minZ));
      TextureCoordinate bottomLeft = this.blockToTexture(new BlockPos(minX, 0, maxZ));
      //TextureCoordinate bottomRight = this.blockToTexture(new BlockPos(maxX, 0, maxZ));

      // Convert to screen coordinates
      int screenMinX = (int)(topLeft.x + this.getX() * guiScale);
      int screenMaxX = (int)(topRight.x + this.getX() * guiScale);
      int screenMinZ = (int)(topLeft.z + this.getY() * guiScale);
      int screenMaxZ = (int)(bottomLeft.z + this.getY() * guiScale);

      // Clamp to visible area
      int visMinX = (int)(this.getX() * guiScale);
      int visMaxX = (int)((this.getX() + this.width) * guiScale);
      int visMinZ = (int)(this.getY() * guiScale);
      int visMaxZ = (int)((this.getY() + this.height) * guiScale);

      screenMinX = Math.max(visMinX, Math.min(visMaxX, screenMinX));
      screenMaxX = Math.max(visMinX, Math.min(visMaxX, screenMaxX));
      screenMinZ = Math.max(visMinZ, Math.min(visMaxZ, screenMinZ));
      screenMaxZ = Math.max(visMinZ, Math.min(visMaxZ, screenMaxZ));

      // Gold/orange color for spawn area border (ARGB format)
      int borderColor = 0xAAFFAA00;  // Semi-transparent orange
      int lineWidth = 2;

      // Draw the four edges of the box
      // Top edge
      guiGraphics.fill(screenMinX, screenMinZ, screenMaxX, screenMinZ + lineWidth, borderColor);
      // Bottom edge
      guiGraphics.fill(screenMinX, screenMaxZ - lineWidth, screenMaxX, screenMaxZ, borderColor);
      // Left edge
      guiGraphics.fill(screenMinX, screenMinZ, screenMinX + lineWidth, screenMaxZ, borderColor);
      // Right edge
      guiGraphics.fill(screenMaxX - lineWidth, screenMinZ, screenMaxX, screenMaxZ, borderColor);
   }

   private void renderStickyIcon(IconData iconData, BlockPos pos) {
      double guiScale = this.minecraft.getWindow().getGuiScale();
      NativeImage icon = iconData.img;
      TextureCoordinate texCenter = this.blockToTexture(pos);
      texCenter = new TextureCoordinate(Math.max(0, Math.min(this.texWidth, texCenter.x)), Math.max(0, Math.min(this.texHeight, texCenter.z)));
      int texStartX = texCenter.x - icon.getWidth();
      int texStartZ = texCenter.z - icon.getHeight();
      int rXMin = (int)(texStartX + this.getX() * guiScale);
      int rZMin = (int)(texStartZ + this.getY() * guiScale);
      int rXMax = rXMin + icon.getWidth() * 2;
      int rZMax = rZMin + icon.getHeight() * 2;
      WorldPreviewClient.renderTexture(iconData.texture, rXMin, rZMin, rXMax, rZMax);
   }

   private void biomesChanged() {
      Short2LongMap tempBiomesSet = new Short2LongOpenHashMap(this.workingVisibleBiomes.length);
      Short2LongMap tempStructuresSet = new Short2LongOpenHashMap(this.workingVisibleStructures.length);

      for (short i = 0; i < this.workingVisibleBiomes.length; i++) {
         if (this.workingVisibleBiomes[i] > 0L) {
            tempBiomesSet.put(i, this.workingVisibleBiomes[i]);
         }
      }

      for (short ix = 0; ix < this.workingVisibleStructures.length; ix++) {
         if (this.workingVisibleStructures[ix] > 0L) {
            tempStructuresSet.put(ix, this.workingVisibleStructures[ix]);
         }
      }

      if (!tempBiomesSet.equals(this.visibleBiomes)) {
         this.dataProvider.onVisibleBiomesChanged(tempBiomesSet);
      }

      if (!tempStructuresSet.equals(this.visibleStructures)) {
         this.dataProvider.onVisibleStructuresChanged(tempStructuresSet);
      }

      Short2LongMap tempRocksSet = new Short2LongOpenHashMap(this.workingVisibleRocks.length);
      for (short ir = 0; ir < this.workingVisibleRocks.length; ir++) {
         if (this.workingVisibleRocks[ir] > 0L) {
            tempRocksSet.put(ir, this.workingVisibleRocks[ir]);
         }
      }
      if (!tempRocksSet.equals(this.visibleRocks)) {
         this.dataProvider.onVisibleRocksChanged(tempRocksSet);
      }

      this.visibleBiomes = tempBiomesSet;
      this.visibleStructures = tempStructuresSet;
      this.visibleRocks = tempRocksSet;
   }

   @Nullable
   private HoverInfo hoveredBiome(double mouseX, double mouseY) {
      if (this.isHovered && this.workManager.previewStorage() != null) {
         int guiScale = (int)this.minecraft.getWindow().getGuiScale();
         BlockPos center = this.center();
         int xMin = center.getX() - this.texWidth / 2 * this.scaleBlockPos - 1;
         int zMin = center.getZ() - this.texHeight / 2 * this.scaleBlockPos - 1;
         int xPos = (int)((mouseX - this.getX()) * guiScale * this.scaleBlockPos);
         int zPos = (int)((mouseY - this.getY()) * guiScale * this.scaleBlockPos);
         int quartX = QuartPos.fromBlock(xMin + xPos);
         int quartY = QuartPos.fromBlock(center.getY());
         int quartZ = QuartPos.fromBlock(zMin + zPos);
         short biome = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 0L);
         short height = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 2L);

         // Fetch TFC climate data
         short tfcTempRaw = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 5L);
         short tfcRainRaw = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 6L);
         short tfcLandWater = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 7L);
         short tfcRockTop = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 8L);
         short tfcRockMid = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 9L);
         short tfcRockBot = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 10L);
         short tfcRockType = this.workManager.previewStorage().getRawData4(quartX, 0, quartZ, 11L);
         float tfcTemp = tfcTempRaw > -32768 ? TFCSampleUtils.denormalizeTemperature(tfcTempRaw) : Float.NaN;
         float tfcRain = tfcRainRaw > -32768 ? TFCSampleUtils.denormalizeRainfall(tfcRainRaw) : Float.NaN;

         if (biome < 0) {
            return new HoverInfo(
               xMin + xPos, center.getY(), zMin + zPos, null, height, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
               tfcTemp, tfcRain, tfcLandWater, tfcRockTop, tfcRockMid, tfcRockBot, tfcRockType
            );
         } else {
            short temperature = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 9L);
            short humidity = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 10L);
            short continentalness = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 11L);
            short erosion = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 12L);
            short depth = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 13L);
            short weirdness = this.workManager.previewStorage().getRawData4(quartX, quartY, quartZ, 14L);
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
                  tfcRockType
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
                  tfcRockType
               );
         }
      } else {
         return null;
      }
   }

   private List<StructHoverHelperEntry> hoveredStructures(double mouseX, double mouseY) {
      if (!this.isHovered) {
         return List.of();
      } else {
         int guiScale = (int)this.minecraft.getWindow().getGuiScale();
         int xTexPos = (int)(mouseX - this.getX()) * guiScale;
         int zTexPos = (int)(mouseY - this.getY()) * guiScale;
         int xGridPos = xTexPos / PreviewSection.SIZE;
         int zGridPos = zTexPos / PreviewSection.SIZE;
         List<StructHoverHelperEntry> res = new ArrayList<>();

         for (int x = xGridPos - 1; x <= xGridPos + 1; x++) {
            for (int z = zGridPos - 1; z <= zGridPos + 1; z++) {
               if (x >= 0 && x < this.hoverHelperGridWidth && z >= 0 && z < this.hoverHelperGridHeight) {
                  StructHoverHelperCell cell = this.hoverHelperGrid[x * this.hoverHelperGridHeight + z];

                  for (StructHoverHelperEntry entry : cell.entries) {
                     if (entry.boundingBox.isInside(xTexPos, 0, zTexPos)) {
                        res.add(entry);
                     }
                  }
               }
            }
         }

         return res;
      }
   }

   private static String nameFormatter(String s) {
      int idx = s.indexOf(58);
      return idx < 0 ? "§e" + s + "§r" : String.format("§5§o%s§r§5:%s§r", s.substring(0, idx), s.substring(idx + 1));
   }

   private void setTooltipNow(Tooltip tooltip) {
       assert this.minecraft.screen != null;
       this.minecraft.screen.setTooltipForNextRenderPass(tooltip, DefaultTooltipPositioner.INSTANCE, true);
   }

   private void updateTooltip(double mouseX, double mouseY) {
      HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
      List<StructHoverHelperEntry> structuresInfos = this.hoveredStructures(mouseX, mouseY);
      if (hoverInfo != null || !structuresInfos.isEmpty()) {
         String blockPosTemplate = "§3X=§b%d§r §3Y=§b%d§r §3Z=§b%d§r";
         if (!structuresInfos.isEmpty()) {
            PreviewSection.PreviewStruct structure = structuresInfos.getFirst().structure;
            if (this.config.showControls) {
               this.setTooltipNow(
                  Tooltip.create(
                     Component.translatable(
                        "world_preview.preview-display.struct.tooltip.controls",
                             nameFormatter(this.dataProvider.structure4Id(structure.structureId()).name()),
                             blockPosTemplate.formatted(structure.center().getX(), structure.center().getY(), structure.center().getZ()))
                  )
               );
            } else {
               this.setTooltipNow(
                  Tooltip.create(
                     Component.translatable(
                        "world_preview.preview-display.struct.tooltip",
                             nameFormatter(this.dataProvider.structure4Id(structure.structureId()).name()),
                             blockPosTemplate.formatted(structure.center().getX(), structure.center().getY(), structure.center().getZ()))
                  )
               );
            }
         } else {
            // Check if we're in a TFC mode
            RenderSettings.RenderMode currentMode = this.renderSettings.mode;
            boolean isTFCMode = currentMode.isTFC();

            if (isTFCMode) {
               // TFC-specific tooltip: no biome, no Y, show TFC-relevant data
               String blockPosTemplateNoY = "§3X=§b%d§r §3Z=§b%d§r";
               StringBuilder tfcInfo = new StringBuilder();
               tfcInfo.append(blockPosTemplateNoY.formatted(hoverInfo.blockX, hoverInfo.blockZ));

               switch (currentMode) {
                  case TFC_TEMPERATURE:
                  case TFC_RAINFALL:
                     if (!Float.isNaN(hoverInfo.tfcTemperature)) {
                        tfcInfo.append("\n§3Temperature:§r §b%.1f°C§r".formatted(hoverInfo.tfcTemperature));
                     }
                     if (!Float.isNaN(hoverInfo.tfcRainfall)) {
                        tfcInfo.append("\n§3Rainfall:§r §b%.0fmm§r".formatted(hoverInfo.tfcRainfall));
                     }
                     if (hoverInfo.tfcLandWater > -32768) {
                        tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                     }
                     break;
                  case TFC_LAND_WATER:
                     if (hoverInfo.tfcLandWater > -32768) {
                        tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                     }
                     if (!Float.isNaN(hoverInfo.tfcTemperature)) {
                        tfcInfo.append("\n§3Temperature:§r §b%.1f°C§r".formatted(hoverInfo.tfcTemperature));
                     }
                     if (!Float.isNaN(hoverInfo.tfcRainfall)) {
                        tfcInfo.append("\n§3Rainfall:§r §b%.0fmm§r".formatted(hoverInfo.tfcRainfall));
                     }
                     break;
                  case TFC_ROCK_TOP:
                  case TFC_ROCK_MID:
                  case TFC_ROCK_BOT:
                     String layerName = switch (currentMode) {
                        case TFC_ROCK_TOP -> "Surface";
                        case TFC_ROCK_MID -> "Middle";
                        case TFC_ROCK_BOT -> "Bottom";
                        default -> "";
                     };
                     short rockId = switch (currentMode) {
                        case TFC_ROCK_TOP -> hoverInfo.tfcRockTop;
                        case TFC_ROCK_MID -> hoverInfo.tfcRockMid;
                        case TFC_ROCK_BOT -> hoverInfo.tfcRockBot;
                        default -> (short) -1;
                     };
                     if (rockId >= 0) {
                        tfcInfo.append("\n§3%s Rock:§r §b%s§r".formatted(layerName, hoverInfo.getTfcRockName(rockId)));
                     }
                     if (hoverInfo.tfcRockType >= 0) {
                        tfcInfo.append("\n§3Rock Type:§r §b%s§r".formatted(hoverInfo.getTfcRockTypeName()));
                     }
                     if (hoverInfo.tfcLandWater > -32768) {
                        tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                     }
                     break;
                  case TFC_ROCK_TYPE:
                     if (hoverInfo.tfcRockType >= 0) {
                        tfcInfo.append("\n§3Rock Type:§r §b%s§r".formatted(hoverInfo.getTfcRockTypeName()));
                     }
                     if (hoverInfo.tfcLandWater > -32768) {
                        tfcInfo.append("\n§3Terrain:§r §b%s§r".formatted(hoverInfo.getTfcLandWaterName()));
                     }
                     break;
                  default:
                     break;
               }
               this.setTooltipNow(Tooltip.create(Component.literal(tfcInfo.toString())));
            } else {
            // Standard non-TFC tooltip
            String height = hoverInfo.height > -32768 ? String.format("§b%d§r", hoverInfo.height) : "§7<N/A>§r";
            String noise = "";
            if (!Double.isNaN(hoverInfo.temperature)) {
               noise = "\n\n§3T=§b%.2f§r §3H=§b%.2f§r §3C=§b%.2f§r\n§3E=§b%.2f§r §3D=§b%.2f§r §3W=§b%.2f§r\n§3PV=§b%.2f§r"
                  .formatted(
                     hoverInfo.temperature,
                     hoverInfo.humidity,
                     hoverInfo.continentalness,
                     hoverInfo.erosion,
                     hoverInfo.depth,
                     hoverInfo.weirdness,
                     hoverInfo.pv
                  );
            }

            // Add TFC climate data if available
            String tfcClimate = "";
            if (!Float.isNaN(hoverInfo.tfcTemperature) || !Float.isNaN(hoverInfo.tfcRainfall)) {
               String tfcTempStr = !Float.isNaN(hoverInfo.tfcTemperature)
                  ? String.format("§b%.1f°C§r", hoverInfo.tfcTemperature)
                  : "§7N/A§r";
               String tfcRainStr = !Float.isNaN(hoverInfo.tfcRainfall)
                  ? String.format("§b%.0fmm§r", hoverInfo.tfcRainfall)
                  : "§7N/A§r";
               tfcClimate = "\n\n§6TFC Climate§r\n§3Temp:§r " + tfcTempStr + "  §3Rain:§r " + tfcRainStr;
            }

            if (this.config.showControls) {
               this.setTooltipNow(
                  Tooltip.create(
                     Component.translatable(
                        "world_preview.preview-display.tooltip.controls",
                             nameFormatter(hoverInfo.entry == null ? "<N/A>" : hoverInfo.entry.name()),
                             blockPosTemplate.formatted(hoverInfo.blockX, hoverInfo.blockY, hoverInfo.blockZ),
                             height,
                             noise + tfcClimate)
                  )
               );
            } else {
               this.setTooltipNow(
                  Tooltip.create(
                     Component.translatable(
                        "world_preview.preview-display.tooltip",
                             nameFormatter(hoverInfo.entry == null ? "<N/A>" : hoverInfo.entry.name()),
                             blockPosTemplate.formatted(hoverInfo.blockX, hoverInfo.blockY, hoverInfo.blockZ),
                             height,
                             noise + tfcClimate)
                  )
               );
            }
            }
         }
      }
   }

   public void playDownSound(SoundManager handler) {
   }

   public void onClick(double mouseX, double mouseY) {
      if (this.minecraft.screen != null) {
         this.minecraft.screen.setFocused(this);
      }

      this.clicked = true;
   }

   protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
      double guiScale = this.minecraft.getWindow().getGuiScale();
      this.totalDragX = this.totalDragX - dragX * guiScale * this.scaleBlockPos;
      this.totalDragZ = this.totalDragZ - dragY * guiScale * this.scaleBlockPos;
   }

    public void onRelease(double mouseX, double mouseY) {
        if (this.clicked) {
            this.clicked = false;
            if (Math.abs(this.totalDragX) <= 4.0 && Math.abs(this.totalDragZ) <= 4.0) {
                HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
                if (hoverInfo == null || hoverInfo.entry == null) {
                    return;
                }

                super.playDownSound(this.minecraft.getSoundManager());
                if (this.selectedBiomeId == hoverInfo.entry.id()) {
                    this.dataProvider.onBiomeVisuallySelected(null);
                } else {
                    this.dataProvider.onBiomeVisuallySelected(hoverInfo.entry);
                }
            }

            this.renderSettings.setCenter(this.center());
            this.totalDragX = 0.0;
            this.totalDragZ = 0.0;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.clicked(mouseX, mouseY) && button == 1) {
         this.playDownSound(this.minecraft.getSoundManager());
         HoverInfo hoverInfo = this.hoveredBiome(mouseX, mouseY);
         if (hoverInfo == null) {
            return true;
         } else {
            String coordinates = String.format("%s %s %s", hoverInfo.blockX, hoverInfo.height == -32768 ? "~" : hoverInfo.height, hoverInfo.blockZ);
            this.minecraft.keyboardHandler.setClipboard(coordinates);
            this.coordinatesCopiedTime = Instant.now();
            this.coordinatesCopiedMsg = Component.translatable("world_preview.preview-display.coordinates.copied", coordinates);
            return true;
         }
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public void setOverlayMessage(@Nullable Component message) {
      this.coordinatesCopiedMsg = message;
      this.coordinatesCopiedTime = message != null ? Instant.now() : null;
   }

   private static int textureColor(int orig) {
      int R = orig >> 16 & 0xFF;
      int G = orig >> 8 & 0xFF;
      int B = orig & 0xFF;
      return R | G << 8 | B << 16 | 0xFF000000;
   }

   /*private static int highlightColor(int orig) {
      int R = orig >> 16 & 0xFF;
      int G = orig >> 8 & 0xFF;
      int B = orig & 0xFF;
      int diff = (R + G + B) / 3 > 200 ? -100 : 100;
      R += diff;
      G += diff;
      B += diff;
      R = Math.max(Math.min(R, 255), 0);
      G = Math.max(Math.min(G, 255), 0);
      B = Math.max(Math.min(B, 255), 0);
      return 0xFF000000 | R << 16 | G << 8 | B;
   }*/

   private static int grayScale(int orig) {
      int R = orig >> 16 & 0xFF;
      int G = orig >> 8 & 0xFF;
      int B = orig & 0xFF;
      int gray = Math.max(32, Math.min(224, (R + G + B) / 3));
      return 0xFF000000 | gray << 16 | gray << 8 | gray;
   }

   public void setSelectedBiomeId(short biomeId) {
      this.selectedBiomeId = biomeId;
   }

   public void setSelectedRockId(short rockId) {
      this.selectedRockId = rockId;
   }

   public void setHighlightCaves(boolean highlightCaves) {
      this.highlightCaves = highlightCaves;
   }

   protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
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
      short tfcRockType
   ) {
      public String getTfcLandWaterName() {
         return switch (tfcLandWater) {
            case TFCRegionWorkUnit.LAND_WATER_OCEAN -> "Ocean";
            case TFCRegionWorkUnit.LAND_WATER_LAND -> "Land";
            case TFCRegionWorkUnit.LAND_WATER_SHORE -> "Shore";
            case TFCRegionWorkUnit.LAND_WATER_LAKE -> "Lake";
            case TFCRegionWorkUnit.LAND_WATER_RIVER -> "River";
            default -> "Unknown";
         };
      }

      public String getTfcRockName(short rockId) {
         return TFCSampleUtils.getRockName(rockId);
      }

      public String getTfcRockTypeName() {
         return TFCSampleUtils.getRockTypeName(tfcRockType);
      }
   }

   private record IconData(@NotNull NativeImage img, @NotNull DynamicTexture texture) {
      public void close() {
         this.texture.close();
         this.img.close();
      }
   }

    private record RenderHelper(
      PreviewSection dataSection, PreviewSection structureSection, PreviewSection.AccessData accessData, int sectionStartTexX, int sectionStartTexZ
   ) {
   }

   private record StructHoverHelperCell(List<StructHoverHelperEntry> entries) {
   }

   private record StructHoverHelperEntry(BoundingBox boundingBox, PreviewSection.PreviewStruct structure) {
   }

   private record TextureCoordinate(int x, int z) {
   }

}
