package com.rustysnail.world.preview.tfc.backend.stubs;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder.Settings;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyServerLevelData implements ServerLevelData
{
    public @NotNull String getLevelName()
    {
        return "dummy";
    }

    public @NotNull BlockPos getSpawnPos()
    {
        return BlockPos.ZERO;
    }

    public float getSpawnAngle()
    {
        return 0.0F;
    }

    public long getGameTime()
    {
        return 0L;
    }

    public void setGameTime(long time)
    {
    }

    public void setThunderTime(int time)
    {
    }

    public long getDayTime()
    {
        return 0L;
    }

    public int getThunderTime()
    {
        return 0;
    }

    public void setDayTime(long time)
    {
    }

    public int getClearWeatherTime()
    {
        return 0;
    }

    public float getDayTimeFraction()
    {
        return 0.0F;
    }

    public void setClearWeatherTime(int time)
    {
    }

    public float getDayTimePerTick()
    {
        return 0.0F;
    }

    public int getWanderingTraderSpawnDelay()
    {
        return 0;
    }

    public void setDayTimePerTick(float v)
    {
    }

    public void setWanderingTraderSpawnDelay(int delay)
    {
    }

    public void setDayTimeFraction(float v)
    {
    }

    public int getWanderingTraderSpawnChance()
    {
        return 0;
    }

    public boolean isThundering()
    {
        return false;
    }

    public void setWanderingTraderSpawnChance(int chance)
    {
    }

    public void setThundering(boolean thundering)
    {
    }

    @Nullable
    public UUID getWanderingTraderId()
    {
        return UUID.randomUUID();
    }

    public int getRainTime()
    {
        return 0;
    }

    public void setWanderingTraderId(@NotNull UUID id)
    {
    }

    public void setRainTime(int time)
    {
    }

    public @NotNull GameType getGameType()
    {
        return GameType.SPECTATOR;
    }

    public boolean isRaining()
    {
        return false;
    }

    public void setWorldBorder(@NotNull Settings serializer)
    {
    }

    public void setRaining(boolean raining)
    {
    }

    @Nullable
    public Settings getWorldBorder()
    {
        return null;
    }

    public boolean isHardcore()
    {
        return false;
    }

    public boolean isInitialized()
    {
        return false;
    }

    public @NotNull GameRules getGameRules()
    {
        return new GameRules();
    }

    public void setInitialized(boolean initialized)
    {
    }

    public @NotNull Difficulty getDifficulty()
    {
        return Difficulty.HARD;
    }

    public boolean isAllowCommands()
    {
        return false;
    }

    public boolean isDifficultyLocked()
    {
        return false;
    }

    public void setGameType(@NotNull GameType type)
    {
    }

    public void setSpawn(@NotNull BlockPos spawnPoint, float spawnAngle)
    {
    }

    @Nullable
    public TimerQueue<MinecraftServer> getScheduledEvents()
    {
        return null;
    }


}
