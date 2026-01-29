package caeruleustait.world.preview.backend.storage;

import caeruleustait.world.preview.RenderSettings;
import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import java.io.File;
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

public interface PreviewStorageCacheManager {
   //int CACHE_FORMAT_VERSION = 1;

   PreviewStorage loadPreviewStorage(long var1, int var3, int var4);

   void storePreviewStorage(long var1, PreviewStorage var3);

   Path cacheDir();

   default String cacheFileCompatPart() {
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

   default void clearCache() {
      try {
         try (Stream<Path> stream = Files.walk(this.cacheDir())) {
            stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
         }
      } catch (IOException var6) {
         throw new RuntimeException(var6);
      }
   }

   default void writeCacheFile(PreviewStorage storage, Path outFile) {
      Path outFileTmp = outFile.getParent().resolve(outFile.getFileName().toString() + ".tmp");
      WorldPreview.LOGGER.info("Writing preview data to {}", outFile);
      ZipEntry entry = new ZipEntry("bin");

      try (
         FileOutputStream fos = new FileOutputStream(outFileTmp.toFile());
         ZipOutputStream zos = new ZipOutputStream(fos)
      ) {
         zos.putNextEntry(entry);
         ObjectOutputStream oos = new ObjectOutputStream(zos);
         oos.writeObject(storage);
         zos.closeEntry();
      } catch (IOException var14) {
         WorldPreview.LOGGER.error("Failed to write cached preview data to {}", outFile, var14);
      }

      try {
         Files.move(outFileTmp, outFile, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var9) {
         WorldPreview.LOGGER.error("Failed to move cached preview data to {} --> {}", outFileTmp, outFile, var9);
      }
   }

   default PreviewStorage readCacheFile(int yMin, int yMax, Path inFile) {
      if (!Files.exists(inFile)) {
         return new PreviewStorage(yMin, yMax);
      } else {
         WorldPreview.LOGGER.info("Reading preview data from {}", inFile);

         try {
            PreviewStorage var7;
            try (
               FileInputStream fis = new FileInputStream(inFile.toFile());
               ZipInputStream zis = new ZipInputStream(fis)
            ) {
               zis.getNextEntry();
               ObjectInputStream ois = new ObjectInputStream(zis);
               var7 = (PreviewStorage)ois.readObject();
            }

            return var7;
         } catch (ClassNotFoundException | IOException var12) {
            WorldPreview.LOGGER.error("Failed to read cached preview data from {}", inFile, var12);
            return new PreviewStorage(yMin, yMax);
         }
      }
   }
}
