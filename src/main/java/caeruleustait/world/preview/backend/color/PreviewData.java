package caeruleustait.world.preview.backend.color;

import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record PreviewData(
   BiomeData[] biomeId2BiomeData,
   StructureData[] structId2StructData,
   Object2ShortMap<String> biome2Id,
   Object2ShortMap<String> struct2Id,
   List<HeightmapPresetData> heightmapPresets,
   Map<String, ColorMap> colorMaps
) {
   public record BiomeData(
      int id,
      ResourceLocation tag,
      int color,
      int resourceOnlyColor,
      boolean isCave,
      boolean resourceOnlyIsCave,
      String name,
      String resourceOnlyName,
      DataSource dataSource
   ) {
   }

   public enum DataSource {
      MISSING,
      RESOURCE,
      CONFIG
   }

   public record HeightmapPresetData(String name, int minY, int maxY) {
   }

   public record StructureData(
      int id, ResourceLocation tag, String name, ResourceLocation icon, ResourceLocation item, boolean showByDefault, DataSource dataSource
   ) {
   }
}
