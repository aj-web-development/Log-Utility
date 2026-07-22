package com.in10s.logutility.service.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogSourceReaderTest {

    @Test
    void plainReaderStreamsLinesAndPicksCorrectReader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("app.log");
        Files.writeString(file, "line one\nline two\nline three\n");

        LogSourceReaderFactory factory = new LogSourceReaderFactory(
                List.of(new PlainLogSourceReader(), new GzipLogSourceReader()));

        LogSourceReader reader = factory.readerFor(file).orElseThrow();
        assertThat(reader).isInstanceOf(PlainLogSourceReader.class);

        try (Stream<String> lines = reader.readLines(file)) {
            assertThat(lines).containsExactly("line one", "line two", "line three");
        }
    }

    @Test
    void gzipReaderDecompressesAndStreams(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("app.2026-07-21.0.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("compressed one\ncompressed two\n".getBytes(StandardCharsets.UTF_8));
        }

        LogSourceReaderFactory factory = new LogSourceReaderFactory(
                List.of(new PlainLogSourceReader(), new GzipLogSourceReader()));

        LogSourceReader reader = factory.readerFor(gz).orElseThrow();
        assertThat(reader).isInstanceOf(GzipLogSourceReader.class);

        try (Stream<String> lines = reader.readLines(gz)) {
            assertThat(lines).containsExactly("compressed one", "compressed two");
        }
    }
}
