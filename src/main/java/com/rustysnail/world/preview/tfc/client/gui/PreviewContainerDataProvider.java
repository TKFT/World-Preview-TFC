package com.rustysnail.world.preview.tfc.client.gui;

import java.nio.file.Path;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorageCacheManager;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.world.ChunkGeneratorExtension;

public interface PreviewContainerDataProvider extends PreviewStorageCacheManager
{
    @Nullable
    WorldCreationContext previewWorldCreationContext();

    void registerSettingsChangeListener(Runnable listener);

    String seed();

    void updateSeed(String seed);

    boolean seedIsEditable();

    @Nullable
    Path tempDataPackDir();

    @Nullable
    MinecraftServer minecraftServer();

    WorldOptions worldOptions(@Nullable WorldCreationContext context);

    WorldDataConfiguration worldDataConfiguration(@Nullable WorldCreationContext context);

    Frozen registryAccess(@Nullable WorldCreationContext context);

    Registry<LevelStem> levelStemRegistry(@Nullable WorldCreationContext context);

    LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess(@Nullable WorldCreationContext context);

    @Nullable
    default ChunkGeneratorExtension vanillaTFCExtension()
    {
        return null;
    }
}
