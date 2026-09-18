package com.english;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationVersionUniquenessTest {

    @Test
    void flywayVersionsAreUnique() throws Exception {
        URL migrationDirectory = getClass().getClassLoader().getResource("db/migration");
        assertNotNull(migrationDirectory);

        try (Stream<Path> files = Files.list(Path.of(migrationDirectory.toURI()))) {
            Map<String, List<String>> byVersion = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V[0-9]+__.+\\.sql"))
                    .collect(Collectors.groupingBy(
                            name -> name.substring(1, name.indexOf("__"))));

            List<String> duplicates = byVersion.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .map(entry -> "V" + entry.getKey() + ": " + entry.getValue())
                    .sorted()
                    .collect(Collectors.toList());
            assertTrue(duplicates.isEmpty(), "重复的 Flyway 版本: " + duplicates);
        }
    }
}
