package com.smarsh.archscan;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects dependency JARs from Maven/Gradle projects.
 * Filters to only include company namespaces (com.smarsh, com.actiance).
 */
public class DependencyJarCollector {

    private static final List<String> COMPANY_NAMESPACES = Arrays.asList(
            "/com/smarsh/",
            "/com/actiance/"
    );

    /**
     * Collect all dependency JAR paths for the given repository.
     * Only includes JARs from company namespaces.
     */
    public static List<Path> collect(Path repoRoot) {
        List<Path> jars = new ArrayList<>();

        // Try Maven first
        Path pomFile = repoRoot.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            jars.addAll(collectMavenDependencies(repoRoot));
        }

        // Try Gradle
        Path gradleFile = repoRoot.resolve("build.gradle");
        Path gradleKtsFile = repoRoot.resolve("build.gradle.kts");
        if (Files.exists(gradleFile) || Files.exists(gradleKtsFile)) {
            jars.addAll(collectGradleDependencies(repoRoot));
        }

        // Filter to company namespaces only
        List<Path> filtered = jars.stream()
                .filter(DependencyJarCollector::isCompanyJar)
                .distinct()
                .collect(Collectors.toList());

        System.out.println("📚 Found " + filtered.size() + " company dependency JARs");
        for (Path jar : filtered) {
            System.out.println("   - " + jar.getFileName());
        }

        return filtered;
    }

    private static boolean isCompanyJar(Path jarPath) {
        String path = jarPath.toString().replace("\\", "/");
        for (String namespace : COMPANY_NAMESPACES) {
            if (path.contains(namespace)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> collectMavenDependencies(Path repoRoot) {
        List<Path> jars = new ArrayList<>();
        try {
            System.out.println("📦 Getting Maven classpath...");

            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", "dependency:build-classpath", "-q", "-DincludeScope=compile", "-Dmdep.outputFile=/dev/stdout"
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                // Maven outputs classpath as colon/semicolon separated paths
                if (line.contains(".jar")) {
                    String separator = File.pathSeparator;
                    String[] paths = line.split(separator);
                    for (String p : paths) {
                        p = p.trim();
                        if (p.endsWith(".jar") && Files.exists(Path.of(p))) {
                            jars.add(Path.of(p));
                        }
                    }
                }
            }

            process.waitFor();

        } catch (Exception e) {
            System.err.println("⚠️  Could not get Maven classpath: " + e.getMessage());
        }
        return jars;
    }

    private static List<Path> collectGradleDependencies(Path repoRoot) {
        List<Path> jars = new ArrayList<>();
        try {
            System.out.println("📦 Getting Gradle classpath...");

            // Create a temporary task to print classpath
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "dependencies", "--configuration", "compileClasspath", "-q"
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // For Gradle, we need to parse the dependency tree output
            // This is more complex, so we'll look for JAR files in gradle cache
            String gradleCache = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1";
            Path cachePath = Path.of(gradleCache);

            if (Files.exists(cachePath)) {
                // Look for company JARs in gradle cache
                Files.walk(cachePath, 6)
                        .filter(p -> p.toString().endsWith(".jar"))
                        .filter(DependencyJarCollector::isCompanyJar)
                        .forEach(jars::add);
            }

            process.waitFor();

        } catch (Exception e) {
            System.err.println("⚠️  Could not get Gradle classpath: " + e.getMessage());
        }
        return jars;
    }
}
