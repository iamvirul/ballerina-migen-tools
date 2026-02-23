/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.mi.cmd;

import io.ballerina.cli.BLauncherCmd;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(name = "connector", description = "Generate MI connector artifacts from Ballerina connectors")
public class ConnectorCmd implements BLauncherCmd {
    private static final String CMD_NAME = "connector";
    private final PrintStream printStream;

    @CommandLine.Option(names = {"--help", "-h"}, usageHelp = true, hidden = true)
    private boolean helpFlag;

    @CommandLine.Option(names = {"--path"},
            description = "Path to the local Ballerina connector project or bala (defaults to CWD). " +
                    "Mutually exclusive with --package.")
    private String sourcePath;

    @CommandLine.Option(names = {"--output", "-o"},
            description = "Output directory (defaults to <path>/target/mi/ or ./target/mi/ for --package)")
    private String targetPath;

    @CommandLine.Option(names = {"--package"},
            description = "Ballerina Central package (e.g., org/name:version). Mutually exclusive with --path.")
    private String packageId;

    public ConnectorCmd() {
        this.printStream = System.out;
    }

    @Override
    public void execute() {
        if (helpFlag) {
            CommandLine.usage(this, printStream);
            return;
        }

        // If both --package and --path are provided, give priority to --path
        if (packageId != null && sourcePath != null) {
            printStream.println("WARNING: Both --package and --path provided. Giving priority to --path.");
            packageId = null;
        }

        Path resolvedSource;

        if (packageId != null) {
            // --package mode: not yet fully supported, placeholder
            printStream.println("Warning: --package is not yet fully supported. " +
                    "Please use 'bal pull <org/package>' and provide the extracted bala path with --path.");
            return;
        } else {
            // --path mode (or CWD default)
            resolvedSource = sourcePath != null
                    ? Paths.get(sourcePath).toAbsolutePath()
                    : Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        }

        // Resolve targetPath: default to <resolvedSource>/target/mi/
        String resolvedTarget = targetPath != null
                ? targetPath
                : resolvedSource.resolve("target").resolve("mi").toString();

        MigenExecutor.executeGeneration(resolvedSource.toString(), resolvedTarget, printStream, true);
    }

    @Override
    public String getName() {
        return CMD_NAME;
    }

    @Override
    public void printLongDesc(StringBuilder stringBuilder) {
        stringBuilder.append("Generate WSO2 Micro Integrator artifacts from Ballerina connectors.\n");
        stringBuilder.append("This command scans the Ballerina connector project or extracted BALA\n");
        stringBuilder.append("and generates the corresponding MI connector artifacts.\n");
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
        stringBuilder.append("bal migen connector [OPTIONS]\n");
    }

    @Override
    public void setParentCmdParser(CommandLine commandLine) {
        // No-op
    }
}
