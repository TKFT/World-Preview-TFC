package caeruleustait.world.preview.client.gui;

import caeruleustait.world.preview.backend.storage.PreviewStorageCacheManager;
import java.nio.file.Path;
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

public interface PreviewContainerDataProvider extends PreviewStorageCacheManager {
   @Nullable
   WorldCreationContext previewWorldCreationContext();

   void registerSettingsChangeListener(Runnable var1);

   String seed();

   void updateSeed(String var1);

   boolean seedIsEditable();

   @Nullable
   Path tempDataPackDir();

   @Nullable
   MinecraftServer minecraftServer();

   WorldOptions worldOptions(@Nullable WorldCreationContext var1);

   WorldDataConfiguration worldDataConfiguration(@Nullable WorldCreationContext var1);

   Frozen registryAccess(@Nullable WorldCreationContext var1);

   Registry<LevelStem> levelStemRegistry(@Nullable WorldCreationContext var1);

   LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess(@Nullable WorldCreationContext var1);
}
