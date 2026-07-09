package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import com.rustysnail.world.preview.tfc.WorldPreview;

import net.dries007.tfc.world.feature.tree.ForestConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime registry of tree species, built from the configured-feature registry so that TFC trees
 * <em>and</em> trees added by addons (AFC, etc.) can appear in the Tree Species legend/map.
 *
 * <p>Species are keyed by a normalized {@link ResourceLocation} (e.g. {@code tfc:oak},
 * {@code afc:baobab}) derived from the {@link ForestConfig.Entry} configured-feature key. Preview
 * short ids are assigned by sorting the species locations deterministically, so a given world
 * yields a stable id per species. Known TFC species keep their curated colors; unknown addon
 * species get a stable, tree-like fallback color from their location hash.
 */
public final class TFCTreeSpeciesRegistry
{
    private final Map<ResourceLocation, Short> speciesToId;
    private final List<ResourceLocation> idToSpecies;
    private final Map<ResourceLocation, ForestConfig.Entry> entryBySpecies;

    private TFCTreeSpeciesRegistry(List<ResourceLocation> sortedSpecies, Map<ResourceLocation, ForestConfig.Entry> entries)
    {
        this.idToSpecies = List.copyOf(sortedSpecies);
        this.entryBySpecies = Map.copyOf(entries);
        Map<ResourceLocation, Short> toId = new HashMap<>();
        for (short i = 0; i < sortedSpecies.size(); i++)
        {
            toId.put(sortedSpecies.get(i), i);
        }
        this.speciesToId = Map.copyOf(toId);
    }

    // ---------------------------------------------------------------- known TFC colors

    private static ResourceLocation tfc(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("tfc", path);
    }

    private static final Map<ResourceLocation, Integer> KNOWN_COLORS = buildKnownColors();

    private static Map<ResourceLocation, Integer> buildKnownColors()
    {
        Map<ResourceLocation, Integer> m = new HashMap<>();
        m.put(tfc("acacia"), 0xFFD8A34D);
        m.put(tfc("ash"), 0xFFA7B97A);
        m.put(tfc("aspen"), 0xFFE7DC85);
        m.put(tfc("birch"), 0xFFDCE7B5);
        m.put(tfc("blackwood"), 0xFF2E4A39);
        m.put(tfc("chestnut"), 0xFF8F6A3F);
        m.put(tfc("douglas_fir"), 0xFF2E5F49);
        m.put(tfc("hickory"), 0xFF86984A);
        m.put(tfc("kapok"), 0xFF2F9C5C);
        m.put(tfc("mangrove"), 0xFF3E5C4B);
        m.put(tfc("maple"), 0xFFCB7C46);
        m.put(tfc("oak"), 0xFF4F8C45);
        m.put(tfc("palm"), 0xFF7FCB61);
        m.put(tfc("pine"), 0xFF2F7440);
        m.put(tfc("rosewood"), 0xFF7C4A62);
        m.put(tfc("sequoia"), 0xFF234F3D);
        m.put(tfc("spruce"), 0xFF3F6F58);
        m.put(tfc("sycamore"), 0xFF7AB48C);
        m.put(tfc("white_cedar"), 0xFF66937B);
        m.put(tfc("willow"), 0xFF75B05D);
        return Map.copyOf(m);
    }

    // Muted, tree-like fallback colors (greens, olives, browns, golds, muted purples) for addon
    // species that have no curated color. Chosen by location hash so a species is always the same.
    private static final int[] FALLBACK_PALETTE = {
        0xFF6E8B3D, // olive green
        0xFF4E7A4E, // forest green
        0xFF8A9A5B, // sage
        0xFFB5A642, // gold-olive
        0xFF7C6A40, // brown
        0xFF9C7A3C, // amber brown
        0xFF5F8A6B, // teal green
        0xFF87794E, // khaki
        0xFF6B5B7B, // muted purple
        0xFFA0885A, // tan
        0xFF4A6E52, // deep green
        0xFF8C7853, // taupe
    };

    // ---------------------------------------------------------------- construction

    /**
     * Builds the registry by scanning every configured feature: for a {@link ForestConfig} it walks
     * {@code entries()} (each holder points at a {@code ForestConfig.Entry} feature); a feature whose
     * config is directly a {@link ForestConfig.Entry} is also registered (addon diagnostics).
     */
    public static TFCTreeSpeciesRegistry build(RegistryAccess registryAccess)
    {
        try
        {
            var registry = registryAccess.registryOrThrow(Registries.CONFIGURED_FEATURE);
            Set<ResourceLocation> species = new HashSet<>();
            Map<ResourceLocation, ForestConfig.Entry> entries = new HashMap<>();
            int forestConfigCount = 0;
            int entryFeatureCount = 0;

            for (Map.Entry<net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>>, ConfiguredFeature<?, ?>> e : registry.entrySet())
            {
                FeatureConfiguration config = e.getValue().config();
                if (config instanceof ForestConfig forestConfig)
                {
                    forestConfigCount++;
                    for (Holder<ConfiguredFeature<?, ?>> holder : forestConfig.entries())
                    {
                        if (holder.value().config() instanceof ForestConfig.Entry entry)
                        {
                            ResourceLocation sp = speciesFromHolder(holder, entry);
                            species.add(sp);
                            entries.putIfAbsent(sp, entry);
                        }
                    }
                }
                else if (config instanceof ForestConfig.Entry entry)
                {
                    entryFeatureCount++;
                    ResourceLocation sp = normalize(e.getKey().location());
                    species.add(sp);
                    entries.putIfAbsent(sp, entry);
                }
            }

            List<ResourceLocation> sorted = new ArrayList<>(species);
            sorted.sort(Comparator.comparing(ResourceLocation::toString));
            TFCTreeSpeciesRegistry result = new TFCTreeSpeciesRegistry(sorted, entries);
            logDiagnostics(forestConfigCount, entryFeatureCount, sorted);
            return result;
        }
        catch (Exception ex)
        {
            WorldPreview.LOGGER.warn("[TFC] Failed to build tree species registry; using fallback", ex);
            return fallback();
        }
    }

    /** Registry containing only the known TFC species (used before a world loads or on failure). */
    public static TFCTreeSpeciesRegistry fallback()
    {
        List<ResourceLocation> sorted = new ArrayList<>(KNOWN_COLORS.keySet());
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        return new TFCTreeSpeciesRegistry(sorted, Map.of());
    }

    // ---------------------------------------------------------------- species id derivation

    /**
     * Species location for a {@code ForestConfig.Entry} holder. Prefers the entry holder's own key,
     * falling back to its {@code normal_tree} feature key. E.g. {@code tfc:tree/oak_entry -> tfc:oak},
     * {@code tfc:tree/dead_oak_entry -> tfc:oak}, {@code afc:tree/baobab_entry -> afc:baobab}.
     */
    public static ResourceLocation speciesFromHolder(Holder<ConfiguredFeature<?, ?>> holder, ForestConfig.Entry entry)
    {
        ResourceLocation key = holder.unwrapKey()
            .map(k -> k.location())
            .orElseGet(() -> entry.treeFeature().unwrapKey().map(k -> k.location()).orElse(null));
        return key == null ? tfc("tree") : normalize(key);
    }

    private static ResourceLocation normalize(ResourceLocation key)
    {
        String path = key.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0)
        {
            path = path.substring(slash + 1); // drop "tree/" (or any) prefix
        }
        if (path.endsWith("_entry"))
        {
            path = path.substring(0, path.length() - "_entry".length());
        }
        if (path.startsWith("dead_"))
        {
            path = path.substring("dead_".length());
        }
        if (path.isEmpty())
        {
            path = "tree";
        }
        return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), path); // keep namespace
    }

    // ---------------------------------------------------------------- accessors

    public int size()
    {
        return this.idToSpecies.size();
    }

    public Optional<Short> idFor(ResourceLocation species)
    {
        return Optional.ofNullable(this.speciesToId.get(species));
    }

    @Nullable
    public ResourceLocation speciesFor(short id)
    {
        return id >= 0 && id < this.idToSpecies.size() ? this.idToSpecies.get(id) : null;
    }

    @Nullable
    public ForestConfig.Entry entryFor(ResourceLocation species)
    {
        return this.entryBySpecies.get(species);
    }

    public int color(short id)
    {
        ResourceLocation rl = speciesFor(id);
        if (rl == null)
        {
            return TFCSampleUtils.COLOR_INVALID;
        }
        Integer known = KNOWN_COLORS.get(rl);
        return known != null ? known : fallbackColor(rl);
    }

    private static int fallbackColor(ResourceLocation rl)
    {
        int idx = Math.floorMod(rl.toString().hashCode(), FALLBACK_PALETTE.length);
        return FALLBACK_PALETTE[idx];
    }

    /** Clean display name; addon (non-tfc) species get a {@code [namespace]} suffix to disambiguate. */
    public String name(short id)
    {
        ResourceLocation rl = speciesFor(id);
        if (rl == null)
        {
            return "Unknown";
        }
        String pretty = titleCase(rl.getPath());
        String ns = rl.getNamespace();
        return ns.equals("tfc") || ns.equals("minecraft") ? pretty : pretty + " [" + ns + "]";
    }

    private static String titleCase(String path)
    {
        String raw = path.replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++)
        {
            char ch = raw.charAt(i);
            if (cap && Character.isLetter(ch)) { sb.append(Character.toUpperCase(ch)); cap = false; }
            else { sb.append(ch); }
            if (ch == ' ') cap = true;
        }
        return sb.toString();
    }

    private static void logDiagnostics(int forestConfigCount, int entryFeatureCount, List<ResourceLocation> sorted)
    {
        WorldPreview.LOGGER.info(
            "[TFC] Tree species registry built: {} ForestConfig features, {} direct ForestConfig.Entry features, {} species",
            forestConfigCount, entryFeatureCount, sorted.size());

        StringBuilder first = new StringBuilder();
        for (int i = 0; i < Math.min(8, sorted.size()); i++)
        {
            first.append(i).append('=').append(sorted.get(i)).append(' ');
        }
        WorldPreview.LOGGER.info("[TFC] Tree species ids: {}", first.toString().trim());

        List<ResourceLocation> addon = sorted.stream()
            .filter(r -> !r.getNamespace().equals("tfc") && !r.getNamespace().equals("minecraft"))
            .toList();
        if (!addon.isEmpty())
        {
            WorldPreview.LOGGER.info("[TFC] Addon tree species detected ({}): {}", addon.size(), addon);
        }
    }
}
