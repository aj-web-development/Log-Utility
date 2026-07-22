package com.in10s.logutility.validation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class PathAvailabilityCheckerImpl implements PathAvailabilityChecker {

    @Override
    public PathCheckResult check(String path) {
        if (!StringUtils.hasText(path)) {
            return new PathCheckResult(false, 0, "No path configured");
        }

        Path target;
        try {
            target = Paths.get(path.trim());
        } catch (InvalidPathException e) {
            return new PathCheckResult(false, 0, "Invalid path: " + e.getMessage());
        }

        if (!Files.exists(target)) {
            return new PathCheckResult(false, 0, "Path does not exist");
        }

        try {
            if (Files.isDirectory(target)) {
                try (Stream<Path> entries = Files.list(target)) {
                    long count = entries.count();
                    return new PathCheckResult(true, count, "Reachable — " + count + " entries");
                }
            }
            if (Files.isRegularFile(target)) {
                long size = Files.size(target);
                return new PathCheckResult(true, 1, "Reachable — file exists (" + size + " bytes)");
            }
            return new PathCheckResult(false, 0, "Path exists but is neither a file nor a directory");
        } catch (IOException | SecurityException e) {
            return new PathCheckResult(false, 0, "Could not access path: " + e.getMessage());
        }
    }
}
