package caeruleustait.world.preview.backend.storage;

import java.io.Serializable;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public abstract class PreviewSection implements Serializable {
   public static final int SIZE = 64;
   public static final int MASK = -64;
   public static final int HALF_SIZE = 32;
   private final int quartX;
   private final int quartZ;
   private final int chunkX;
   private final int chunkZ;
   private final BitSet completed = new BitSet(512);

   protected PreviewSection(int quartX, int quartZ) {
      this.quartX = quartX & MASK;
      this.quartZ = quartZ & MASK;
      this.chunkX = this.quartX >> 2;
      this.chunkZ = this.quartZ >> 2;
   }

   public abstract int size();

   public abstract short get(int var1, int var2);

   public abstract void set(int var1, int var2, short var3);

   public abstract List<PreviewStruct> structures();

   public abstract void addStructure(PreviewStruct var1);

   public synchronized boolean isCompleted(ChunkPos chunkPos) {
      return this.completed.get((chunkPos.x - this.chunkX) * 16 + (chunkPos.z - this.chunkZ));
   }

   public synchronized void markCompleted(ChunkPos chunkPos) {
      this.completed.set((chunkPos.x - this.chunkX) * 16 + (chunkPos.z - this.chunkZ));
   }

   public AccessData calcQuartOffsetData(int minQuartX, int minQuartZ, int maxQuartX, int maxQuartZ) {
      int accessMinX = minQuartX - this.quartX;
      int accessMinZ = minQuartZ - this.quartZ;
      int accessMaxX = maxQuartX - this.quartX;
      int accessMaxZ = maxQuartZ - this.quartZ;
      return new AccessData(accessMinX, accessMinZ, Math.min(accessMaxX, SIZE), Math.min(accessMaxZ, SIZE), accessMaxX > SIZE, accessMaxZ > SIZE);
   }

   public int quartX() {
      return this.quartX;
   }

   public int quartZ() {
      return this.quartZ;
   }


   public record AccessData(int minX, int minZ, int maxX, int maxZ, boolean continueX, boolean continueZ) implements Serializable {
   }

   public record PreviewStruct(BlockPos center, short structureId, BoundingBox boundingBox) {
   }
}
