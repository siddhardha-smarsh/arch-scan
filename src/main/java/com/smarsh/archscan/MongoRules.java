package com.smarsh.archscan;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import java.util.*;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * MongoDB Governance Rules - Using ArchUnit's native transitive dependency checking
 */
public class MongoRules {

    private static Set<String> consumerPackages = new HashSet<>();
    
    public static void setConsumerPackages(Set<String> packages) {
        consumerPackages = packages;
    }

    private static final String SDK_PACKAGE = "com.smarsh.mongodb.sdk";
    private static final String RAW_DRIVER_FACTORY_V4_V5 = "com.mongodb.client.MongoClients";
    private static final String RAW_DRIVER_FACTORY_V3 = "com.mongodb.MongoClient";

    private static final Set<String> LEGACY_CLASSES = new HashSet<>(Arrays.asList(
            "com.mongodb.Mongo",
            "com.mongodb.DB",
            "com.mongodb.DBCollection",
            "com.mongodb.MongoClient"
    ));

    private static final Set<String> ZOMBIE_CLASSES = new HashSet<>(Arrays.asList(
            "com.actiance.platform.sfab.ias.connector.mongodb.MongoDbService",
            "com.smarsh.microserivce.connector.mongodb.MongoDbService"
    ));

    // ==========================================================
    // RULE 1: Single Gateway Pattern
    // ==========================================================

    public static List<String> checkSingleGateway(JavaClasses classes) {
        System.out.println("  📋 Rule 1: Single Gateway Pattern (no raw connection creation)");

        if (consumerPackages.isEmpty()) {
            System.out.println("     ⚠️  No consumer packages defined, skipping rule\n");
            return Collections.emptyList();
        }

        DescribedPredicate<JavaClass> createsRawConnection =
                new DescribedPredicate<JavaClass>("creates raw mongo connection") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        if (javaClass.getPackageName().startsWith(SDK_PACKAGE)) return false;
                        if (javaClass.getPackageName().startsWith("com.mongodb")) return false;

                        boolean callsV4 = javaClass.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> call.getTarget().getOwner().getName().equals(RAW_DRIVER_FACTORY_V4_V5)
                                        && call.getTarget().getName().equals("create"));

                        boolean callsV3 = javaClass.getConstructorCallsFromSelf().stream()
                                .anyMatch(call -> call.getTarget().getOwner().getName().equals(RAW_DRIVER_FACTORY_V3));

                        return callsV4 || callsV3;
                    }
                };

        List<String> allViolations = new ArrayList<>();
        
        for (String pkg : consumerPackages) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(pkg + "..")
                    .should().transitivelyDependOnClassesThat(createsRawConnection)
                    .because("My code must not use any library or class that bypasses the SDK to create connections.");

            EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                allViolations.addAll(result.getFailureReport().getDetails());
            }
        }

        printViolationSummary(allViolations.size());
        return allViolations;
    }

    // ==========================================================
    // RULE 2: No Legacy Driver Classes
    // ==========================================================

    public static List<String> checkLegacyDriver(JavaClasses classes) {
        System.out.println("  📋 Rule 2: No Legacy Driver Usage (v2/v3 classes)");

        if (consumerPackages.isEmpty()) {
            System.out.println("     ⚠️  No consumer packages defined, skipping rule\n");
            return Collections.emptyList();
        }

        DescribedPredicate<JavaClass> usesLegacyDriver =
                new DescribedPredicate<JavaClass>("uses legacy driver") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.getDirectDependenciesFromSelf().stream()
                                .anyMatch(dep -> LEGACY_CLASSES.contains(dep.getTargetClass().getName()));
                    }
                };

        List<String> allViolations = new ArrayList<>();
        
        for (String pkg : consumerPackages) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(pkg + "..")
                    .should().transitivelyDependOnClassesThat(usesLegacyDriver)
                    .because("My code must not rely on legacy MongoDB driver classes (v2/v3).");

            EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                allViolations.addAll(result.getFailureReport().getDetails());
            }
        }

        printViolationSummary(allViolations.size());
        return allViolations;
    }

    // ==========================================================
    // RULE 3: No Zombie Service Usage
    // ==========================================================

    public static List<String> checkActiveLegacyUsage(JavaClasses classes) {
        System.out.println("  📋 Rule 3: No Zombie Service Usage (deprecated MongoDbService)");

        if (consumerPackages.isEmpty()) {
            System.out.println("     ⚠️  No consumer packages defined, skipping rule\n");
            return Collections.emptyList();
        }

        DescribedPredicate<JavaClass> activelyUsesLegacyCode =
                new DescribedPredicate<JavaClass>("actively uses legacy code") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        if (javaClass.isInterface() || javaClass.isEnum()) return false;

                        boolean hasDependency = javaClass.getDirectDependenciesFromSelf().stream()
                                .anyMatch(dep -> ZOMBIE_CLASSES.contains(dep.getTargetClass().getName()));
                        if (hasDependency) return true;

                        boolean callsMethod = javaClass.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> ZOMBIE_CLASSES.contains(call.getTarget().getOwner().getName()));
                        if (callsMethod) return true;

                        return javaClass.getConstructorCallsFromSelf().stream()
                                .anyMatch(call -> ZOMBIE_CLASSES.contains(call.getTarget().getOwner().getName()));
                    }
                };

        List<String> allViolations = new ArrayList<>();
        
        for (String pkg : consumerPackages) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(pkg + "..")
                    .should().transitivelyDependOnClassesThat(activelyUsesLegacyCode)
                    .because("We must not rely on any code that actively executes legacy Mongo logic.");

            EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                allViolations.addAll(result.getFailureReport().getDetails());
            }
        }

        printViolationSummary(allViolations.size());
        return allViolations;
    }

    private static void printViolationSummary(int count) {
        if (count == 0) {
            System.out.println("     ✅ No violations found\n");
        } else {
            System.out.println("     ❌ " + count + " violation(s) found\n");
        }
    }
}
