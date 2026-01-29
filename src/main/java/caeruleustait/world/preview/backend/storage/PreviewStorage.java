package caeruleustait.world.preview.backend.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;

public class PreviewStorage implements Serializable {
    @Serial
    private static final long serialVersionUID = -275836689822028264L;
    public static final long FLAG_BITS = 4L;
    public static final long FLAG_MASK = 15L;
    public static final long XZ_BITS = 30L;
    public static final long XZ_MASK = 1073741823L;
    public static final long XZ_OFFSET = 536870912L;
    public static final long FLAG_SHIFT = 0L;
    public static final long Z_SHIFT = 4L;
    public static final long X_SHIFT = 34L;
    public static final long FLAG_BIOME = 0L;
    public static final long FLAG_STRUCT_START = 1L;
    public static final long FLAG_HEIGHT = 2L;
    public static final long FLAG_STRUCT_REF = 4L;
    public static final long FLAG_TFC_TEMPERATURE = 5L;
    public static final long FLAG_TFC_RAINFALL = 6L;
    public static final long FLAG_TFC_LAND_WATER = 7L;
    public static final long FLAG_TFC_ROCK_TOP = 8L;    // Surface rock layer
    public static final long FLAG_TFC_ROCK_MID = 9L;    // Middle rock layer
    public static final long FLAG_TFC_ROCK_BOT = 10L;   // Bottom rock layer
    public static final long FLAG_TFC_ROCK_TYPE = 11L;  // Rock type category (0=Ocean, 1=Volcanic, 2=Land, 3=Uplift)
    private transient Long2ObjectMap<PreviewBlock>[] blocks;
    private final int yMin;
    private final int yMax;

    @SuppressWarnings("unchecked")
    public PreviewStorage(int yMin, int yMax) {
        this.blocks = (Long2ObjectMap<PreviewBlock>[]) new Long2ObjectMap[(yMax - yMin >> 3) + 1];

        for (int i = 0; i < this.blocks.length; i++) {
            this.blocks[i] = new Long2ObjectOpenHashMap<>(1024, 0.5F);
        }

        this.yMin = yMin;
        this.yMax = yMax;
    }

    /**
     * Removes all cached preview blocks for the given flags.
     * <p>
     * Note: only the lowest 4 bits of each flag are used in the cache key.
     * This is intentional and matches how blocks are keyed internally.
     */
    public void invalidateFlags(long... flags) {
        if (flags == null) return;

        for (long f : flags) {
            long wanted = f & FLAG_MASK;

            // blocks is Long2ObjectMap<PreviewBlock>[] in this project
            for (Long2ObjectMap<PreviewBlock> yMap : this.blocks) {
                var it = yMap.keySet().longIterator();
                while (it.hasNext()) {
                    long key = it.nextLong();
                    if ((key & FLAG_MASK) == wanted) {
                        it.remove(); // removes the map entry for this key
                    }
                }
            }
        }
    }

    /**
     * Removes all cached preview blocks.
     * Used when resolution changes and all data needs to be regenerated.
     */
    public void invalidateAll() {
        for (Long2ObjectMap<PreviewBlock> yMap : this.blocks) {
            yMap.clear();
        }
    }



    public PreviewSection section4(BlockPos bp, long flags) {
        int quartX = QuartPos.fromBlock(bp.getX());
        int indexY = bp.getY() - this.yMin >> 3;
        int quartZ = QuartPos.fromBlock(bp.getZ());
        PreviewBlock block;
        synchronized (this.blocks[indexY]) {
            block = this.blocks[indexY].computeIfAbsent(quartPosToSectionLong(quartX, quartZ, flags), x -> new PreviewBlock(flags));
        }

        return block.get(quartX, quartZ);
    }

    public PreviewSection section4(ChunkPos chunkPos, int y, long flags) {
        int quartX = QuartPos.fromSection(chunkPos.x);
        int indexY = y - this.yMin >> 3;
        int quartZ = QuartPos.fromSection(chunkPos.z);
        PreviewBlock block;
        synchronized (this.blocks[indexY]) {
            block = this.blocks[indexY].computeIfAbsent(quartPosToSectionLong(quartX, quartZ, flags), x -> new PreviewBlock(flags));
        }

        return block.get(quartX, quartZ);
    }

    public PreviewSection section4(int quartX, int quartY, int quartZ, long flags) {
        int indexY = QuartPos.toBlock(quartY) - this.yMin >> 3;
        PreviewBlock block;
        synchronized (this.blocks[indexY]) {
            block = this.blocks[indexY].computeIfAbsent(quartPosToSectionLong(quartX, quartZ, flags), x -> new PreviewBlock(flags));
        }

        return block.get(quartX, quartZ);
    }

    public short getBiome4(BlockPos bp) {
        int quartX = QuartPos.fromBlock(bp.getX());
        int quartY = QuartPos.fromBlock(bp.getY());
        int quartZ = QuartPos.fromBlock(bp.getZ());
        return this.getRawData4(quartX, quartY, quartZ, 0L);
    }

    public short getRawData4(int quartX, int quartY, int quartZ, long flags) {
        int indexY = QuartPos.toBlock(quartY) - this.yMin >> 3;
        PreviewBlock block;
        synchronized (this.blocks[indexY]) {
            block = this.blocks[indexY].get(quartPosToSectionLong(quartX, quartZ, flags));
        }

        if (block == null) {
            return -32768;
        } else {
            PreviewSection section = block.get(quartX, quartZ);
            return section.get(quartX - section.quartX(), quartZ - section.quartZ());
        }
    }

    public static long blockPos2SectionLong(BlockPos bp, long flags) {
        return quartPosToSectionLong(QuartPos.fromBlock(bp.getX()), QuartPos.fromBlock(bp.getZ()), flags);
    }

    public static long quartPosToSectionLong(long quartX, long quartZ, long flags) {
        long sX = quartX >> 11;
        long sZ = quartZ >> 11;
        return (sX & 1073741823L) << 34 | (sZ & 1073741823L) << 4 | (flags & 15L) << 0;
    }

    public static long compressXYZ(long x, long z, long flags) {
        return (x & 1073741823L) << 34 | (z & 1073741823L) << 4 | (flags & 15L);
    }

    @Serial
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeInt(this.blocks.length);

        for (Long2ObjectMap<PreviewBlock> ySec : this.blocks) {
            ObjectSet<Entry<PreviewBlock>> entrySet = ySec.long2ObjectEntrySet();
            oos.writeInt(entrySet.size());

            for (Entry<PreviewBlock> entry : entrySet) {
                oos.writeLong(entry.getLongKey());
                oos.writeObject(entry.getValue());
            }
        }
    }

    @Serial
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        this.blocks = (Long2ObjectMap<PreviewBlock>[]) new Long2ObjectMap[(this.yMax - this.yMin >> 3) + 1];
        int serializedSize = ois.readInt();
        if (serializedSize != this.blocks.length) {
            throw new IOException("serializedSize != sections.length: " + serializedSize + " != " + this.blocks.length);
        } else {
            for (int i = 0; i < this.blocks.length; i++) {
                this.blocks[i] = new Long2ObjectOpenHashMap<>(1024, 0.5F);
                int size = ois.readInt();

                for (int j = 0; j < size; j++) {
                    long key = ois.readLong();
                    PreviewBlock section = (PreviewBlock) ois.readObject();
                    this.blocks[i].put(key, section);
                }
            }
        }
    }

    public List<Short> compressionStatistics() {
        List<Short> res = new ArrayList<>();

        for (Long2ObjectMap<PreviewBlock> blockMap : this.blocks) {

            for (PreviewBlock block : blockMap.values()) {
                for (PreviewSection section : block.sections()) {
                    if (section instanceof PreviewSectionCompressed cSection) {
                        res.add(cSection.mapSize());
                    }
                }
            }
        }

        return res;
    }
}
