package com.smarsh.archscan;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ClassDirectoryCollector {

    public static List<Path> collect(Path repoRoot) throws IOException {

        List<Path> classDirs = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoRoot)) {

            paths.filter(Files::isDirectory)
                    .filter(ClassDirectoryCollector::isMainClassDirectory)
                    .forEach(dir -> {
                        if (containsClassFiles(dir)) {
                            classDirs.add(dir);
                        }
                    });
        }

        return classDirs;
    }

    private static boolean isMainClassDirectory(Path path) {

        String normalized = path.toString().replace("\\", "/");

        // Maven main classes
        if (normalized.endsWith("/target/classes")) {
            return true;
        }

        // Gradle main classes
        if (normalized.endsWith("/build/classes/java/main")) {
            return true;
        }

        return false;
    }

    private static boolean containsClassFiles(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.anyMatch(p -> p.toString().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }
}