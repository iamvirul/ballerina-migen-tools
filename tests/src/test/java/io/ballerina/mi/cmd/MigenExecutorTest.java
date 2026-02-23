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

import io.ballerina.mi.model.Connector;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.PackageName;
import io.ballerina.projects.PackageOrg;
import io.ballerina.projects.PackageVersion;
import io.ballerina.projects.SemanticVersion;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MigenExecutorTest {

    private List<Path> tempDirs = new ArrayList<>();

    @AfterMethod
    public void resetConnector() {
        Connector.reset();
    }

    @AfterMethod
    public void cleanupTempDirs() {
        for (Path tempDir : tempDirs) {
            try {
                if (Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    // ignore
                                }
                            });
                }
            } catch (IOException e) {
                // ignore
            }
        }
        tempDirs.clear();
    }

    @Test
    public void testGenerateMIArtifactsWhenNoComponents() throws Exception {
        createConnector("wso2", "empty", "1.0.0");

        Path tempDir = Files.createTempDirectory("migen-no-components");
        tempDirs.add(tempDir);
        boolean result = MigenExecutor.generateMIArtifacts(tempDir, tempDir, true, System.out);
        Assert.assertFalse(result);
    }

    @Test
    public void testGenerateMIArtifactsWhenGenerationAborted() throws Exception {
        Connector connector = createConnector("wso2", "aborted", "1.0.0");
        connector.setGenerationAborted(true, "analysis failed");

        Path tempDir = Files.createTempDirectory("migen-aborted");
        tempDirs.add(tempDir);
        boolean result = MigenExecutor.generateMIArtifacts(tempDir, tempDir, true, System.out);
        Assert.assertFalse(result);
    }

    private static Connector createConnector(String org, String module, String version) {
        PackageDescriptor descriptor = mock(PackageDescriptor.class);
        PackageOrg packageOrg = mock(PackageOrg.class);
        PackageName packageName = mock(PackageName.class);
        PackageVersion packageVersion = mock(PackageVersion.class);

        when(descriptor.org()).thenReturn(packageOrg);
        when(descriptor.name()).thenReturn(packageName);
        when(descriptor.version()).thenReturn(packageVersion);
        when(packageOrg.value()).thenReturn(org);
        when(packageName.value()).thenReturn(module);
        when(packageVersion.value()).thenReturn(SemanticVersion.from(version));

        return Connector.getConnector(descriptor);
    }
}
