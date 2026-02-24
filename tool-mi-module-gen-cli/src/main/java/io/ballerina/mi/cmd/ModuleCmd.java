/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(name = "module", description = "Generate MI module artifacts from @mi:Operation functions")
public class ModuleCmd implements BLauncherCmd {
    private static final String CMD_NAME = "module";
    private final PrintStream printStream;

    @CommandLine.Option(names = {"--help", "-h"}, usageHelp = true, hidden = true)
    private boolean helpFlag;

    @CommandLine.Option(names = {"--path"}, description = "Path to the Ballerina project (defaults to CWD)")
    private String sourcePath;

    @CommandLine.Option(names = {"--output", "-o"}, description = "Output directory (defaults to <path>/target/mi/)")
    private String targetPath;

    public ModuleCmd() {
        this.printStream = System.out;
    }

    @Override
    public void execute() {
        if (helpFlag) {
            String commandUsageInfo = BLauncherCmd.getCommandUsageInfo("migen-module",
                    ModuleCmd.class.getClassLoader());
            printStream.println(commandUsageInfo);
            return;
        }

        // Resolve sourcePath: default to CWD
        Path resolvedSource = sourcePath != null
                ? Paths.get(sourcePath).toAbsolutePath()
                : Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        // Validate that the resolved path is a Ballerina project (has Ballerina.toml)
        if (!Files.exists(resolvedSource.resolve("Ballerina.toml"))) {
            printStream.println("ERROR: No Ballerina.toml found at: " + resolvedSource +
                    ". Please run this command from inside a Ballerina project or specify --path.");
            return;
        }

        // Resolve targetPath: default to <resolvedSource>/target/mi/
        String resolvedTarget = targetPath != null
                ? targetPath
                : resolvedSource.resolve("target").resolve("mi").toString();

        MigenExecutor.executeGeneration(resolvedSource.toString(), resolvedTarget, printStream, false);
    }

    @Override
    public String getName() {
        return CMD_NAME;
    }

    @Override
    public void printLongDesc(StringBuilder stringBuilder) {
        stringBuilder.append("Generate WSO2 Micro Integrator module artifacts from @mi:Operation functions\n");
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
        stringBuilder.append("bal migen module [OPTIONS]\n");
    }

    @Override
    public void setParentCmdParser(CommandLine commandLine) {
        // No-op
    }
}
