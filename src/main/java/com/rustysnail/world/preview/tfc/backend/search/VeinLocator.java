package com.rustysnail.world.preview.tfc.backend.search;

import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.rustysnail.world.preview.tfc.backend.worker.SampleUtils;
import com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils;

import net.dries007.tfc.world.feature.vein.IVeinConfig;
import net.dries007.tfc.world.feature.vein.PipeVeinFeature;
import net.dries007.tfc.world.feature.vein.VeinFeature;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.settings.RockSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class VeinLocator
{
    private VeinLocator() {}

    public static Map<Long, List<PreviewSection.PreviewFeature>> findVeinsForRegion(
        SampleUtils sampleUtils,
        TFCSampleUtils tfcSampleUtils,
        long worldSeed,
        ChunkPos minChunk,
        ChunkPos maxChunk,
        Set<Long> seenCenters,
        BooleanSupplier cancelled
    )
    {
        if (cancelled.getAsBoolean())
        {
            return Map.of();
        }

        List<TargetVein> targets = resolveTargets(sampleUtils.registryAccess());
        if (targets.isEmpty())
        {
            return Map.of();
        }

        Map<Long, List<PreviewSection.PreviewFeature>> result = new HashMap<>();
        for (int chunkX = minChunk.x; chunkX <= maxChunk.x; chunkX++)
        {
            for (int chunkZ = minChunk.z; chunkZ <= maxChunk.z; chunkZ++)
            {
                if (cancelled.getAsBoolean())
                {
                    return result;
                }

                for (TargetVein target : targets)
                {
                    BlockPos center = sampleCandidateCenter(worldSeed, chunkX, chunkZ, target);
                    if (center == null)
                    {
                        continue;
                    }

                    if (!matchesReplaceableRock(center, target, tfcSampleUtils))
                    {
                        continue;
                    }

                    long dedupeKey = packBlockKey(center.getX(), center.getZ());
                    synchronized (seenCenters)
                    {
                        if (!seenCenters.add(dedupeKey))
                        {
                            continue;
                        }
                    }

                    ChunkPos cp = new ChunkPos(center);
                    result.computeIfAbsent(cp.toLong(), ignored -> new ArrayList<>())
                        .add(new PreviewSection.PreviewFeature(target.featureId, center));
                }
            }
        }

        return result;
    }

    private static List<TargetVein> resolveTargets(net.minecraft.core.RegistryAccess registryAccess)
    {
        Registry<ConfiguredFeature<?, ?>> configuredFeatures = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);
        List<TargetVein> targets = new ArrayList<>();

        for (SearchableFeature feature : FeatureDetectors.getPreviewVeinFeatures())
        {
            short featureId = FeatureDetectors.getFeatureId(feature);
            if (featureId < 0)
            {
                continue;
            }

            ResourceLocation configuredFeatureId = feature.getPlacedFeatureId();
            ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE, configuredFeatureId);
            ConfiguredFeature<?, ?> configured = configuredFeatures.get(key);
            if (configured == null)
            {
                continue;
            }
            if (!(configured.feature() instanceof VeinFeature<?, ?> veinFeature))
            {
                continue;
            }
            if (!(configured.config() instanceof IVeinConfig veinConfig))
            {
                continue;
            }
            if (veinConfig.config().states().isEmpty())
            {
                continue;
            }

            targets.add(new TargetVein(
                featureId,
                veinConfig,
                veinConfig.config().states().keySet(),
                veinFeature instanceof PipeVeinFeature
            ));
        }

        return targets;
    }

    private static BlockPos sampleCandidateCenter(long worldSeed, int chunkX, int chunkZ, TargetVein target)
    {
        int rarity = target.config.config().rarity();
        if (rarity <= 0)
        {
            return null;
        }

        RandomSource random = new XoroshiroRandomSource(
            worldSeed ^ chunkX * 61728364132L,
            target.config.config().seed() ^ chunkZ * 16298364123L
        );

        if (random.nextInt(rarity) != 0)
        {
            return null;
        }

        if (target.consumeAngleBeforeCenter)
        {
            random.nextFloat();
        }

        int blockX = (chunkX << 4) + random.nextInt(16);
        int blockY = sampleVeinY(random, target.config.verticalRadius(), target.config.minY(), target.config.maxY());
        int blockZ = (chunkZ << 4) + random.nextInt(16);
        return new BlockPos(blockX, blockY, blockZ);
    }

    private static int sampleVeinY(RandomSource random, int verticalRadius, int minY, int maxY)
    {
        int range = maxY - minY - 2 * verticalRadius;
        if (range > 0)
        {
            return minY + verticalRadius + random.nextInt(range);
        }
        return (minY + maxY) / 2;
    }

    private static boolean matchesReplaceableRock(BlockPos center, TargetVein target, TFCSampleUtils tfcSampleUtils)
    {
        Region.Point point;
        try
        {
            point = tfcSampleUtils.samplePoint(center.getX(), center.getZ());
        }
        catch (Exception ignored)
        {
            return false;
        }

        if (point == null)
        {
            return false;
        }

        for (int layer = 0; layer < 3; layer++)
        {
            RockSettings rock = tfcSampleUtils.sampleRockAtLayer(point.rock, layer);
            if (rock != null && target.replaceableBlocks.contains(rock.raw()))
            {
                return true;
            }
        }
        return false;
    }

    private static long packBlockKey(int blockX, int blockZ)
    {
        return (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
    }

    private record TargetVein(
        short featureId,
        IVeinConfig config,
        Set<Block> replaceableBlocks,
        boolean consumeAngleBeforeCenter
    )
    {
    }
}
