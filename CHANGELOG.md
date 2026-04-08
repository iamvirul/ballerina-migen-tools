# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] - 2026-04-08

### Fixed
- Fixed resource function invocations hanging on the second request due to Strand lifecycle not being properly managed. The `BalExecutor` now calls `strand.done()` in a `finally` block and uses `CompletableFuture.get()` instead of polling, ensuring HTTP connections are released back to the pool.
- Fixed JVM method name generation for resource functions with special characters in path segments (e.g., Slack's `auth.test`). Dots, slashes, and other JVM-reserved characters are now encoded using Ballerina's `&XXXX` format (e.g., `.` → `&0046`), with proper unescaping of Ballerina identifier escape sequences before encoding.
- Fixed invalid XML parameter names caused by forward slashes and backslashes in Ballerina record field names (e.g., `prefs\/externalMembersDisabled`). `sanitizeParamName` now replaces `\/`, `/`, and `\` with underscores.
- Fixed dots appearing in generated Synapse operation/template names for connectors with dot-separated path segments (e.g., Slack). `toPascalCase`, `toPascalCaseSegment`, and `sanitizeXmlName` now treat dots as word separators, producing names like `getAdminAppsApprovedList` instead of `getAdmin_.apps_.approved_.list`.
- Fixed `NumberFormatException` when optional parameters are missing from request JSON. `json-eval()` returns empty string `""` for missing fields; `ParamHandler` and `DataTransformer.setNestedField` now convert empty strings to null/skip for non-string types, allowing Ballerina to receive `()` (nil) for optional parameters.
- Fixed Ballerina execution errors not being propagated properly from the `BalExecutor`. `InterruptedException` and `ExecutionException` from `CompletableFuture.get()` are now handled explicitly with proper interrupt flag restoration and `BError` unwrapping.
- Fixed union parameter handling for connector init configurations. Union member pointers, record fields, and discriminator properties are now correctly emitted in `init.xml`, and record-typed union members are properly reconstructed from flattened context fields.
- Fixed connection-type prefix not being propagated in `ParamHandler.getUnionParameter`, which caused runtime errors when resolving union parameters in connectors with prefixed property keys (e.g., SAP JCo).
- Fixed an infinite recursion bug in `XmlPropertyWriter` caused by cyclic nested `UnionFunctionParam` types (e.g. `ClientCredentialsGrantConfig`). The generator now tracks visited types and stops recursive property unrolling cleanly.

## [1.0.0] - 2025-02-05

### Added
- Initial release of the `ballerina-migen-tools` framework.
