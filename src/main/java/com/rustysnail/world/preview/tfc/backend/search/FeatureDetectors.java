package com.rustysnail.world.preview.tfc.backend.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.noise.Cellular2D;
import net.dries007.tfc.world.volcano.CenteredFeatureNoise;
import net.dries007.tfc.world.volcano.CenteredFeatureNoiseSampler;
import net.dries007.tfc.world.volcano.VolcanoVariant;

public final class FeatureDetectors
{

    private static final ResourceLocation STRATOVOLCANOES_ID = ResourceLocation.fromNamespaceAndPath("tfc", "stratovolcanoes");
    private static final Map<ResourceLocation, Short> FEATURE_ID_MAP = new HashMap<>();
    private static final List<SearchableFeature> FEATURE_BY_ID = new ArrayList<>();
    private static final int VARIANT_CACHE_SIZE = 256;
    private static final Map<VariantCacheKey, Optional<String>> VARIANT_CACHE = new LinkedHashMap<>(16, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<VariantCacheKey, Optional<String>> eldest)
        {
            return size() > VARIANT_CACHE_SIZE;
        }
    };

    public static short getFeatureId(SearchableFeature feature)
    {
        Short id = FEATURE_ID_MAP.get(feature.id());
        return id != null ? id : -1;
    }

    public static @Nullable SearchableFeature getFeatureById(int id)
    {
        if (id < 0 || id >= FEATURE_BY_ID.size()) return null;
        return FEATURE_BY_ID.get(id);
    }

    public static int getFeatureCount()
    {
        return FEATURE_BY_ID.size();
    }

    private static final int NOISE_CACHE_SIZE = 4;
    private static final long[] cachedSeeds = new long[NOISE_CACHE_SIZE];
    private static final CenteredFeatureNoiseSampler[] cachedTuffSamplers = new CenteredFeatureNoiseSampler[NOISE_CACHE_SIZE];
    private static final CenteredFeatureNoiseSampler[] cachedTuyaSamplers = new CenteredFeatureNoiseSampler[NOISE_CACHE_SIZE];
    private static final CenteredFeatureNoiseSampler[] cachedCinderSamplers = new CenteredFeatureNoiseSampler[NOISE_CACHE_SIZE];
    private static final CenteredFeatureNoiseSampler[] cachedStratovolcanoSamplers = new CenteredFeatureNoiseSampler[NOISE_CACHE_SIZE];
    private static int cacheIndex = 0;

    static
    {
        java.util.Arrays.fill(cachedSeeds, Long.MIN_VALUE);
    }

    private static synchronized CenteredFeatureNoiseSampler getTuffRingSampler(long seed)
    {
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed && cachedTuffSamplers[i] != null)
            {
                return cachedTuffSamplers[i];
            }
        }
        int idx = cacheIndex;
        cacheIndex = (cacheIndex + 1) % NOISE_CACHE_SIZE;
        cachedSeeds[idx] = seed;
        cachedTuffSamplers[idx] = CenteredFeatureNoise.tuffRing(Seed.of(seed));
        cachedTuyaSamplers[idx] = CenteredFeatureNoise.tuya(Seed.of(seed));
        cachedCinderSamplers[idx] = CenteredFeatureNoise.cinder(Seed.of(seed));
        cachedStratovolcanoSamplers[idx] = CenteredFeatureNoise.stratovolcano(Seed.of(seed));
        return cachedTuffSamplers[idx];
    }

    private static synchronized CenteredFeatureNoiseSampler getTuyaSampler(long seed)
    {
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed && cachedTuyaSamplers[i] != null)
            {
                return cachedTuyaSamplers[i];
            }
        }
        getTuffRingSampler(seed);
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed)
            {
                return cachedTuyaSamplers[i];
            }
        }
        return CenteredFeatureNoise.tuya(Seed.of(seed));
    }

    private static synchronized CenteredFeatureNoiseSampler getCinderSampler(long seed)
    {
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed && cachedCinderSamplers[i] != null)
            {
                return cachedCinderSamplers[i];
            }
        }
        getTuffRingSampler(seed);
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed)
            {
                return cachedCinderSamplers[i];
            }
        }
        return CenteredFeatureNoise.cinder(Seed.of(seed));
    }

    private static synchronized CenteredFeatureNoiseSampler getStratovolcanoSampler(long seed)
    {
        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed && cachedStratovolcanoSamplers[i] != null)
            {
                return cachedStratovolcanoSamplers[i];
            }
        }

        getTuffRingSampler(seed);

        for (int i = 0; i < NOISE_CACHE_SIZE; i++)
        {
            if (cachedSeeds[i] == seed)
            {
                return cachedStratovolcanoSamplers[i];
            }
        }

        return CenteredFeatureNoise.stratovolcano(Seed.of(seed));
    }

    public static @Nullable String getFeatureVariant(
        @Nullable SearchableFeature feature,
        long seed,
        @Nullable BlockPos center
    )
    {
        if (feature == null || center == null || !STRATOVOLCANOES_ID.equals(feature.id()))
        {
            return null;
        }

        VariantCacheKey key = new VariantCacheKey(seed, center.getX(), center.getZ());
        synchronized (VARIANT_CACHE)
        {
            Optional<String> cached = VARIANT_CACHE.get(key);
            if (cached != null)
            {
                return cached.orElse(null);
            }
        }

        String variantName = null;
        try
        {
            CenteredFeatureNoiseSampler sampler = getStratovolcanoSampler(seed);
            Cellular2D.Cell cell = sampler.getCellularNoise().cell(center.getX(), center.getZ());
            VolcanoVariant variant = sampler.getVolcanoVariant(cell);
            variantName = variant != null ? variant.name() : null;
        }
        catch (RuntimeException ignored)
        {
            // A stale feature icon or incompatible TFC worldgen state must not break tooltip rendering.
        }

        synchronized (VARIANT_CACHE)
        {
            VARIANT_CACHE.put(key, Optional.ofNullable(variantName));
        }
        return variantName;
    }

    public static @Nullable Component getFeatureVariantName(
        @Nullable SearchableFeature feature,
        long seed,
        @Nullable BlockPos center
    )
    {
        String variantName = getFeatureVariant(feature, seed, center);
        if (variantName == null || variantName.isBlank())
        {
            return null;
        }

        String fallback = titleCaseVariantName(variantName);
        if (fallback.isBlank())
        {
            return null;
        }
        return Component.translatableWithFallback(
            "feature.worldpreview.stratovolcano.variant." + variantName,
            fallback
        );
    }

    static String titleCaseVariantName(String serializedName)
    {
        String[] words = serializedName.strip().split("[_\\s-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words)
        {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0))).append(lower, 1, lower.length());
        }
        return result.toString();
    }

    private record VariantCacheKey(long seed, int centerX, int centerZ) {}

    private static final FeatureTest TUFF_RING_TEST = new FeatureTest()
    {
        @Override
        public boolean matches(FeatureQuery q)
        {
            return findCenter(q) != null;
        }

        @Override
        public @Nullable BlockPos findCenter(FeatureQuery q)
        {
            if (q.biomeLookup() == null) return null;

            CenteredFeatureNoiseSampler sampler = getTuffRingSampler(q.seed());

            return getBlockPos(q, sampler);
        }
    };

    private static final FeatureTest TUYA_TEST = new FeatureTest()
    {
        @Override
        public boolean matches(FeatureQuery q)
        {
            return findCenter(q) != null;
        }

        @Override
        public @Nullable BlockPos findCenter(FeatureQuery q)
        {
            if (q.biomeLookup() == null) return null;

            CenteredFeatureNoiseSampler sampler = getTuyaSampler(q.seed());

            return getBlockPos(q, sampler);
        }
    };

    private static final FeatureTest CINDER_CONE_TEST = new FeatureTest()
    {
        @Override
        public boolean matches(FeatureQuery q)
        {
            return findCenter(q) != null;
        }

        @Override
        public @Nullable BlockPos findCenter(FeatureQuery q)
        {
            if (q.biomeLookup() == null) return null;

            CenteredFeatureNoiseSampler sampler = getCinderSampler(q.seed());

            return getBlockPos(q, sampler);
        }
    };

    private static final FeatureTest STRATOVOLCANO_TEST = new FeatureTest()
    {
        @Override
        public boolean matches(FeatureQuery q)
        {
            return findCenter(q) != null;
        }

        @Override
        public @Nullable BlockPos findCenter(FeatureQuery q)
        {
            if (q.biomeLookup() == null) return null;

            CenteredFeatureNoiseSampler sampler = getStratovolcanoSampler(q.seed());

            return getBlockPos(q, sampler);
        }
    };

    private static @Nullable BlockPos getBlockPos(FeatureQuery q, CenteredFeatureNoiseSampler sampler)
    {
        BlockPos cellCenter = sampler.calculateCenter(q.blockX(), 64, q.blockZ(), 1);
        if (cellCenter == null) return null;

        FeatureQuery.BiomeLookup biomeLookup = q.biomeLookup();
        if (biomeLookup == null) return null;

        BiomeExtension biome = biomeLookup.getBiome(
            QuartPos.fromBlock(cellCenter.getX()),
            QuartPos.fromBlock(cellCenter.getZ())
        );

        if (biome == null || !sampler.isValidBiome(biome)) return null;

        return sampler.calculateCenter(q.blockX(), 64, q.blockZ(), sampler.getFrequency(biome));
    }

    private static final FeatureTest VOLCANO_TEST = q -> q.point() != null && q.point().hotSpotAge >= 1;

    private static final FeatureTest VOLCANO_CALDERA_TEST = q -> q.point() != null && q.point().hotSpotAge == 1;

    private static final FeatureTest LAKE_TEST = q -> q.point() != null && q.point().lake();

    private static final List<SearchableFeature> MANUAL_FEATURES = List.of(

        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "tuff_rings"),
            Component.translatable("feature.worldpreview.tuff_ring"),
            TUFF_RING_TEST
        ),
        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "cinder_cones"),
            Component.translatable("feature.worldpreview.cinder_cone"),
            CINDER_CONE_TEST
        ),
        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "tuyas"),
            Component.translatable("feature.worldpreview.tuya"),
            TUYA_TEST
        ),
        new SearchableFeature(
            STRATOVOLCANOES_ID,
            Component.translatable("feature.worldpreview.stratovolcano"),
            STRATOVOLCANO_TEST
        ),

        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "volcano_caldera"),
            Component.translatable("feature.worldpreview.volcano_caldera"),
            VOLCANO_CALDERA_TEST,
            true
        ),
        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "pumice"),
            Component.translatable("feature.worldpreview.pumice"),
            VOLCANO_TEST
        ),

        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "flood_fill_lake"),
            Component.translatable("feature.worldpreview.flood_fill_lake"),
            LAKE_TEST
        ),

        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "water_spring"),
            Component.translatable("feature.worldpreview.water_spring"),
            null,
            true
        ),
        new SearchableFeature(
            ResourceLocation.fromNamespaceAndPath("tfc", "lava_spring"),
            Component.translatable("feature.worldpreview.lava_spring"),
            null,
            true
        )
    );


    static
    {
        short id = 0;
        for (SearchableFeature feature : MANUAL_FEATURES)
        {
            FEATURE_ID_MAP.put(feature.id(), id++);
            FEATURE_BY_ID.add(feature);
        }
    }

    private FeatureDetectors() {}

    public static List<SearchableFeature> getManualFeatures()
    {
        return MANUAL_FEATURES;
    }

    public static boolean isCompatibleWithBiomeExt(SearchableFeature feature, @Nullable BiomeExtension ext)
    {
        if (ext == null) return false;

        String featurePath = feature.id().getPath();
        String biomePath = ext.key().location().getPath();

        switch (featurePath)
        {
            case "tuff_rings" ->
            {
                return ext.hasTuffRings();
            }
            case "cinder_cones" ->
            {
                return ext.hasCinderCones();
            }
            case "tuyas" ->
            {
                return ext.hasTuyas();
            }
            case "stratovolcanoes" ->
            {
                return ext.hasStratovolcanoes();
            }
        }

        if (featurePath.startsWith("volcano_") || featurePath.equals("pumice"))
        {
            return isVolcanicBiome(biomePath);
        }

        if (featurePath.startsWith("tuya_"))
        {
            return ext.hasTuyas() || biomePath.contains("tuya");
        }

        if (featurePath.contains("hot_spring"))
        {
            return isVolcanicBiome(biomePath);
        }

        if (featurePath.contains("spring"))
        {
            return true;
        }

        if (featurePath.contains("lake"))
        {
            return !biomePath.contains("ocean") && !biomePath.contains("shore");
        }

        return true;
    }

    private static boolean isVolcanicBiome(String biomePath)
    {
        return biomePath.contains("volcano") ||
            biomePath.contains("volcanic") ||
            biomePath.equals("canyons") ||
            biomePath.equals("doline_canyons");
    }

    public static Set<SearchableFeature> getProbeRequiredFeatures(Set<SearchableFeature> required)
    {
        boolean hasProbe = false;
        for (SearchableFeature f : required)
        {
            if (f.requiresProbe())
            {
                hasProbe = true;
                break;
            }
        }
        if (!hasProbe)
        {
            return Collections.emptySet();
        }

        Set<SearchableFeature> result = new HashSet<>();
        for (SearchableFeature f : required)
        {
            if (f.requiresProbe())
            {
                result.add(f);
            }
        }
        return result;
    }

    public static Set<ResourceLocation> getProbeFeatureIds(Set<SearchableFeature> features)
    {
        if (features.isEmpty())
        {
            return Collections.emptySet();
        }

        Set<ResourceLocation> result = new HashSet<>();
        for (SearchableFeature f : features)
        {
            if (f.requiresProbe())
            {
                result.add(f.getPlacedFeatureId());
            }
        }
        return result;
    }
}
