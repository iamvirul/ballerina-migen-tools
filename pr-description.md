## Purpose

The `bal migen connector` command only accepted pre-compiled `.bala` packages (from Ballerina Central via `--package` or a local bala directory via `--path`). Developers authoring a Ballerina connector from source had no supported path to generate MI connector artifacts locally without first publishing to Ballerina Central.

Resolves https://github.com/wso2/product-integrator-mi/issues/4902

## Goals

- Allow `bal migen connector` to be run from inside (or pointed at) a handwritten Ballerina source project that contains `public client` classes.
- Produce the same MI connector artifacts (XML descriptors, JAR, ZIP) that are generated from a `.bala` project.
- Provide a clear, actionable error message when the source project has no `public client` class, guiding the user to `bal migen module` if applicable.

## Approach

Two targeted changes:

**1. `MigenExecutor.java` — replace hard rejection with client-class detection**

Previously, any `BuildProject` passed to the `connector` command was immediately rejected:
```java
if (isConnector && isBuildProject) {
    printStream.println("ERROR: Expected a Ballerina connector (Bala) project...");
    return;
}
```
This guard is removed. Instead, after compilation succeeds, a new `hasPublicClientClass` helper inspects the compiled package's semantic model across all modules and checks for symbols with both `PUBLIC` and `CLIENT` qualifiers. If found, execution continues into `BalConnectorAnalyzer` as normal. If not found, a descriptive error is printed.

**2. `ConnectorSerializer.java` — fix NPE when `CONNECTOR_TARGET_PATH` is null**

The `CONNECTOR_TARGET_PATH` system property is only set during `BalaProject` builds. For source projects the emitted JAR lives at `sourcePath` (`bin/<name>.jar`). `copyResourcesAndPackage` now falls back to `sourcePath` when the system property is absent, eliminating the `NullPointerException`.

No changes were required to `BalConnectorAnalyzer`, `ConnectorSerializer` (beyond the null-guard), or `ConnectorValidator` — all operate on the compiled model and are already project-type-agnostic.

## User stories

- As a Ballerina connector developer, I want to run `bal migen connector` from inside my source project and get MI connector artifacts, so I can test locally without publishing to Ballerina Central first.
- As a developer who accidentally runs `bal migen connector` on a non-connector project, I want a clear error message that tells me to use `bal migen module` instead.

## Release note

`bal migen connector` now supports handwritten Ballerina connector source projects. When run inside a project containing a `public client` class, the command generates MI connector artifacts using the same pipeline as pre-compiled `.bala` packages. If no `public client` class is found, a descriptive error is shown.

## Documentation

N/A — CLI behaviour change is self-describing via updated error messages and the CHANGELOG entry. No product documentation page covers this command at this level of detail.

## Training

N/A

## Certification

N/A — no impact on certification exams; this is an additive CLI enhancement with no change to MI runtime behaviour.

## Marketing

N/A

## Automation tests

- Unit tests
  - Existing `MigenExecutorTest` and `ConnectorSerializerTest` suites continue to pass.
  - A new test case covering a `BuildProject` with a `public client` class should be added to `MigenExecutorTest` to validate the happy path and the no-client-class error path.
- Integration tests
  - Manually validated against `tests/src/test/resources/ballerina/project4` (source connector project with 38 components). MI connector artifacts were generated successfully and the ZIP passed `ConnectorValidator`.

## Security checks

- Followed secure coding standards in http://wso2.com/technical-reports/wso2-secure-engineering-guidelines? yes
- Ran FindSecurityBugs plugin and verified report? yes
- Confirmed that this PR doesn't commit any keys, passwords, tokens, usernames, or other secrets? yes

## Samples

Run from inside any Ballerina source project that defines a `public client` class:
```bash
cd my-ballerina-connector/
bal migen connector
# Generating MI connector artifacts...
# Found N component(s)
# Validating artifacts...
# MI connector generation completed successfully.
```
Output artifacts are written to `target/mi/` by default, identical in structure to artifacts generated from a `.bala` package.

## Related PRs

N/A

## Migrations (if applicable)

N/A — no breaking changes. Existing `.bala` and `--package` workflows are unaffected.

## Test environment

- JDK 21
- macOS 15 (Darwin 25.3.0)
- Ballerina 2201.13.1

## Learning

The `BalConnectorAnalyzer.isClientClass` method already used `Qualifier.PUBLIC` and `Qualifier.CLIENT` checks on `ClassSymbol`. The new `hasPublicClientClass` helper reuses the same qualifier pattern to detect connector projects before analysis begins, keeping detection logic consistent with the analyzer.
