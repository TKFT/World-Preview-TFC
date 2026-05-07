package com.rustysnail.world.preview.tfc.backend.search;

import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.volcano.CenteredFeatureNoise;
import net.dries007.tfc.world.volcano.CenteredFeatureNoiseSampler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
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

    private static final String INTERNAL_NAMESPACE = "world_preview_tfc";

    private static final Map<ResourceLocation, Short> FEATURE_ID_MAP = new HashMap<>();
    private static final List<SearchableFeature> FEATURE_BY_ID = new ArrayList<>();

    private static final Map<ResourceLocation, ResourceLocation> FEATURE_ICON_OVERRIDES = new HashMap<>();
    private static final Map<ResourceLocation, List<ResourceLocation>> FEATURE_CONFIGURED_IDS = new HashMap<>();
    private static final Set<ResourceLocation> PREVIEW_STRUCTURE_FEATURE_IDS = new HashSet<>();

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

    private static final SearchableFeature TUFF_RINGS = new SearchableFeature(
        tfcId("tuff_rings"),
        Component.translatable("feature.worldpreview.tuff_ring"),
        TUFF_RING_TEST
    );

    private static final SearchableFeature CINDER_CONES = new SearchableFeature(
        tfcId("cinder_cones"),
        Component.translatable("feature.worldpreview.cinder_cone"),
        CINDER_CONE_TEST
    );

    private static final SearchableFeature TUYAS = new SearchableFeature(
        tfcId("tuyas"),
        Component.translatable("feature.worldpreview.tuya"),
        TUYA_TEST
    );

    private static final SearchableFeature VOLCANO_CALDERA = new SearchableFeature(
        tfcId("volcano_caldera"),
        Component.translatable("feature.worldpreview.volcano_caldera"),
        VOLCANO_CALDERA_TEST,
        true
    );

    private static final SearchableFeature PUMICE = new SearchableFeature(
        tfcId("pumice"),
        Component.translatable("feature.worldpreview.pumice"),
        VOLCANO_TEST
    );

    private static final SearchableFeature FLOOD_FILL_LAKE = new SearchableFeature(
        tfcId("flood_fill_lake"),
        Component.translatable("feature.worldpreview.flood_fill_lake"),
        LAKE_TEST
    );

    private static final SearchableFeature WATER_SPRING = new SearchableFeature(
        tfcId("water_spring"),
        Component.translatable("feature.worldpreview.water_spring"),
        null,
        true
    );

    private static final SearchableFeature LAVA_SPRING = new SearchableFeature(
        tfcId("lava_spring"),
        Component.translatable("feature.worldpreview.lava_spring"),
        null,
        true
    );

    private static final List<SearchableFeature> NON_ORE_FEATURES = List.of(
        TUFF_RINGS,
        CINDER_CONES,
        TUYAS,
        VOLCANO_CALDERA,
        PUMICE,
        FLOOD_FILL_LAKE,
        WATER_SPRING,
        LAVA_SPRING
    );

    // Preview-map ore groups
    private static final SearchableFeature PREVIEW_NATIVE_COPPER = oreFeature("preview/native_copper", "Native Copper");
    private static final SearchableFeature PREVIEW_MALACHITE = oreFeature("preview/malachite", "Malachite");
    private static final SearchableFeature PREVIEW_TETRAHEDRITE = oreFeature("preview/tetrahedrite", "Tetrahedrite");
    private static final SearchableFeature PREVIEW_HEMATITE = oreFeature("preview/hematite", "Hematite");
    private static final SearchableFeature PREVIEW_MAGNETITE = oreFeature("preview/magnetite", "Magnetite");
    private static final SearchableFeature PREVIEW_LIMONITE = oreFeature("preview/limonite", "Limonite");
    private static final SearchableFeature PREVIEW_LIGNITE = oreFeature("preview/lignite", "Lignite");
    private static final SearchableFeature PREVIEW_BITUMINOUS_COAL = oreFeature("preview/bituminous_coal", "Bituminous Coal");
    private static final SearchableFeature PREVIEW_BISMUTH = oreFeature("preview/bismuth", "Bismuth");
    private static final SearchableFeature PREVIEW_GOLD = oreFeature("preview/gold", "Gold");
    private static final SearchableFeature PREVIEW_SILVER = oreFeature("preview/silver", "Silver");
    private static final SearchableFeature PREVIEW_TIN = oreFeature("preview/tin", "Tin");
    private static final SearchableFeature PREVIEW_ZINC = oreFeature("preview/zinc", "Zinc");
    private static final SearchableFeature PREVIEW_PYRITE = oreFeature("preview/pyrite", "Pyrite");
    private static final SearchableFeature PREVIEW_GRAPHITE = oreFeature("preview/graphite", "Graphite");
    private static final SearchableFeature PREVIEW_SULFUR = oreFeature("preview/sulfur", "Sulfur");
    private static final SearchableFeature PREVIEW_CRYOLITE = oreFeature("preview/cryolite", "Cryolite");
    private static final SearchableFeature PREVIEW_CINNABAR = oreFeature("preview/cinnabar", "Cinnabar");
    private static final SearchableFeature PREVIEW_SALTPETER = oreFeature("preview/saltpeter", "Saltpeter");
    private static final SearchableFeature PREVIEW_SYLVITE = oreFeature("preview/sylvite", "Sylvite");
    private static final SearchableFeature PREVIEW_BORAX = oreFeature("preview/borax", "Borax");
    private static final SearchableFeature PREVIEW_GYPSUM = oreFeature("preview/gypsum", "Gypsum");
    private static final SearchableFeature PREVIEW_SALT = oreFeature("preview/salt", "Salt");
    private static final SearchableFeature PREVIEW_EMERALD = oreFeature("preview/emerald", "Emerald");
    private static final SearchableFeature PREVIEW_DIAMOND = oreFeature("preview/diamond", "Diamond");
    private static final SearchableFeature PREVIEW_RUBY = oreFeature("preview/ruby", "Ruby");
    private static final SearchableFeature PREVIEW_LAPIS_LAZULI = oreFeature("preview/lapis_lazuli", "Lapis Lazuli");
    private static final SearchableFeature PREVIEW_AMETHYST = oreFeature("preview/amethyst", "Amethyst");
    private static final SearchableFeature PREVIEW_GEODE = oreFeature("preview/geode", "Geode");
    private static final SearchableFeature PREVIEW_OPAL = oreFeature("preview/opal", "Opal");
    private static final SearchableFeature PREVIEW_GARNIERITE = oreFeature("preview/garnierite", "Garnierite");

    private static final List<SearchableFeature> PREVIEW_ORE_FEATURES = List.of(
        PREVIEW_NATIVE_COPPER,
        PREVIEW_MALACHITE,
        PREVIEW_TETRAHEDRITE,
        PREVIEW_HEMATITE,
        PREVIEW_MAGNETITE,
        PREVIEW_LIMONITE,
        PREVIEW_LIGNITE,
        PREVIEW_BITUMINOUS_COAL,
        PREVIEW_BISMUTH,
        PREVIEW_GOLD,
        PREVIEW_SILVER,
        PREVIEW_TIN,
        PREVIEW_ZINC,
        PREVIEW_PYRITE,
        PREVIEW_GRAPHITE,
        PREVIEW_SULFUR,
        PREVIEW_CRYOLITE,
        PREVIEW_CINNABAR,
        PREVIEW_SALTPETER,
        PREVIEW_SYLVITE,
        PREVIEW_BORAX,
        PREVIEW_GYPSUM,
        PREVIEW_SALT,
        PREVIEW_EMERALD,
        PREVIEW_DIAMOND,
        PREVIEW_RUBY,
        PREVIEW_LAPIS_LAZULI,
        PREVIEW_AMETHYST,
        PREVIEW_GEODE,
        PREVIEW_OPAL,
        PREVIEW_GARNIERITE
    );

    // Seed-search ore groups
    private static final SearchableFeature SEED_COPPER = oreFeature("seed/copper", "Copper");
    private static final SearchableFeature SEED_IRON = oreFeature("seed/iron", "Iron");
    private static final SearchableFeature SEED_COAL = oreFeature("seed/coal", "Coal");
    private static final SearchableFeature SEED_BISMUTH = oreFeature("seed/bismuth", "Bismuth");
    private static final SearchableFeature SEED_GOLD = oreFeature("seed/gold", "Gold");
    private static final SearchableFeature SEED_SILVER = oreFeature("seed/silver", "Silver");
    private static final SearchableFeature SEED_TIN = oreFeature("seed/tin", "Tin");
    private static final SearchableFeature SEED_ZINC = oreFeature("seed/zinc", "Zinc");
    private static final SearchableFeature SEED_PYRITE = oreFeature("seed/pyrite", "Pyrite");
    private static final SearchableFeature SEED_GRAPHITE = oreFeature("seed/graphite", "Graphite");
    private static final SearchableFeature SEED_SULFUR = oreFeature("seed/sulfur", "Sulfur");
    private static final SearchableFeature SEED_REDSTONE = oreFeature("seed/redstone", "Redstone");
    private static final SearchableFeature SEED_SALTPETER = oreFeature("seed/saltpeter", "Saltpeter");
    private static final SearchableFeature SEED_SYLVITE = oreFeature("seed/sylvite", "Sylvite");
    private static final SearchableFeature SEED_BORAX = oreFeature("seed/borax", "Borax");
    private static final SearchableFeature SEED_GYPSUM = oreFeature("seed/gypsum", "Gypsum");
    private static final SearchableFeature SEED_SALT = oreFeature("seed/salt", "Salt");
    private static final SearchableFeature SEED_EMERALD = oreFeature("seed/emerald", "Emerald");
    private static final SearchableFeature SEED_DIAMOND = oreFeature("seed/diamond", "Diamond");
    private static final SearchableFeature SEED_RUBY = oreFeature("seed/ruby", "Ruby");
    private static final SearchableFeature SEED_LAPIS_LAZULI = oreFeature("seed/lapis_lazuli", "Lapis Lazuli");
    private static final SearchableFeature SEED_AMETHYST = oreFeature("seed/amethyst", "Amethyst");
    private static final SearchableFeature SEED_OPAL = oreFeature("seed/opal", "Opal");
    private static final SearchableFeature SEED_NICKEL = oreFeature("seed/nickel", "Nickle");

    private static final List<SearchableFeature> SEED_ORE_FEATURES = List.of(
        SEED_COPPER,
        SEED_IRON,
        SEED_COAL,
        SEED_BISMUTH,
        SEED_GOLD,
        SEED_SILVER,
        SEED_TIN,
        SEED_ZINC,
        SEED_PYRITE,
        SEED_GRAPHITE,
        SEED_SULFUR,
        SEED_REDSTONE,
        SEED_SALTPETER,
        SEED_SYLVITE,
        SEED_BORAX,
        SEED_GYPSUM,
        SEED_SALT,
        SEED_EMERALD,
        SEED_DIAMOND,
        SEED_RUBY,
        SEED_LAPIS_LAZULI,
        SEED_AMETHYST,
        SEED_OPAL,
        SEED_NICKEL
    );

    private static final List<SearchableFeature> MANUAL_FEATURES = concat(NON_ORE_FEATURES, PREVIEW_ORE_FEATURES);
    private static final List<SearchableFeature> SEED_SEARCH_FEATURES = concat(NON_ORE_FEATURES, SEED_ORE_FEATURES);

    static
    {
        // Preview map mappings + icons
        registerPreviewMapping(PREVIEW_NATIVE_COPPER, "rich_native_copper", "vein/surface_native_copper");
        registerPreviewMapping(PREVIEW_MALACHITE, "rich_malachite", "vein/surface_malachite", "vein/normal_malachite");
        registerPreviewMapping(PREVIEW_TETRAHEDRITE, "rich_tetrahedrite", "vein/surface_tetrahedrite", "vein/normal_tetrahedrite");
        registerPreviewMapping(PREVIEW_HEMATITE, "rich_hematite", "vein/surface_hematite");
        registerPreviewMapping(PREVIEW_MAGNETITE, "rich_magnetite", "vein/surface_magnetite");
        registerPreviewMapping(PREVIEW_LIMONITE, "rich_limonite", "vein/surface_limonite");
        registerPreviewMapping(PREVIEW_LIGNITE, "lignite", "vein/lignite");
        registerPreviewMapping(PREVIEW_BITUMINOUS_COAL, "bituminous_coal", "vein/bituminous_coal");
        registerPreviewMapping(PREVIEW_BISMUTH, "rich_bismuthinite", "vein/surface_bismuthinite", "vein/normal_bismuthinite");
        registerPreviewMapping(PREVIEW_GOLD, "rich_native_gold", "vein/normal_native_gold", "vein/rich_native_gold");
        registerPreviewMapping(PREVIEW_SILVER, "rich_native_silver", "vein/surface_native_silver", "vein/normal_native_silver");
        registerPreviewMapping(PREVIEW_TIN, "rich_cassiterite", "vein/surface_cassiterite");
        registerPreviewMapping(PREVIEW_ZINC, "rich_sphalerite", "vein/surface_sphalerite", "vein/normal_sphalerite");
        registerPreviewMapping(PREVIEW_PYRITE, "pyrite", "vein/fake_native_gold");
        registerPreviewMapping(PREVIEW_GRAPHITE, "graphite", "vein/graphite");
        registerPreviewMapping(PREVIEW_SULFUR, "sulfur", "vein/sulfur", "vein/tuff_sulfur");
        registerPreviewMapping(PREVIEW_CRYOLITE, "cryolite", "vein/cryolite");
        registerPreviewMapping(PREVIEW_CINNABAR, "cinnabar", "vein/cinnabar");
        registerPreviewMapping(PREVIEW_SALTPETER, "saltpeter", "vein/saltpeter");
        registerPreviewMapping(PREVIEW_SYLVITE, "sylvite", "vein/sylvite");
        registerPreviewMapping(PREVIEW_BORAX, "borax", "vein/borax");
        registerPreviewMapping(PREVIEW_GYPSUM, "gypsum", "vein/gypsum");
        registerPreviewMapping(PREVIEW_SALT, "halite", "vein/halite");
        registerPreviewMapping(PREVIEW_EMERALD, "emerald", "vein/emerald");
        registerPreviewMapping(PREVIEW_DIAMOND, "diamond", "vein/diamond");
        registerPreviewMapping(PREVIEW_RUBY, "ruby", "vein/ruby");
        registerPreviewMapping(PREVIEW_LAPIS_LAZULI, "lapis_lazuli", "vein/lapis_lazuli");
        registerPreviewMapping(PREVIEW_AMETHYST, "amethyst", "vein/amethyst");
        registerPreviewMapping(PREVIEW_GEODE, "amethyst", "geode");
        registerPreviewMapping(PREVIEW_OPAL, "opal", "vein/opal");
        registerPreviewMapping(PREVIEW_GARNIERITE, "rich_garnierite", "vein/normal_garnierite", "vein/gabbro_garnierite");

        // Seed-search mappings
        registerConfiguredMapping(SEED_COPPER, "vein/surface_native_copper", "vein/surface_malachite", "vein/surface_tetrahedrite", "vein/normal_malachite", "vein/normal_tetrahedrite");
        registerConfiguredMapping(SEED_IRON, "vein/surface_hematite", "vein/surface_magnetite", "vein/surface_limonite");
        registerConfiguredMapping(SEED_COAL, "vein/lignite", "vein/bituminous_coal");
        registerConfiguredMapping(SEED_BISMUTH, "vein/surface_bismuthinite", "vein/normal_bismuthinite");
        registerConfiguredMapping(SEED_GOLD, "vein/normal_native_gold", "vein/rich_native_gold");
        registerConfiguredMapping(SEED_SILVER, "vein/surface_native_silver", "vein/normal_native_silver");
        registerConfiguredMapping(SEED_TIN, "vein/surface_cassiterite");
        registerConfiguredMapping(SEED_ZINC, "vein/surface_sphalerite", "vein/normal_sphalerite");
        registerConfiguredMapping(SEED_PYRITE, "vein/fake_native_gold");
        registerConfiguredMapping(SEED_GRAPHITE, "vein/graphite");
        registerConfiguredMapping(SEED_SULFUR, "vein/sulfur", "vein/tuff_sulfur");
        registerConfiguredMapping(SEED_REDSTONE, "vein/cryolite", "vein/cinnabar");
        registerConfiguredMapping(SEED_SALTPETER, "vein/saltpeter");
        registerConfiguredMapping(SEED_SYLVITE, "vein/sylvite");
        registerConfiguredMapping(SEED_BORAX, "vein/borax");
        registerConfiguredMapping(SEED_GYPSUM, "vein/gypsum");
        registerConfiguredMapping(SEED_SALT, "vein/halite");
        registerConfiguredMapping(SEED_EMERALD, "vein/emerald");
        registerConfiguredMapping(SEED_DIAMOND, "vein/diamond");
        registerConfiguredMapping(SEED_RUBY, "vein/ruby");
        registerConfiguredMapping(SEED_LAPIS_LAZULI, "vein/lapis_lazuli");
        registerConfiguredMapping(SEED_AMETHYST, "vein/amethyst", "geode");
        registerConfiguredMapping(SEED_OPAL, "vein/opal");
        registerConfiguredMapping(SEED_NICKEL, "vein/normal_garnierite", "vein/gabbro_garnierite");

        short id = 0;
        for (SearchableFeature feature : MANUAL_FEATURES)
        {
            FEATURE_ID_MAP.put(feature.id(), id++);
            FEATURE_BY_ID.add(feature);
        }
    }

    private FeatureDetectors() {}

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

    public static List<SearchableFeature> getManualFeatures()
    {
        return MANUAL_FEATURES;
    }

    public static List<SearchableFeature> getSeedSearchFeatures()
    {
        return SEED_SEARCH_FEATURES;
    }

    public static List<SearchableFeature> getPreviewVeinFeatures()
    {
        return PREVIEW_ORE_FEATURES;
    }

    public static boolean showInStructuresList(SearchableFeature feature)
    {
        return feature != null && PREVIEW_STRUCTURE_FEATURE_IDS.contains(feature.id());
    }

    public static @Nullable ResourceLocation getIconOverride(SearchableFeature feature)
    {
        return feature == null ? null : FEATURE_ICON_OVERRIDES.get(feature.id());
    }

    public static List<ResourceLocation> getConfiguredVeinIds(SearchableFeature feature)
    {
        if (feature == null)
        {
            return List.of();
        }
        List<ResourceLocation> configuredIds = FEATURE_CONFIGURED_IDS.get(feature.id());
        return configuredIds != null ? configuredIds : List.of(feature.getPlacedFeatureId());
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
                result.addAll(getConfiguredVeinIds(f));
            }
        }
        return result;
    }

    private static SearchableFeature oreFeature(String internalPath, String displayName)
    {
        return new SearchableFeature(internalId(internalPath), Component.literal(displayName), null, true);
    }

    private static ResourceLocation tfcId(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("tfc", path);
    }

    private static ResourceLocation internalId(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(INTERNAL_NAMESPACE, path);
    }

    private static ResourceLocation oreIcon(String oreItemPath)
    {
        return tfcId("textures/item/ore/" + oreItemPath + ".png");
    }

    private static void registerPreviewMapping(SearchableFeature feature, String iconItemName, String... configuredIds)
    {
        registerConfiguredMapping(feature, configuredIds);
        FEATURE_ICON_OVERRIDES.put(feature.id(), oreIcon(iconItemName));
        PREVIEW_STRUCTURE_FEATURE_IDS.add(feature.id());
    }

    private static void registerConfiguredMapping(SearchableFeature feature, String... configuredIds)
    {
        List<ResourceLocation> ids = new ArrayList<>(configuredIds.length);
        for (String id : configuredIds)
        {
            ids.add(tfcId(id));
        }
        FEATURE_CONFIGURED_IDS.put(feature.id(), List.copyOf(ids));
    }

    private static List<SearchableFeature> concat(List<SearchableFeature> left, List<SearchableFeature> right)
    {
        List<SearchableFeature> result = new ArrayList<>(left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return List.copyOf(result);
    }
}
