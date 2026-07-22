package com.in10s.logutility.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import com.in10s.logutility.response.validation.PathCheckResult;

/** Pure unit tests — no Spring context — against a real temp directory/file. */
class PathAvailabilityCheckerImplTest {

    private final PathAvailabilityChecker checker = new PathAvailabilityCheckerImpl();

    @Test
    void blankPathIsUnreachable() {
        PathCheckResult result = checker.check("   ");
        assertThat(result.reachable()).isFalse();
        assertThat(result.message()).isEqualTo("No path configured");
    }

    @Test
    void nullPathIsUnreachable() {
        PathCheckResult result = checker.check(null);
        assertThat(result.reachable()).isFalse();
    }

    @Test
    void missingPathIsUnreachable(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        PathCheckResult result = checker.check(missing.toString());
        assertThat(result.reachable()).isFalse();
        assertThat(result.message()).isEqualTo("Path does not exist");
    }

    @Test
    void reachableDirectoryReportsEntryCount(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("a.log"));
        Files.createFile(tempDir.resolve("b.log.gz"));

        PathCheckResult result = checker.check(tempDir.toString());

        assertThat(result.reachable()).isTrue();
        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(result.message()).contains("2 entries");
    }

    @Test
    void reachableFileReportsSize(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.log");
        Files.writeString(file, "hello world");

        PathCheckResult result = checker.check(file.toString());

        assertThat(result.reachable()).isTrue();
        assertThat(result.fileCount()).isEqualTo(1);
        assertThat(result.message()).contains("bytes");
    }

    @Test
    void emptyDirectoryIsReachableWithZeroEntries(@TempDir Path tempDir) {
        PathCheckResult result = checker.check(tempDir.toString());

        assertThat(result.reachable()).isTrue();
        assertThat(result.fileCount()).isZero();
    }
}
