// package com.smarsh.archscan;

// /*
//  * COMMENTED OUT - This test file is kept for reference.
//  * The logic has been moved to MongoRules.java for standalone CLI usage.
//  *

// import com.tngtech.archunit.base.DescribedPredicate;
// import com.tngtech.archunit.core.domain.Dependency;
// import com.tngtech.archunit.core.domain.JavaClass;
// import com.tngtech.archunit.core.domain.JavaClasses;
// import com.tngtech.archunit.core.importer.ClassFileImporter;
// import com.tngtech.archunit.core.importer.ImportOption;
// import com.tngtech.archunit.core.importer.Location;
// import com.tngtech.archunit.lang.ArchRule;
// import com.tngtech.archunit.lang.EvaluationResult;
// import org.testng.Assert;
// import org.testng.annotations.Test;

// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// public class UniversalCleanlinessTest {

//     // 🎯 CONFIGURATION
//     private static final String CONSUMER_PACKAGE = "com.smarsh.admin.commands";

//     private static final List<String> COMPANY_NAMESPACES = Arrays.asList(
//             "/com/smarsh/",
//             "/com/actiance/"
//     );

//     private static final Set<String> ZOMBIE_CLASSES = new HashSet<>(Arrays.asList(
//             "com.actiance.platform.sfab.ias.connector.mongodb.MongoDbService",
//             "com.smarsh.microserivce.connector.mongodb.MongoDbService"
//     ));

//     private static final String ADAPTER_CLASS = "com.smarsh.mongo.datalayer.ea.infrastructure.MongoFeatureToggleAdapter";

//     // Memory Bank
//     private static final Map<String, String> evidenceMap = new ConcurrentHashMap<>();

//     @Test(description = "🌍 Deep Scan: Full Chain Extraction Report")
//     public void ensureNoActiveLegacyUsageReachable() {
//         System.out.println("⏳ Initializing Deep Scan...");

//         // 1. FILTER
//         ImportOption allowOnlyInternalCode = new ImportOption() {
//             @Override
//             public boolean includes(Location location) {
//                 String path = location.toString().replace("\\", "/");
//                 if (ADAPTER_CLASS != null && !ADAPTER_CLASS.trim().isEmpty()) {
//                     String simpleName = ADAPTER_CLASS.substring(ADAPTER_CLASS.lastIndexOf('.') + 1);
//                     if (path.contains(simpleName)) return false;
//                 }
//                 for (String namespace : COMPANY_NAMESPACES) {
//                     if (path.contains(namespace)) return true;
//                 }
//                 return false;
//             }
//         };

//         // 2. IMPORT
//         JavaClasses allClasses = new ClassFileImporter()
//                 .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
//                 .withImportOption(allowOnlyInternalCode)
//                 .importClasspath();

//         System.out.println("🔎 Analyzing reachability graph of " + allClasses.size() + " classes...");

//         // 3. PREDICATE
//         DescribedPredicate<JavaClass> isTaintedByActiveUsage =
//                 new DescribedPredicate<JavaClass>("actively uses legacy code") {
//                     @Override
//                     public boolean test(JavaClass javaClass) {
//                         if (javaClass.isInterface() || javaClass.isEnum()) return false;

//                         for (Dependency dep : javaClass.getDirectDependenciesFromSelf()) {
//                             String target = dep.getTargetClass().getName();
//                             if (ZOMBIE_CLASSES.contains(target)) {
//                                 evidenceMap.put(javaClass.getName(), target);
//                             }
//                         }

//                         boolean callsMethod = javaClass.getMethodCallsFromSelf().stream()
//                                 .anyMatch(call -> ZOMBIE_CLASSES.contains(call.getTarget().getOwner().getName()));
//                         if (callsMethod) return true;

//                         return javaClass.getConstructorCallsFromSelf().stream()
//                                 .anyMatch(call -> ZOMBIE_CLASSES.contains(call.getTarget().getOwner().getName()));
//                     }
//                 };

//         // 4. RULE
//         ArchRule rule = noClasses()
//                 .that().resideInAPackage(CONSUMER_PACKAGE + "..")
//                 .should().transitivelyDependOnClassesThat(isTaintedByActiveUsage)
//                 .because("We must not rely on any code that actively executes legacy Mongo logic.");

//         EvaluationResult evaluate = rule.evaluate(allClasses);
//         if (evaluate.hasViolation()) {
//             Assert.fail(evaluate.getFailureReport().toString());
//         }

//         //evaluateAndReport(rule, allClasses);

//     }


//     /**
//      * 📋 SIMPLIFIED MIGRATION MANIFEST
//      * Columns: [Library Class To Extract] | [Used By Source Class]
//      */
//     private void evaluateAndReport(ArchRule rule, JavaClasses classes) {
//         EvaluationResult result = rule.evaluate(classes);

//         if (!result.hasViolation()) {
//             return;
//         }

//         // Two columns only: Library Class | Source Class
//         String format = "%-95s | %s";

//         System.err.println("\n🛑 ARCHITECTURE VIOLATION DETECTED!");
//         System.err.println("=======================================================================================================================================================================");
//         System.err.println(String.format(format, "LIBRARY CLASS TO EXTRACT (The Chain Link)", "USED BY SOURCE CLASS (Your Code)"));
//         System.err.println("=======================================================================================================================================================================");

//         Pattern classPattern = Pattern.compile("<([^>]+)>");

//         // Map<LibraryClass, Set<SourceClass>>
//         // TreeMap ensures alphabetical sorting of the library classes
//         Map<String, Set<String>> extractionList = new TreeMap<>();

//         for (String line : result.getFailureReport().getDetails()) {

//             // 1. Identify the Source Class (First <Class> tag)
//             Matcher matcher = classPattern.matcher(line);
//             String sourceClass = "";
//             if (matcher.find()) {
//                 sourceClass = cleanClassName(matcher.group(1));
//             }

//             // 2. Extract the Dependency Chain (Everything inside [...])
//             // Example: [com.lib.A->com.lib.B->com.lib.C]
//             int startBracket = line.indexOf("[");
//             int endBracket = line.indexOf("]");

//             if (startBracket != -1 && endBracket != -1) {
//                 String chainContent = line.substring(startBracket + 1, endBracket);
//                 String[] chainLinks = chainContent.split("->");

//                 // 3. Process Every Link in the Chain
//                 for (String link : chainLinks) {
//                     String libraryClass = cleanClassName(link.trim());

//                     // FILTER:
//                     // - Exclude the Source Class itself (not a library class)
//                     // - Exclude the Zombie itself (we know about it, and we can't move it)
//                     if (!libraryClass.equals(sourceClass) && !ZOMBIE_CLASSES.contains(libraryClass)) {

//                         extractionList.computeIfAbsent(libraryClass, k -> new TreeSet<>()).add(sourceClass);
//                     }
//                 }
//             }
//         }

//         // 4. Print Grouped Report
//         for (Map.Entry<String, Set<String>> entry : extractionList.entrySet()) {
//             String libClass = entry.getKey();
//             Set<String> sources = entry.getValue();

//             boolean firstRow = true;
//             for (String source : sources) {
//                 if (firstRow) {
//                     // Print Library Class name on the first line
//                     System.err.println(String.format(format, libClass, source));
//                     firstRow = false;
//                 } else {
//                     // Leave first column blank for subsequent usages
//                     System.err.println(String.format(format, "", source));
//                 }
//             }
//             // Separator line
//             System.err.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------");
//         }

//         System.err.println("\n");
//         Assert.fail("Found " + extractionList.size() + " unique library classes that need to be extracted.");
//     }

//     private String cleanClassName(String input) {
//         if (input.contains("(")) {
//             return input.substring(0, input.indexOf('('));
//         }
//         return input;
//     }
// }

// */ // END OF COMMENTED OUT CODE
