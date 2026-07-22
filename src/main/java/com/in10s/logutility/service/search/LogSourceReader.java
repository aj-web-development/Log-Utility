package com.in10s.logutility.service.search;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Streams the lines of one log file. Implementations must stream lazily (one line buffered at a
 * time), never materialising the whole file. The returned {@link Stream} owns the underlying
 * reader and closes it when the stream is closed, so callers must use try-with-resources.
 */
public interface LogSourceReader {

    /** Whether this reader handles the given file (typically decided by extension). */
    boolean supports(Path file);

    /** Opens the file and returns a lazily-streamed, self-closing line stream. */
    Stream<String> readLines(Path file) throws IOException;
}
