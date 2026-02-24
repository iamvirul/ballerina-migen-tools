# Ballerina tool for WSO2 Micro Integrator module generator

[![codecov](https://codecov.io/gh/wso2/ballerina-migen-tools/branch/main/graph/badge.svg)](https://codecov.io/gh/wso2/ballerina-migen-tools)
[![GitHub Release](https://img.shields.io/github/v/release/wso2/ballerina-migen-tools)](https://github.com/wso2/ballerina-migen-tools/releases)
![Last Commit](https://img.shields.io/github/last-commit/wso2/ballerina-migen-tools)
[![Publish Release Workflow](https://github.com/wso2/ballerina-migen-tools/actions/workflows/publish-release.yml/badge.svg)](https://github.com/wso2/ballerina-migen-tools/actions/workflows/publish-release.yml)

## Overview

The `migen` tool allows generation of modules for WSO2 Micro Integrator from Ballerina code.

**Version compatibility**:

**Tool version**|**`wso2/mi` Connector version**| **Ballerina Version** |**Java version**|**WSO2 MI version**|
:-----:|:-----:|:---------------------:|:-----:|:-----:
0.2| 0.2|       2201.10.3       | 17| 4.2.0, 4.3.0
\>= 0.3 | 0.3|     2201.12.7         | 21| >= 4.4.0
\>= 0.4.1 | 0.4.1+|     2201.13.x         | 21| >= 4.4.0


## Steps to create a module for WSO2 MI from Ballerina

### Pull `migen` tool

First, you need to pull the `migen` tool which is used to create the module.

```bash
$ bal tool pull migen
```

### Write Ballerina transformation

### Import the `wso2/mi` module

Create a new Ballerina project or use an existing one and write your transformation logic. Import the module `wso2/mi` in your Ballerina program.

```ballerina
import wso2/mi;
```

Write Ballerina Transformation. For example,

```ballerina
import wso2/mi;

@mi:Operation
public function GPA(xml rawMarks, xml credits) returns xml {
   // Your logic to calculate the GPA
}
```

Ballerina function that contains `@mi:Operation` annotation maps with a component in Ballerina module.

### Generate the module

Finally, use the `bal migen` command to generate the Module for the WSO2 Micro Integrator.

```bash
$ bal migen module --path <path_to_ballerina_project> -o <output_directory>
```

Above command generates the module zip in the specified output directory.

## Generate MI Connector from Ballerina Connector

**Option 1: Directly from Ballerina Central (fetches automatically):**

```bash
$ bal migen connector --package ballerinax/<connector_name>
$ bal migen connector --package ballerinax/<connector_name>:<version>
```

**Option 2: From a locally cached bala:**

```bash
$ bal migen connector --path $HOME/.ballerina/repositories/central.ballerina.io/bala/ballerinax/<connector_name>/<version>/any -o <output_directory>
```

## Local build

1. Clone the repository [ballerina-migen-tools](https://github.com/wso2/ballerina-migen-tools.git)

2. Build the tool and publish locally:

   > [!IMPORTANT]
   > You need to set the `packageUser` and `packagePAT` environment variables to your GitHub username and Personal Access Token (PAT) respectively. These are required to publish the tool to your local repository.

   ```bash
   $ export packageUser=<your_github_username>
   $ export packagePAT=<your_github_pat>
   ```

   Then run the build command:

   ```bash
   $ ./gradlew clean :tool-migen:localPublish
   ```

### Run tests

   ```bash
   $ ./gradlew test
   ```

### Regenerate expected test artifacts

Use the dedicated Gradle task instead of enabling TestNG methods:

```bash
$ ./gradlew :mi-tests:generateExpectedArtifacts -PartifactTarget=project1
```

- `artifactTarget` can be one of `project1`, `project2`, `project3`, `project4`, `project6`, or `central`.
- For `central`, optionally override Central packages with `-PcentralPackage=org/name:version,org2/name2:version2`
  (defaults to `ballerinax/milvus:1.1.0,ballerinax/azure.ai.search:1.0.0`). Multiple entries generate artifacts for each package, placing them in per-connector folders derived from `org-name`.

Example for Central pulls:

```bash
$ ./gradlew :mi-tests:generateExpectedArtifacts -PartifactTarget=central \
    -PcentralPackage=org/name:1.0.0,org2/name2:2.0.0
```

## Contribute to Ballerina

As an open-source project, Ballerina welcomes contributions from the community.

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All the contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).
