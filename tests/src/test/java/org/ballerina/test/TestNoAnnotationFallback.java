/*
 * Copyright (c) 2026, WSO2 LLC. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerina.test;

import io.ballerina.mi.cmd.ModuleCmd;
import io.ballerina.mi.model.Connector;
import io.ballerina.stdlib.mi.Mediator;
import io.ballerina.stdlib.mi.ModuleInfo;
import org.apache.synapse.data.connector.DefaultConnectorResponse;
import org.apache.synapse.mediators.template.TemplateContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

/**
 * Verifies that when a Ballerina module has no {@code @mi:Operation} annotations the tool
 * falls back to generating MI artifacts for all public functions, and that those functions
 * are executable at runtime through the Mediator.
 */
public class TestNoAnnotationFallback {

    private static final String PROJECT_NAME = "noAnnotationProject";
    private static final Path PROJECT_PATH =
            Paths.get("src", "test", "resources", "ballerina", PROJECT_NAME);

    @BeforeMethod
    public void resetConnector() {
        Connector.reset();
    }

    @AfterMethod
    public void cleanupTargetDir() throws Exception {
        Path targetPath = PROJECT_PATH.resolve("target");
        if (Files.exists(targetPath)) {
            try (var walk = Files.walk(targetPath)) {
                walk.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.delete(p); } catch (Exception ignored) {}
                        });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Artifact generation tests
    // -------------------------------------------------------------------------

    @Test(description = "Fallback: connector.xml is generated for a module with no @mi:Operation annotations")
    public void testConnectorXmlGenerated() throws Exception {
        runModuleCmd();

        Path connectorXml = PROJECT_PATH.resolve("target").resolve("generated").resolve("connector.xml");
        Assert.assertTrue(Files.exists(connectorXml),
                "connector.xml should be generated in fallback mode");
    }

    @Test(description = "Fallback: artifacts for the public 'add' function are generated")
    public void testAddFunctionArtifactGenerated() throws Exception {
        runModuleCmd();

        Path uiSchema = PROJECT_PATH.resolve("target").resolve("generated")
                .resolve("uischema").resolve("add.json");
        Assert.assertTrue(Files.exists(uiSchema),
                "uischema/add.json should exist — 'add' is a public function");
    }

    @Test(description = "Fallback: artifacts for the public 'greet' function are generated")
    public void testGreetFunctionArtifactGenerated() throws Exception {
        runModuleCmd();

        Path uiSchema = PROJECT_PATH.resolve("target").resolve("generated")
                .resolve("uischema").resolve("greet.json");
        Assert.assertTrue(Files.exists(uiSchema),
                "uischema/greet.json should exist — 'greet' is a public function");
    }

    @Test(description = "Fallback: no artifact is generated for the private 'privateHelper' function")
    public void testPrivateFunctionNotGenerated() throws Exception {
        runModuleCmd();

        Path uiSchema = PROJECT_PATH.resolve("target").resolve("generated")
                .resolve("uischema").resolve("privateHelper.json");
        Assert.assertFalse(Files.exists(uiSchema),
                "uischema/privateHelper.json must not exist — privateHelper is a private function");
    }

    @Test(description = "Fallback: exactly two operations are generated (add and greet)")
    public void testExactlyTwoOperationsGenerated() throws Exception {
        runModuleCmd();

        Path uiSchemaDir = PROJECT_PATH.resolve("target").resolve("generated").resolve("uischema");
        Assert.assertTrue(Files.exists(uiSchemaDir), "uischema directory must exist");

        long jsonCount = Files.list(uiSchemaDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .count();
        Assert.assertEquals(jsonCount, 2,
                "Expected exactly 2 uischema JSON files (add, greet) but found: " + jsonCount);
    }

    // -------------------------------------------------------------------------
    // Runtime execution tests (require noAnnotationProject.jar on classpath)
    // -------------------------------------------------------------------------

    @Test(description = "Runtime: 'add' function returns the sum of two integers")
    public void testAddFunctionExecution() {
        ModuleInfo moduleInfo = new ModuleInfo("testOrg", PROJECT_NAME, "1");
        Mediator mediator = new Mediator(moduleInfo);

        TestMessageContext context = new TestMessageContext();
        context.setProperty("paramFunctionName", "add");
        context.setProperty("paramSize", 2);
        context.setProperty("param0", "a");
        context.setProperty("paramType0", "int");
        context.setProperty("param1", "b");
        context.setProperty("paramType1", "int");
        context.setProperty("returnType", "int");

        Stack<TemplateContext> stack = new Stack<>();
        TemplateContext templateContext = new TemplateContext("testTemplateFunc", new ArrayList<>());
        stack.push(templateContext);

        HashMap<Object, Object> map = new HashMap<>();
        map.put("a", "10");
        map.put("b", "32");
        map.put("responseVariable", "result");
        templateContext.setMappedValues(map);

        context.setProperty("_SYNAPSE_FUNCTION_STACK", stack);
        mediator.mediate(context);

        Assert.assertEquals(
                ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString(),
                "42");
    }

    @Test(description = "Runtime: 'greet' function returns the expected greeting string")
    public void testGreetFunctionExecution() {
        ModuleInfo moduleInfo = new ModuleInfo("testOrg", PROJECT_NAME, "1");
        Mediator mediator = new Mediator(moduleInfo);

        TestMessageContext context = new TestMessageContext();
        context.setProperty("paramFunctionName", "greet");
        context.setProperty("paramSize", 1);
        context.setProperty("param0", "name");
        context.setProperty("paramType0", "string");
        context.setProperty("returnType", "string");

        Stack<TemplateContext> stack = new Stack<>();
        TemplateContext templateContext = new TemplateContext("testTemplateFunc", new ArrayList<>());
        stack.push(templateContext);

        HashMap<Object, Object> map = new HashMap<>();
        map.put("name", "WSO2");
        map.put("responseVariable", "result");
        templateContext.setMappedValues(map);

        context.setProperty("_SYNAPSE_FUNCTION_STACK", stack);
        mediator.mediate(context);

        Assert.assertEquals(
                ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString(),
                "Hello, WSO2!");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void runModuleCmd() throws Exception {
        ModuleCmd moduleCmd = new ModuleCmd();

        Field sourcePathField = ModuleCmd.class.getDeclaredField("sourcePath");
        sourcePathField.setAccessible(true);
        sourcePathField.set(moduleCmd, PROJECT_PATH.toAbsolutePath().toString());

        Field targetPathField = ModuleCmd.class.getDeclaredField("targetPath");
        targetPathField.setAccessible(true);
        targetPathField.set(moduleCmd, PROJECT_PATH.resolve("target").toAbsolutePath().toString());

        moduleCmd.execute();
    }

}
