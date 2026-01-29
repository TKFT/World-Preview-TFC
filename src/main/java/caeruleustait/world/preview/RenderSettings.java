package caeruleustait.world.preview;

import caeruleustait.world.preview.backend.sampler.ChunkSampler;
import caeruleustait.world.preview.backend.sampler.FullQuartSampler;
import caeruleustait.world.preview.backend.sampler.QuarterQuartSampler;
import caeruleustait.world.preview.backend.sampler.SingleQuartSampler;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class RenderSettings {
   private BlockPos center = new BlockPos(0, 0, 0);
   private int quartExpand = 1;
   private int quartStride = 1;
   public SamplerType samplerType = SamplerType.AUTO;
   public ResourceLocation dimension = null;
   public boolean hideAllStructures = false;
   public transient RenderMode mode = RenderMode.BIOMES;
   public TFCMapQuality tfcMapQuality = TFCMapQuality.AUTO;

    public boolean tfcUseAccurateSampling() {
        if (tfcMapQuality == TFCMapQuality.ACCURATE) return true;
        if (tfcMapQuality == TFCMapQuality.FAST) return false;

        // AUTO
        return pixelsPerChunk() >= 4;  // tweak threshold as you like
    }


    public BlockPos center() {
      return this.center;
   }

   public void setCenter(BlockPos center) {
      this.center = center;
   }

   public void resetCenter() {
      this.center = new BlockPos(0, WorldPreview.get().workManager().yMax(), 0);
   }

   public int quartExpand() {
      return this.quartExpand;
   }

   public int quartStride() {
      return this.quartStride;
   }

   public int pixelsPerChunk() {
      return 4 * this.quartExpand / this.quartStride;
   }

   public void setPixelsPerChunk(int blocksPerChunk) {
      switch (blocksPerChunk) {
         case 1:
            this.quartExpand = 1;
            this.quartStride = 4;
            break;
         case 2:
            this.quartExpand = 1;
            this.quartStride = 2;
            break;
         case 4:
            this.quartExpand = 1;
            this.quartStride = 1;
            break;
         case 8:
            this.quartExpand = 2;
            this.quartStride = 1;
            break;
         case 16:
            this.quartExpand = 4;
            this.quartStride = 1;
            break;
         default:
            throw new RuntimeException("Invalid blocksPerChunk=" + blocksPerChunk);
      }
   }

   public enum RenderMode {
      BIOMES(0L, true),
      HEIGHTMAP(2L, false),
      // TFC-specific render modes (using flags 5-13)
      TFC_TEMPERATURE(5L, false),  // Mapping Temperature
      TFC_RAINFALL(6L, false),     // Mapping Rainfall
      TFC_LAND_WATER(7L, false),   // Simple land and water map TODO: Add support for rivers and lakes
      TFC_ROCK_TOP(8L, false),     // Surface/top rock layer (rock ID 0-19)
      TFC_ROCK_MID(9L, false),     // Middle rock layer (rock ID 0-19)
      TFC_ROCK_BOT(10L, false),    // Bottom rock layer (rock ID 0-19)
      TFC_ROCK_TYPE(11L, false),   // Rock type category (Ocean/Volcanic/Land/Uplift)
      TFC_KAOLINITE(12L, false),   // Kaolin Clay Spawning Areas
      TFC_TEST(13L, false);

      public final long flag;
      public final boolean useY;

      RenderMode(long flag, boolean useY) {
         this.flag = flag;
         this.useY = useY;
      }

      public boolean isTFC() {
         return this.name().startsWith("TFC_");
      }

   }

   public enum SamplerType {
      AUTO(x -> (switch (x) {
         case 1 -> new FullQuartSampler();
         case 2 -> new QuarterQuartSampler();
         case 4 -> new SingleQuartSampler();
         default -> throw new RuntimeException("Unsupported quart stride: " + x);
      })),
      FULL(x -> new FullQuartSampler()),
      QUARTER(x -> new QuarterQuartSampler()),
      SINGLE(x -> new SingleQuartSampler());

      private final IntFunction<ChunkSampler> samplerFactory;

      SamplerType(IntFunction<ChunkSampler> samplerFactory) {
         this.samplerFactory = samplerFactory;
      }

      public ChunkSampler create(int quartStride) {
         return this.samplerFactory.apply(quartStride);
      }
   }

    public enum TFCMapQuality {
        FAST,        // current method
        ACCURATE,    // multi-sample per sampler point
        AUTO         // choose based on zoom/resolution
    }

}
