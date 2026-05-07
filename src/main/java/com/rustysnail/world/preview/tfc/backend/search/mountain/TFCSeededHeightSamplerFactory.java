package com.rustysnail.world.preview.tfc.backend.search.mountain;

import com.rustysnail.world.preview.tfc.mixin.TFCChunkGeneratorAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeNoise;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.AreaFactory;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;
import net.dries007.tfc.world.surface.SurfaceManager;

public class TFCSeededHeightSamplerFactory
{
    /**
     * Creates a height-only initialized TFCChunkGenerator clone for the given seed,
     * and wraps it in a TFCSeededHeightSampler.
     * <p>
     * Only initializes the state required for createHeightFillerForChunk/sampleHeight.
     * NoiseSampler (which requires registry access) is intentionally omitted since
     * it is not used in the height sampling path.
     */
    public static TFCSeededHeightSampler create(ChunkGenerator activeGenerator, long seed)
    {
        if (!(activeGenerator instanceof TFCChunkGenerator activeGen))
        {
            throw new IllegalArgumentException("Active generator is not a TFCChunkGenerator: " + activeGenerator.getClass().getName());
        }

        TFCChunkGeneratorAccessor accessor = (TFCChunkGeneratorAccessor) activeGen;
        BiomeSourceExtension copiedBiomeSource = accessor.worldpreview$getCustomBiomeSource().copy();
        Holder<NoiseGeneratorSettings> noiseSettings = accessor.worldpreview$getNoiseSettings();
        Settings settings = ((ChunkGeneratorExtension) activeGen).settings();

        TFCChunkGenerator cloned = new TFCChunkGenerator(copiedBiomeSource, noiseSettings, settings);
        TFCChunkGeneratorAccessor clonedAccessor = (TFCChunkGeneratorAccessor) cloned;

        Seed tfcSeed = Seed.of(seed);

        RegionGenerator regionGenerator = new RegionGenerator(settings, tfcSeed);
        AreaFactory factory = TFCLayers.createRegionBiomeLayer(regionGenerator, tfcSeed);
        ConcurrentArea<BiomeExtension> biomeLayer = new ConcurrentArea<>(factory, TFCLayers::getFromLayerId);

        clonedAccessor.worldpreview$setSeed(tfcSeed);
        clonedAccessor.worldpreview$setChunkDataGenerator(regionGenerator.chunkDataGenerator());
        clonedAccessor.worldpreview$setSurfaceManager(new SurfaceManager(tfcSeed));
        clonedAccessor.worldpreview$setTideHeightNoise(BiomeNoise.shoreTideLevelNoise(tfcSeed));

        copiedBiomeSource.initRandomState(regionGenerator, biomeLayer);

        return new TFCSeededHeightSampler(seed, cloned);
    }
}
