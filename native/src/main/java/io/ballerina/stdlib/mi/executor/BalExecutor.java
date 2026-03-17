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
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.async.StrandMetadata;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.ballerina.stdlib.mi.Constants.FUNCTION_NAME;

public class BalExecutor {

    protected Log log = LogFactory.getLog(BalExecutor.class);
    private final ParamHandler paramHandler = new ParamHandler();
    private static final long TIMEOUT_SECONDS = 300; // 5 minutes timeout

    /**
     * Synchronous callback implementation that blocks until completion.
     */
    private static class SyncCallback implements Callback {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Object> result = new AtomicReference<>();
        private final AtomicReference<BError> error = new AtomicReference<>();

        @Override
        public void notifySuccess(Object result) {
            this.result.set(result);
            latch.countDown();
        }

        @Override
        public void notifyFailure(BError error) {
            this.error.set(error);
            latch.countDown();
        }

        public Object waitForResult(long timeoutSeconds) throws BallerinaExecutionException {
            try {
                if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw new BallerinaExecutionException("Function invocation timed out after " + timeoutSeconds + " seconds",
                            new Exception("Timeout"));
                }
                BError err = error.get();
                if (err != null) {
                    throw new BallerinaExecutionException(err.getMessage(), err);
                }
                return result.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BallerinaExecutionException("Function invocation interrupted", e);
            }
        }
    }

    /**
     * Converts args to interleaved format required by Ballerina runtime.
     * Format: [arg0, true, arg1, true, ...] where true indicates argument is provided.
     */
    private Object[] toInterleavedArgs(Object[] args) {
        Object[] interleaved = new Object[args.length * 2];
        for (int i = 0; i < args.length; i++) {
            interleaved[i * 2] = args[i];
            interleaved[i * 2 + 1] = true;
        }
        return interleaved;
    }

    /**
     * Invokes a method on a BObject using the async API and waits for completion.
     */
    private Object invokeMethodSync(Runtime rt, BObject bObject, String methodName, Object[] args)
            throws BallerinaExecutionException {
        SyncCallback callback = new SyncCallback();
        StrandMetadata metadata = new StrandMetadata(
                BalConnectorConfig.getModule().getOrg(),
                BalConnectorConfig.getModule().getName(),
                BalConnectorConfig.getModule().getMajorVersion(),
                methodName
        );

        Object[] interleavedArgs = toInterleavedArgs(args);
        rt.invokeMethodAsync(bObject, methodName, null, metadata, callback, interleavedArgs);
        return callback.waitForResult(TIMEOUT_SECONDS);
    }

    /**
     * Invokes a module-level function using the async API and waits for completion.
     * Note: Module-level functions don't need interleaved args, only object methods do.
     */
    private Object invokeFunctionSync(Runtime rt, String functionName, Object[] args)
            throws BallerinaExecutionException {
        SyncCallback callback = new SyncCallback();
        rt.invokeMethodAsync(functionName, callback, args);
        return callback.waitForResult(TIMEOUT_SECONDS);
    }

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
                result = invokeFunctionSync(rt, functionName, args);
            } else if (callable instanceof BObject bObject) {
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
                    result = invokeMethodSync(rt, bObject, jvmMethodName, argsWithPathParams);
                } else {
                    String functionName = SynapseUtils.getPropertyAsString(context, Constants.FUNCTION_NAME);
                    result = invokeMethodSync(rt, bObject, functionName, args);
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
}
