package com.rustysnail.world.preview.tfc.backend.worker;

import com.rustysnail.world.preview.tfc.backend.storage.PreviewSection;
import com.mojang.datafixers.util.Pair;
import java.util.List;
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
