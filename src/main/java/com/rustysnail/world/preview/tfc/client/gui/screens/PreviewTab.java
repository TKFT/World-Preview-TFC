package com.rustysnail.world.preview.tfc.client.gui.screens;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewStorage;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.PreviewContainerDataProvider;
import com.rustysnail.world.preview.tfc.mixin.client.CreateWorldScreenAccessor;
import com.rustysnail.world.preview.tfc.mixin.client.TabNavigationBarAccessor;

import net.dries007.tfc.world.ChunkGeneratorExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldLoader.DataLoadOutput;
import net.minecraft.server.WorldLoader.InitConfig;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldDimensions.Complete;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewTab implements Tab, AutoCloseable, PreviewContainerDataProvider
{
    private final CreateWorldScreen createWorldScreen;
    private final WorldCreationUiState uiState;
    private final PreviewContainer previewContainer;
    private final WorldPreview worldPreview = WorldPreview.get();
    private final Minecraft minecraft;
    private final ExecutorService loadingExecutor = Executors.newFixedThreadPool(2);

    public PreviewTab(CreateWorldScreen screen, Minecraft minecraft)
    {
        this.createWorldScreen = screen;
        this.uiState = screen.getUiState();
        this.minecraft = minecraft;
        this.previewContainer = new PreviewContainer(screen, this);
    }

    @NotNull
    public Component getTabTitle()
    {
        return WorldPreviewComponents.TITLE;
    }

    public void visitChildren(@NotNull Consumer<AbstractWidget> consumer)
    {
        this.previewContainer.widgets().forEach(consumer);
    }

    public void doLayout(@NotNull ScreenRectangle screenRectangle)
    {
        this.previewContainer.doLayout(screenRectangle);
    }

    @Override
    public void close()
    {
        this.previewContainer.close();
        this.loadingExecutor.shutdownNow();
    }

    @Nullable
    @Override
    public WorldCreationContext previewWorldCreationContext()
    {
        WorldCreationContext wcContext = this.uiState.getSettings();
        WorldDataConfiguration worldDataConfiguration = wcContext.dataConfiguration();
        PackRepository packRepository = ((CreateWorldScreenAccessor) this.createWorldScreen)
            .invokeGetDataPackSelectionSettings(worldDataConfiguration)
            .getSecond();
        PackConfig packConfig = new PackConfig(packRepository, worldDataConfiguration, false, true);
        InitConfig initConfig = new InitConfig(packConfig, CommandSelection.INTEGRATED, 2);

        record Cookie(WorldGenSettings worldGenSettings)
        {
        }

        CompletableFuture<WorldCreationContext> completableFuture = WorldLoader.load(initConfig, dataLoadContext -> {
            WorldDimensions worldDimensions;
            try
            {
                ResourceKey<WorldPreset> worldPresetKey = Objects.requireNonNull(this.uiState.getWorldType().preset()).unwrapKey().orElseThrow();
                WorldPreset worldPreset = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.WORLD_PRESET).getOrThrow(worldPresetKey);
                worldDimensions = worldPreset.createWorldDimensions();
            }
            catch (NoSuchElementException | IllegalStateException | NullPointerException e)
            {
                worldDimensions = WorldPresets.createNormalWorldDimensions(dataLoadContext.datapackWorldgen());
            }

            WorldGenSettings worldGenSettings = new WorldGenSettings(wcContext.options(), worldDimensions);
            return new DataLoadOutput<>(new Cookie(worldGenSettings), dataLoadContext.datapackDimensions());
        }, (closeableResourceManager, reloadableServerResources, layeredRegistryAccess, cookie) -> {
            closeableResourceManager.close();
            return new WorldCreationContext(cookie.worldGenSettings(), layeredRegistryAccess, reloadableServerResources, worldDataConfiguration);
        }, this.loadingExecutor, this.loadingExecutor);

        try
        {
            return completableFuture.get();
        }
        catch (ExecutionException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path cacheDir()
    {
        Path previewDir = this.worldPreview.configDir().resolve("world-preview");
        try
        {
            Files.createDirectories(previewDir);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to create preview cache directory: " + previewDir, e);
        }
        return previewDir;
    }

    private String filename(long seed)
    {
        return String.format("%s-%s.zip", seed, this.cacheFileCompatPart());
    }

    @Override
    public void storePreviewStorage(long seed, PreviewStorage storage)
    {
        if (this.worldPreview.cfg().cacheInNew)
        {
            this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.SAVING_PREVIEW));
            this.writeCacheFile(this.previewContainer.workManager().previewStorage(), this.cacheDir().resolve(this.filename(seed)));
            this.minecraft.forceSetScreen(this.createWorldScreen);
        }
    }

    @Override
    public PreviewStorage loadPreviewStorage(long seed, int yMin, int yMax)
    {
        if (!this.worldPreview.cfg().cacheInNew)
        {
            return new PreviewStorage(yMin, yMax);
        }
        else
        {
            this.minecraft.forceSetScreen(new PreviewCacheLoadingScreen(WorldPreviewComponents.LOADING_PREVIEW));
            PreviewStorage res = this.readCacheFile(yMin, yMax, this.cacheDir().resolve(this.filename(seed)));
            this.minecraft.forceSetScreen(this.createWorldScreen);
            return res;
        }
    }

    public PreviewContainer mainScreenWidget()
    {
        return this.previewContainer;
    }

    public void start()
    {
        this.previewContainer.start();
    }

    public void stop()
    {
        this.previewContainer.stop();
    }

    public void previewSeedFromSearch(String seed, @Nullable BlockPos location)
    {
        this.previewContainer.setSeed(seed);

        if (location != null)
        {
            this.previewContainer.centerMapOn(location);
        }

        // Switch to the Preview tab
        TabNavigationBar navBar = ((CreateWorldScreenAccessor) this.createWorldScreen).getTabNavigationBar();
        TabNavigationBarAccessor accessor = (TabNavigationBarAccessor) navBar;
        int index = accessor.getTabs().indexOf(this);
        if (index >= 0)
        {
            navBar.selectTab(index, false);
        }
    }

    @Override
    public void registerSettingsChangeListener(Runnable listener)
    {
        this.uiState.addListener(x -> listener.run());
    }

    @Override
    public String seed()
    {
        return this.uiState.getSeed();
    }

    @Override
    public void updateSeed(String newSeed)
    {
        this.uiState.setSeed(newSeed);
    }

    @Override
    public boolean seedIsEditable()
    {
        return true;
    }

    @Nullable
    @Override
    public Path tempDataPackDir()
    {
        return ((CreateWorldScreenAccessor) this.createWorldScreen).invokeGetTempDataPackDir();
    }

    @Nullable
    @Override
    public MinecraftServer minecraftServer()
    {
        return null;
    }

    @Override
    public WorldOptions worldOptions(@Nullable WorldCreationContext wcContext)
    {
        if (wcContext == null)
        {
            throw new AssertionError();
        }
        else
        {
            return wcContext.options();
        }
    }

    @Override
    public WorldDataConfiguration worldDataConfiguration(@Nullable WorldCreationContext wcContext)
    {
        if (wcContext == null)
        {
            throw new AssertionError();
        }
        else
        {
            return wcContext.dataConfiguration();
        }
    }

    @Override
    public Frozen registryAccess(@Nullable WorldCreationContext wcContext)
    {
        if (wcContext == null)
        {
            throw new AssertionError();
        }
        else
        {
            return wcContext.worldgenLoadContext();
        }
    }

    @Override
    public Registry<LevelStem> levelStemRegistry(@Nullable WorldCreationContext wcContext)
    {
        if (wcContext == null)
        {
            throw new AssertionError();
        }
        else
        {
            Complete worldDimensions = wcContext.selectedDimensions().bake(wcContext.datapackDimensions());
            return worldDimensions.dimensions();
        }
    }

    @Override
    public LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess(@Nullable WorldCreationContext wcContext)
    {
        if (wcContext == null)
        {
            throw new AssertionError();
        }
        else
        {
            Complete worldDimensions = wcContext.selectedDimensions().bake(wcContext.datapackDimensions());
            return wcContext.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, worldDimensions.dimensionsRegistryAccess());
        }
    }

    @Nullable
    @Override
    public ChunkGeneratorExtension vanillaTFCExtension()
    {
        WorldCreationContext ctx = this.uiState.getSettings();
        try
        {
            Complete baked = ctx.selectedDimensions().bake(ctx.datapackDimensions());
            LevelStem overworld = baked.dimensions().get(LevelStem.OVERWORLD.location());
            if (overworld != null && overworld.generator() instanceof ChunkGeneratorExtension ext)
            {
                return ext;
            }
        }
        catch (Exception e)
        {
            WorldPreview.LOGGER.warn("Failed to get vanilla TFC extension from world creation context", e);
        }
        return null;
    }
}
