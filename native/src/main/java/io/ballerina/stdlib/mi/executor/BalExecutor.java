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

package io.ballerina.stdlib.mi.executor;

import com.google.gson.JsonParser;
import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.values.*;
import io.ballerina.runtime.internal.values.MapValueImpl;
import io.ballerina.stdlib.mi.*;
import io.ballerina.stdlib.mi.utils.SynapseUtils;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.data.connector.ConnectorResponse;
import org.apache.synapse.data.connector.DefaultConnectorResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static io.ballerina.stdlib.mi.Constants.FUNCTION_NAME;

public class BalExecutor {

    protected Log log = LogFactory.getLog(BalExecutor.class);
    private final ParamHandler paramHandler = new ParamHandler();

    public boolean execute(Runtime rt, Object callable, MessageContext context) throws AxisFault, BallerinaExecutionException {
        String paramSize = SynapseUtils.getPropertyAsString(context, Constants.SIZE);
        int size = 0;
        if (paramSize != null && !paramSize.isEmpty()) {
            try {
                size = Integer.parseInt(paramSize);
            } catch (NumberFormatException e) {
                throw new SynapseException("Invalid value for property '" + Constants.SIZE + "': " + paramSize, e);
            }
        }
        Object[] args = new Object[size];
        paramHandler.setParameters(args, context);
        
        try {
            Object result;
            if (callable instanceof Module) {
                String functionName = SynapseUtils.getPropertyAsString(context, Constants.FUNCTION_NAME);
                result = rt.callFunction((Module) callable, functionName, null, args);
            } else if (callable instanceof BObject) {
                String functionType = SynapseUtils.getPropertyAsString(context, Constants.FUNCTION_TYPE);
                if (Constants.FUNCTION_TYPE_RESOURCE.equals(functionType)) {
                    String jvmMethodName = SynapseUtils.getPropertyAsString(context, Constants.JVM_METHOD_NAME);
                    if (jvmMethodName != null) {
                        jvmMethodName = jvmMethodName.replace("$$", "$^");
                    }
                    if (jvmMethodName == null || jvmMethodName.isEmpty()) {
                        jvmMethodName = SynapseUtils.getPropertyAsString(context, FUNCTION_NAME);
                    }
                    if (jvmMethodName == null || jvmMethodName.isEmpty()) {
                        throw new SynapseException("Neither jvmMethodName nor paramFunctionName is available for resource function invocation");
                    }
                    Object[] argsWithPathParams = paramHandler.prependPathParams(args, context);
                    result = invokeResourceFunction((BObject) callable, rt, jvmMethodName, argsWithPathParams);
                } else {
                    String functionName = SynapseUtils.getPropertyAsString(context, Constants.FUNCTION_NAME);
                    result = rt.callMethod((BObject) callable, functionName, null, args);
                }
            } else {
                throw new SynapseException("Unsupported callable type: " + callable.getClass().getName());
            }

            if (result instanceof BError bError) {
                throw new BallerinaExecutionException(bError.getMessage(), bError.fillInStackTrace());
            }
            Object processedResult = processResponse(result);
            
            ConnectorResponse connectorResponse = new DefaultConnectorResponse();
            String resultProperty = getResultProperty(context);
            boolean overwriteBody = isOverwriteBody(context);

            if (overwriteBody) {
                PayloadWriter.overwriteBody(context, processedResult);
            } else {
                connectorResponse.setPayload(processedResult);
            }
            context.setVariable(resultProperty, connectorResponse);
        } catch (BError bError) {
            log.error("BError caught during execution: " + bError.getMessage(), bError);
            throw new BallerinaExecutionException(bError.getMessage(), bError.fillInStackTrace());
        } catch (AxisFault | BallerinaExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during execution: " + e.getMessage(), e);
            throw new SynapseException("Error during Ballerina function execution", e);
        }
        return true;
    }

    private Object processResponse(Object result) {
        if (result == null) return null;
        if (result instanceof BXml) return BXmlConverter.toOMElement((BXml) result);
        if (result instanceof BDecimal) return ((BDecimal) result).value().toString();
        if (result instanceof BString) return ((BString) result).getValue();
        if (result instanceof BArray) return JsonParser.parseString(TypeConverter.arrayToJsonString((BArray) result));
        if (result instanceof BMap) return JsonParser.parseString(((MapValueImpl<?, ?>) result).getJSONString());
        if (result instanceof Long || result instanceof Integer || result instanceof Boolean || result instanceof Double || result instanceof Float) {
            return JsonParser.parseString(result.toString());
        }
        log.warn("Unhandled result type: " + result.getClass().getSimpleName() + ", returning as-is");
        return result;
    }

    private static String getResultProperty(MessageContext context) {
        return SynapseUtils.lookupTemplateParameter(context, Constants.RESPONSE_VARIABLE).toString();
    }

    private static boolean isOverwriteBody(MessageContext context) {
        return Boolean.parseBoolean((String) SynapseUtils.lookupTemplateParameter(context, Constants.OVERWRITE_BODY));
    }

    private Object invokeResourceFunction(BObject callable, Runtime rt, String jvmMethodName, Object[] args)
            throws BallerinaExecutionException {
        Object strand = null;
        Class<?> strandClass;
        try {
            Field schedulerField = rt.getClass().getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            Object scheduler = schedulerField.get(rt);

            strandClass = Class.forName("io.ballerina.runtime.internal.scheduling.Strand");
            Class<?> schedulerClass = Class.forName("io.ballerina.runtime.internal.scheduling.Scheduler");

            java.lang.reflect.Constructor<?> strandCtor = null;
            Object[] ctorArgs = null;

            try {
                strandCtor = strandClass.getDeclaredConstructor(schedulerClass);
                ctorArgs = new Object[]{scheduler};
            } catch (NoSuchMethodException e) {
                for (Constructor<?> c : strandClass.getDeclaredConstructors()) {
                    if (c.getParameterCount() > 0 && c.getParameterTypes()[0].equals(schedulerClass)) {
                        strandCtor = c;
                        Class<?>[] paramTypes = c.getParameterTypes();
                        ctorArgs = new Object[paramTypes.length];
                        ctorArgs[0] = scheduler;
                        for (int i = 1; i < paramTypes.length; i++) {
                            if (paramTypes[i] == boolean.class) ctorArgs[i] = false;
                            else if (paramTypes[i] == int.class) ctorArgs[i] = 0;
                            else if (paramTypes[i] == long.class) ctorArgs[i] = 0L;
                            else if (paramTypes[i] == double.class) ctorArgs[i] = 0.0;
                            else if (paramTypes[i] == float.class) ctorArgs[i] = 0.0f;
                            else if (paramTypes[i] == String.class) ctorArgs[i] = "mi-strand";
                            else ctorArgs[i] = null;
                        }
                        break;
                    }
                }
            }

            if (strandCtor == null) {
                throw new BallerinaExecutionException(
                        "Could not find Strand constructor accepting Scheduler",
                        new Exception("Strand constructor missing"));
            }
            strandCtor.setAccessible(true);
            strand = strandCtor.newInstance(ctorArgs);

            Method callMethod = callable.getClass().getMethod(
                    "call", strandClass, String.class, Object[].class);
            Object result = callMethod.invoke(callable, strand, jvmMethodName, args);

            if (result == null) {
                // Wait for async completion using the strand's future CompletableFuture
                Field futureField = strandClass.getDeclaredField("future");
                futureField.setAccessible(true);
                Object futureValue = futureField.get(strand);

                if (futureValue != null) {
                    // Use CompletableFuture.get() for proper blocking wait instead of polling
                    Field cfField = futureValue.getClass().getDeclaredField("completableFuture");
                    cfField.setAccessible(true);
                    Object completableFuture = cfField.get(futureValue);
                    if (completableFuture instanceof java.util.concurrent.CompletableFuture<?> cf) {
                        cf.get();
                    }

                    Field resultField = futureValue.getClass().getDeclaredField("result");
                    resultField.setAccessible(true);
                    result = resultField.get(futureValue);

                    Field panicField = futureValue.getClass().getDeclaredField("panic");
                    panicField.setAccessible(true);
                    Object panic = panicField.get(futureValue);
                    if (panic != null) {
                        if (panic instanceof BError) throw (BError) panic;
                        if (panic instanceof Throwable)
                            throw new BallerinaExecutionException(
                                    "Panic in Ballerina function: " + ((Throwable) panic).getMessage(),
                                    (Throwable) panic);
                        throw new BallerinaExecutionException(
                                "Panic in Ballerina function: " + panic,
                                new Exception(String.valueOf(panic)));
                    }
                }
            }
            return result;
        } catch (BError | BallerinaExecutionException bError) {
            throw bError;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof BError) throw (BError) e.getCause();
            throw new BallerinaExecutionException("Resource invocation failed: " + e.getCause().getMessage(),
                    e.getCause());
        } catch (Exception e) {
            log.error("Failed to invoke resource function: " + e.getMessage(), e);
            throw new BallerinaExecutionException("Resource invocation failed: " + e.getMessage(), e);
        } finally {
            // Clean up the strand to release HTTP connection pool resources
            if (strand != null) {
                try {
                    Method doneMethod = strand.getClass().getMethod("done");
                    doneMethod.invoke(strand);
                } catch (Exception e) {
                    log.warn("Failed to mark strand as done: " + e.getMessage());
                }
            }
        }
    }
}
