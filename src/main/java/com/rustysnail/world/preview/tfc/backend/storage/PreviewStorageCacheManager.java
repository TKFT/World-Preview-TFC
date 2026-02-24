package com.rustysnail.world.preview.tfc.backend.storage;

import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public interface PreviewStorageCacheManager
{
    //int CACHE_FORMAT_VERSION = 1;

    PreviewStorage loadPreviewStorage(long seed, int yMin, int yMax);

    void storePreviewStorage(long seed, PreviewStorage storage);

    Path cacheDir();

    default String cacheFileCompatPart()
    {
        WorldPreview worldPreview = WorldPreview.get();
        RenderSettings settings = worldPreview.renderSettings();
        WorldPreviewConfig cfg = worldPreview.cfg();
        long flags = 0L;
        flags |= 1L;
        flags |= (settings.samplerType.ordinal() & 15) << 4;
        flags |= 1536L;
        flags |= 20480L;
        flags |= cfg.enableCompression ? 65536L : 0L;
        return String.format("%s-%d-%d", settings.dimension, settings.pixelsPerChunk(), flags)
            .replace(":", "_")
            .replace(";", "_")
            .replace("/", "_")
            .replace("\\", "_");
    }

    default void clearCache()
    {
        try
        {
            try (Stream<Path> stream = Files.walk(this.cacheDir()))
            {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try
                    {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException e)
                    {
                        WorldPreview.LOGGER.warn("Failed to delete cache path {}", path, e);
                    }
                });
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    default void writeCacheFile(PreviewStorage storage, Path outFile)
    {
        Path outFileTmp = outFile.getParent().resolve(outFile.getFileName().toString() + ".tmp");
        WorldPreview.LOGGER.info("Writing preview data to {}", outFile);
        ZipEntry entry = new ZipEntry("bin");

        try (
            FileOutputStream fos = new FileOutputStream(outFileTmp.toFile());
            ZipOutputStream zos = new ZipOutputStream(fos)
        )
        {
            zos.putNextEntry(entry);
            ObjectOutputStream oos = new ObjectOutputStream(zos);
            oos.writeObject(storage);
            zos.closeEntry();
        }
        catch (IOException e)
        {
            WorldPreview.LOGGER.error("Failed to write cached preview data to {}", outFile, e);
        }

        try
        {
            Files.move(outFileTmp, outFile, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            WorldPreview.LOGGER.error("Failed to move cached preview data to {} --> {}", outFileTmp, outFile, e);
        }
    }

    default PreviewStorage readCacheFile(int yMin, int yMax, Path inFile)
    {
        if (!Files.exists(inFile))
        {
            return new PreviewStorage(yMin, yMax);
        }
        WorldPreview.LOGGER.info("Reading preview data from {}", inFile);

        try (
            FileInputStream fis = new FileInputStream(inFile.toFile());
            ZipInputStream zis = new ZipInputStream(fis)
        )
        {
            zis.getNextEntry();
            ObjectInputStream ois = new ObjectInputStream(zis);
            return (PreviewStorage) ois.readObject();
        }
        catch (ClassNotFoundException | IOException e)
        {
            WorldPreview.LOGGER.error("Failed to read cached preview data from {}", inFile, e);
            return new PreviewStorage(yMin, yMax);
        }
    }
}
