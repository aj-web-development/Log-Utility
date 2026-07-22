package com.app.logutility.service.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobFileResolverTest {

    private final GlobFileResolver resolver = new GlobFileResolver();

    @Test
    void resolvesFilenameGlobInConcreteDirectory(@TempDir Path base) throws IOException {
        Files.createFile(base.resolve("app.2026-07-21.0.log.gz"));
        Files.createFile(base.resolve("app.2026-07-21.1.log.gz"));
        Files.createFile(base.resolve("app.2026-07-20.0.log.gz")); // different day, must not match

        List<Path> found = resolver.resolve(base, "app.2026-07-21.*.log.gz");

        assertThat(found).extracting(p -> p.getFileName().toString())
                .containsExactlyInAnyOrder("app.2026-07-21.0.log.gz", "app.2026-07-21.1.log.gz");
    }

    @Test
    void resolvesAcrossDirectorySegments(@TempDir Path base) throws IOException {
        Path day = Files.createDirectories(base.resolve("2026-07-21").resolve("14"));
        Files.createFile(day.resolve("app.0.log.gz"));
        Files.createFile(day.resolve("app.1.log.gz"));
        Files.createDirectories(base.resolve("2026-07-21").resolve("15"))
                .resolve("app.0.log.gz");
        Files.createFile(base.resolve("2026-07-21").resolve("15").resolve("app.0.log.gz"));

        List<Path> found = resolver.resolve(base, "2026-07-21/*/app.*.log.gz");

        assertThat(found).hasSize(3);
    }

    @Test
    void missingDirectoryYieldsNoFiles(@TempDir Path base) {
        List<Path> found = resolver.resolve(base, "does-not-exist/app.*.log.gz");
        assertThat(found).isEmpty();
    }

    @Test
    void nonDirectoryBaseYieldsNoFiles(@TempDir Path base) throws IOException {
        Path file = Files.createFile(base.resolve("a-file"));
        assertThat(resolver.resolve(file, "*.log")).isEmpty();
    }
}
