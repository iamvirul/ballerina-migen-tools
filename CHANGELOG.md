# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Added `--package` option to the `bal migen module` command to pull and generate MI artifacts directly from a Ballerina Central package without a local project. When `--package <org/name:version>` is specified, the package is pulled from Central, extracted, and analyzed via `BalModuleAnalyzer`. If no version is given, the latest published version is used. ([#4843](https://github.com/wso2/product-integrator-mi/issues/4843))
- Added fallback generation mode to the `bal migen module` command: when a Ballerina module contains no `@mi:Operation` annotations, MI artifacts are now generated for all public module-level functions automatically. Private functions are excluded. This allows third-party and Ballerina Central packages to be used as MI modules without modification. ([#4841](https://github.com/wso2/product-integrator-mi/issues/4841))
### Fixed
- Fixed enum-typed combo box fields in the generated UI schema always defaulting to the first enum value instead of the declared Ballerina default. `BalConnectorAnalyzer` now traverses `UnionFunctionParam` members, applies a per-field lazy fallback for fields inherited via record inclusion, and resolves defaults through a regex text-scan of the field type's module bala source files. ([#4847](https://github.com/wso2/product-integrator-mi/issues/4847))
- Fixed connector artifact generation failing on Windows due to mixed path separators in classpath template paths. `File.separator` (`\`) combined with hardcoded `/` produced paths like `balConnector\functions/functions_template.xml` that `ClassPathTemplateLoader` could not resolve. The template path is now normalised to forward slashes before classpath lookup. ([#1461](https://github.com/wso2/mi-vscode/issues/1461))

## [1.0.2] - 2026-04-20

### Fixed
- Fixed `NullPointerException` at runtime when invoking connector functions with enum-typed parameters. `ParamHandler` now handles the `ENUM` type case by converting the string value to a `BString`. ([#4829](https://github.com/wso2/product-integrator-mi/issues/4829))
- Fixed `ClassCastException` when passing `map` or `record` typed parameters from Synapse templates that wrap JSON values in single quotes. `cleanupJsonString` now strips surrounding single quotes before parsing. ([#4830](https://github.com/wso2/product-integrator-mi/issues/4830))
- Fixed enum parameter default values being generated as the Ballerina identifier name (e.g. `DEFAULT`) instead of the actual constant value (e.g. `0`) in the UI schema. `BalConnectorAnalyzer` now resolves enum constant identifiers to their runtime values via the semantic model. ([#4831](https://github.com/wso2/product-integrator-mi/issues/4831))
- Fixed `InherentTypeViolation` runtime errors by ensuring generic maps and arrays are explicitly converted to their expected inherent types before Ballerina function invocation. This includes strict matching for Union member selection and recursive unwrapping of Intersection and Type Reference types. ([#4828](https://github.com/wso2/product-integrator-mi/issues/4828))
- Fixed union map parameter pointer mismatch in `XmlPropertyWriter` where the generated `init.xml` property used `config_map` (underscore) while the `<parameter>` declaration and uischema field both used `configMap` (camelCase). This caused `lookupTemplateParameter` to return null for `map<string>` union members, propagating as a null argument to `createObjectValue` and triggering a `NullPointerException` in the Ballerina connector's `init` method. ([#4811](https://github.com/wso2/product-integrator-mi/issues/4811))
- Fixed `typedesc`-backed union parameters generating spurious `<parameter>` declarations and union pointer properties in functions XML. Member parameters and pointer properties are now suppressed via `{{#unless typeDescriptor}}` guards; only the discriminator `DataType` property is emitted, matching the runtime's `typedesc` resolution path. ([#4812](https://github.com/wso2/product-integrator-mi/issues/4812))
- Fixed attribute group `enableCondition` not being propagated when all child fields of a grouped union member share the same condition. The condition is now promoted to the enclosing `attributeGroup` (merged with any parent condition), so MI Studio correctly hides or shows the group when its union branch is deselected. ([#4813](https://github.com/wso2/product-integrator-mi/issues/4813))
- Fixed filename mismatch between `component.xml` manifest references and the actual generated function XML files on disk. `Component` now exposes `getFileName()` and `getOriginalFileName()` helpers that apply the same `sanitizeFileName` logic used by the serializer, and the templates use these instead of the raw `name`/`originalName` values. ([#4833](https://github.com/wso2/product-integrator-mi/issues/4833))
- Fixed incomplete parameter list in generated connector artifacts caused by a field budget cutoff introduced as a workaround for recursive record types. Replaced the budget counter with path-based cycle detection using `RecordTypeSymbol.signature()`, which stops expansion only on true cycles while allowing all legitimate fields to be fully expanded. ([#4834](https://github.com/wso2/product-integrator-mi/issues/4834))
- Fixed optional config record fields being included in the reconstructed Ballerina record even when disabled via the `enable_*` checkbox. The generator now emits `<parameter name="enable_*"/>` declarations in `init.xml` for optional records/arrays/maps, and the runtime pre-pass in `DataTransformer` collects disabled path prefixes and skips all corresponding fields during reconstruction. ([#4835](https://github.com/wso2/product-integrator-mi/issues/4835))
- Fixed `No such method` runtime error for resource functions with `@` in the path segment (e.g., Discord's `GET /users/@me`). The Ballerina compiler returns `\@me` as the segment signature; `@` is now recognised as a valid single-escape character so the backslash is stripped before JVM encoding, producing the correct method name `$get$users$@me`. ([#4836](https://github.com/wso2/product-integrator-mi/issues/4836))

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
- Fixed an infinite recursion bug in `XmlPropertyWriter` caused by cyclic nested `UnionFunctionParam` types (e.g., `ClientCredentialsGrantConfig`). The generator now tracks visited types and stops recursive property unrolling cleanly.

## [1.0.0] - 2025-02-05

### Added
- Initial release of the `ballerina-migen-tools` framework.
