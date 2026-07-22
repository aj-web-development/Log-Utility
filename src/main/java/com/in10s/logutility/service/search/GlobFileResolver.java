package com.in10s.logutility.service.search;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a root-relative glob (as produced by {@link DatePruner}) against a base directory into
 * concrete files. Walks the glob one path segment at a time using
 * {@link Files#newDirectoryStream(Path, String)}, which matches a glob against a single name — this
 * is portable across filesystems, unlike a whole-path {@code glob:} {@code PathMatcher} whose
 * separator handling differs between Windows and Unix.
 */
@Component
public class GlobFileResolver {

    public List<Path> resolve(Path baseDir, String relativeGlob) {
        List<Path> results = new ArrayList<>();
        if (baseDir == null || !Files.isDirectory(baseDir) || relativeGlob == null) {
            return results;
        }
        expand(baseDir, relativeGlob.split("/"), 0, results);
        return results;
    }

    private void expand(Path dir, String[] segments, int index, List<Path> out) {
        if (index >= segments.length) {
            return;
        }
        String segment = segments[index];
        boolean last = index == segments.length - 1;

        if (segment.isEmpty()) { // leading/trailing/double slash
            expand(dir, segments, index + 1, out);
            return;
        }

        if (!hasGlobChar(segment)) {
            Path next = dir.resolve(segment);
            if (last) {
                if (Files.isRegularFile(next)) {
                    out.add(next);
                }
            } else if (Files.isDirectory(next)) {
                expand(next, segments, index + 1, out);
            }
            return;
        }

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, segment)) {
            for (Path entry : entries) {
                if (last) {
                    if (Files.isRegularFile(entry)) {
                        out.add(entry);
                    }
                } else if (Files.isDirectory(entry)) {
                    expand(entry, segments, index + 1, out);
                }
            }
        } catch (IOException ignored) {
            // Unreadable directory — contributes no files; the node's reachability check + the
            // unreachableNodes warning cover the user-visible signal.
        }
    }

    private static boolean hasGlobChar(String segment) {
        return segment.indexOf('*') >= 0 || segment.indexOf('?') >= 0
                || segment.indexOf('[') >= 0 || segment.indexOf('{') >= 0;
    }
}
