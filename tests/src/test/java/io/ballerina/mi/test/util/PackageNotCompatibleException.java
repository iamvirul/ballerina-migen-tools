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

package io.ballerina.mi.test.util;

/**
 * Exception thrown when a Ballerina package is not available or compatible
 * with the current Ballerina distribution version.
 */
public class PackageNotCompatibleException extends Exception {

    private final String packageName;
    private final String ballerinaVersion;

    public PackageNotCompatibleException(String packageName, String ballerinaVersion, String message) {
        super(message);
        this.packageName = packageName;
        this.ballerinaVersion = ballerinaVersion;
    }

    public PackageNotCompatibleException(String packageName, String ballerinaVersion, String message, Throwable cause) {
        super(message, cause);
        this.packageName = packageName;
        this.ballerinaVersion = ballerinaVersion;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getBallerinaVersion() {
        return ballerinaVersion;
    }

    @Override
    public String getMessage() {
        return String.format("Package '%s' is not compatible with Ballerina %s. %s",
                packageName, ballerinaVersion, super.getMessage());
    }
}
