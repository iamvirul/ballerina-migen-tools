/*
 * Copyright (c) 2026, WSO2 LLC. (http://wso2.com).
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

import io.ballerina.stdlib.mi.BalConnectorConfig;
import io.ballerina.stdlib.mi.BalConnectorFunction;
import io.ballerina.stdlib.mi.ModuleInfo;
import org.apache.synapse.data.connector.DefaultConnectorResponse;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for multi-client connector invocation.
 * Validates that two separate client classes (ChatClient, UsersClient) in the same module
 * can be independently initialized and their remote functions invoked correctly.
 */
public class TestMultiClientConnector {

    private static final String CHAT_CONNECTION_NAME = "testChatConnection";
    private static final String USERS_CONNECTION_NAME = "testUsersConnection";
    private static final String CHAT_CONNECTION_TYPE = "MULTICLIENTPROJECT_CHATCLIENT";
    private static final String USERS_CONNECTION_TYPE = "MULTICLIENTPROJECT_USERSCLIENT";

    @BeforeClass
    public void setupRuntime() throws Exception {
        ModuleInfo moduleInfo = new ModuleInfo("testOrg", "multiClientProject", "1");
        BalConnectorConfig config = new BalConnectorConfig(moduleInfo);

        // Initialize ChatClient connection
        TestMessageContext chatInit = ConnectorContextBuilder.connectorContext()
                .connectionName(CHAT_CONNECTION_NAME)
                .isConnection(true)
                .objectTypeName("ChatClient")
                .addParameter("connectionType", "string", CHAT_CONNECTION_TYPE)
                .addParameter("baseUrl", "string", "http://chat.test.com")
                .build();

        chatInit.setProperty(CHAT_CONNECTION_TYPE + "_objectTypeName", "ChatClient");
        chatInit.setProperty(CHAT_CONNECTION_TYPE + "_paramSize", 1);
        chatInit.setProperty(CHAT_CONNECTION_TYPE + "_paramFunctionName", "init");
        chatInit.setProperty(CHAT_CONNECTION_TYPE + "_param0", "baseUrl");
        chatInit.setProperty(CHAT_CONNECTION_TYPE + "_paramType0", "string");

        config.connect(chatInit);

        // Initialize UsersClient connection
        TestMessageContext usersInit = ConnectorContextBuilder.connectorContext()
                .connectionName(USERS_CONNECTION_NAME)
                .isConnection(true)
                .objectTypeName("UsersClient")
                .addParameter("connectionType", "string", USERS_CONNECTION_TYPE)
                .addParameter("baseUrl", "string", "http://users.test.com")
                .build();

        usersInit.setProperty(USERS_CONNECTION_TYPE + "_objectTypeName", "UsersClient");
        usersInit.setProperty(USERS_CONNECTION_TYPE + "_paramSize", 1);
        usersInit.setProperty(USERS_CONNECTION_TYPE + "_paramFunctionName", "init");
        usersInit.setProperty(USERS_CONNECTION_TYPE + "_param0", "baseUrl");
        usersInit.setProperty(USERS_CONNECTION_TYPE + "_paramType0", "string");

        config.connect(usersInit);
    }

    @Test(description = "Test ChatClient sendMessage remote function")
    public void testChatClientSendMessage() throws Exception {
        BalConnectorFunction connector = new BalConnectorFunction();

        TestMessageContext context = ConnectorContextBuilder.connectorContext()
                .connectionName(CHAT_CONNECTION_NAME)
                .methodName("sendMessage")
                .returnType("string")
                .addParameter("channelId", "string", "general")
                .addParameter("content", "string", "hello")
                .build();

        connector.connect(context);

        String result = ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString();
        Assert.assertEquals(result, "sent:general:hello", "sendMessage should concatenate channelId and content");
    }

    @Test(description = "Test ChatClient getMessages remote function")
    public void testChatClientGetMessages() throws Exception {
        BalConnectorFunction connector = new BalConnectorFunction();

        TestMessageContext context = ConnectorContextBuilder.connectorContext()
                .connectionName(CHAT_CONNECTION_NAME)
                .methodName("getMessages")
                .returnType("string")
                .addParameter("channelId", "string", "general")
                .build();

        connector.connect(context);

        String result = ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString();
        Assert.assertEquals(result, "messages:general", "getMessages should return messages prefix with channelId");
    }

    @Test(description = "Test UsersClient getUser remote function")
    public void testUsersClientGetUser() throws Exception {
        BalConnectorFunction connector = new BalConnectorFunction();

        TestMessageContext context = ConnectorContextBuilder.connectorContext()
                .connectionName(USERS_CONNECTION_NAME)
                .methodName("getUser")
                .returnType("string")
                .addParameter("userId", "string", "123")
                .build();

        connector.connect(context);

        String result = ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString();
        Assert.assertEquals(result, "user:123", "getUser should return user prefix with userId");
    }

    @Test(description = "Test UsersClient createUser remote function")
    public void testUsersClientCreateUser() throws Exception {
        BalConnectorFunction connector = new BalConnectorFunction();

        TestMessageContext context = ConnectorContextBuilder.connectorContext()
                .connectionName(USERS_CONNECTION_NAME)
                .methodName("createUser")
                .returnType("string")
                .addParameter("name", "string", "Alice")
                .addParameter("email", "string", "alice@test.com")
                .build();

        connector.connect(context);

        String result = ((DefaultConnectorResponse) context.getVariable("result")).getPayload().toString();
        Assert.assertEquals(result, "created:Alice:alice@test.com",
                "createUser should return created prefix with name and email");
    }
}
