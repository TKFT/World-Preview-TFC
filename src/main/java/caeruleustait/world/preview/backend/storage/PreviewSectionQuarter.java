package caeruleustait.world.preview.backend.storage;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

public class PreviewSectionQuarter extends PreviewSection {
   @Serial
   private static final long serialVersionUID = -5498800134519414529L;
   private final short[] data = new short[256];

   public PreviewSectionQuarter(int quartX, int quartZ) {
      super(quartX, quartZ);
      Arrays.fill(this.data, (short)-32768);
   }

   @Override
   public short get(int x, int z) {
      return this.data[(x >> 2) * 16 + (z >> 2)];
   }

   @Override
   public void set(int x, int z, short biome) {
      this.data[(x >> 2) * 16 + (z >> 2)] = biome;
   }

   @Override
   public int size() {
      return 16;
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
