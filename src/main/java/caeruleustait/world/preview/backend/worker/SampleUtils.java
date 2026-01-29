package caeruleustait.world.preview.backend.worker;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.WorldPreviewConfig;
import caeruleustait.world.preview.backend.storage.PreviewLevel;
import caeruleustait.world.preview.backend.stubs.DummyMinecraftServer;
import caeruleustait.world.preview.backend.stubs.DummyServerLevelData;
import caeruleustait.world.preview.backend.stubs.EmptyAquifer;
import caeruleustait.world.preview.mixin.MinecraftServerAccessor;
import caeruleustait.world.preview.mixin.NoiseBasedChunkGeneratorAccessor;
import caeruleustait.world.preview.mixin.NoiseChunkAccessor;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.WorldLoader.PackConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.TargetPoint;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.DensityFunctions.BeardifierMarker;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.PathAllowList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SampleUtils implements AutoCloseable {
   private final Path tempDir;
   private final DataFixer dataFixer;
   private final LevelStorageAccess levelStorageAccess;
   private final LevelHeightAccessor levelHeightAccessor;
   private final CloseableResourceManager resourceManager;
   private final BiomeSource biomeSource;
   private final RandomState randomState;
   private final ChunkGenerator chunkGenerator;
   private final RegistryAccess registryAccess;
   private final ChunkGeneratorStructureState chunkGeneratorStructureState;
   private final StructureCheck structureCheck;
   private final StructureManager structureManager;
   private final StructureTemplateManager structureTemplateManager;
   private final PreviewLevel previewLevel;
   private final Registry<Structure> structureRegistry;
   private final ResourceKey<Level> dimension;
   private final NoiseGeneratorSettings noiseGeneratorSettings;
   private final MinecraftServer minecraftServer;
   private final ServerLevel serverLevel;
   private final WorldPreviewConfig cfg = WorldPreview.get().cfg();

   public SampleUtils(
      @NotNull MinecraftServer server,
      BiomeSource biomeSource,
      ChunkGenerator chunkGenerator,
      WorldOptions worldOptions,
      LevelStem levelStem,
      LevelHeightAccessor levelHeightAccessor
   ) throws IOException {
      this.tempDir = null;
      this.minecraftServer = server;
      this.dataFixer = this.minecraftServer.getFixerUpper();
      this.levelStorageAccess = ((MinecraftServerAccessor)this.minecraftServer).getStorageSource();
      this.levelHeightAccessor = levelHeightAccessor;
      this.resourceManager = (CloseableResourceManager) this.minecraftServer.getResourceManager();
      this.biomeSource = biomeSource;
      this.chunkGenerator = chunkGenerator;
      this.registryAccess = this.minecraftServer.registryAccess();
      this.structureRegistry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);
      this.structureTemplateManager = this.minecraftServer.getStructureManager();
      this.previewLevel = new PreviewLevel(this.registryAccess, this.levelHeightAccessor);
      ResourceKey<LevelStem> levelStemResourceKey = this.registryAccess
         .registryOrThrow(Registries.LEVEL_STEM)
         .getResourceKey(levelStem)
         .orElseThrow();
      this.dimension = Registries.levelStemToLevel(levelStemResourceKey);
      if (chunkGenerator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator) {
         this.randomState = RandomState.create(
                 noiseBasedChunkGenerator.generatorSettings().value(),
            this.registryAccess.lookupOrThrow(Registries.NOISE),
            worldOptions.seed()
         );
      } else {
         this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), this.registryAccess.lookupOrThrow(Registries.NOISE), worldOptions.seed());
      }

      this.structureCheck = new StructureCheck(
         null,
         this.registryAccess,
         this.structureTemplateManager,
         this.dimension,
         this.chunkGenerator,
         this.randomState,
         this.levelHeightAccessor,
         chunkGenerator.getBiomeSource(),
         worldOptions.seed(),
         this.dataFixer
      );
      this.structureManager = new StructureManager(this.previewLevel, worldOptions, this.structureCheck);
      this.chunkGeneratorStructureState = this.chunkGenerator
         .createState(this.registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, worldOptions.seed());
      if (chunkGenerator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator) {
         this.noiseGeneratorSettings = noiseBasedChunkGenerator.generatorSettings().value();
      } else {
         this.noiseGeneratorSettings = null;
      }

      this.serverLevel = null;
   }

   public SampleUtils(
      BiomeSource biomeSource,
      ChunkGenerator chunkGenerator,
      LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
      WorldOptions worldOptions,
      LevelStem levelStem,
      LevelHeightAccessor levelHeightAccessor,
      WorldDataConfiguration worldDataConfiguration,
      Proxy proxy,
      @Nullable Path tempDataPackDir
   ) throws IOException, RuntimeException {
      try {
         this.tempDir = Files.createTempDirectory("world_preview");
      } catch (IOException var27) {
         throw new RuntimeException(var27);
      }

      this.dataFixer = DataFixers.getDataFixer();
      LevelStorageSource levelStorageSource = new LevelStorageSource(
         this.tempDir, this.tempDir.resolve("backups"), new DirectoryValidator(new PathAllowList(List.of())), this.dataFixer
      );
      this.levelStorageAccess = levelStorageSource.createAccess("world_preview");
      this.levelHeightAccessor = levelHeightAccessor;
      Path dataPackDir = this.levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR);
      FileUtil.createDirectoriesSafe(dataPackDir);
      if (tempDataPackDir != null) {
         try (Stream<Path> stream = Files.walk(tempDataPackDir)) {
            stream.filter(x -> !x.equals(tempDataPackDir)).forEach(x -> {
               try {
                  Util.copyBetweenDirs(tempDataPackDir, dataPackDir, x);
               } catch (IOException var4x) {
                  WorldPreview.LOGGER.error("Failed to copy datapack file: {}", x, var4x);
               }
            });
         }
      }

      this.biomeSource = biomeSource;
      this.chunkGenerator = chunkGenerator;
      this.registryAccess = layeredRegistryAccess.compositeAccess();
      this.structureRegistry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);
      this.previewLevel = new PreviewLevel(this.registryAccess, this.levelHeightAccessor);
      PackRepository packRepository = ServerPacksSource.createPackRepository(this.levelStorageAccess);
      this.resourceManager = new PackConfig(packRepository, worldDataConfiguration, false, false).createResourceManager().getSecond();
      HolderGetter<Block> holderGetter = this.registryAccess
         .registryOrThrow(Registries.BLOCK)
         .asLookup()
         .filterFeatures(worldDataConfiguration.enabledFeatures());
      this.structureTemplateManager = new StructureTemplateManager(this.resourceManager, this.levelStorageAccess, this.dataFixer, holderGetter);
      ResourceKey<LevelStem> levelStemResourceKey = this.registryAccess
         .registryOrThrow(Registries.LEVEL_STEM)
         .getResourceKey(levelStem)
         .orElseThrow();
      this.dimension = Registries.levelStemToLevel(levelStemResourceKey);
      int functionCompilationLevel = 0;
      Executor executor = Executors.newSingleThreadExecutor();
      LevelSettings levelSettings = new LevelSettings("temp", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), worldDataConfiguration);
      PrimaryLevelData primaryLevelData = new PrimaryLevelData(levelSettings, worldOptions, SpecialWorldProperty.NONE, Lifecycle.stable());
      CompletableFuture<ReloadableServerResources> future = ReloadableServerResources.loadResources(
         this.resourceManager, layeredRegistryAccess, worldDataConfiguration.enabledFeatures(), CommandSelection.DEDICATED, 0, executor, executor
      );

      ReloadableServerResources reloadableServerResources;
      try {
         reloadableServerResources = future.get();
      } catch (InterruptedException var25) {
         Thread.currentThread().interrupt();
         throw new RuntimeException(var25);
      } catch (ExecutionException var26) {
         throw new RuntimeException(var26);
      }

      WorldStem worldStem = new WorldStem(this.resourceManager, reloadableServerResources, layeredRegistryAccess, primaryLevelData);
      ChunkProgressListener chunkProgressListener = new ChunkProgressListener() {
         public void updateSpawnPos(@NotNull ChunkPos center) {
         }

         public void onStatusChange(@NotNull ChunkPos chunkPosition, @Nullable ChunkStatus newStatus) {
         }

         public void start() {
         }

         public void stop() {
         }
      };
      
      this.minecraftServer = new DummyMinecraftServer(
         new Thread(() -> {}),
         this.levelStorageAccess,
         packRepository,
         worldStem,
         proxy,
         this.dataFixer,
         new Services(null, null, null, null),
         i -> chunkProgressListener
      );
      WorldPreview.get().loaderSpecificSetup(this.minecraftServer);
      new ServerLevel(
         this.minecraftServer,
         Executors.newSingleThreadExecutor(),
         this.levelStorageAccess,
         new DerivedLevelData(worldStem.worldData(), worldStem.worldData().overworldData()),
         this.dimension,
         levelStem,
         new ChunkProgressListener() {
            public void updateSpawnPos(@NotNull ChunkPos center) {
            }

            public void onStatusChange(@NotNull ChunkPos chunkPosition, @Nullable ChunkStatus newStatus) {
            }

            public void start() {
            }

            public void stop() {
            }
         },
         false,
         BiomeManager.obfuscateSeed(worldOptions.seed()),
         List.of(),
         false,
         null
      );
      if (chunkGenerator instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator) {
         this.noiseGeneratorSettings = noiseBasedChunkGenerator.generatorSettings().value();
         this.randomState = RandomState.create(
                 noiseBasedChunkGenerator.generatorSettings().value(),
            this.registryAccess.lookupOrThrow(Registries.NOISE),
            worldOptions.seed()
         );
      } else {
         this.noiseGeneratorSettings = null;
         this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), this.registryAccess.lookupOrThrow(Registries.NOISE), worldOptions.seed());
      }

      this.structureCheck = new StructureCheck(
         null,
         this.registryAccess,
         this.structureTemplateManager,
         this.dimension,
         this.chunkGenerator,
         this.randomState,
         this.levelHeightAccessor,
         chunkGenerator.getBiomeSource(),
         worldOptions.seed(),
         this.dataFixer
      );
      this.structureManager = new StructureManager(this.previewLevel, worldOptions, this.structureCheck);
      this.chunkGeneratorStructureState = this.chunkGenerator
         .createState(this.registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, worldOptions.seed());
      this.chunkGeneratorStructureState.ensureStructuresGenerated();
      this.serverLevel = new ServerLevel(
         this.minecraftServer,
         Executors.newSingleThreadExecutor(),
         this.levelStorageAccess,
         new DummyServerLevelData(),
         this.dimension,
         levelStem,
         chunkProgressListener,
         false,
         BiomeManager.obfuscateSeed(worldOptions.seed()),
         List.of(),
         false,
         null
      );
   }

   @Nullable
   public ServerPlayer getPlayers(UUID playerId) {
      return this.minecraftServer instanceof DummyMinecraftServer ? null : this.minecraftServer.getPlayerList().getPlayer(playerId);
   }

   private static short doubleToShort(double val, double factor) {
      return (short)Math.min(32767L, Math.max(-32768L, (long)(val * factor * 32767.0)));
   }

   public boolean hasRawNoiseInfo() {
      return this.cfg.storeNoiseSamples && this.biomeSource instanceof MultiNoiseBiomeSource;
   }

   public BiomeResult doSample(BlockPos pos) {
      Sampler sampler = this.randomState.sampler();
      if (this.hasRawNoiseInfo()) {
         SinglePointContext singlePointContext = new SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
         double temperature = sampler.temperature().compute(singlePointContext);
         double humidity = sampler.humidity().compute(singlePointContext);
         double continentalness = sampler.continentalness().compute(singlePointContext);
         double erosion = sampler.erosion().compute(singlePointContext);
         double depth = sampler.depth().compute(singlePointContext);
         double weirdness = sampler.weirdness().compute(singlePointContext);
         short[] noiseData = new short[]{
            doubleToShort(temperature, 1.0),
            doubleToShort(humidity, 1.0),
            doubleToShort(continentalness, 0.5),
            doubleToShort(erosion, 1.0),
            doubleToShort(depth, 0.5),
            doubleToShort(weirdness, 0.75)
         };
         TargetPoint targetPoint = Climate.target((float)temperature, (float)humidity, (float)continentalness, (float)erosion, (float)depth, (float)weirdness);
         MultiNoiseBiomeSource noiseBiomeSource = (MultiNoiseBiomeSource) this.biomeSource;
         Holder<Biome> biome = noiseBiomeSource.getNoiseBiome(targetPoint);
         return new BiomeResult(biome.unwrapKey().orElseThrow(), noiseData);
      } else {
         return new BiomeResult(
                 this.biomeSource
                    .getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), this.randomState.sampler())
                    .unwrapKey()
                    .orElseThrow(),
            null
         );
      }
   }


   public net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> getBiomeKey(BlockPos pos) {
      return this.biomeSource
              .getNoiseBiome(
                      QuartPos.fromBlock(pos.getX()),
                      QuartPos.fromBlock(pos.getY()),
                      QuartPos.fromBlock(pos.getZ()),
                      this.randomState.sampler()
              )
              .unwrapKey()
              .orElseThrow();
   }

   public List<Pair<ResourceLocation, StructureStart>> doStructures(ChunkPos chunkPos) {
      ProtoChunk protoChunk = (ProtoChunk) this.previewLevel.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
       assert protoChunk != null;
       this.chunkGenerator
         .createStructures(this.registryAccess, this.chunkGeneratorStructureState, this.structureManager, protoChunk, this.structureTemplateManager);
      Map<Structure, StructureStart> raw = protoChunk.getAllStarts();
      List<Pair<ResourceLocation, StructureStart>> res = new ArrayList<>(raw.size());

      for (Entry<Structure, StructureStart> entry : protoChunk.getAllStarts().entrySet()) {
         res.add(new Pair<>(this.structureRegistry.getKey(entry.getKey()), entry.getValue()));
      }

      return res;
   }

   public NoiseChunk getNoiseChunk(ChunkPos startChunk, int numChunks, boolean keepAquifer) {
      NoiseSettings noiseSettings = this.noiseGeneratorSettings.noiseSettings();
      NoiseChunk noiseChunk = new NoiseChunk(
         numChunks * 16 / noiseSettings.getCellWidth(),
         this.randomState,
         startChunk.getMinBlockX(),
         startChunk.getMinBlockZ(),
         noiseSettings,
         BeardifierMarker.INSTANCE,
         this.noiseGeneratorSettings,
         ((NoiseBasedChunkGeneratorAccessor)this.chunkGenerator).getGlobalFluidPicker().get(),
         Blender.empty()
      );
      if (!keepAquifer) {
         ((NoiseChunkAccessor)noiseChunk).setAquifer(new EmptyAquifer());
      }

      return noiseChunk;
   }

   public NoiseGeneratorSettings noiseGeneratorSettings() {
      return this.noiseGeneratorSettings;
   }

   public short doHeightSlow(BlockPos pos) {
      return (short)this.chunkGenerator.getBaseHeight(pos.getX(), pos.getZ(), Types.OCEAN_FLOOR_WG, this.levelHeightAccessor, this.randomState);
   }

   public NoiseColumn doIntersectionsSlow(BlockPos pos) {
      return this.chunkGenerator.getBaseColumn(pos.getX(), pos.getZ(), this.levelHeightAccessor, this.randomState);
   }

   @Override
   public void close() throws Exception {
      if (this.serverLevel != null) {
         this.serverLevel.close();
      }

      if (this.minecraftServer instanceof DummyMinecraftServer) {
         WorldPreview.get().loaderSpecificTeardown(this.minecraftServer);
      }

      if (this.tempDir != null) {
         deleteDirectoryLegacyIO(this.tempDir.toFile());
      }
   }

   public static void deleteDirectoryLegacyIO(File file) {
      File[] list = file.listFiles();
      if (list != null) {
         for (File temp : list) {
            deleteDirectoryLegacyIO(temp);
         }
      }

      if (!file.delete()) {
         WorldPreview.LOGGER.warn("Unable to delete file or directory : {}", file);
      }
   }

   public CloseableResourceManager resourceManager() {
      return this.resourceManager;
   }

   public RegistryAccess registryAccess() {
      return this.registryAccess;
   }

   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public record BiomeResult(ResourceKey<Biome> biome, short[] noiseResult) {
   }
}
