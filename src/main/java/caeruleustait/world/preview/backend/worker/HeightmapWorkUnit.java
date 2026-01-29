package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import caeruleustait.world.preview.mixin.NoiseChunkAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.QuartPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public class HeightmapWorkUnit extends WorkUnit {
   private final ChunkSampler sampler;
   private final int numChunks;

   public HeightmapWorkUnit(ChunkSampler sampler, SampleUtils sampleUtils, ChunkPos chunkPos, int numChunks, PreviewData previewData) {
      super(sampleUtils, chunkPos, previewData, 0);
      this.sampler = sampler;
      this.numChunks = numChunks;
   }

   @Override
   protected List<WorkResult> doWork() {
      WorkResult res = new WorkResult(this, QuartPos.fromBlock(0), this.primarySection, new ArrayList<>(this.numChunks * this.numChunks * 4 * 4), List.of());
      NoiseGeneratorSettings noiseGeneratorSettings = this.sampleUtils.noiseGeneratorSettings();
      WorldPreviewConfig config = this.workManager.config();
      if (noiseGeneratorSettings == null) {
         return List.of(res);
      } else {
         NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings();
         NoiseChunk noiseChunk = this.sampleUtils.getNoiseChunk(this.chunkPos, this.numChunks, false);
         MutableBlockPos mutableBlockPos = new MutableBlockPos();
         int cellWidth = noiseSettings.getCellWidth();
         int cellHeight = noiseSettings.getCellHeight();
         int minY = config.onlySampleInVisualRange ? config.heightmapMinY : noiseSettings.minY();
         int maxY = config.onlySampleInVisualRange ? config.heightmapMaxY : minY + noiseSettings.height();
         int cellMinY = Mth.floorDiv(minY, noiseSettings.getCellHeight());
         int cellCountY = Mth.floorDiv(maxY - minY, noiseSettings.getCellHeight());
         int cellOffsetY = config.onlySampleInVisualRange ? cellMinY - Mth.floorDiv(noiseSettings.minY(), noiseSettings.getCellHeight()) : 0;
         int minBlockX = this.chunkPos.getMinBlockX();
         int minBlockZ = this.chunkPos.getMinBlockZ();
         int cellCountXZ = 16 * this.numChunks / cellWidth;
         int cellStrideXZ = Math.max(1, this.sampler.blockStride() / cellWidth);
         int todoArraySize = Math.max(1, cellWidth / this.sampler.blockStride()) * Math.max(1, cellWidth / this.sampler.blockStride());
         Predicate<BlockState> predicate = Types.OCEAN_FLOOR_WG.isOpaque();
         noiseChunk.initializeForFirstCellX();

         try {
            for (int cellX = 0; cellX < cellCountXZ && !this.isCanceled(); cellX += cellStrideXZ) {
               noiseChunk.advanceCellX(cellX);
               int cellZ = 0;

               label162:
               while (true) {
                  if (cellZ < cellCountXZ && !this.isCanceled()) {
                     List<XZPair> positions = new ArrayList<>(todoArraySize);

                     for (int xInCell = 0; xInCell < cellWidth; xInCell += this.sampler.blockStride()) {
                        for (int zInCell = 0; zInCell < cellWidth; zInCell += this.sampler.blockStride()) {
                           int x = minBlockX + cellX * cellWidth + xInCell;
                           int z = minBlockZ + cellZ * cellWidth + zInCell;
                           positions.add(new XZPair(x, (double)xInCell / cellWidth, z, (double)zInCell / cellWidth));
                        }
                     }

                     int cellY = cellCountY - 1;

                     label147:
                     while (true) {
                        if (cellY >= 0 && !positions.isEmpty() && !this.isCanceled()) {
                           noiseChunk.selectCellYZ(cellY + cellOffsetY, cellZ);
                           int yInCell = cellHeight - 1;

                           while (yInCell >= 0 && !positions.isEmpty()) {
                              int y = (cellMinY + cellY) * cellHeight + yInCell;
                              noiseChunk.updateForY(y, (double)yInCell / cellHeight);

                              for (int idx = 0; idx < positions.size(); idx++) {
                                 XZPair curr = positions.get(idx);
                                 noiseChunk.updateForX(curr.x, curr.dX);
                                 noiseChunk.updateForZ(curr.z, curr.dZ);
                                 BlockState blockState = ((NoiseChunkAccessor)noiseChunk).invokeGetInterpolatedState();
                                 if (blockState == null) {
                                    blockState = noiseGeneratorSettings.defaultBlock();
                                 }

                                 if (predicate.test(blockState)) {
                                    mutableBlockPos.set(curr.x, 0, curr.z);
                                    this.sampler.expandRaw(mutableBlockPos, (short)(y + 1), res);
                                    positions.remove(idx--);
                                 }
                              }

                              yInCell--;
                           }

                           cellY--;
                           continue label147;
                        }

                        cellZ += cellStrideXZ;
                        continue label162;
                     }
                  }

                  noiseChunk.swapSlices();
                  break;
               }
            }
         } finally {
            noiseChunk.stopInterpolation();
         }

         return List.of(res);
      }
   }

   @Override
   public long flags() {
      return 2L;
   }

   private record XZPair(int x, double dX, int z, double dZ) {
   }
}
