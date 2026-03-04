// package com.smarsh.archscan;

// /*
//  * COMMENTED OUT - This test file is kept for reference.
//  * The logic has been moved to MongoRules.java for standalone CLI usage.
//  *

// import com.tngtech.archunit.base.DescribedPredicate;
// import com.tngtech.archunit.core.domain.JavaClass;
// import com.tngtech.archunit.core.domain.JavaClasses;
// import com.tngtech.archunit.core.importer.ClassFileImporter;
// import com.tngtech.archunit.core.importer.ImportOption;
// import com.tngtech.archunit.core.importer.Location;
// import com.tngtech.archunit.lang.ArchRule;
// import org.testng.annotations.BeforeClass;
// import org.testng.annotations.Test;

// import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// // 🏁 MIGRATION VERIFICATION AUDITOR
// // Run this test manually to confirm the "Definition of Done" for the MongoDB Migration.
// // IT PROVES TWO THINGS:
// // 1. The "Choke Point": No one (including libraries) is bypassing the SDK to create connections.
// // 2. The "Clean Up": No legacy Mongo driver classes (v3/v2) exist in the codebase.

// public class MongoGovernanceTest {

//     // 🎯 CONFIGURATION
//     private static final String CONSUMER_PACKAGE = "com.example"; //Change this as per your consumer package
//     private static final String SDK_PACKAGE = "com.smarsh.mongodb.sdk";

//     private static final String RAW_DRIVER_FACTORY_V4_V5 = "com.mongodb.client.MongoClients";
//     private static final String RAW_DRIVER_FACTORY_V3 = "com.mongodb.MongoClient";

//     private static JavaClasses allClasses;

//     @BeforeClass
//     public static void setup() {
//         System.out.println("🚀 Starting Reachability Scan...");

//         // 🛑 FILTER: Scan widely, but exclude standard noise
//         ImportOption ignoreIrrelevant = new ImportOption() {
//             @Override
//             public boolean includes(Location location) {
//                 String path = location.toString().replace("\\", "/");
//                 return !path.contains("/jre/") && !path.contains("/jdk/")
//                         && !path.contains("/org/springframework/")
//                         && !path.contains("/org/junit/")
//                         && !path.contains("/org/apache/")
//                         && !path.contains("/org/mongodb/"); // Exclude driver itself
//             }
//         };

//         allClasses = new ClassFileImporter()
//                 .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
//                 .withImportOption(ignoreIrrelevant)
//                 .importClasspath();
//     }

//     /**
//      * ✅ CHECK 1: The Choke Point
//      * Fails if 'My Code' depends on any class (Lib/Internal) that creates a connection,
//      * UNLESS that class is the SDK.
//      */
//     @Test(description = "Ensure My Code only reaches DB via SDK")
//     public void verifySingleGatewayPattern() {
//         // 1. Identify "Guilty" Classes
//         DescribedPredicate<JavaClass> createsRawConnection =
//                 new DescribedPredicate<JavaClass>("creates raw mongo connection") {
//                     @Override
//                     public boolean test(JavaClass javaClass) {
//                         // Safe Zone: SDK is allowed to create connections
//                         if (javaClass.getPackageName().startsWith(SDK_PACKAGE)) return false;
//                         // Safe Zone: The driver itself is allowed to create connections (it's the factory!)
//                         if (javaClass.getPackageName().startsWith("com.mongodb")) return false;

//                         // Check for V4/V5 Factory Call
//                         boolean callsV4 = javaClass.getMethodCallsFromSelf().stream()
//                                 .anyMatch(call -> call.getTarget().getOwner().getName().equals(RAW_DRIVER_FACTORY_V4_V5)
//                                         && call.getTarget().getName().equals("create"));

//                         // Check for V3 Constructor Call
//                         boolean callsV3 = javaClass.getConstructorCallsFromSelf().stream()
//                                 .anyMatch(call -> call.getTarget().getOwner().getName().equals(RAW_DRIVER_FACTORY_V3));

//                         return callsV4 || callsV3;
//                     }
//                 };

//         // 2. The Rule (Reachability)
//         ArchRule rule = noClasses()
//                 .that().resideInAPackage(CONSUMER_PACKAGE + "..")
//                 .should().transitivelyDependOnClassesThat(createsRawConnection)
//                 .because("My code must not use any library or class that bypasses the SDK to create connections.");

//         // 3. Simple Check (Standard Assertion)
//         rule.check(allClasses);
//     }

//     /**
//      * ✅ CHECK 2: Legacy Artifacts
//      * Fails if 'My Code' depends on any Legacy V3 driver classes.
//      */
//     @Test(description = "Ensure My Code never reaches Legacy Driver classes")
//     public void verifyNoLegacyArtifacts() {
//         DescribedPredicate<JavaClass> usesLegacyDriver =
//                 new DescribedPredicate<JavaClass>("uses legacy driver") {
//                     @Override
//                     public boolean test(JavaClass javaClass) {
//                         return javaClass.getDirectDependenciesFromSelf().stream()
//                                 .anyMatch(dep -> {
//                                     String target = dep.getTargetClass().getName();
//                                     return target.equals("com.mongodb.Mongo") ||
//                                             target.equals("com.mongodb.DB") ||
//                                             target.equals("com.mongodb.DBCollection") ||
//                                             target.equals("com.mongodb.MongoClient");
//                                 });
//                     }
//                 };

//         ArchRule rule = noClasses()
//                 .that().resideInAPackage(CONSUMER_PACKAGE + "..")
//                 .should().transitivelyDependOnClassesThat(usesLegacyDriver)
//                 .because("My code must not rely on legacy MongoDB driver classes (v2/v3).");

//         rule.check(allClasses);
//     }
// }

// */ // END OF COMMENTED OUT CODE
