package caeruleustait.world.preview.backend.worker.tfc;

import caeruleustait.world.preview.WorldPreview;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class KaolinBiomeRules {
    public static final String KAOLIN_FEATURE_TAG = "#tfc:in_biome/veins/kaolin";
    private final Set<ResourceLocation> allowedBiomes = new HashSet<>();

    public void rebuild(ResourceManager rm) {
        allowedBiomes.clear();

        // Biomes live at: data/<namespace>/worldgen/biome/*.json
        // ResourceManager gives paths like "tfc/worldgen/biome/doline_plateau.json"
        Map<ResourceLocation, Resource> biomes = rm.listResources(
                "worldgen/biome",
                rl -> rl.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, Resource> e : biomes.entrySet()) {
            ResourceLocation fileId = e.getKey(); // e.g. tfc:worldgen/biome/doline_plateau.json
            ResourceLocation biomeId = toBiomeId(fileId);
            if (biomeId == null) continue;

            try (var in = e.getValue().open()) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (hasKaolinTag(obj)) {
                    WorldPreview.LOGGER.info("Kaolin Biome added {}", biomeId);
                    allowedBiomes.add(biomeId);
                }
            } catch (Throwable ignored) {
                // swallow; missing/invalid biome json shouldn't hard-crash preview
            }
        }
    }

    public boolean isBiomeAllowed(ResourceLocation biomeId) {
        return allowedBiomes.contains(biomeId);
    }

    @Nullable
    private static ResourceLocation toBiomeId(ResourceLocation fileId) {
        // fileId path: "worldgen/biome/<name>.json"
        String p = fileId.getPath();
        if (!p.startsWith("worldgen/biome/")) return null;
        String name = p.substring("worldgen/biome/".length());
        if (!name.endsWith(".json")) return null;
        name = name.substring(0, name.length() - 5);
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), name);
    }

    private static boolean hasKaolinTag(JsonObject biomeJson) {
        if (!biomeJson.has("features")) return false;
        JsonArray features = biomeJson.getAsJsonArray("features");
        for (JsonElement el : features) {
            if (el.isJsonPrimitive() && KAOLIN_FEATURE_TAG.equals(el.getAsString())) {
                return true;
            }
        }
        return false;
    }
}
