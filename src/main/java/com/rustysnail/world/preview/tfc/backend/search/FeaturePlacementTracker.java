package com.rustysnail.world.preview.tfc.backend.search;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FeaturePlacementTracker
{

    private static final ThreadLocal<Boolean> RECORDING = ThreadLocal.withInitial(() -> false);

    private static final Map<ResourceLocation, Map<Long, Map<ResourceLocation, List<BlockPos>>>> DATA = new ConcurrentHashMap<>();

    private static volatile Set<ResourceLocation> WHITELIST = Set.of();
    private static final boolean RECORD_ALL_TFC = true;

    private FeaturePlacementTracker() {}

    public static boolean isRecording()
    {
        return RECORDING.get();
    }

    public static void setRecording(boolean recording)
    {
        RECORDING.set(recording);
    }

    public static void clearAll()
    {
        DATA.clear();
    }

    public static void setWhitelist(Set<ResourceLocation> whitelist)
    {
        WHITELIST = whitelist == null ? Set.of() : Set.copyOf(whitelist);
    }

    private static boolean shouldRecord(ResourceLocation id)
    {
        if (!WHITELIST.isEmpty()) return WHITELIST.contains(id);
        if (RECORD_ALL_TFC) return "tfc".equals(id.getNamespace());
        return false;
    }

    public static void onFeaturePlaced(WorldGenLevel level, ResourceLocation id, BlockPos pos)
    {
        if (!shouldRecord(id)) return;

        ResourceLocation dim = level.dimensionType().effectsLocation();
        long chunkKey = new ChunkPos(pos).toLong();

        DATA.computeIfAbsent(dim, d -> new ConcurrentHashMap<>())
            .computeIfAbsent(chunkKey, c -> new ConcurrentHashMap<>())
            .computeIfAbsent(id, f -> Collections.synchronizedList(new ArrayList<>()))
            .add(pos.immutable());
    }

    public static Map<ResourceLocation, List<BlockPos>> query(
        ResourceLocation dimension,
        ChunkPos min,
        ChunkPos max
    )
    {
        Map<Long, Map<ResourceLocation, List<BlockPos>>> dimMap = DATA.get(dimension);
        if (dimMap == null) return Map.of();

        Map<ResourceLocation, List<BlockPos>> out = new HashMap<>();
        for (int cx = min.x; cx <= max.x; cx++)
        {
            for (int cz = min.z; cz <= max.z; cz++)
            {
                long ck = ChunkPos.asLong(cx, cz);
                Map<ResourceLocation, List<BlockPos>> perChunk = dimMap.get(ck);
                if (perChunk == null) continue;

                for (var e : perChunk.entrySet())
                {
                    out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
                }
            }
        }
        return out;
    }
}
