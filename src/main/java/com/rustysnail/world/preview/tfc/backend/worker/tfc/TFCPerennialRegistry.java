package com.rustysnail.world.preview.tfc.backend.worker.tfc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.mixin.FruitTreeSaplingBlockAccessor;
import com.rustysnail.world.preview.tfc.mixin.SeasonalPlantBlockAccessor;
import com.rustysnail.world.preview.tfc.mixin.SpreadingBushBlockAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.common.blocks.plant.fruit.BananaSaplingBlock;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeSaplingBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.plant.fruit.SpreadingBushBlock;
import net.dries007.tfc.common.blocks.plant.fruit.StationaryBerryBushBlock;
import net.dries007.tfc.common.blocks.plant.fruit.WaterloggedBerryBushBlock;
import net.dries007.tfc.common.items.PlantableInfo;
import net.dries007.tfc.util.climate.ClimateRange;

/**
 * Runtime registry of repeatedly-producing plants. Discovery deliberately uses both item metadata
 * and block inheritance: addons such as Firmalife register ordinary {@link BlockItem}s for
 * {@link FruitTreeSaplingBlock} subclasses.
 */
public final class TFCPerennialRegistry
{
    private static volatile TFCPerennialRegistry active = new TFCPerennialRegistry(List.of());

    public static TFCPerennialRegistry active()
    {
        return active;
    }

    public static void setActive(TFCPerennialRegistry registry)
    {
        active = registry;
    }

    public static TFCPerennialRegistry build(ResourceManager resourceManager)
    {
        Map<ResourceLocation, ClimateRange> climateById = loadClimateRanges(resourceManager);
        Map<ResourceLocation, MutableEntry> found = new HashMap<>();

        // Pass A: prefer valid PlantableInfo fields.
        for (Item item : BuiltInRegistries.ITEM)
        {
            if (!(item instanceof BlockItem blockItem) || !(item instanceof PlantableInfo plantable))
            {
                continue;
            }
            Block block = blockItem.getBlock();
            if (block instanceof ICropBlock)
            {
                continue;
            }

            List<Lifecycle> lifecycle = safePlantableLifecycle(plantable);
            PerennialType knownType = typeOf(block);
            if (lifecycle == null && knownType == null)
            {
                continue;
            }

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            MutableEntry entry = found.computeIfAbsent(blockId, id -> new MutableEntry(itemId, blockId, block));
            entry.id = itemId;
            if (knownType != null)
            {
                entry.type = knownType;
                entry.habitat = habitatOf(knownType);
            }
            else
            {
                entry.type = PerennialType.ADDON_PERENNIAL;
                entry.habitat = PerennialHabitat.CUSTOM;
            }

            ClimateRange plantableClimate = safePlantableClimate(plantable);
            if (plantableClimate != null) entry.climateRange = plantableClimate;
            if (lifecycle != null) entry.lifecycle = lifecycle;
            int growthTicks = safePlantableGrowth(plantable);
            if (growthTicks > 0) entry.growthTicks = growthTicks;
        }

        // Pass B: block inheritance catches addon ordinary BlockItems. Most-specific checks matter.
        for (Block block : BuiltInRegistries.BLOCK)
        {
            PerennialType type = typeOf(block);
            if (type == null)
            {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            MutableEntry entry = found.computeIfAbsent(blockId, id -> new MutableEntry(blockId, blockId, block));
            entry.type = type;
            entry.habitat = habitatOf(type);
            mergeBlockAccessors(entry);
        }

        List<PerennialEntry> entries = new ArrayList<>(found.size());
        EnumMap<PerennialType, Integer> counts = new EnumMap<>(PerennialType.class);
        int missingClimate = 0;
        int missingLifecycle = 0;
        for (MutableEntry entry : found.values())
        {
            if (entry.climateRange == null)
            {
                entry.climateRange = resolveResourceClimate(entry.plantedBlockId, entry.type, climateById);
            }
            if (entry.climateRange == null) missingClimate++;
            if (entry.lifecycle == null) missingLifecycle++;
            counts.merge(entry.type, 1, Integer::sum);
            entries.add(entry.freeze());
        }

        entries.sort(
            Comparator.comparing((PerennialEntry e) -> e.type().ordinal())
                .thenComparing(PerennialEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.id().toString())
        );

        WorldPreview.LOGGER.info(
            "[TFC Perennial] Discovery summary: total={}, fruitTrees={}, bananas={}, stationaryBerries={}, "
                + "spreadingBerries={}, waterloggedBerries={}, addonPerennials={}, missingClimate={}, missingLifecycle={}",
            entries.size(),
            counts.getOrDefault(PerennialType.FRUIT_TREE, 0),
            counts.getOrDefault(PerennialType.BANANA, 0),
            counts.getOrDefault(PerennialType.STATIONARY_BERRY, 0),
            counts.getOrDefault(PerennialType.SPREADING_BERRY, 0),
            counts.getOrDefault(PerennialType.WATERLOGGED_BERRY, 0),
            counts.getOrDefault(PerennialType.ADDON_PERENNIAL, 0),
            missingClimate, missingLifecycle
        );
        return new TFCPerennialRegistry(entries);
    }

    static String normalizePlantName(String path)
    {
        int slash = path.lastIndexOf('/');
        String name = (slash >= 0 ? path.substring(slash + 1) : path).toLowerCase(Locale.ROOT);
        for (String suffix : List.of("_sapling", "_tree", "_bush", "_plant"))
        {
            if (name.endsWith(suffix))
            {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    @Nullable
    static ClimateRange resolveResourceClimate(
        ResourceLocation blockId,
        PerennialType type,
        Map<ResourceLocation, ClimateRange> climateById
    )
    {
        ResourceLocation expected = expectedClimateId(blockId, type);
        ClimateRange exact = climateById.get(expected);
        if (exact != null)
        {
            return exact;
        }

        String normalized = normalizePlantName(blockId.getPath());
        ClimateRange match = null;
        int matches = 0;
        for (Map.Entry<ResourceLocation, ClimateRange> candidate : climateById.entrySet())
        {
            if (candidate.getKey().getNamespace().equals(blockId.getNamespace())
                && normalizePlantName(candidate.getKey().getPath()).equals(normalized))
            {
                match = candidate.getValue();
                matches++;
            }
        }
        return matches == 1 ? match : null;
    }

    private static ResourceLocation expectedClimateId(ResourceLocation blockId, PerennialType type)
    {
        String path = blockId.getPath();
        if (type == PerennialType.FRUIT_TREE || type == PerennialType.BANANA)
        {
            if (path.endsWith("_sapling"))
            {
                path = path.substring(0, path.length() - "_sapling".length()) + "_tree";
            }
        }
        return ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), path);
    }

    private static Map<ResourceLocation, ClimateRange> loadClimateRanges(ResourceManager resourceManager)
    {
        Map<ResourceLocation, ClimateRange> out = new HashMap<>();
        final String directory = "tfc/climate_range/plant";
        final String prefix = "tfc/climate_range/";
        Map<ResourceLocation, Resource> files =
            resourceManager.listResources(directory, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet())
        {
            String path = file.getKey().getPath();
            if (!path.startsWith(prefix))
            {
                continue;
            }
            String relative = path.substring(prefix.length(), path.length() - ".json".length());
            ResourceLocation climateId =
                ResourceLocation.fromNamespaceAndPath(file.getKey().getNamespace(), relative);
            try (var in = file.getValue().open())
            {
                var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                ClimateRange.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .ifPresent(range -> out.put(climateId, range));
            }
            catch (Exception ignored)
            {
                // Datapack compatibility boundary. Missing values are counted in the one summary log.
            }
        }
        return out;
    }

    @Nullable
    private static ClimateRange safePlantableClimate(PlantableInfo plantable)
    {
        try
        {
            return plantable.getClimateRangeInfo();
        }
        catch (RuntimeException | LinkageError ignored)
        {
            return null;
        }
    }

    @Nullable
    private static List<Lifecycle> safePlantableLifecycle(PlantableInfo plantable)
    {
        try
        {
            List<Lifecycle> lifecycle = plantable.getLifecycleInfo();
            return lifecycle != null && lifecycle.size() == 12 ? List.copyOf(lifecycle) : null;
        }
        catch (RuntimeException | LinkageError ignored)
        {
            return null;
        }
    }

    private static int safePlantableGrowth(PlantableInfo plantable)
    {
        try
        {
            return plantable.getGrowthTimeInfo();
        }
        catch (RuntimeException | LinkageError ignored)
        {
            return -1;
        }
    }

    private static void mergeBlockAccessors(MutableEntry entry)
    {
        try
        {
            if (entry.block instanceof FruitTreeSaplingBlock)
            {
                FruitTreeSaplingBlockAccessor accessor = (FruitTreeSaplingBlockAccessor) (Object) entry.block;
                if (entry.climateRange == null) entry.climateRange = accessor.worldPreviewTfc$getClimateRange().get();
                if (entry.lifecycle == null) entry.lifecycle = immutableLifecycle(accessor.worldPreviewTfc$getStages());
                if (entry.growthTicks <= 0)
                {
                    Integer ticks = accessor.worldPreviewTfc$getTicksToGrow().get();
                    if (ticks != null && ticks > 0) entry.growthTicks = ticks;
                }
            }
            else if (entry.block instanceof StationaryBerryBushBlock)
            {
                SeasonalPlantBlockAccessor accessor = (SeasonalPlantBlockAccessor) (Object) entry.block;
                if (entry.climateRange == null) entry.climateRange = accessor.worldPreviewTfc$getClimateRange().get();
                if (entry.lifecycle == null) entry.lifecycle = immutableLifecycle(accessor.worldPreviewTfc$getLifecycle());
            }
        }
        catch (RuntimeException | LinkageError ignored)
        {
            // Compatibility boundary: a missing/changed accessor leaves resource fallback available.
        }
    }

    @Nullable
    private static List<Lifecycle> immutableLifecycle(@Nullable Lifecycle[] lifecycle)
    {
        return lifecycle != null && lifecycle.length == 12 ? List.of(lifecycle.clone()) : null;
    }

    @Nullable
    static PerennialType typeOf(Block block)
    {
        if (block instanceof WaterloggedBerryBushBlock) return PerennialType.WATERLOGGED_BERRY;
        if (block instanceof SpreadingBushBlock) return PerennialType.SPREADING_BERRY;
        if (block instanceof StationaryBerryBushBlock) return PerennialType.STATIONARY_BERRY;
        if (block instanceof BananaSaplingBlock) return PerennialType.BANANA;
        if (block instanceof FruitTreeSaplingBlock) return PerennialType.FRUIT_TREE;
        return null;
    }

    private static PerennialHabitat habitatOf(PerennialType type)
    {
        return type == PerennialType.WATERLOGGED_BERRY
            ? PerennialHabitat.FRESHWATER_WATERLOGGED
            : PerennialHabitat.NORMAL_LAND;
    }

    private static String deriveName(ResourceLocation id)
    {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        for (String suffix : List.of("_sapling", "_tree", "_bush", "_plant"))
        {
            if (name.endsWith(suffix))
            {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        StringBuilder title = new StringBuilder(name.length());
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (c == '_')
            {
                title.append(' ');
                capitalize = true;
            }
            else
            {
                title.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        if (!id.getNamespace().equals("tfc"))
        {
            title.append(" [").append(id.getNamespace()).append(']');
        }
        return title.toString();
    }

    private final List<PerennialEntry> entries;
    private final Map<ResourceLocation, PerennialEntry> byId;

    private TFCPerennialRegistry(List<PerennialEntry> entries)
    {
        this.entries = List.copyOf(entries);
        this.byId = new HashMap<>();
        for (PerennialEntry entry : entries)
        {
            this.byId.put(entry.id(), entry);
        }
    }

    public List<PerennialEntry> entries()
    {
        return this.entries;
    }

    @Nullable
    public PerennialEntry get(ResourceLocation id)
    {
        return this.byId.get(id);
    }

    @Nullable
    public PerennialEntry first()
    {
        return this.entries.isEmpty() ? null : this.entries.getFirst();
    }

    public int size()
    {
        return this.entries.size();
    }

    public enum PerennialType
    {
        FRUIT_TREE,
        BANANA,
        STATIONARY_BERRY,
        SPREADING_BERRY,
        WATERLOGGED_BERRY,
        ADDON_PERENNIAL
    }

    public enum PerennialHabitat
    {
        NORMAL_LAND,
        FRESHWATER_WATERLOGGED,
        CUSTOM
    }

    public record PerennialEntry(
        ResourceLocation id,
        ResourceLocation plantedBlockId,
        Block plantedBlock,
        String displayName,
        PerennialType type,
        PerennialHabitat habitat,
        @Nullable ClimateRange climateRange,
        @Nullable List<Lifecycle> lifecycle,
        int growthTicks
    )
    {
        public PerennialEntry
        {
            lifecycle = lifecycle == null ? null : List.copyOf(lifecycle);
        }

        public boolean hasClimateData()
        {
            return climateRange != null;
        }

        public boolean propagates()
        {
            return type == PerennialType.SPREADING_BERRY;
        }

        public int spreadingMaxHeight()
        {
            if (!(plantedBlock instanceof SpreadingBushBlock))
            {
                return 0;
            }
            try
            {
                return ((SpreadingBushBlockAccessor) (Object) plantedBlock).worldPreviewTfc$getMaxHeight();
            }
            catch (RuntimeException | LinkageError ignored)
            {
                return 0;
            }
        }
    }

    private static final class MutableEntry
    {
        ResourceLocation id;
        final ResourceLocation plantedBlockId;
        final Block block;
        PerennialType type = PerennialType.ADDON_PERENNIAL;
        PerennialHabitat habitat = PerennialHabitat.CUSTOM;
        @Nullable ClimateRange climateRange;
        @Nullable List<Lifecycle> lifecycle;
        int growthTicks = -1;

        MutableEntry(ResourceLocation id, ResourceLocation plantedBlockId, Block block)
        {
            this.id = id;
            this.plantedBlockId = plantedBlockId;
            this.block = block;
        }

        PerennialEntry freeze()
        {
            return new PerennialEntry(
                id, plantedBlockId, block, deriveName(plantedBlockId), type, habitat,
                climateRange, lifecycle, growthTicks
            );
        }
    }
}
