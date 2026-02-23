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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CentralPackagePuller {

    private static final String CENTRAL_API_VERSION_URL = "https://api.central.ballerina.io/2.0/registry/packages/%s/%s";
    private static final String CENTRAL_API_URL = "https://api.central.ballerina.io/2.0/registry/packages/%s/%s/%s";

    public static Path pullAndExtractPackage(String org, String name, String version, Path targetDir) throws Exception {
        if (version == null || version.isEmpty()) {
            version = fetchLatestVersion(org, name);
        }

        String apiUrl = String.format(CENTRAL_API_URL, org, name, version);

        // 1. Fetch package details
        String balaUrl;
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("Failed to fetch package details from Ballerina Central. HTTP code: "
                        + connection.getResponseCode());
            }

            JsonObject responseJson;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                responseJson = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (!responseJson.has("balaURL")) {
                throw new RuntimeException("The package details did not contain a balaURL.");
            }

            balaUrl = responseJson.get("balaURL").getAsString();
        } finally {
            connection.disconnect();
        }

        // 2. Download and Extract bala
        Path extractedBalaPath = targetDir.resolve("extracted-bala").resolve(org + "-" + name + "-" + version);
        if (Files.exists(extractedBalaPath)) {
            Utils.deleteDirectory(extractedBalaPath);
        }
        Files.createDirectories(extractedBalaPath);

        HttpURLConnection downloadConnection = (HttpURLConnection) new URL(balaUrl).openConnection();
        try {
            downloadConnection.setRequestMethod("GET");

            if (downloadConnection.getResponseCode() != 200) {
                throw new RuntimeException("Failed to download bala file. HTTP code: "
                        + downloadConnection.getResponseCode());
            }

            try (ZipInputStream zis = new ZipInputStream(downloadConnection.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path extractedFilePath = extractedBalaPath.resolve(entry.getName()).normalize();
                    if (!extractedFilePath.startsWith(extractedBalaPath)) {
                        throw new RuntimeException("Bad zip entry: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(extractedFilePath);
                    } else {
                        if (extractedFilePath.getParent() != null) {
                            Files.createDirectories(extractedFilePath.getParent());
                        }
                        Files.copy(zis, extractedFilePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
        } finally {
            downloadConnection.disconnect();
        }

        return extractedBalaPath;
    }

    private static String fetchLatestVersion(String org, String name) throws Exception {
        String apiUrl = String.format(CENTRAL_API_VERSION_URL, org, name);
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("Failed to fetch package versions from Ballerina Central. HTTP code: "
                        + connection.getResponseCode());
            }

            JsonArray versionsArray;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                versionsArray = JsonParser.parseReader(reader).getAsJsonArray();
            }
            if (versionsArray.isEmpty()) {
                throw new RuntimeException("No versions found for package " + org + "/" + name);
            }

            return versionsArray.get(0).getAsString();
        } finally {
            connection.disconnect();
        }
    }
}
