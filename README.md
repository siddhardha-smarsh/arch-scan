# arch-scan

Standalone Architecture Governance Scanner for MongoDB Migration verification.

## What it does

Runs three ArchUnit-based rules to verify MongoDB migration compliance:

1. **Single Gateway Rule** - Ensures all MongoDB connections go through the SDK (`com.smarsh.mongodb.sdk`)
2. **Legacy Driver Rule** - Detects any legacy MongoDB driver classes (v3/v2) in compiled code
3. **Active Legacy Usage Rule** - Finds transitive dependencies on zombie/legacy MongoDB classes

## Prerequisites

- Java 17+
- Maven 3.6+
- Target repo must be compiled (`mvn compile`)

## Build

```bash
mvn clean package
```

This creates `target/arch-scan-1.0.0.jar` (fat JAR with all dependencies).

## Usage

```bash
java -Xmx10g -jar target/arch-scan-1.0.0.jar /path/to/your/repo
```

### Example

```bash
# Clone and build arch-scan
git clone https://github.com/siddhardha-smarsh/arch-scan.git
cd arch-scan
mvn clean package

# Scan a target repo
cd /path/to/your-service
mvn compile
java -Xmx10g -jar /path/to/arch-scan/target/arch-scan-1.0.0.jar .
```

## Output

- Console output with violations grouped by rule
- `arch-scan-report.txt` written to the scanned repo's root directory

### Sample Output

```
================================================================================
                         ARCH-SCAN GOVERNANCE REPORT
================================================================================
Repository: /path/to/your-repo
Scan Time: 2026-03-04 10:30:15
================================================================================

RULE 1: Single Gateway Check
-----------------------------
Classes creating MongoDB connections outside the SDK:
  - com.example.service.MyService
  - com.example.dao.LegacyDao

RULE 2: Legacy Driver Presence
------------------------------
No legacy driver classes found in codebase. ✓

RULE 3: Active Legacy Usage
---------------------------
Classes with transitive dependencies on legacy MongoDB:
  - com.example.service.DataService → com.actiance.platform.sfab.ias.connector.mongodb.MongoDbService

================================================================================
SUMMARY: 3 violations found
================================================================================
```

## How it works

1. Scans for `target/classes` directories (supports multi-module repos)
2. Collects dependency JARs from Maven (filters to `com.smarsh` and `com.actiance` namespaces)
3. Imports all classes using ArchUnit
4. Runs governance rules checking transitive dependencies
5. Generates report

## Memory

Requires ~10GB heap for large codebases with many dependencies. Adjust `-Xmx` as needed.
