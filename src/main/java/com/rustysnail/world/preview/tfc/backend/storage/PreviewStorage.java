package com.rustysnail.world.preview.tfc.backend.storage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;

public class PreviewStorage implements Serializable
{
    public static final int FLAG_BITS = 8;
    public static final long FLAG_MASK = 0xFFL;
    @Serial
    private static final long serialVersionUID = -275836689822028264L;
    private static final int COORD_BITS = 28;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1L;

    public static long quartPosToSectionLong(long quartX, long quartZ, long flags)
    {
        long sX = quartX >> 11;
        long sZ = quartZ >> 11;
        return (sX & COORD_MASK) << (COORD_BITS + FLAG_BITS)
            | (sZ & COORD_MASK) << FLAG_BITS
            | (flags & FLAG_MASK);
    }

    private final int yMin;
    private final int yMax;
    private transient Long2ObjectMap<PreviewBlock>[] blocks;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public PreviewStorage(int yMin, int yMax)
    {
        this.blocks = (Long2ObjectMap<PreviewBlock>[]) new Long2ObjectMap[(yMax - yMin >> 3) + 1];

        for (int i = 0; i < this.blocks.length; i++)
        {
            this.blocks[i] = new Long2ObjectOpenHashMap<>(1024, 0.5F);
        }

        this.yMin = yMin;
        this.yMax = yMax;
    }

    public void invalidateFlags(long... flags)
    {
        for (long f : flags)
        {
            long wanted = f & FLAG_MASK;

            for (Long2ObjectMap<PreviewBlock> block : this.blocks)
            {
                synchronized (block)
                {
                    var it = block.keySet().longIterator();
                    while (it.hasNext())
                    {
                        long key = it.nextLong();
                        if ((key & FLAG_MASK) == wanted)
                        {
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    public void invalidateAll()
    {
        for (Long2ObjectMap<PreviewBlock> block : this.blocks)
        {
            synchronized (block)
            {
                block.clear();
            }
        }
    }

    public PreviewSection section4(ChunkPos chunkPos, int y, long flags)
    {
        int quartX = QuartPos.fromSection(chunkPos.x);
        int indexY = y - this.yMin >> 3;
        int quartZ = QuartPos.fromSection(chunkPos.z);
        PreviewBlock block;
        synchronized (this.blocks[indexY])
        {
            block = this.blocks[indexY].computeIfAbsent(quartPosToSectionLong(quartX, quartZ, flags), x -> new PreviewBlock(flags));
        }

        return block.get(quartX, quartZ);
    }

    public PreviewSection section4(int quartX, int quartY, int quartZ, long flags)
    {
        int indexY = QuartPos.toBlock(quartY) - this.yMin >> 3;
        PreviewBlock block;
        synchronized (this.blocks[indexY])
        {
            block = this.blocks[indexY].computeIfAbsent(quartPosToSectionLong(quartX, quartZ, flags), x -> new PreviewBlock(flags));
        }

        return block.get(quartX, quartZ);
    }

    public short getRawData4(int quartX, int quartY, int quartZ, long flags)
    {
        int indexY = QuartPos.toBlock(quartY) - this.yMin >> 3;
        PreviewBlock block;
        synchronized (this.blocks[indexY])
        {
            block = this.blocks[indexY].get(quartPosToSectionLong(quartX, quartZ, flags));
        }

        if (block == null)
        {
            return -32768;
        }
        else
        {
            PreviewSection section = block.get(quartX, quartZ);
            return section.get(quartX - section.quartX(), quartZ - section.quartZ());
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream oos) throws IOException
    {
        oos.defaultWriteObject();
        oos.writeInt(this.blocks.length);

        for (Long2ObjectMap<PreviewBlock> ySec : this.blocks)
        {
            ObjectSet<Entry<PreviewBlock>> entrySet = ySec.long2ObjectEntrySet();
            oos.writeInt(entrySet.size());

            for (Entry<PreviewBlock> entry : entrySet)
            {
                oos.writeLong(entry.getLongKey());
                oos.writeObject(entry.getValue());
            }
        }
    }

    @Serial
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException
    {
        ois.defaultReadObject();
        this.blocks = (Long2ObjectMap<PreviewBlock>[]) new Long2ObjectMap[(this.yMax - this.yMin >> 3) + 1];
        int serializedSize = ois.readInt();
        if (serializedSize != this.blocks.length)
        {
            throw new IOException("serializedSize != sections.length: " + serializedSize + " != " + this.blocks.length);
        }
        else
        {
            for (int i = 0; i < this.blocks.length; i++)
            {
                this.blocks[i] = new Long2ObjectOpenHashMap<>(1024, 0.5F);
                int size = ois.readInt();

                for (int j = 0; j < size; j++)
                {
                    long key = ois.readLong();
                    PreviewBlock section = (PreviewBlock) ois.readObject();
                    this.blocks[i].put(key, section);
                }
            }
        }
    }
}
