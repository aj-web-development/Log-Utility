package com.app.logutility.service.search;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Selects the right {@link LogSourceReader} for a file (plain vs gzip). */
@Component
public class LogSourceReaderFactory {

    private final List<LogSourceReader> readers;

    public LogSourceReaderFactory(List<LogSourceReader> readers) {
        this.readers = readers;
    }

    public Optional<LogSourceReader> readerFor(Path file) {
        return readers.stream().filter(r -> r.supports(file)).findFirst();
    }
}
