# Verification for this delivery

- Java runtime: OpenJDK 17.0.20.
- Source/target: Java 17, compiled with Eclipse ECJ 3.40.0 (the environment has a JRE but no javac).
- Test runner: JUnit Platform Console Standalone 6.0.0, matching the repository's JUnit 6 dependency.
- Database: H2 2.3.232, matching build.gradle.
- Result: **95 tests passed, 0 failures, 0 skipped**.
- Breakdown: 39 original persistence tests, 48 transaction integration tests, 8 JDBC fault-injection tests.

Coverage includes transaction-boundary commit visibility and rollback; transaction connection sharing for reads, aggregates and updates; all seven propagation modes; savepoint rollback and callback removal; rollback-only after caught failures; independent REQUIRES_NEW commit and suspend/resume; Java-processing and actual running-SQL timeout; remaining statement timeout; owner-deadline inheritance; common timeout configuration and snapshotting; optional explicit overrides; isolation conflicts; read-only enforcement; configured rollback exceptions; transactions for unannotated proxy methods; optional interface/class/method annotation overrides; manual clearing of both operation collections; cross-service invocation and unwrapped exceptions; async return rejection and no ThreadLocal transfer; dirty flags for later unsaved edits; commit failures with uncertain outcomes; rollback/close/setup/restoration failures; connection abort and ThreadLocal cleanup.

The Gradle wrapper JAR was restored, gradlew made executable, and the distribution pinned to Gradle 9.1.0 with the official distribution SHA-256. Tests were compiled and run directly using ECJ/JUnit rather than Gradle because this execution environment lacks a JDK compiler. On a machine with JDK 17, use `./gradlew test` and `./gradlew build`.

Not validated here: the Road Rescue server's handler/service wiring (not included in this repository), a live MySQL instance, vendor-specific cancellation/savepoint behavior, and a particular connection pool's physical abort/discard handling. TRANSACTIONS.md describes integration and timeout semantics explicitly.
