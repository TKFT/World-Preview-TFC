package com.rustysnail.world.preview.tfc.backend.storage;

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
import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.WorldPreviewConfig;

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
        // Cache format bump: the combined TFC work unit now records completion on a dedicated flag
        // (TFC_GENERATION_COMPLETE_FLAG) instead of the temperature section. Changing the cache key
        // makes pre-bump caches (whose completion markers meant "temperature only") be ignored, so
        // forest/tree sections are regenerated rather than served empty.
        flags |= 131072L;
        // Second bump: tree-species ids are now assigned by the runtime TFCTreeSpeciesRegistry
        // (ResourceLocation-sorted) and special water/invalid values moved to 32760-32763, so stored
        // tree-species values from older caches are no longer meaningful.
        flags |= 262144L;
        // Third bump (Commit H0): the PreviewStorage section-flag namespace widened from 4 to 8 bits
        // and the packed section-key layout changed (28-bit sX << 36 | 28-bit sZ << 8 | 8-bit flag,
        // was 30-bit sX << 34 | 30-bit sZ << 4 | 4-bit flag). Old serialized keys are incompatible,
        // so pre-bump caches must be ignored (we do not migrate them).
        flags |= 524288L;
        // Fourth bump (Commit H1): new TFC_CROP_SUITABILITY section (flag 16). Crop sections are
        // crop-specific and session-only (invalidated whenever the selected crop / water mode changes),
        // so they must never be reinterpreted across a format change.
        flags |= 1048576L;
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
