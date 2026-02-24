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

package io.ballerina.mi.util;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertThrows;

/**
 * Tests for CentralPackagePuller validation methods.
 * Ensures path traversal attacks are properly rejected.
 */
public class CentralPackagePullerTest {

    // ========== Version Validation Tests ==========

    @DataProvider(name = "validVersions")
    public Object[][] validVersions() {
        return new Object[][] {
            { "1.0.0" },
            { "0.0.1" },
            { "10.20.30" },
            { "1.2.3-alpha" },
            { "1.2.3-alpha.1" },
            { "1.2.3-beta.2" },
            { "1.0.0-rc.1" },
            { "1.0.0+build.123" },
            { "1.0.0-alpha+build.456" },
            { "1.2.3-alpha.1.beta.2+build.789" },
        };
    }

    @Test(dataProvider = "validVersions")
    public void testValidVersions(String version) {
        // Should not throw
        CentralPackagePuller.validateVersion(version);
    }

    @DataProvider(name = "invalidVersionsPathTraversal")
    public Object[][] invalidVersionsPathTraversal() {
        return new Object[][] {
            { "1.0.0/../../../etc/passwd" },
            { "../1.0.0" },
            { "1.0.0/.." },
            { "1.0.0/subdir" },
            { "1.0.0\\subdir" },
            { "..\\..\\windows\\system32" },
            { "1.0.0/../" },
        };
    }

    @Test(dataProvider = "invalidVersionsPathTraversal")
    public void testInvalidVersionsPathTraversal(String version) {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validateVersion(version);
        });
    }

    @DataProvider(name = "invalidVersionsFormat")
    public Object[][] invalidVersionsFormat() {
        return new Object[][] {
            { "1.0" },           // Missing patch
            { "1" },             // Missing minor and patch
            { "v1.0.0" },        // Leading 'v'
            { "1.0.0.0" },       // Too many parts
            { "a.b.c" },         // Non-numeric
            { "" },              // Empty (caught by null check)
            { "  " },            // Whitespace
            { "1.0.0 " },        // Trailing space
            { " 1.0.0" },        // Leading space
        };
    }

    @Test(dataProvider = "invalidVersionsFormat")
    public void testInvalidVersionsFormat(String version) {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validateVersion(version);
        });
    }

    @Test
    public void testNullVersion() {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validateVersion(null);
        });
    }

    // ========== Package Identifier Validation Tests ==========

    @DataProvider(name = "validIdentifiers")
    public Object[][] validIdentifiers() {
        return new Object[][] {
            { "ballerinax" },
            { "wso2" },
            { "my_org" },
            { "my-org" },
            { "trigger.github" },
            { "kafka" },
            { "azure.ai.search" },
            { "_private" },
            { "Org123" },
        };
    }

    @Test(dataProvider = "validIdentifiers")
    public void testValidPackageIdentifiers(String identifier) {
        // Should not throw
        CentralPackagePuller.validatePackageIdentifier(identifier, "org");
        CentralPackagePuller.validatePackageIdentifier(identifier, "name");
    }

    @DataProvider(name = "invalidIdentifiersPathTraversal")
    public Object[][] invalidIdentifiersPathTraversal() {
        return new Object[][] {
            { "../etc" },
            { "org/../passwd" },
            { "..\\windows" },
            { "name/subdir" },
            { "name\\subdir" },
            { "valid..name" },
        };
    }

    @Test(dataProvider = "invalidIdentifiersPathTraversal")
    public void testInvalidIdentifiersPathTraversal(String identifier) {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validatePackageIdentifier(identifier, "org");
        });
    }

    @DataProvider(name = "invalidIdentifiersFormat")
    public Object[][] invalidIdentifiersFormat() {
        return new Object[][] {
            { "123org" },        // Starts with number
            { "-invalid" },     // Starts with hyphen
            { ".invalid" },     // Starts with dot
            { "has space" },    // Contains space
            { "has@symbol" },   // Contains special char
            { "" },             // Empty
        };
    }

    @Test(dataProvider = "invalidIdentifiersFormat")
    public void testInvalidIdentifiersFormat(String identifier) {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validatePackageIdentifier(identifier, "name");
        });
    }

    @Test
    public void testNullIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            CentralPackagePuller.validatePackageIdentifier(null, "org");
        });
    }
}
