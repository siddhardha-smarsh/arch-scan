package com.smarsh.archscan;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Main {

    private static final long RECOMMENDED_HEAP_BYTES = 10L * 1024 * 1024 * 1024; // 10GB

    public static void main(String[] args) {

        printBanner();

        if (args.length != 1) {
            System.err.println("Usage: java -Xmx10g -jar arch-scan.jar <repo-path>");
            System.exit(1);
        }

        Path repoPath = Path.of(args[0]).toAbsolutePath().normalize();
        File repoDir = repoPath.toFile();

        if (!repoDir.exists() || !repoDir.isDirectory()) {
            System.err.println("❌ Invalid repository path: " + repoPath);
            System.exit(1);
        }

        checkHeap();

        System.out.println("📁 Repository: " + repoPath);
        System.out.println();

        // -------------------------------
        // Phase 2: Class Directory Scan
        // -------------------------------
        System.out.println("🔎 Scanning for compiled class directories...");

        List<Path> classDirs;

        try {
            classDirs = ClassDirectoryCollector.collect(repoPath);
        } catch (Exception e) {
            System.err.println("❌ Failed while scanning repository: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (classDirs.isEmpty()) {
            System.err.println("❌ No compiled classes detected.");
            System.err.println("   Please run: mvn compile  OR  gradle build");
            System.exit(1);
        }

        System.out.println("📦 Modules detected: " + classDirs.size());
        for (Path dir : classDirs) {
            System.out.println("   - " + dir);
        }

        System.out.println();

        // -------------------------------
        // Phase 2b: Collect Dependency JARs
        // -------------------------------
        System.out.println("🔎 Scanning for dependency JARs (company namespaces only)...");
        List<Path> dependencyJars = DependencyJarCollector.collect(repoPath);

        System.out.println();
        System.out.println("🚀 Starting ArchUnit import...\n");

        JavaClasses classes = ArchUnitLoader.importClasses(classDirs, dependencyJars);

        if (classes == null || classes.size() == 0) {
            System.err.println("❌ No classes imported.");
            System.exit(1);
        }

        // -------------------------------
        // Phase 3: Detect Consumer Packages (from repo classes only)
        // -------------------------------
        Set<String> consumerPackages = extractConsumerPackages(classes, classDirs);
        System.out.println("📋 Consumer packages (to be checked):");
        for (String pkg : consumerPackages) {
            System.out.println("   - " + pkg);
        }
        System.out.println();
        
        // Configure rules to only check consumer classes
        MongoRules.setConsumerPackages(consumerPackages);

        // -------------------------------
        // Phase 4 + 5: Rule Execution + Report
        // -------------------------------

        System.out.println("🧪 Running Mongo Governance Rules...\n");

        // Run all rules and collect violations
        List<String> rule1Violations = MongoRules.checkSingleGateway(classes);
        List<String> rule2Violations = MongoRules.checkLegacyDriver(classes);
        List<String> rule3Violations = MongoRules.checkActiveLegacyUsage(classes);
        
        int totalViolations = rule1Violations.size() + rule2Violations.size() + rule3Violations.size();
        
        // Generate report
        Path reportPath = repoPath.resolve("arch-scan-report.txt");
        writeReport(reportPath, consumerPackages, rule1Violations, rule2Violations, rule3Violations);
        System.out.println("📄 Report written to: " + reportPath.toAbsolutePath());
        
        if (totalViolations == 0) {
            System.out.println("\n✅ All Mongo governance rules passed.");
            System.exit(0);
        } else {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("❌ GOVERNANCE VIOLATIONS SUMMARY");
            System.out.println("=".repeat(60));
            System.out.println("   Rule 1 (Single Gateway Pattern): " + rule1Violations.size() + " violation(s)");
            System.out.println("   Rule 2 (Legacy Driver Usage):    " + rule2Violations.size() + " violation(s)");
            System.out.println("   Rule 3 (Zombie Service Usage):   " + rule3Violations.size() + " violation(s)");
            System.out.println("   TOTAL:                           " + totalViolations + " violation(s)");
            System.out.println("=".repeat(60));
            System.exit(1);
        }
    }
    
    /**
     * Write violations report to file
     */
    private static void writeReport(Path reportPath, Set<String> consumerPackages,
                                     List<String> rule1, List<String> rule2, List<String> rule3) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath.toFile()))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            writer.println("================================================================================");
            writer.println("                    ARCH-SCAN GOVERNANCE REPORT");
            writer.println("================================================================================");
            writer.println("Generated: " + timestamp);
            writer.println("Consumer Packages: " + String.join(", ", consumerPackages));
            writer.println();
            
            int total = rule1.size() + rule2.size() + rule3.size();
            writer.println("SUMMARY");
            writer.println("--------------------------------------------------------------------------------");
            writer.println("  Rule 1 (Single Gateway Pattern): " + rule1.size() + " violation(s)");
            writer.println("  Rule 2 (Legacy Driver Usage):    " + rule2.size() + " violation(s)");
            writer.println("  Rule 3 (Zombie Service Usage):   " + rule3.size() + " violation(s)");
            writer.println("  TOTAL:                           " + total + " violation(s)");
            writer.println();
            
            // Rule 1 violations
            if (!rule1.isEmpty()) {
                writer.println("================================================================================");
                writer.println("RULE 1: Single Gateway Pattern");
                writer.println("Description: No code should bypass the MongoDB SDK to create connections");
                writer.println("================================================================================");
                writer.println();
                int idx = 1;
                for (String violation : rule1) {
                    writer.println("[" + idx + "] " + formatViolationForReport(violation));
                    writer.println();
                    idx++;
                }
            }
            
            // Rule 2 violations
            if (!rule2.isEmpty()) {
                writer.println("================================================================================");
                writer.println("RULE 2: No Legacy Driver Usage");
                writer.println("Description: No code should use legacy MongoDB driver classes (v2/v3)");
                writer.println("================================================================================");
                writer.println();
                int idx = 1;
                for (String violation : rule2) {
                    writer.println("[" + idx + "] " + formatViolationForReport(violation));
                    writer.println();
                    idx++;
                }
            }
            
            // Rule 3 violations
            if (!rule3.isEmpty()) {
                writer.println("================================================================================");
                writer.println("RULE 3: No Zombie Service Usage");
                writer.println("Description: No code should depend on deprecated MongoDbService classes");
                writer.println("================================================================================");
                writer.println();
                int idx = 1;
                for (String violation : rule3) {
                    writer.println("[" + idx + "] " + formatViolationForReport(violation));
                    writer.println();
                    idx++;
                }
            }
            
            writer.println("================================================================================");
            writer.println("                              END OF REPORT");
            writer.println("================================================================================");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to write report: " + e.getMessage());
        }
    }
    
    /**
     * Format violation for clean report output
     */
    private static String formatViolationForReport(String violation) {
        // Parse the violation and format it cleanly
        // Input: "Class <com.foo.Bar> transitively depends on <com.baz.Qux> by [A->B->C] in (Bar.java:0)"
        
        StringBuilder sb = new StringBuilder();
        
        // Extract class name
        int classStart = violation.indexOf("<");
        int classEnd = violation.indexOf(">");
        if (classStart >= 0 && classEnd > classStart) {
            String className = violation.substring(classStart + 1, classEnd);
            sb.append("Source: ").append(className).append("\n");
        }
        
        // Check if transitive or direct
        if (violation.contains("transitively depends on")) {
            int targetStart = violation.indexOf("transitively depends on <") + 25;
            int targetEnd = violation.indexOf(">", targetStart);
            if (targetStart > 25 && targetEnd > targetStart) {
                String target = violation.substring(targetStart, targetEnd);
                sb.append("    Target: ").append(target).append("\n");
            }
            
            // Extract chain
            int chainStart = violation.indexOf("[");
            int chainEnd = violation.indexOf("]");
            if (chainStart >= 0 && chainEnd > chainStart) {
                String chain = violation.substring(chainStart + 1, chainEnd);
                String[] links = chain.split("->");
                sb.append("    Chain:\n");
                for (int i = 0; i < links.length; i++) {
                    String prefix = (i == 0) ? "      " : "      -> ";
                    sb.append(prefix).append(links[i].trim()).append("\n");
                }
            }
        } else if (violation.contains("depends on")) {
            // Direct dependency
            int targetStart = violation.indexOf("depends on <") + 12;
            int targetEnd = violation.indexOf(">", targetStart);
            if (targetStart > 12 && targetEnd > targetStart) {
                String target = violation.substring(targetStart, targetEnd);
                sb.append("    Directly depends on: ").append(target).append("\n");
            }
        }
        
        return sb.toString().trim();
    }

    /**
     * Extract consumer packages from repo classes only (not from JARs).
     * Only classes whose source location is in the classDirs are considered.
     */
    private static Set<String> extractConsumerPackages(JavaClasses classes, List<Path> classDirs) {
        Set<String> packages = new TreeSet<>();
        
        for (JavaClass javaClass : classes) {
            String sourceUri = javaClass.getSource().get().getUri().toString();
            
            // Only include classes from repo class directories (not JARs)
            boolean isFromRepo = classDirs.stream()
                    .anyMatch(dir -> sourceUri.contains(dir.toString().replace("\\", "/")));
            
            if (isFromRepo) {
                String pkg = javaClass.getPackageName();
                // Get the top-level package (e.g., com.smarsh.export.packager)
                String[] parts = pkg.split("\\.");
                if (parts.length >= 4) {
                    packages.add(parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3]);
                } else if (parts.length >= 3) {
                    packages.add(parts[0] + "." + parts[1] + "." + parts[2]);
                } else {
                    packages.add(pkg);
                }
            }
        }
        return packages;
    }

    /**
     * Extract top-level packages (e.g., com.smarsh.export.packager) from imported classes
     */
    private static Set<String> extractTopLevelPackages(JavaClasses classes) {
        Set<String> packages = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            // Get top 3-4 levels as the "consumer package"
            String[] parts = pkg.split("\\.");
            if (parts.length >= 4) {
                packages.add(parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3]);
            } else if (parts.length >= 3) {
                packages.add(parts[0] + "." + parts[1] + "." + parts[2]);
            } else {
                packages.add(pkg);
            }
        }
        return packages;
    }

    private static void printBanner() {
        System.out.println("==================================================");
        System.out.println("              ARCH SCAN - v1.0.0");
        System.out.println("==================================================\n");
    }

    private static void checkHeap() {
        long maxHeap = Runtime.getRuntime().maxMemory();

        System.out.printf("🧠 Max Heap: %.2f GB%n", bytesToGb(maxHeap));

        if (maxHeap < RECOMMENDED_HEAP_BYTES) {
            System.out.println("⚠ Recommended heap size: 10 GB");
            System.out.println("  You may run: java -Xmx10g -jar arch-scan.jar .");
        }

        System.out.println();
    }

    private static double bytesToGb(long bytes) {
        return bytes / (1024.0 * 1024 * 1024);
    }
}