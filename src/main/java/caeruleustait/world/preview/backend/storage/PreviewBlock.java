package caeruleustait.world.preview.backend.storage;

import caeruleustait.world.preview.WorldPreview;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class PreviewBlock implements Serializable {
   @Serial
   private static final long serialVersionUID = -6140310220242894115L;
   //public static final int PREVIEW_BLOCK_SHIFT = 5;
   //public static final int PREVIEW_BLOCK_SIZE = 32;
   //public static final int PREVIEW_BLOCK_MASK = 31;
   private final long flags;
   private final PreviewSection[] sections = new PreviewSection[1024];

   public PreviewBlock(long flags) {
      this.flags = flags;
   }

   @NotNull
   public synchronized PreviewSection get(int quartX, int quartZ) {
      int idx = (quartX >> 6 & 31) * 32 + (quartZ >> 6 & 31);
      PreviewSection section = this.sections[idx];
      if (section == null) {
         section = this.sections[idx] = this.sectionFactory(quartX, quartZ);
      }

      return section;
   }

   private PreviewSection sectionFactory(int quartX, int quartZ) {
      if (this.flags == 1L) {
         return new PreviewSectionStructure(quartX, quartZ);
      } else {
         int quartStride = WorldPreview.get().renderSettings().quartStride();
         if (WorldPreview.get().cfg().enableCompression) {
            return (switch (quartStride) {
               case 1 -> new PreviewSectionCompressed.Full(quartX, quartZ);
               case 2 -> new PreviewSectionCompressed.Half(quartX, quartZ);
               case 4 -> new PreviewSectionCompressed.Quarter(quartX, quartZ);
               default -> throw new IllegalStateException("Unexpected quartStride value: " + quartStride);
            });
         } else {
            return (switch (quartStride) {
               case 1 -> new PreviewSectionFull(quartX, quartZ);
               case 2 -> new PreviewSectionHalf(quartX, quartZ);
               case 4 -> new PreviewSectionQuarter(quartX, quartZ);
               default -> throw new IllegalStateException("Unexpected quartStride value: " + quartStride);
            });
         }
      }
   }

   public PreviewSection[] sections() {
      return Arrays.copyOf(this.sections, this.sections.length);
   }
}
