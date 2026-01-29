package caeruleustait.world.preview.backend.storage;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

public abstract class PreviewSectionCompressed extends PreviewSection {
   @Serial
   private static final long serialVersionUID = 6458820535476205432L;
   private final int size;
   private short[] data = new short[1];
   private short[] mapData = new short[0];
   private transient short lastIdx = 0;

   public PreviewSectionCompressed(int quartX, int quartZ, int size) {
      super(quartX, quartZ);
      this.size = size;
      this.data[0] = -32768;
   }

   public abstract int xzToIdx(int var1, int var2);

   @Override
   public short get(int x, int z) {
      int idx = this.xzToIdx(x, z);

      try {
         return this.getReal(idx);
      } catch (IndexOutOfBoundsException var5) {
         return -32768;
      }
   }

   private short getReal(int idx) {
      return switch (this.mapData.length) {
         case 0 -> this.data[0];
         case 1 -> this.data[idx];
         case 4 -> {
            short word = this.data[idx >> 3];
            int map_idx = word >> ((idx & 7) << 1) & 3;
            yield this.mapData[map_idx];
         }
         case 16 -> {
            short word = this.data[idx >> 2];
            int map_idx = word >> ((idx & 3) << 2) & 15;
            yield this.mapData[map_idx];
         }
         case 256 -> {
            short word = this.data[idx >> 1];
            int map_idx = word >> ((idx & 1) << 3) & 0xFF;
            yield this.mapData[map_idx];
         }
         default -> throw new IllegalStateException("Unexpected value: " + this.mapData.length);
      };
   }

   private void internalSetData(int x, int z, short value) {
      int idx = this.xzToIdx(x, z);
      switch (this.mapData.length) {
         case 0:
            this.data[0] = value;
            break;
         case 1:
            this.data[idx] = value;
            break;
         case 4: {
            int didx = idx >> 3;
            int shift = (idx & 7) << 1;
            int mask = ~(3 << shift);
            this.data[didx] = (short)(this.data[didx] & mask | (value & 3) << shift);
            break;
         }
         case 16: {
            int didx = idx >> 2;
            int shift = (idx & 3) << 2;
            int mask = ~(15 << shift);
            this.data[didx] = (short)(this.data[didx] & mask | (value & 15) << shift);
            break;
         }
         case 256: {
            int didx = idx >> 1;
            int shift = (idx & 1) << 3;
            int mask = ~(255 << shift);
            this.data[didx] = (short)(this.data[didx] & mask | (value & 255) << shift);
            break;
         }
         default:
            throw new IllegalStateException("Unexpected value: " + this.mapData.length);
      }
   }

   private short cacheMapIdx(short value) {
      if (this.mapData[this.lastIdx] == value) {
         return this.lastIdx;
      } else {
         for (short i = 0; i < this.mapData.length; i++) {
            if (value == this.mapData[i]) {
               return this.lastIdx = i;
            }

            if (this.mapData[i] == -32768) {
               this.mapData[i] = value;
               return this.lastIdx = i;
            }
         }
         return switch (this.mapData.length) {
            case 4 -> {
               short[] newMapData = Arrays.copyOf(this.mapData, 16);
               newMapData[4] = value;
               Arrays.fill(newMapData, 5, 16, (short)-32768);
               short[] newData = new short[this.data.length * 2];

               for (int i = 0; i < this.data.length; i++) {
                  short s = this.data[i];
                  newData[i * 2] = (short)(s & 3 | (s >> 2 & 3) << 4 | (s >> 4 & 3) << 8 | (s >> 6 & 3) << 12);
                  newData[i * 2 + 1] = (short)(s >> 8 & 3 | (s >> 10 & 3) << 4 | (s >> 12 & 3) << 8 | (s >> 14 & 3) << 12);
               }

               this.mapData = newMapData;
               this.data = newData;
               yield 4;
            }
            case 16 -> {
               short[] newMapData = Arrays.copyOf(this.mapData, 256);
               newMapData[16] = value;
               Arrays.fill(newMapData, 17, 256, (short)-32768);
               short[] newData = new short[this.data.length * 2];

               for (int i = 0; i < this.data.length; i++) {
                  short s = this.data[i];
                  newData[i * 2] = (short)(s & 15 | (s >> 4 & 15) << 8);
                  newData[i * 2 + 1] = (short)(s >> 8 & 15 | (s >> 12 & 15) << 8);
               }

               this.mapData = newMapData;
               this.data = newData;
               yield 16;
            }
            case 256 -> {
               short[] newData = new short[this.data.length * 2];

               for (int i = 0; i < this.data.length; i++) {
                  short s = this.data[i];
                  newData[i * 2] = this.mapData[s & 255];
                  newData[i * 2 + 1] = this.mapData[s >> 8 & 0xFF];
               }

               this.mapData = new short[1];
               this.data = newData;
               yield value;
            }
            default -> throw new IllegalStateException("Unexpected value: " + this.mapData.length);
         };
      }
   }

   @Override
   public synchronized void set(int x, int z, short biome) {
      if (this.mapData.length == 0) {
         if (this.data[0] != biome) {
            if (this.data[0] == -32768) {
               this.data[0] = biome;
            } else {
               short[] newData = new short[this.size * this.size >> 3];
               Arrays.fill(newData, (short)0);
               this.mapData = new short[]{this.data[0], biome, -32768, -32768};
               this.data = newData;
               this.internalSetData(x, z, (short)1);
            }
         }
      } else if (this.mapData.length == 1) {
         this.data[this.xzToIdx(x, z)] = biome;
      } else {
         this.internalSetData(x, z, this.cacheMapIdx(biome));
      }
   }

   @Override
   public int size() {
      return this.size;
   }

   @Override
   public List<PreviewStruct> structures() {
      throw new NotImplementedException();
   }

   @Override
   public void addStructure(PreviewStruct structureData) {
      throw new NotImplementedException();
   }

   public synchronized short mapSize() {
      short s;
      for (s = 0; s < this.mapData.length; s++) {
         if (this.mapData[s] == -32768) {
            return s;
         }
      }

      return s;
   }

   public static class Full extends PreviewSectionCompressed {
      public Full(int quartX, int quartZ) {
         super(quartX, quartZ, SIZE);
      }

      @Override
      public int xzToIdx(int x, int z) {
         return x * SIZE + z;
      }
   }

   public static class Half extends PreviewSectionCompressed {
      public Half(int quartX, int quartZ) {
         super(quartX, quartZ, HALF_SIZE);
      }

      @Override
      public int xzToIdx(int x, int z) {
         return (x >> 1) * 32 + (z >> 1);
      }
   }

   public static class Quarter extends PreviewSectionCompressed {
      public Quarter(int quartX, int quartZ) {
         super(quartX, quartZ, 16);
      }

      @Override
      public int xzToIdx(int x, int z) {
         return (x >> 2) * 16 + (z >> 2);
      }
   }
}
