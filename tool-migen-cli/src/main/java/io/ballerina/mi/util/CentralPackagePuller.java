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
import org.wso2.ballerinalang.util.RepoUtils;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CentralPackagePuller {

    private static final String CENTRAL_SEARCH_API_URL = "https://api.central.ballerina.io/2.0/registry/search-packages?q=org%%3A%s%%20package%%3A%s&offset=0&limit=1&ballerina_version=%s";
    private static final String CENTRAL_SEARCH_API_URL_NO_VERSION = "https://api.central.ballerina.io/2.0/registry/search-packages?q=org%%3A%s%%20package%%3A%s&offset=0&limit=1";
    private static final String CENTRAL_API_URL = "https://api.central.ballerina.io/2.0/registry/packages/%s/%s/%s";

    /** TCP connect timeout for all Central API calls (10 s). */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Read timeout for lightweight JSON API calls (30 s). */
    private static final int READ_TIMEOUT_MS = 30_000;
    /** Read timeout for the bala download connection (60 s — payload can be large). */
    private static final int READ_TIMEOUT_DOWNLOAD_MS = 60_000;

    /**
     * Pattern for valid semantic version strings.
     * Allows: major.minor.patch with optional pre-release and build metadata.
     * Examples: 1.0.0, 1.2.3-alpha, 1.0.0-beta.1+build.123
     */
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
            "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
            "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    /**
     * Pattern for valid package org and name identifiers.
     * Allows alphanumeric characters, underscores, hyphens, and dots (for submodules).
     * Must start with a letter or underscore.
     */
    private static final Pattern PACKAGE_IDENTIFIER_PATTERN = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_.-]*$"
    );

    public static Path pullAndExtractPackage(String org, String name, String version, Path targetDir) throws Exception {
        // Validate org and name to prevent path traversal
        validatePackageIdentifier(org, "org");
        validatePackageIdentifier(name, "name");

        String balaUrl;

        if (version == null || version.isEmpty()) {
            // Use search API which returns both version and balaURL in one request
            PackageInfo packageInfo = fetchLatestPackageInfo(org, name);
            version = packageInfo.version;
            balaUrl = packageInfo.balaUrl;
        } else {
            // Validate version to prevent path traversal
            validateVersion(version);
            balaUrl = fetchBalaUrl(org, name, version);
        }

        // 2. Download and Extract bala
        // Construct path safely with normalization and containment check
        Path extractedBalaBase = targetDir.resolve("extracted-bala").normalize();
        Path extractedBalaPath = extractedBalaBase.resolve(org + "-" + name + "-" + version).normalize();

        // Verify the resolved path stays within the expected base directory (defense in depth)
        if (!extractedBalaPath.startsWith(extractedBalaBase)) {
            throw new SecurityException(
                    "Path traversal detected: resolved path escapes the target directory. " +
                    "org=" + org + ", name=" + name + ", version=" + version);
        }

        if (Files.exists(extractedBalaPath)) {
            Utils.deleteDirectory(extractedBalaPath);
        }
        Files.createDirectories(extractedBalaPath);

        HttpURLConnection downloadConnection = (HttpURLConnection) new URL(balaUrl).openConnection();
        try {
            downloadConnection.setRequestMethod("GET");
            downloadConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            downloadConnection.setReadTimeout(READ_TIMEOUT_DOWNLOAD_MS);

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

    private record PackageInfo(String version, String balaUrl) {
    }

    private static PackageInfo fetchLatestPackageInfo(String org, String name) throws Exception {
        String ballerinaVersion = RepoUtils.getBallerinaVersion();
        String apiUrl;

        if (ballerinaVersion != null) {
            apiUrl = String.format(CENTRAL_SEARCH_API_URL, org, name,
                    URLEncoder.encode(ballerinaVersion, StandardCharsets.UTF_8.name()));
        } else {
            apiUrl = String.format(CENTRAL_SEARCH_API_URL_NO_VERSION, org, name);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode == 404) {
                throw new PackageNotCompatibleException(org + "/" + name,
                        "Package '" + org + "/" + name + "' not found in Ballerina Central.\n" +
                        "This package may not exist or may not be compatible with the current migen tool.\n" +
                        "Please verify the package name exists in Ballerina Central.");
            }
            if (responseCode != 200) {
                throw new RuntimeException("Failed to fetch package from Ballerina Central. HTTP code: "
                        + responseCode);
            }

            JsonObject responseJson;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                responseJson = JsonParser.parseReader(reader).getAsJsonObject();
            }

            JsonArray packages = responseJson.getAsJsonArray("packages");
            if (packages == null || packages.isEmpty()) {
                String versionMsg = ballerinaVersion != null
                        ? " compatible with Ballerina " + ballerinaVersion
                        : "";
                throw new PackageNotCompatibleException(org + "/" + name,
                        "No versions available for package '" + org + "/" + name + "'" + versionMsg + " in Ballerina Central.\n" +
                        "This package may not be compatible with the current migen tool.\n" +
                        (ballerinaVersion != null ? "Your Ballerina version: " + ballerinaVersion : ""));
            }

            // Find exact match for org and name
            for (int i = 0; i < packages.size(); i++) {
                JsonObject pkg = packages.get(i).getAsJsonObject();
                String pkgOrg = pkg.get("organization").getAsString();
                String pkgName = pkg.get("name").getAsString();
                if (org.equals(pkgOrg) && name.equals(pkgName)) {
                    return new PackageInfo(pkg.get("version").getAsString(), pkg.get("balaURL").getAsString());
                }
            }

            // Fallback to first result if no exact match
            JsonObject firstPkg = packages.get(0).getAsJsonObject();
            return new PackageInfo(firstPkg.get("version").getAsString(), firstPkg.get("balaURL").getAsString());
        } finally {
            connection.disconnect();
        }
    }

    private static String fetchBalaUrl(String org, String name, String version) throws Exception {
        String apiUrl = String.format(CENTRAL_API_URL, org, name, version);

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode == 404) {
                throw new PackageNotCompatibleException(org + "/" + name + ":" + version,
                        "Package '" + org + "/" + name + ":" + version + "' not found in Ballerina Central.\n" +
                        "This package may not exist or may not be compatible with the current migen tool.\n" +
                        "Please verify the package name and version, or try a different version.");
            }
            if (responseCode != 200) {
                throw new RuntimeException("Failed to fetch package details from Ballerina Central. HTTP code: "
                        + responseCode);
            }

            JsonObject responseJson;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                responseJson = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (!responseJson.has("balaURL")) {
                throw new RuntimeException("The package details did not contain a balaURL.");
            }

            return responseJson.get("balaURL").getAsString();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Validates that a version string is a valid semantic version.
     * Rejects versions containing path traversal characters or invalid formats.
     *
     * @param version the version string to validate
     * @throws IllegalArgumentException if the version is invalid
     */
    static void validateVersion(String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        // Check for path traversal characters
        if (version.contains("..") || version.contains("/") || version.contains("\\")) {
            throw new IllegalArgumentException(
                    "Invalid version: contains path traversal characters. version=" + version);
        }

        // Validate against semantic version pattern
        if (!SEMVER_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Invalid version format: must be a valid semantic version (e.g., 1.0.0). version=" + version);
        }
    }

    /**
     * Validates that a package identifier (org or name) is safe and well-formed.
     * Rejects identifiers containing path traversal characters or invalid formats.
     *
     * @param identifier the identifier string to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IllegalArgumentException if the identifier is invalid
     */
    static void validatePackageIdentifier(String identifier, String fieldName) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }

        // Check for path traversal characters
        if (identifier.contains("..") || identifier.contains("/") || identifier.contains("\\")) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + ": contains path traversal characters. " + fieldName + "=" + identifier);
        }

        // Validate against package identifier pattern
        if (!PACKAGE_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " format: must contain only alphanumeric characters, " +
                    "underscores, hyphens, and dots, starting with a letter or underscore. " +
                    fieldName + "=" + identifier);
        }
    }
}
