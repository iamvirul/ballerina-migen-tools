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

import org.testng.Assert;
import org.testng.annotations.Test;
import picocli.CommandLine;

public class MiCmdTest {

    @Test
    public void testCliMetadataMethods() {
        MiCmd cmd = new MiCmd();
        Assert.assertEquals(cmd.getName(), "migen");

        StringBuilder help = new StringBuilder();
        cmd.printLongDesc(help);
        Assert.assertTrue(help.length() > 0);
        Assert.assertTrue(help.toString().contains("Generate WSO2 Micro Integrator artifacts"));

        StringBuilder usage = new StringBuilder();
        cmd.printUsage(usage);
        Assert.assertTrue(usage.toString().contains("bal migen <subcommand>"));
        
        cmd.setParentCmdParser(new CommandLine(cmd));
    }
}
