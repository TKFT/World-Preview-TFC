package caeruleustait.world.preview.client.gui;

import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.client.gui.widgets.lists.BiomesList;
import caeruleustait.world.preview.client.gui.widgets.lists.StructuresList;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PreviewDisplayDataProvider {
   PreviewData previewData();

   BiomesList.BiomeEntry biome4Id(int var1);

   StructuresList.StructureEntry structure4Id(int var1);

   NativeImage[] structureIcons();

   NativeImage playerIcon();

   NativeImage spawnIcon();

   NativeImage worldSpawnIcon();

   ItemStack[] structureItems();

   void onBiomeVisuallySelected(BiomesList.BiomeEntry var1);

   void onVisibleBiomesChanged(Short2LongMap var1);

   void onVisibleStructuresChanged(Short2LongMap var1);

   void onVisibleRocksChanged(Short2LongMap var1);

   StructureRenderInfo[] renderStructureMap();

   int[] heightColorMap();

   //int[] noiseColorMap();

   int[] tfcTemperatureColorMap();

   int[] tfcRainfallColorMap();

   int yMin();

   int yMax();

   boolean isUpdating();

   boolean setupFailed();

   //void requestRefresh();

   @NotNull
   PreviewDisplayDataProvider.PlayerData getPlayerData(UUID var1);

   /**
    * Gets the world spawn center point.
    * For new world creation, this is typically (0, 0) or based on world settings.
    * For TFC worlds, this uses the spawn center from TFC settings.
    *
    * @return The world spawn center position, or null if not available
    */
   @Nullable
   BlockPos getWorldSpawnPos();

   /**
    * Gets the spawn search distance (radius around spawn center).
    * For TFC worlds, this is the spawnDistance setting.
    * For vanilla worlds, returns 0 (no spawn area to display).
    *
    * @return The spawn distance in blocks, or 0 if not applicable
    */
   int getWorldSpawnDistance();

   record PlayerData(@Nullable BlockPos currentPos, @Nullable BlockPos spawnPos) {
   }

   interface StructureRenderInfo {
      boolean show();
   }
}
