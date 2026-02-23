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
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MigenSubcommandTest {

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testModuleCmdExecution() throws Exception {
        try (MockedStatic<MigenExecutor> mockedMigenExecutor = mockStatic(MigenExecutor.class)) {
            ModuleCmd moduleCmd = new ModuleCmd();
            String testTargetPath = "/tmp/generatedModuleArtifacts";

            // Create a real temp dir with a Ballerina.toml so ModuleCmd doesn't exit early
            Path tempSourceDir = Files.createTempDirectory("testModuleProject");
            Files.createFile(tempSourceDir.resolve("Ballerina.toml"));
            String testSourcePath = tempSourceDir.toString();

            setField(moduleCmd, "sourcePath", testSourcePath);
            setField(moduleCmd, "targetPath", testTargetPath);

            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(outContent));

            try {
                moduleCmd.execute();
            } finally {
                System.setOut(originalOut);
            }

            // Use any(PrintStream.class) — System.out identity may differ between call-time and verify-time
            mockedMigenExecutor.verify(() ->
                MigenExecutor.executeGeneration(eq(testSourcePath), eq(testTargetPath), any(PrintStream.class), eq(false)),
                times(1)
            );

            // Cleanup
            Files.deleteIfExists(tempSourceDir.resolve("Ballerina.toml"));
            Files.deleteIfExists(tempSourceDir);
        }
    }

    @Test
    public void testConnectorCmdExecution() throws Exception {
        try (MockedStatic<MigenExecutor> mockedMigenExecutor = mockStatic(MigenExecutor.class)) {
            ConnectorCmd connectorCmd = new ConnectorCmd();
            String testSourcePath = "/tmp/testConnectorBala";
            String testTargetPath = "/tmp/generatedConnectorArtifacts";

            setField(connectorCmd, "sourcePath", testSourcePath);
            setField(connectorCmd, "targetPath", testTargetPath);

            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(outContent));

            try {
                connectorCmd.execute();
            } finally {
                System.setOut(originalOut);
            }

            // Use any(PrintStream.class) — System.out identity may differ between call-time and verify-time
            mockedMigenExecutor.verify(() ->
                MigenExecutor.executeGeneration(eq(testSourcePath), eq(testTargetPath), any(PrintStream.class), eq(true)),
                times(1)
            );
        }
    }

    @Test
    public void testConnectorCmdWithPackageIdWarning() throws Exception {
        try (MockedStatic<MigenExecutor> mockedMigenExecutor = mockStatic(MigenExecutor.class)) {
            ConnectorCmd connectorCmd = new ConnectorCmd();
            String testPackageId = "wso2/some_connector:1.0.0";

            // Only set packageId — setting both packageId AND sourcePath triggers
            // the mutual-exclusion error, not the --package-not-supported warning
            setField(connectorCmd, "packageId", testPackageId);

            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            PrintStream capturedOut = new PrintStream(outContent);
            System.setOut(capturedOut);
            setField(connectorCmd, "printStream", capturedOut);

            try {
                connectorCmd.execute();
            } finally {
                System.setOut(originalOut);
            }

            // When --package is provided, ConnectorCmd prints a warning and returns early
            // without calling executeGeneration (feature not yet supported)
            String consoleOutput = outContent.toString();
            Assert.assertTrue(consoleOutput.contains("--package is not yet fully supported"),
                "Expected warning message not found. Actual: " + consoleOutput);

            // executeGeneration should NOT be called — command returns early
            mockedMigenExecutor.verify(() ->
                MigenExecutor.executeGeneration(any(), any(), any(PrintStream.class), eq(true)),
                never()
            );
        }
    }
}
