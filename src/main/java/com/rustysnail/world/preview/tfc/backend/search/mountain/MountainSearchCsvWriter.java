package com.rustysnail.world.preview.tfc.backend.search.mountain;

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

    public static Path write(Path directory, List<MountainSearchResult> results) throws IOException
    {
        Files.createDirectories(directory);
        String filename = "tallest_mountains_" + LocalDateTime.now().format(TIMESTAMP) + ".csv";
        Path file = directory.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
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

        return file;
    }
}
