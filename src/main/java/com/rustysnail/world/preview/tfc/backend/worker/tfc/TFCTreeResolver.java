package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import com.rustysnail.world.preview.tfc.WorldPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.client.overworld.SolarCalculator;
import net.dries007.tfc.util.EnvironmentHelpers;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.feature.tree.ForestConfig;
import net.dries007.tfc.world.settings.Settings;

public final class TFCTreeResolver
{
    private static final ResourceLocation FOREST_ID = ResourceLocation.fromNamespaceAndPath("tfc", "forest");
    private static final ResourceLocation DEAD_FOREST_ID = ResourceLocation.fromNamespaceAndPath("tfc", "dead_forest");
    private static final ResourceLocation MANGROVE_FOREST_ID = ResourceLocation.fromNamespaceAndPath("tfc", "mangrove_forest");

    @Nullable
    public static TFCTreeResolver create(RegistryAccess registryAccess, Settings settings)
    {
        try
        {
            var registry = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);

            Map<ResourceLocation, List<SpeciesEntry>> byConfig = new HashMap<>();
            int addonConfigs = 0;
            for (Map.Entry<net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>>, ConfiguredFeature<?, ?>> e : registry.entrySet())
            {
                if (e.getValue().config() instanceof ForestConfig forestConfig)
                {
                    ResourceLocation id = e.getKey().location();
                    byConfig.put(id, loadEntries(forestConfig));
                    if (!id.getNamespace().equals("tfc"))
                    {
                        addonConfigs++;
                    }
                }
            }

            if (byConfig.isEmpty())
            {
                WorldPreview.LOGGER.warn("[TFC] No forest configs found; dominant-tree resolution disabled");
                return null;
            }

            Registry<Biome> biomeRegistry = null;
            try
            {
                biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            }
            catch (Exception e)
            {
                WorldPreview.LOGGER.warn("[TFC] Biome registry unavailable; biome-specific forest config detection disabled", e);
            }

            WorldPreview.LOGGER.info(
                "[TFC] Loaded {} forest configs ({} addon): forest={}, dead={}, mangrove={} entries; biome detection {}",
                byConfig.size(), addonConfigs,
                byConfig.getOrDefault(FOREST_ID, List.of()).size(),
                byConfig.getOrDefault(DEAD_FOREST_ID, List.of()).size(),
                byConfig.getOrDefault(MANGROVE_FOREST_ID, List.of()).size(),
                biomeRegistry != null ? "enabled" : "disabled");
            return new TFCTreeResolver(settings, Map.copyOf(byConfig), biomeRegistry);
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.warn("[TFC] Failed to load forest configs for dominant-tree resolution", e);
            return null;
        }
    }

    static boolean isMangroveConfig(ResourceLocation id)
    {
        return id.equals(MANGROVE_FOREST_ID) || id.getPath().contains("mangrove");
    }

    static boolean isDeadConfig(ResourceLocation id)
    {
        return id.equals(DEAD_FOREST_ID) || id.getPath().contains("dead");
    }

    static boolean isNormalConfig(ResourceLocation id)
    {
        return !isMangroveConfig(id) && !isDeadConfig(id);
    }

    private static List<SpeciesEntry> loadEntries(ForestConfig forestConfig)
    {
        List<SpeciesEntry> result = new ArrayList<>();
        for (Holder<ConfiguredFeature<?, ?>> holder : forestConfig.entries())
        {
            if (holder.value().config() instanceof ForestConfig.Entry entry)
            {
                ResourceLocation species = TFCTreeSpeciesRegistry.speciesFromHolder(holder, entry);
                short id = TFCSampleUtils.treeSpeciesId(species);
                result.add(new SpeciesEntry(id, entry));
            }
        }
        return result;
    }

    private static boolean isSaltMarsh(@Nullable BiomeExtension biome)
    {
        return biome != null && biome.key().location().getPath().equals("salt_marsh");
    }

    @Nullable
    private static ResourceLocation pickByContext(List<ResourceLocation> candidates, @Nullable BiomeExtension biome, ForestType forestType)
    {
        if (isSaltMarsh(biome))
        {
            ResourceLocation m = firstMatching(candidates, TFCTreeResolver::isMangroveConfig);
            if (m != null) return m;
        }
        if (forestType.isDead())
        {
            ResourceLocation d = firstMatching(candidates, TFCTreeResolver::isDeadConfig);
            if (d != null) return d;
        }
        ResourceLocation n = firstMatching(candidates, TFCTreeResolver::isNormalConfig);
        if (n != null) return n;
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    @Nullable
    private static ResourceLocation firstMatching(List<ResourceLocation> candidates, Predicate<ResourceLocation> test)
    {
        for (ResourceLocation c : candidates)
        {
            if (test.test(c)) return c;
        }
        return null;
    }

    private final Settings settings;
    private final Map<ResourceLocation, List<SpeciesEntry>> entriesByConfig;
    @Nullable
    private final Registry<Biome> biomeRegistry;
    private final Map<ResourceLocation, List<ResourceLocation>> biomeConfigCache = new ConcurrentHashMap<>();

    private final Map<CacheKey, Result> resultCache = new ConcurrentHashMap<>();
    private volatile boolean loggedNoBiomeRegistry = false;

    private TFCTreeResolver(Settings settings, Map<ResourceLocation, List<SpeciesEntry>> entriesByConfig, @Nullable Registry<Biome> biomeRegistry)
    {
        this.settings = settings;
        this.entriesByConfig = entriesByConfig;
        this.biomeRegistry = biomeRegistry;
    }

    public Result resolve(ChunkData chunkData, ForestType forestType, @Nullable BiomeExtension biome, int blockX, int blockZ, int surfaceY)
    {
        final ResourceLocation configId = selectConfigId(biome, forestType);
        final CacheKey key = new CacheKey(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockZ), forestType.ordinal(), configId);
        Result cached = this.resultCache.get(key);
        if (cached != null)
        {
            return cached;
        }

        Result result = computeResult(chunkData, forestType, configId, blockX, blockZ, surfaceY);
        this.resultCache.put(key, result);
        return result;
    }

    @Nullable
    private ResourceLocation selectConfigId(@Nullable BiomeExtension biome, ForestType forestType)
    {
        if (biome != null)
        {
            List<ResourceLocation> candidates = biomeForestConfigs(biome);
            if (!candidates.isEmpty())
            {
                ResourceLocation chosen = pickByContext(candidates, biome, forestType);
                if (chosen != null)
                {
                    return chosen;
                }
            }
        }

        if (isSaltMarsh(biome) && this.entriesByConfig.containsKey(MANGROVE_FOREST_ID))
        {
            return MANGROVE_FOREST_ID;
        }
        if (forestType.isDead() && this.entriesByConfig.containsKey(DEAD_FOREST_ID))
        {
            return DEAD_FOREST_ID;
        }
        if (this.entriesByConfig.containsKey(FOREST_ID))
        {
            return FOREST_ID;
        }
        return null;
    }

    private List<ResourceLocation> biomeForestConfigs(BiomeExtension biome)
    {
        return this.biomeConfigCache.computeIfAbsent(biome.key().location(), id -> detectBiomeForestConfigs(biome, id));
    }

    private List<ResourceLocation> detectBiomeForestConfigs(BiomeExtension biome, ResourceLocation biomeId)
    {
        if (this.biomeRegistry == null)
        {
            if (!this.loggedNoBiomeRegistry)
            {
                this.loggedNoBiomeRegistry = true;
                WorldPreview.LOGGER.debug("[TFC] No biome registry; forest config falls back to fixed rules");
            }
            return List.of();
        }
        try
        {
            Biome biomeObj = this.biomeRegistry.get(biome.key());
            if (biomeObj == null)
            {
                return List.of();
            }
            TreeSet<ResourceLocation> found = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
            for (HolderSet<PlacedFeature> step : biomeObj.getGenerationSettings().features())
            {
                try
                {
                    for (Holder<PlacedFeature> placedHolder : step)
                    {
                        PlacedFeature placed;
                        try
                        {
                            placed = placedHolder.value();
                        }
                        catch (Exception unbound)
                        {
                            continue; // unbound/missing placed feature holder
                        }
                        Holder<ConfiguredFeature<?, ?>> cfHolder = placed.feature();
                        ConfiguredFeature<?, ?> cf;
                        try
                        {
                            cf = cfHolder.value();
                        }
                        catch (Exception unbound)
                        {
                            continue;
                        }
                        if (cf.config() instanceof ForestConfig)
                        {
                            cfHolder.unwrapKey()
                                .map(ResourceKey::location)
                                .filter(this.entriesByConfig::containsKey)
                                .ifPresent(found::add);
                        }
                    }
                }
                catch (Exception stepUnbound)
                {
                    // unbound tag / feature set at this decoration step
                }
            }
            List<ResourceLocation> result = List.copyOf(found);
            if (result.size() > 1)
            {
                WorldPreview.LOGGER.debug("[TFC] Biome {} references multiple forest configs {}", biomeId, result);
            }
            else if (!result.isEmpty())
            {
                WorldPreview.LOGGER.debug("[TFC] Biome {} forest config: {}", biomeId, result.getFirst());
            }
            return result;
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.debug("[TFC] Failed scanning biome {} features for forest configs: {}", biomeId, e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- biome -> config detection

    private Result computeResult(ChunkData chunkData, ForestType forestType, @Nullable ResourceLocation configId, int blockX, int blockZ, int surfaceY)
    {
        List<SpeciesEntry> entries = configId != null ? this.entriesByConfig.get(configId) : null;
        final ResourceLocation sourceConfig = entries != null && !entries.isEmpty() ? configId : null;
        if (!TFCSampleUtils.forestTypeHasVegetation(forestType))
        {
            return new Result(TFCSampleUtils.VALUE_INVALID, List.of(), sourceConfig);
        }
        if (entries == null || entries.isEmpty())
        {
            return new Result(TFCSampleUtils.VALUE_INVALID, List.of(), null);
        }

        final BlockPos pos = new BlockPos(blockX, surfaceY, blockZ);

        final boolean northern = SolarCalculator.getInNorthernHemisphere(blockZ, this.settings.temperatureScale());
        final float rainVariance = chunkData.getRainVariance(pos) * (northern ? 1f : -1f);
        final float groundwater = chunkData.getAverageGroundwater(pos);
        final int elevation = surfaceY;
        final float averageTemperature = EnvironmentHelpers.adjustAvgTempForElev(surfaceY, chunkData.getAverageSeaLevelTemp(pos));

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
            return new Result(TFCSampleUtils.VALUE_INVALID, List.of(), sourceConfig);
        }
        candidates.sort(Comparator.comparingDouble(
            se -> se.entry().distanceFromMean(averageTemperature, groundwater, rainVariance, elevation)));

        final int maxSize = forestType.getMaxTreeTypes();
        while (candidates.size() > maxSize)
        {
            candidates.removeLast();
        }
        int alternate = forestType.getAlternateSize();
        while (candidates.size() > 1 && alternate > 0)
        {
            candidates.removeFirst();
            alternate--;
        }

        final int mostLikely = candidates.size() == 2 ? 1 : 0;

        final short dominantId = candidates.get(mostLikely).speciesId();
        final List<Short> possibleIds = new ArrayList<>(candidates.size());
        for (SpeciesEntry se : candidates)
        {
            possibleIds.add(se.speciesId());
        }
        return new Result(dominantId, possibleIds, sourceConfig);
    }

    public record Result(short dominantId, List<Short> possibleIds, @Nullable ResourceLocation sourceConfig)
    {
        public static final Result NONE = new Result(TFCSampleUtils.VALUE_INVALID, List.of(), null);
    }

    private record SpeciesEntry(short speciesId, ForestConfig.Entry entry) {}

    private record CacheKey(int quartX, int quartZ, int forestOrdinal, @Nullable ResourceLocation config) {}
}
