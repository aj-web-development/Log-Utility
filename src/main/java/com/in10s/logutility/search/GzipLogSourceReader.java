package com.in10s.logutility.search;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** Reads gzip-compressed {@code .log.gz} rotated files, decompressing and streaming line by line. */
@Component
public class GzipLogSourceReader implements LogSourceReader {

    @Override
    public boolean supports(Path file) {
        return file.getFileName().toString().toLowerCase().endsWith(".gz");
    }

    @Override
    public Stream<String> readLines(Path file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> PlainLogSourceReader.closeQuietly(reader));
    }
}
