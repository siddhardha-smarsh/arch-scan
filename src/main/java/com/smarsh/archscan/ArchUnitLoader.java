package com.smarsh.archscan;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ArchUnitLoader {

    /**
     * Import classes from repo directories AND dependency JARs.
     */
    public static JavaClasses importClasses(List<Path> classDirs, List<Path> dependencyJars) {

        printMemory("Before import");

        System.out.println("\n📥 Importing from " + classDirs.size() + " class directories + " + dependencyJars.size() + " dependency JARs");

        try {
            // Convert all paths to URLs
            List<URL> urls = new ArrayList<>();
            
            // Add class directories
            for (Path dir : classDirs) {
                urls.add(dir.toUri().toURL());
            }
            
            // Add JARs as jar: URLs
            for (Path jar : dependencyJars) {
                // JAR URLs need the jar:file: scheme
                URL jarUrl = new URL("jar:" + jar.toUri().toURL() + "!/");
                urls.add(jarUrl);
            }
            
            System.out.println("   First few URLs:");
            for (int i = 0; i < Math.min(5, urls.size()); i++) {
                System.out.println("     " + urls.get(i).toString().substring(0, Math.min(80, urls.get(i).toString().length())));
            }

            ClassFileImporter importer = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

            JavaClasses classes = importer.importUrls(urls);

            printMemory("After import");

            System.out.println("📊 Total classes imported: " + classes.size());
            System.out.println();

            return classes;
            
        } catch (Exception e) {
            System.err.println("❌ Error importing classes: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void printMemory(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();

        System.out.printf(
                "🧠 %s - Used: %.2f GB | Max: %.2f GB%n",
                phase,
                bytesToGb(used),
                bytesToGb(max)
        );
    }

    private static double bytesToGb(long bytes) {
        return bytes / (1024.0 * 1024 * 1024);
    }
}