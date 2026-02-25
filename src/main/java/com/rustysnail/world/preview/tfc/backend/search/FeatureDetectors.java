package com.rustysnail.world.preview.tfc.backend.search;

import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.volcano.CenteredFeatureNoise;
import net.dries007.tfc.world.volcano.CenteredFeatureNoiseSampler;

import net.minecraft.core.QuartPos;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FeatureDetectors
{

    private static final Map<ResourceLocation, Short> FEATURE_ID_MAP = new HashMap<>();
    private static final List<SearchableFeature> FEATURE_BY_ID = new ArrayList<>();

    private static final ResourceLocation NATIVE_SILVER_VEIN_ID = ResourceLocation.fromNamespaceAndPath("tfc", "vein/surface_native_silver");
    private static final ResourceLocation NATIVE_GOLD_VEIN_ID = ResourceLocation.fromNamespaceAndPath("tfc", "vein/normal_native_gold");

    public static short getFeatureId(SearchableFeature feature)
    {
        Short id = FEATURE_ID_MAP.get(feature.id());
        return id != null ? id : -1;
    }

    public static SearchableFeature getFeatureById(int id)
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
    private static int cacheIndex = 0;

    static
    {
        java.util.Arrays.fill(cachedSeeds, Long.MIN_VALUE);
    }

    private static CenteredFeatureNoiseSampler getTuffRingSampler(long seed)
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
        return cachedTuffSamplers[idx];
    }

    private static CenteredFeatureNoiseSampler getTuyaSampler(long seed)
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

    private static CenteredFeatureNoiseSampler getCinderSampler(long seed)
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

    private static @Nullable BlockPos getBlockPos(FeatureQuery q, CenteredFeatureNoiseSampler sampler)
    {
        BlockPos cellCenter = sampler.calculateCenter(q.blockX(), 64, q.blockZ(), 1);
        if (cellCenter == null) return null;

        BiomeExtension biome = Objects.requireNonNull(q.biomeLookup()).getBiome(
            QuartPos.fromBlock(cellCenter.getX()),
            QuartPos.fromBlock(cellCenter.getZ())
        );

        if (biome == null || !sampler.isValidBiome(biome)) return null;

        return sampler.calculateCenter(q.blockX(), 64, q.blockZ(), sampler.getRarity(biome));
    }

    private static final FeatureTest VOLCANO_TEST = q -> q.point() != null && q.point().hotSpotAge >= 1;

    private static final FeatureTest VOLCANO_CALDERA_TEST = q -> q.point() != null && q.point().hotSpotAge == 1;

    private static final FeatureTest LAKE_TEST = q -> q.point() != null && q.point().lake();

    private static final SearchableFeature NATIVE_SILVER_VEIN = new SearchableFeature(
        NATIVE_SILVER_VEIN_ID,
        Component.translatable("feature.worldpreview.native_silver_vein"),
        null,
        true
    );

    private static final SearchableFeature NATIVE_GOLD_VEIN = new SearchableFeature(
        NATIVE_GOLD_VEIN_ID,
        Component.translatable("feature.worldpreview.native_gold_vein"),
        null,
        true
    );

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

        NATIVE_SILVER_VEIN,
        NATIVE_GOLD_VEIN,

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

    public static List<SearchableFeature> getPreviewVeinFeatures()
    {
        return List.of(NATIVE_SILVER_VEIN, NATIVE_GOLD_VEIN);
    }

    public static boolean showInStructuresList(SearchableFeature feature)
    {
        return feature != null && (
            feature.id().equals(NATIVE_SILVER_VEIN_ID) ||
            feature.id().equals(NATIVE_GOLD_VEIN_ID)
        );
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
