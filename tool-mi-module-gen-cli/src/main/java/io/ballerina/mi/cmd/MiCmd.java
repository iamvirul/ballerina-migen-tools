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

@CommandLine.Command(name = "migen", description = "Generate WSO2 MI artifacts",

        subcommands = {ModuleCmd.class, ConnectorCmd.class})

public class MiCmd implements BLauncherCmd {
    private static final String CMD_NAME = "migen";
    private final PrintStream printStream;

    @CommandLine.Option(names = {"--help", "-h"}, usageHelp = true, hidden = true)
    private boolean helpFlag;

    public MiCmd() {
        this.printStream = System.out;
    }

    @Override
    public void execute() {
        String commandUsageInfo = BLauncherCmd.getCommandUsageInfo(CMD_NAME, MiCmd.class.getClassLoader());
        printStream.println(commandUsageInfo);
    }

    @Override
    public String getName() {
        return CMD_NAME;
    }

    @Override
    public void printLongDesc(StringBuilder stringBuilder) {
        stringBuilder.append("Generate WSO2 Micro Integrator artifacts from Ballerina\n");
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
        stringBuilder.append("bal migen <subcommand> [OPTIONS]\n");
    }

    @Override
    public void setParentCmdParser(CommandLine commandLine) {
        // No-op
    }

}
