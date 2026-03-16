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

package io.ballerina.stdlib.mi;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.Runtime;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Factory for creating Ballerina Runtime instances.
 * Uses reflection to support both 2201.8.x and newer Ballerina versions.
 */
public class RuntimeFactory {

    private static final Log log = LogFactory.getLog(RuntimeFactory.class);

    private RuntimeFactory() {
    }

    /**
     * Creates and initializes a Runtime instance for the given module.
     * Compatible with Ballerina 2201.8.x which uses BalRuntime constructor.
     *
     * @param module the Ballerina module
     * @return initialized Runtime instance
     * @throws RuntimeException if runtime creation fails
     */
    public static Runtime createRuntime(Module module) {
        try {
            // Try 2201.8.x approach: new BalRuntime(module)
            Class<?> balRuntimeClass = Class.forName("io.ballerina.runtime.internal.BalRuntime");
            Constructor<?> constructor = balRuntimeClass.getDeclaredConstructor(Module.class);
            constructor.setAccessible(true);
            Runtime rt = (Runtime) constructor.newInstance(module);

            // Call init() if it exists
            try {
                Method initMethod = balRuntimeClass.getMethod("init");
                initMethod.invoke(rt);
            } catch (NoSuchMethodException e) {
                log.debug("init() method not found, skipping");
            }

            // Call start() if it exists
            try {
                Method startMethod = balRuntimeClass.getMethod("start");
                startMethod.invoke(rt);
            } catch (NoSuchMethodException e) {
                log.debug("start() method not found, skipping");
            }

            return rt;
        } catch (ClassNotFoundException e) {
            // Fallback: try newer API (Runtime.from)
            return createRuntimeNewApi(module);
        } catch (Exception e) {
            log.error("Failed to create Runtime using BalRuntime constructor", e);
            throw new RuntimeException("Failed to create Ballerina Runtime: " + e.getMessage(), e);
        }
    }

    private static Runtime createRuntimeNewApi(Module module) {
        try {
            Method fromMethod = Runtime.class.getMethod("from", Module.class);
            Runtime rt = (Runtime) fromMethod.invoke(null, module);

            Method initMethod = Runtime.class.getMethod("init");
            initMethod.invoke(rt);

            Method startMethod = Runtime.class.getMethod("start");
            startMethod.invoke(rt);

            return rt;
        } catch (Exception e) {
            log.error("Failed to create Runtime using Runtime.from()", e);
            throw new RuntimeException("Failed to create Ballerina Runtime: " + e.getMessage(), e);
        }
    }
}
