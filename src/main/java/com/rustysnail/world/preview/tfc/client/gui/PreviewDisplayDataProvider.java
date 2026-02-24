package com.rustysnail.world.preview.tfc.client.gui;

import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomesList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.StructuresList;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PreviewDisplayDataProvider
{
    PreviewData previewData();

    BiomesList.BiomeEntry biome4Id(int id);

    StructuresList.StructureEntry structure4Id(int id);

    NativeImage[] structureIcons();

    NativeImage playerIcon();

    NativeImage spawnIcon();

    NativeImage worldSpawnIcon();

    NativeImage[] featureIcons();

    @Nullable
    SearchableFeature feature4Id(int id);

    ItemStack[] structureItems();

    void onBiomeVisuallySelected(BiomesList.BiomeEntry entry);

    void onVisibleBiomesChanged(Short2LongMap visibleBiomes);

    void onVisibleStructuresChanged(Short2LongMap visibleStructures);

    void onVisibleRocksChanged(Short2LongMap visibleRocks);

    StructureRenderInfo[] renderStructureMap();

    int[] heightColorMap();

    int[] tfcTemperatureColorMap();

    int[] tfcRainfallColorMap();

    int yMin();

    int yMax();

    boolean isUpdating();

    boolean setupFailed();

    @NotNull
    PreviewDisplayDataProvider.PlayerData getPlayerData(UUID playerId);

    @Nullable
    BlockPos getWorldSpawnPos();

    int getWorldSpawnDistance();

    record PlayerData(@Nullable BlockPos currentPos, @Nullable BlockPos spawnPos)
    {
    }

    interface StructureRenderInfo
    {
        boolean show();
    }
}
