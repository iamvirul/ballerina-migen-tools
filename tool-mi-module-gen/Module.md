# Module Overview

The `tool.migen` Ballerina tool allows generation of modules and connectors for WSO2 Micro Integrator from Ballerina code. This tool enables developers to write transformation logic in Ballerina and generate deployable modules and connectors for WSO2 MI.

## Features

- Generate WSO2 Micro Integrator modules from Ballerina code
- Generate MI connectors from Ballerina connectors
- Support for `@mi:Operation` annotation to define MI components
- Package-aware CWD detection (no need to specify `--path` when inside a Ballerina project)
- Seamless integration with Ballerina build tools

## Usage

### Pull the Tool

```bash
$ bal tool pull migen
```

### Generate MI module from Ballerina source

Create a new Ballerina project or use an existing one and write your transformation logic. Import the module `wso2/mi` in your Ballerina program.

```ballerina
import wso2/mi;

@mi:Operation
public function GPA(xml rawMarks, xml credits) returns xml {
   // Your logic to calculate the GPA
}
```

### Generate the Module

Run from inside the Ballerina project (package-aware):

```bash
$ bal migen module
```

Or specify the path and output directory explicitly:

```bash
$ bal migen module --path <path_to_ballerina_project> --output <output_directory>
```

### Generate MI Connector from Ballerina Connector

You can generate an MI connector from an existing Ballerina connector.

**Option 1: From a local connector project/bala (run from inside the connector directory):**

```bash
$ bal migen connector
```

**Option 2: From a central package (fetches automatically):**

```bash
$ bal migen connector --package ballerinax/github
```

**Option 3: Specify an explicit bala path:**

```bash
$ bal migen connector --path {user.home}/.ballerina/repositories/central.ballerina.io/bala/ballerinax/<connector_name>/<version>/any --output <output_directory>
```

For example, to generate an MI connector from the locally cached `ballerinax/github` connector:

```bash
$ bal pull ballerinax/github
$ bal migen connector --path {user.home}/.ballerina/repositories/central.ballerina.io/bala/ballerinax/github/6.0.0/any --output generatedMiConnector
```

## Command Reference

```
bal migen <subcommand> [OPTIONS]

SUBCOMMANDS
  module      Generate MI module from @mi:Operation annotated functions
  connector   Generate MI connector from a Ballerina connector

COMMON OPTIONS
  -p, --path <path>      Path to local project/bala (defaults to CWD)
  -o, --output <path>    Output directory (defaults to <path>/target/mi/ or ./target/mi/)
  -h, --help             Show help

CONNECTOR-ONLY OPTIONS
  --package <org/name:version>   Ballerina Central package. Mutually exclusive with --path.
```

## Version Compatibility

|   Tool Version    | Ballerina Version | Java Version | WSO2 MI Version |
|:-----------------:|:-----------------:|:------------:|:---------------:|
|       0.4.0       |     2201.12.x     |      21      |    >= 4.4.0     |
|     \>= 0.4.1     |     2201.13.x     |      21      |    >= 4.4.0     |

## Related Links

- [WSO2 Micro Integrator Documentation](https://mi.docs.wso2.com/)
- [Ballerina Documentation](https://ballerina.io/learn/)
