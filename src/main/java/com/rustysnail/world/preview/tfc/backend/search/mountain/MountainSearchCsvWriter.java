package com.rustysnail.world.preview.tfc.backend.search.mountain;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MountainSearchCsvWriter
{
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String HEADER = "rank,seed,height,x,z,coarse_height,refined_height,samples_checked,elapsed_millis";

    /** Compatibility overload — no config metadata in sidecar. */
    public static Path write(Path directory, List<MountainSearchResult> results) throws IOException
    {
        return write(directory, results, null, results.size());
    }

    /**
     * Writes a CSV and a JSON sidecar with the same timestamp stem.
     * Returns the path to the CSV file.
     */
    public static Path write(Path directory, List<MountainSearchResult> results,
                             @Nullable MountainSeedSearchConfig config, int testedSeeds) throws IOException
    {
        Files.createDirectories(directory);
        String stem = "tallest_mountains_" + LocalDateTime.now().format(TIMESTAMP);
        Path csvFile = directory.resolve(stem + ".csv");
        Path jsonFile = directory.resolve(stem + ".json");

        try (BufferedWriter writer = Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8))
        {
            writer.write(HEADER);
            writer.newLine();
            for (int rank = 0; rank < results.size(); rank++)
            {
                MountainSearchResult r = results.get(rank);
                writer.write(String.join(",",
                    String.valueOf(rank + 1),
                    String.valueOf(r.seed()),
                    String.valueOf(r.height()),
                    String.valueOf(r.x()),
                    String.valueOf(r.z()),
                    String.valueOf(r.coarseHeight()),
                    String.valueOf(r.refinedHeight()),
                    String.valueOf(r.samplesChecked()),
                    String.valueOf(r.elapsedMillis())
                ));
                writer.newLine();
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8))
        {
            writer.write(buildJson(results, config, testedSeeds));
        }

        return csvFile;
    }

    private static String buildJson(List<MountainSearchResult> results,
                                    @Nullable MountainSeedSearchConfig config, int testedSeeds)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"testedSeeds\": ").append(testedSeeds).append(",\n");
        if (config != null)
        {
            sb.append("  \"maxSeeds\": ").append(config.maxSeeds()).append(",\n");
            sb.append("  \"radius\": ").append(config.radius()).append(",\n");
            sb.append("  \"centerX\": ").append(config.center().getX()).append(",\n");
            sb.append("  \"centerZ\": ").append(config.center().getZ()).append(",\n");
            sb.append("  \"randomSalt\": ").append(config.randomSalt()).append(",\n");
        }
        sb.append("  \"topResults\": ").append(results.size()).append(",\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++)
        {
            MountainSearchResult r = results.get(i);
            sb.append("    {\n");
            sb.append("      \"rank\": ").append(i + 1).append(",\n");
            sb.append("      \"seed\": ").append(r.seed()).append(",\n");
            sb.append("      \"height\": ").append(r.height()).append(",\n");
            sb.append("      \"x\": ").append(r.x()).append(",\n");
            sb.append("      \"z\": ").append(r.z()).append(",\n");
            sb.append("      \"coarseHeight\": ").append(r.coarseHeight()).append(",\n");
            sb.append("      \"refinedHeight\": ").append(r.refinedHeight()).append(",\n");
            sb.append("      \"samplesChecked\": ").append(r.samplesChecked()).append(",\n");
            sb.append("      \"elapsedMillis\": ").append(r.elapsedMillis()).append("\n");
            sb.append("    }");
            if (i < results.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
}
