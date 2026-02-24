package com.rustysnail.world.preview.tfc.backend.storage;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.NotImplementedException;

public class PreviewSectionStructure extends PreviewSection
{
    @Serial
    private static final long serialVersionUID = -3170004481651979128L;
    private transient List<PreviewStruct> structures = new ArrayList<>();
    private transient List<PreviewFeature> features = new ArrayList<>();

    @Serial
    private void writeObject(ObjectOutputStream oos) throws IOException
    {
        oos.defaultWriteObject();
        SerializablePreviewStruct[] res = this.structures
            .stream()
            .map(SerializablePreviewStruct::fromStruct)
            .toArray(SerializablePreviewStruct[]::new);
        oos.writeObject(res);
        SerializablePreviewFeature[] featureRes = this.features
            .stream()
            .map(SerializablePreviewFeature::fromFeature)
            .toArray(SerializablePreviewFeature[]::new);
        oos.writeObject(featureRes);
    }

    @Serial
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException
    {
        ois.defaultReadObject();
        SerializablePreviewStruct[] res = (SerializablePreviewStruct[]) ois.readObject();
        this.structures = Arrays.stream(res).map(SerializablePreviewStruct::toStruct).collect(Collectors.toList());
        try
        {
            SerializablePreviewFeature[] featureRes = (SerializablePreviewFeature[]) ois.readObject();
            this.features = Arrays.stream(featureRes).map(SerializablePreviewFeature::toFeature).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            // Handle older serialized data without features
            this.features = new ArrayList<>();
        }
    }

    public PreviewSectionStructure(int quartX, int quartZ)
    {
        super(quartX, quartZ);
    }

    @Override
    public synchronized List<PreviewStruct> structures()
    {
        return new ArrayList<>(this.structures);
    }

    @Override
    public synchronized void addStructure(PreviewStruct structureData)
    {
        this.structures.add(structureData);
    }

    @Override
    public synchronized List<PreviewFeature> features()
    {
        return new ArrayList<>(this.features);
    }

    @Override
    public synchronized void addFeature(PreviewFeature feature)
    {
        this.features.add(feature);
    }

    @Override
    public short get(int x, int z)
    {
        throw new NotImplementedException();
    }

    @Override
    public void set(int x, int z, short biome)
    {
        throw new NotImplementedException();
    }

    @Override
    public int size()
    {
        return this.structures.size();
    }

    record SerializablePreviewStruct(int cX, int cY, int cZ, short structureId, int bbMinX, int bbMinY, int bbMinZ, int bbMaxX, int bbMaxY, int bbMaxZ)
        implements Serializable
    {
        static SerializablePreviewStruct fromStruct(PreviewStruct s)
        {
            BlockPos c = s.center();
            BoundingBox bb = s.boundingBox();
            return new SerializablePreviewStruct(
                c.getX(), c.getY(), c.getZ(), s.structureId(), bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ()
            );
        }

        PreviewStruct toStruct()
        {
            return new PreviewStruct(
                new BlockPos(this.cX, this.cY, this.cZ),
                this.structureId,
                new BoundingBox(this.bbMinX, this.bbMinY, this.bbMinZ, this.bbMaxX, this.bbMaxY, this.bbMaxZ)
            );
        }
    }

    record SerializablePreviewFeature(short featureTypeId, int cX, int cY, int cZ)
        implements Serializable
    {
        static SerializablePreviewFeature fromFeature(PreviewFeature f)
        {
            BlockPos c = f.center();
            return new SerializablePreviewFeature(f.featureTypeId(), c.getX(), c.getY(), c.getZ());
        }

        PreviewFeature toFeature()
        {
            return new PreviewFeature(this.featureTypeId, new BlockPos(this.cX, this.cY, this.cZ));
        }
    }
}
