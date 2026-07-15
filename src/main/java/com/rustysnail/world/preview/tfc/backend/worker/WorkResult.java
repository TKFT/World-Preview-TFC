package com.rustysnail.world.preview.tfc.backend.worker;

import java.util.List;
import com.mojang.datafixers.util.Pair;
import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public record WorkResult(
    WorkUnit workUnit, int quartY, PreviewSection section, List<BlockResult> results, List<Pair<ResourceLocation, StructureStart>> structures
)
{
    public record BlockResult(int quartX, int quartZ, short value)
    {
    }
}
