package com.rustysnail.world.preview.tfc.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.surface.SurfaceManager;

@Mixin(TFCChunkGenerator.class)
public interface TFCChunkGeneratorAccessor
{
    @Accessor("customBiomeSource")
    BiomeSourceExtension worldpreview$getCustomBiomeSource();

    @Accessor("noiseSettings")
    Holder<NoiseGeneratorSettings> worldpreview$getNoiseSettings();

    @Accessor("seed")
    void worldpreview$setSeed(Seed seed);

    @Accessor("chunkDataGenerator")
    void worldpreview$setChunkDataGenerator(ChunkDataGenerator generator);

    @Accessor("surfaceManager")
    void worldpreview$setSurfaceManager(SurfaceManager manager);

    @Accessor("tideHeightNoise")
    void worldpreview$setTideHeightNoise(Noise2D noise);
}
