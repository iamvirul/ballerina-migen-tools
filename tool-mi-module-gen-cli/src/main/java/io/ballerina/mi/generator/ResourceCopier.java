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

package io.ballerina.mi.generator;

import io.ballerina.mi.model.Connector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Copies connector resources (icons, JARs, mediator classes) from the classpath/filesystem
 * to the generated connector output directory.
 * <p>
 * Extracted from ConnectorSerializer to encapsulate all resource copying logic.
 *
 * @since 0.6.0
 */
public final class ResourceCopier {

    private ResourceCopier() {
        // Utility class — no instantiation
    }

    /**
     * Copy resources from the JAR file or directory to the destination directory.
     *
     * @param classLoader   Class loader to load resources
     * @param destination   Destination directory
     * @param jarPath       Path to the JAR file or classes directory
     * @param org           Organization name
     * @param module        Module name
     * @param moduleVersion Module version
     * @throws IOException If an I/O error occurs
     */
    public static void copyResources(ClassLoader classLoader, Path destination, URI jarPath, String org,
                                     String module, String moduleVersion)
            throws IOException {
        Path sourcePath = Paths.get(jarPath);
        if (Files.isDirectory(sourcePath)) {
            // Running from classes directory (e.g. tests or dev)
            // In Gradle, resources might be in a separate directory (build/resources/main)
            // while CodeSource points to build/classes/java/main.
            Path resourcesPath = sourcePath;
            if (sourcePath.endsWith(Paths.get("classes", "java", "main"))) {
                Path potentialResources = sourcePath.getParent().getParent().getParent().resolve("resources").resolve("main");
                if (Files.exists(potentialResources)) {
                    resourcesPath = potentialResources;
                }
            }
            
            copyResourcesFromDirectory(classLoader, resourcesPath, destination, Connector.LIB_PATH, ".jar");
            copyIcons(classLoader, resourcesPath, destination);
        } else {
            // Running from JAR file (production)
            try (JarFile jar = new JarFile(sourcePath.toFile())) {
                copyResourcesFromJar(classLoader, jar, destination, Connector.LIB_PATH, ".jar");
                copyIcons(classLoader, jar, destination);
            }
        }
    }

    /**
     * Copies connector icons from a JAR file source.
     */
    static void copyIcons(ClassLoader classLoader, JarFile jar, Path destination) throws IOException {
        Connector connector = Connector.getConnector();
        if (connector.getIconPath() == null) {
            copyResourcesFromJar(classLoader, jar, destination, Connector.ICON_FOLDER, ".png");
            return;
        }

        Path iconPath = resolveIconPath(connector.getIconPath(), destination);
        if (!Files.exists(iconPath)) {
            copyResourcesFromJar(classLoader, jar, destination, Connector.ICON_FOLDER, ".png");
            return;
        }

        copyIconsFromDisk(iconPath, destination, () ->
                copyResourcesFromJar(classLoader, jar, destination, Connector.ICON_FOLDER, ".png"));
    }

    /**
     * Copies connector icons from a directory source.
     */
    static void copyIcons(ClassLoader classLoader, Path sourceRoot, Path destination) throws IOException {
        Connector connector = Connector.getConnector();
        if (connector.getIconPath() == null) {
            copyResourcesFromDirectory(classLoader, sourceRoot, destination, Connector.ICON_FOLDER, ".png");
            return;
        }

        Path iconPath = resolveIconPath(connector.getIconPath(), destination);
        if (!Files.exists(iconPath)) {
            copyResourcesFromDirectory(classLoader, sourceRoot, destination, Connector.ICON_FOLDER, ".png");
            return;
        }

        copyIconsFromDisk(iconPath, destination, () ->
                copyResourcesFromDirectory(classLoader, sourceRoot, destination, Connector.ICON_FOLDER, ".png"));
    }

    private static Path resolveIconPath(String iconPathStr, Path destination) {
        Path iconPath = Paths.get(iconPathStr);
        if (!iconPath.isAbsolute()) {
            return destination.getParent().resolve(iconPathStr).normalize();
        }
        return iconPath;
    }

    private static void copyIconsFromDisk(Path iconPath, Path destination, FallbackAction fallback) throws IOException {
        if (Files.isRegularFile(iconPath)) {
            copySingleIconAsBoth(iconPath, destination);
            return;
        }

        try (Stream<Path> stream = Files.walk(iconPath)) {
            List<Path> paths = stream
                    .filter(f -> f.toString().endsWith(".png"))
                    .toList();

            if (paths.size() == 1) {
                copySingleIconAsBoth(paths.get(0), destination);
            } else if (paths.size() == 2) {
                copyIconsBySize(destination, paths);
            } else {
                fallback.run();
            }
        }
    }

    @FunctionalInterface
    interface FallbackAction {
        void run() throws IOException;
    }

    private static void copySingleIconAsBoth(Path singleIconPath, Path destination) throws IOException {
        Path smallOutputPath = destination.resolve(Connector.ICON_FOLDER).resolve(Connector.SMALL_ICON_NAME);
        Path largeOutputPath = destination.resolve(Connector.ICON_FOLDER).resolve(Connector.LARGE_ICON_NAME);
        Files.createDirectories(smallOutputPath.getParent());

        copyIconToDestination(singleIconPath, smallOutputPath);
        copyIconToDestination(singleIconPath, largeOutputPath);
    }

    private static void copyIconsBySize(Path destination, List<Path> paths) throws IOException {
        Path smallOutputPath = destination.resolve(Connector.ICON_FOLDER).resolve(Connector.SMALL_ICON_NAME);
        Path largeOutputPath = destination.resolve(Connector.ICON_FOLDER).resolve(Connector.LARGE_ICON_NAME);
        Path smallIconPath;
        Path largeIconPath;
        Files.createDirectories(smallOutputPath.getParent());

        if (Files.size(paths.get(0)) > Files.size(paths.get(1))) {
            smallIconPath = paths.get(1);
            largeIconPath = paths.get(0);
        } else {
            smallIconPath = paths.get(0);
            largeIconPath = paths.get(1);
        }

        copyIconToDestination(smallIconPath, smallOutputPath);
        copyIconToDestination(largeIconPath, largeOutputPath);
    }

    private static void copyIconToDestination(Path iconPath, Path destination) throws IOException {
        try (InputStream inputStream = Files.newInputStream(iconPath)) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyResourcesFromJar(ClassLoader classLoader, JarFile jar, Path destination,
                                             String resourceFolder, String fileExtension) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith(resourceFolder + "/") && entry.getName().endsWith(fileExtension)) {
                // When copying from JAR resources, we use the entry name as the resource path.
                // However, classLoader.getResourceAsStream expects path starting from classpath root.
                // JarEntry names usually don't have leading slash, which is good.
                copyResource(classLoader, entry.getName(), destination);
            }
        }
    }

    private static void copyResourcesFromDirectory(ClassLoader classLoader, Path sourceRoot, Path destination,
                                                   String resourceFolder, String fileExtension) throws IOException {
        Path resourcePath = sourceRoot.resolve(resourceFolder);
        if (!Files.exists(resourcePath)) {
            // It might be a JAR resource path, so try to find it via classloader if not on disk?
            // No, sourceRoot is the classes directory (e.g. build/classes/java/main).
            // Resources should be in build/resources/main which might not be sourceRoot.
            // But typical Gradle setup: classes and resources are separate dirs.
            // If jarPath points to classes dir, it won't have resources.
            // We should rely on ClassLoader resources for fallback?
            // If sourceRoot is a directory, it implies (in this context) it's the classpath root or one of them.
            // But actually, we just need to list files that match the pattern.
            // If specific files are not found, we skip?
            return;
        }

        List<Path> matchingPaths;
        try (Stream<Path> stream = Files.walk(resourcePath)) {
            matchingPaths = stream.filter(p -> p.toString().endsWith(fileExtension))
                                  .toList();
        }
        for (Path p : matchingPaths) {
            // Build the classpath-relative name by relativizing against sourceRoot
            // (e.g. sourceRoot=".../resources/main", p=".../resources/main/icons/icon.png"
            // → relative="icons/icon.png"), which matches the resource name expected
            // by ClassLoader.getResourceAsStream.
            Path relative = sourceRoot.relativize(p);
            // Normalise to forward slashes for ClassLoader resource lookup on all platforms.
            String resourceName = relative.toString().replace('\\', '/');
            copyResource(classLoader, resourceName, destination);
        }
    }

    private static void copyResource(ClassLoader classLoader, String resourcePath, Path destination) throws IOException {
        Path outputPath = destination.resolve(resourcePath);
        Files.createDirectories(outputPath.getParent());
        try (InputStream inputStream = getFileFromResourceAsStream(classLoader, resourcePath)) {
            Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IllegalArgumentException e) {
            // Propagate resource resolution failures: at this stage, all discovered resources
            // are expected to be loadable via the provided ClassLoader, so a missing resource
            // indicates a configuration or packaging error that should fail fast.
            throw e;
        }
    }

    static InputStream getFileFromResourceAsStream(ClassLoader classLoader, String fileName) {
        if (fileName == null) {
            throw new NullPointerException("Resource file name cannot be null");
        }
        InputStream inputStream = classLoader.getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found " + fileName);
        } else {
            return inputStream;
        }
    }
}
