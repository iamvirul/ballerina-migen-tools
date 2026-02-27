// Copyright (c) 2026 WSO2 LLC. (http://www.wso2.org).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

# Client for chat operations.
@display {label: "Chat", iconPath: "icon.png"}
public isolated client class ChatClient {
    final string baseUrl;

    # Initializes the chat client.
    #
    # + baseUrl - Base URL of the chat service
    # + return - An error if initialization failed
    public isolated function init(string baseUrl) returns error? {
        self.baseUrl = baseUrl;
    }

    # Send a message to a channel.
    #
    # + channelId - The channel to send the message to
    # + content - The message content
    # + return - The result or an error
    remote isolated function sendMessage(string channelId, string content) returns string|error {
        return "sent:" + channelId + ":" + content;
    }

    # Get messages from a channel.
    #
    # + channelId - The channel to retrieve messages from
    # + return - The result or an error
    remote isolated function getMessages(string channelId) returns string|error {
        return "messages:" + channelId;
    }
}
