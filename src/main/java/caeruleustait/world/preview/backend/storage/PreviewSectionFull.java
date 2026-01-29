package caeruleustait.world.preview.backend.storage;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

public class PreviewSectionFull extends PreviewSection {
   @Serial
   private static final long serialVersionUID = 6458820535476205432L;
   private final short[] data = new short[SIZE * SIZE];

   public PreviewSectionFull(int quartX, int quartZ) {
      super(quartX, quartZ);
      Arrays.fill(this.data, (short)-32768);
   }

   @Override
   public short get(int x, int z) {
      return this.data[x * SIZE + z];
   }

   @Override
   public void set(int x, int z, short biome) {
      this.data[x * SIZE + z] = biome;
   }

   @Override
   public int size() {
      return SIZE;
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
