package com.rustysnail.world.preview.tfc.client.gui;

import java.util.UUID;
import com.mojang.blaze3d.platform.NativeImage;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.search.SearchableFeature;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.BiomesList;
import com.rustysnail.world.preview.tfc.client.gui.widgets.lists.StructuresList;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

    @Nullable
    Component featureVariantName(int featureId, BlockPos center);

    ItemStack[] structureItems();

    void onBiomeVisuallySelected(BiomesList.BiomeEntry entry);

    void onVisibleBiomesChanged(Short2LongMap visibleBiomes);

    void onVisibleStructuresChanged(Short2LongMap visibleStructures);

    void onVisibleRocksChanged(Short2LongMap visibleRocks);

    /**
     * A forest-type / tree-species category was selected by clicking the map. {@code value} is a
     * forest ordinal, tree species id, or {@link com.rustysnail.world.preview.tfc.backend.worker.tfc.TFCSampleUtils#VALUE_WATER};
     * {@code Short.MIN_VALUE} clears the current selection.
     */
    void onTFCMapValueVisuallySelected(com.rustysnail.world.preview.tfc.RenderSettings.RenderMode mode, short value);

    StructureRenderInfo[] renderStructureMap();

    int[] heightColorMap();

    int[] tfcTemperatureColorMap();

    int[] tfcRainfallColorMap();

    void onColorPalettesChanged(long revision);

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
