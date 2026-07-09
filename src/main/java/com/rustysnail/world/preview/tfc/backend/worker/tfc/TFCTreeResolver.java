package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;

import net.dries007.tfc.client.overworld.SolarCalculator;
import net.dries007.tfc.util.EnvironmentHelpers;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.feature.tree.ForestConfig;
import net.dries007.tfc.world.settings.Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic "most likely tree" resolver for the preview. Mirrors TFC's
 * {@link net.dries007.tfc.world.feature.tree.ForestFeature#place} candidate pipeline
 * ({@code getTrees} + {@code getTree}) using the <em>runtime</em> {@link ForestConfig}s from the
 * configured-feature registry, so datapack / addon tree entries and their real climate ranges are
 * honored instead of a hardcoded table. Rather than placing a random tree it reports the single
 * most probable species plus the final (trimmed) candidate list.
 *
 * <p>Loaded once per preview session; the per-config entry lists are immutable afterward and the
 * result cache is thread-safe, so instances are shared across the {@code WorkManager} thread pool.
 */
public final class TFCTreeResolver
{
    /** Which of the three TFC forest configs applies at a position. */
    public enum ConfigType
    {
        FOREST,          // tfc:forest        (normal, never mangrove)
        DEAD_FOREST,     // tfc:dead_forest   (ForestType#isDead)
        MANGROVE_FOREST  // tfc:mangrove_forest (salt_marsh biome)
    }

    /** Result of a resolution: the most likely species id and the final possible-species names. */
    public record Result(short speciesId, List<String> possibleSpecies)
    {
        public static final Result NONE = new Result(TFCSampleUtils.VALUE_INVALID, List.of());

        public boolean hasTree()
        {
            return speciesId != TFCSampleUtils.VALUE_INVALID;
        }
    }

    /** A configured-feature entry paired with the stable preview species id derived from its key. */
    private record SpeciesEntry(short speciesId, ForestConfig.Entry entry) {}

    private final Settings settings;
    private final List<SpeciesEntry> forestEntries;
    private final List<SpeciesEntry> deadForestEntries;
    private final List<SpeciesEntry> mangroveEntries;

    // Cache keyed by (quartX, quartZ, forestType ordinal, config type) — see packKey.
    private final Map<Long, Result> resultCache = new ConcurrentHashMap<>();

    private TFCTreeResolver(Settings settings, List<SpeciesEntry> forestEntries, List<SpeciesEntry> deadForestEntries, List<SpeciesEntry> mangroveEntries)
    {
        this.settings = settings;
        this.forestEntries = forestEntries;
        this.deadForestEntries = deadForestEntries;
        this.mangroveEntries = mangroveEntries;
    }

    @Nullable
    public static TFCTreeResolver create(RegistryAccess registryAccess, Settings settings)
    {
        try
        {
            var registry = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);
            List<SpeciesEntry> forest = loadConfig(registry, "forest");
            List<SpeciesEntry> dead = loadConfig(registry, "dead_forest");
            List<SpeciesEntry> mangrove = loadConfig(registry, "mangrove_forest");
            if (forest.isEmpty() && dead.isEmpty() && mangrove.isEmpty())
            {
                WorldPreview.LOGGER.warn("[TFC] No forest configs found; dominant-tree resolution disabled");
                return null;
            }
            WorldPreview.LOGGER.info("[TFC] Loaded forest configs: forest={}, dead={}, mangrove={} entries",
                forest.size(), dead.size(), mangrove.size());
            return new TFCTreeResolver(settings, forest, dead, mangrove);
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.warn("[TFC] Failed to load forest configs for dominant-tree resolution", e);
            return null;
        }
    }

    private static List<SpeciesEntry> loadConfig(net.minecraft.core.Registry<ConfiguredFeature<?, ?>> registry, String path)
    {
        List<SpeciesEntry> result = new ArrayList<>();
        ConfiguredFeature<?, ?> configured = registry.get(ResourceLocation.fromNamespaceAndPath("tfc", path));
        if (configured == null || !(configured.config() instanceof ForestConfig forestConfig))
        {
            return result;
        }
        for (Holder<ConfiguredFeature<?, ?>> holder : forestConfig.entries())
        {
            FeatureConfiguration cfg = holder.value().config();
            if (!(cfg instanceof ForestConfig.Entry entry))
            {
                continue;
            }
            // Resolve the species location the same way the registry does, then map to its runtime id.
            ResourceLocation species = TFCTreeSpeciesRegistry.speciesFromHolder(holder, entry);
            short id = TFCSampleUtils.treeSpeciesId(species);
            result.add(new SpeciesEntry(id, entry));
        }
        return result;
    }

    private List<SpeciesEntry> entriesFor(ConfigType type)
    {
        return switch (type)
        {
            case DEAD_FOREST -> this.deadForestEntries;
            case MANGROVE_FOREST -> this.mangroveEntries;
            default -> this.forestEntries;
        };
    }

    /**
     * Resolves the most likely species at a position. {@code chunkData} must be the (already
     * generated) data for the chunk containing the position; it is never regenerated here.
     * {@code surfaceY} is the terrain height used for elevation temperature adjustment and the
     * elevation climate check.
     */
    public Result resolve(ChunkData chunkData, ForestType forestType, ConfigType configType, int blockX, int blockZ, int surfaceY)
    {
        final long key = packKey(blockX, blockZ, forestType, configType);
        Result cached = this.resultCache.get(key);
        if (cached != null)
        {
            return cached;
        }

        Result result = computeResult(chunkData, forestType, configType, blockX, blockZ, surfaceY);
        this.resultCache.put(key, result);
        return result;
    }

    private Result computeResult(ChunkData chunkData, ForestType forestType, ConfigType configType, int blockX, int blockZ, int surfaceY)
    {
        // Forest types that place neither trees nor bushes have no dominant species.
        if (!TFCSampleUtils.forestTypeHasVegetation(forestType))
        {
            return Result.NONE;
        }

        final List<SpeciesEntry> entries = entriesFor(configType);
        if (entries.isEmpty())
        {
            return Result.NONE;
        }

        final BlockPos pos = new BlockPos(blockX, surfaceY, blockZ);

        // Climate inputs, matching ForestFeature#getTrees exactly.
        final boolean northern = SolarCalculator.getInNorthernHemisphere(blockZ, this.settings.temperatureScale());
        final float rainVariance = chunkData.getRainVariance(pos) * (northern ? 1f : -1f);
        final float groundwater = chunkData.getAverageGroundwater(pos);
        final int elevation = surfaceY;
        final float averageTemperature = EnvironmentHelpers.adjustAvgTempForElev(surfaceY, chunkData.getAverageSeaLevelTemp(pos));

        // 1. Filter by climate validity, 2. sort ascending by distance from mean.
        final List<SpeciesEntry> candidates = new ArrayList<>();
        for (SpeciesEntry se : entries)
        {
            if (se.entry().isValid(averageTemperature, groundwater, rainVariance, elevation))
            {
                candidates.add(se);
            }
        }
        if (candidates.isEmpty())
        {
            return Result.NONE;
        }
        candidates.sort(Comparator.comparingDouble(
            se -> se.entry().distanceFromMean(averageTemperature, groundwater, rainVariance, elevation)));

        // 4. Limit to maxTreeTypes, 5. drop the closest getAlternateSize() while more than one remains.
        final int maxSize = forestType.getMaxTreeTypes();
        while (candidates.size() > maxSize)
        {
            candidates.removeLast();
        }
        int alternate = forestType.getAlternateSize();
        while (candidates.size() > 1 && alternate > 0)
        {
            candidates.remove(0);
            alternate--;
        }

        // Most likely index of TFC's weighted 60% walk:
        //   size 1 -> 0; size 2 -> 1 (0.6 vs 0.4); size >= 3 -> 0 (largest single probability, 0.4).
        final int mostLikely = candidates.size() == 2 ? 1 : 0;

        final short speciesId = candidates.get(mostLikely).speciesId();
        final List<String> possible = new ArrayList<>(candidates.size());
        for (SpeciesEntry se : candidates)
        {
            possible.add(TFCSampleUtils.getTreeSpeciesName(se.speciesId()));
        }
        return new Result(speciesId, possible);
    }

    private static long packKey(int blockX, int blockZ, ForestType forestType, ConfigType configType)
    {
        final long qx = QuartPos.fromBlock(blockX) & 0x3FFFFFFL; // 26 bits
        final long qz = QuartPos.fromBlock(blockZ) & 0x3FFFFFFL; // 26 bits
        final long ft = forestType.ordinal() & 0x3FL;            // 6 bits
        final long ct = configType.ordinal() & 0x3L;             // 2 bits
        return (qx << 38) | (qz << 12) | (ft << 6) | ct;
    }
}
