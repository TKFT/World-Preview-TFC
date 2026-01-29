package caeruleustait.world.preview.backend.storage;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

public class PreviewSectionHalf extends PreviewSection {
   @Serial
   private static final long serialVersionUID = 2274224369048667840L;
   private final short[] data = new short[1024];

   public PreviewSectionHalf(int quartX, int quartZ) {
      super(quartX, quartZ);
      Arrays.fill(this.data, (short)-32768);
   }

   @Override
   public short get(int x, int z) {
      return this.data[(x >> 1) * 32 + (z >> 1)];
   }

   @Override
   public void set(int x, int z, short biome) {
      this.data[(x >> 1) * 32 + (z >> 1)] = biome;
   }

   @Override
   public int size() {
      return 32;
   }

   @Override
   public List<PreviewStruct> structures() {
      throw new NotImplementedException();
   }

   @Override
   public void addStructure(PreviewStruct structureData) {
      throw new NotImplementedException();
   }
}
