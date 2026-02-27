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

# Client for user management operations.
@display {label: "Users", iconPath: "icon.png"}
public isolated client class UsersClient {
    final string baseUrl;

    # Initializes the users client.
    #
    # + baseUrl - Base URL of the user service
    # + return - An error if initialization failed
    public isolated function init(string baseUrl) returns error? {
        self.baseUrl = baseUrl;
    }

    # Get a user by ID.
    #
    # + userId - The user ID
    # + return - The result or an error
    remote isolated function getUser(string userId) returns string|error {
        return "user:" + userId;
    }

    # Create a new user.
    #
    # + name - The user's name
    # + email - The user's email
    # + return - The result or an error
    remote isolated function createUser(string name, string email) returns string|error {
        return "created:" + name + ":" + email;
    }
}
