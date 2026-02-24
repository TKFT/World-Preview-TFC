package com.rustysnail.world.preview.tfc.backend.worker;

import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.sampler.ChunkSampler;
import com.rustysnail.world.preview.tfc.mixin.NoiseChunkAccessor;
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

public class HeightmapWorkUnit extends WorkUnit
{
    private final ChunkSampler sampler;
    private final int numChunks;

    public HeightmapWorkUnit(ChunkSampler sampler, SampleUtils sampleUtils, ChunkPos chunkPos, int numChunks, PreviewData previewData)
    {
        super(sampleUtils, chunkPos, previewData, 0);
        this.sampler = sampler;
        this.numChunks = numChunks;
    }

    @Override
    protected List<WorkResult> doWork()
    {
        WorkResult result = new WorkResult(this, QuartPos.fromBlock(0), this.primarySection, new ArrayList<>(this.numChunks * this.numChunks * 4 * 4), List.of());
        NoiseGeneratorSettings noiseGeneratorSettings = this.sampleUtils.noiseGeneratorSettings();
        WorldPreviewConfig config = this.workManager.config();
        if (noiseGeneratorSettings == null)
        {
            return List.of(result);
        }

        NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings();
        NoiseChunk noiseChunk = this.sampleUtils.getNoiseChunk(this.chunkPos, this.numChunks, false);
        MutableBlockPos mutableBlockPos = new MutableBlockPos();
        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int minY = config.onlySampleInVisualRange ? config.heightmapMinY : noiseSettings.minY();
        int maxY = config.onlySampleInVisualRange ? config.heightmapMaxY : minY + noiseSettings.height();
        int cellMinY = Mth.floorDiv(minY, cellHeight);
        int cellCountY = Mth.floorDiv(maxY - minY, cellHeight);
        int cellOffsetY = config.onlySampleInVisualRange ? cellMinY - Mth.floorDiv(noiseSettings.minY(), cellHeight) : 0;
        int minBlockX = this.chunkPos.getMinBlockX();
        int minBlockZ = this.chunkPos.getMinBlockZ();
        int cellCountXZ = 16 * this.numChunks / cellWidth;
        int cellStrideXZ = Math.max(1, this.sampler.blockStride() / cellWidth);
        int samplesPerCell = Math.max(1, cellWidth / this.sampler.blockStride()) * Math.max(1, cellWidth / this.sampler.blockStride());
        Predicate<BlockState> isOpaque = Types.OCEAN_FLOOR_WG.isOpaque();
        noiseChunk.initializeForFirstCellX();

        try
        {
            for (int cellX = 0; cellX < cellCountXZ && !this.isCanceled(); cellX += cellStrideXZ)
            {
                noiseChunk.advanceCellX(cellX);

                for (int cellZ = 0; cellZ < cellCountXZ && !this.isCanceled(); cellZ += cellStrideXZ)
                {
                    List<XZPair> positions = new ArrayList<>(samplesPerCell);

                    for (int xInCell = 0; xInCell < cellWidth; xInCell += this.sampler.blockStride())
                    {
                        for (int zInCell = 0; zInCell < cellWidth; zInCell += this.sampler.blockStride())
                        {
                            int x = minBlockX + cellX * cellWidth + xInCell;
                            int z = minBlockZ + cellZ * cellWidth + zInCell;
                            positions.add(new XZPair(x, (double) xInCell / cellWidth, z, (double) zInCell / cellWidth));
                        }
                    }

                    for (int cellY = cellCountY - 1; cellY >= 0 && !positions.isEmpty() && !this.isCanceled(); cellY--)
                    {
                        noiseChunk.selectCellYZ(cellY + cellOffsetY, cellZ);

                        for (int yInCell = cellHeight - 1; yInCell >= 0 && !positions.isEmpty(); yInCell--)
                        {
                            int y = (cellMinY + cellY) * cellHeight + yInCell;
                            noiseChunk.updateForY(y, (double) yInCell / cellHeight);

                            for (int idx = 0; idx < positions.size(); idx++)
                            {
                                XZPair curr = positions.get(idx);
                                noiseChunk.updateForX(curr.x, curr.dX);
                                noiseChunk.updateForZ(curr.z, curr.dZ);
                                BlockState blockState = ((NoiseChunkAccessor) noiseChunk).invokeGetInterpolatedState();
                                if (blockState == null)
                                {
                                    blockState = noiseGeneratorSettings.defaultBlock();
                                }

                                if (isOpaque.test(blockState))
                                {
                                    mutableBlockPos.set(curr.x, 0, curr.z);
                                    this.sampler.expandRaw(mutableBlockPos, (short) (y + 1), result);
                                    positions.remove(idx--);
                                }
                            }
                        }
                    }
                }

                noiseChunk.swapSlices();
            }
        }
        finally
        {
            noiseChunk.stopInterpolation();
        }

        return List.of(result);
    }

    @Override
    public long flags()
    {
        return 2L;
    }

    private record XZPair(int x, double dX, int z, double dZ)
    {
    }
}