package com.in10s.logutility.search;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Reads uncompressed {@code .log} files, streaming one line at a time. */
@Component
public class PlainLogSourceReader implements LogSourceReader {

    @Override
    public boolean supports(Path file) {
        return !file.getFileName().toString().toLowerCase().endsWith(".gz");
    }

    @Override
    public Stream<String> readLines(Path file) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> closeQuietly(reader));
    }

    static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // best-effort close of a read-only stream
        }
    }
}
