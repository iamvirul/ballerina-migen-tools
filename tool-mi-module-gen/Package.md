# Package Overview

The `tool.migen` package provides a Ballerina tool for generating WSO2 Micro Integrator modules from Ballerina code.

## Overview

This tool enables developers to:
- Write transformation and integration logic using Ballerina
- Generate deployable modules for WSO2 Micro Integrator
- Generate MI connectors from Ballerina connectors
- Leverage Ballerina's type safety and expressiveness for MI development

## Quick Start

### Prerequisites

- [Ballerina](https://ballerina.io/downloads/) 2201.13.1 or later
- Java 21 or later
- WSO2 Micro Integrator 4.4.0 or later

### Installation

Pull the tool using the Ballerina CLI:

```bash
$ bal tool pull tool.migen
```

### Usage

1. **Create a Ballerina project** with your transformation logic:

```ballerina
import wso2/mi;

@mi:Operation
public function transform(xml input) returns xml {
    // Your transformation logic here
}
```

2. **Generate the MI module**:

```bash
$ bal migen module -p <path_to_ballerina_project> -o <output_directory>
```

3. **Deploy** the generated module to WSO2 Micro Integrator.

### Generate MI Connector from Ballerina Connector

You can generate an MI connector from an existing Ballerina connector.

**Option 1: Directly from Ballerina Central (fetches automatically):**

```bash
$ bal migen connector --package ballerinax/<connector_name>
$ bal migen connector --package ballerinax/<connector_name>:<version>
```

**Option 2: From a locally cached bala:**

```bash
$ bal migen connector -p $HOME/.ballerina/repositories/central.ballerina.io/bala/ballerinax/<connector_name>/<version>/any -o <output_directory>
```

For example, to generate an MI connector from the locally cached `ballerinax/github` connector:

```bash
$ bal pull ballerinax/github
$ bal migen connector -p {user.home}/.ballerina/repositories/central.ballerina.io/bala/ballerinax/github/6.0.0/any -o generatedMiConnector
```

## Command Options

| Command | Option | Description |
|---------|--------|-------------|
| `module` | `-p, --path` | Path to the Ballerina project (defaults to CWD) |
| | `-o, --output` | Output directory for the generated artifacts (defaults to `target/mi/`) |
| `connector` | `--package` | Ballerina Central package (`org/name` or `org/name:version`) |
| | `-p, --path` | Path to the Ballerina connector bala (mutually exclusive with `--package`) |
| | `-o, --output` | Output directory for the generated artifacts (defaults to `target/mi/`) |
