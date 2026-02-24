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
            description = "Path to the local Ballerina connector bala or extracted bala directory " +
                    "(defaults to CWD). Mutually exclusive with --package.")
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
            String commandUsageInfo = BLauncherCmd.getCommandUsageInfo("migen-connector",
                    ConnectorCmd.class.getClassLoader());
            printStream.println(commandUsageInfo);
            return;
        }

        // If both --package and --path are provided, fail fast as they are mutually exclusive.
        if (packageId != null && sourcePath != null) {
            printStream.println("ERROR: Options --package and --path are mutually exclusive. Please provide only one.");
            System.exit(1);
        }

        Path resolvedSource;

        if (packageId != null) {
            String[] parts = packageId.split(":");
            String basePackage;
            String version = null;

            if (parts.length == 1) {
                basePackage = parts[0];
            } else if (parts.length == 2) {
                basePackage = parts[0];
                version = parts[1];
            } else {
                printStream.println("ERROR: Invalid package format. Expected org/name or org/name:version");
                return;
            }

            String[] orgName = basePackage.split("/");
            if (orgName.length != 2) {
                printStream.println("ERROR: Invalid package format. Expected org/name or org/name:version");
                return;
            }
            String org = orgName[0];
            String name = orgName[1];

            // Resolve targetPath: default to CWD/target/mi/
            String target = targetPath != null
                    ? targetPath
                    : Paths.get(System.getProperty("user.dir")).resolve("target").resolve("mi").toString();

            try {
                resolvedSource = io.ballerina.mi.util.CentralPackagePuller.pullAndExtractPackage(
                        org, name, version, Paths.get(target));
            } catch (Exception e) {
                printStream.println("ERROR: Failed to download package " + packageId + " - " + e.getMessage());
                return;
            }
        } else {
            // --path mode (or CWD default)
            resolvedSource = sourcePath != null
                    ? Paths.get(sourcePath).toAbsolutePath()
                    : Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        }

        String resolvedTarget;
        if (packageId != null) {
            resolvedTarget = targetPath != null
                    ? targetPath
                    : Paths.get(System.getProperty("user.dir")).resolve("target").resolve("mi").toString();
        } else {
            resolvedTarget = targetPath != null
                    ? targetPath
                    : resolvedSource.resolve("target").resolve("mi").toString();
        }

        MigenExecutor.executeGeneration(resolvedSource.toString(), resolvedTarget, printStream, true);
    }

    @Override
    public String getName() {
        return CMD_NAME;
    }

    @Override
    public void printLongDesc(StringBuilder stringBuilder) {
        stringBuilder.append("Generate WSO2 Micro Integrator connector from Ballerina connector\n");
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
