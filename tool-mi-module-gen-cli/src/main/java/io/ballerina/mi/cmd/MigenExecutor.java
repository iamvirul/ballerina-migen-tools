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

package io.ballerina.mi.cmd;

import io.ballerina.mi.analyzer.Analyzer;
import io.ballerina.mi.analyzer.BalConnectorAnalyzer;
import io.ballerina.mi.analyzer.BalModuleAnalyzer;
import io.ballerina.mi.generator.ConnectorSerializer;
import io.ballerina.mi.model.Connector;
import io.ballerina.mi.util.Constants;
import io.ballerina.mi.util.ResourceLifecycleManager;
import io.ballerina.mi.util.Utils;
import io.ballerina.mi.validator.ConnectorValidator;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectLoadResult;
import io.ballerina.projects.directory.BalaProject;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.directory.ProjectLoader;
import io.ballerina.projects.EmitResult;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class MigenExecutor {
    private static final String CONNECTOR_NAME_SEPARATOR = "-";

    public static void executeGeneration(String sourcePath, String targetPath, PrintStream printStream, boolean isConnector) {
        if (sourcePath == null || targetPath == null) {
            printStream.println("ERROR: Missing required arguments. Please provide source and target paths.");
            return;
        }

        Path miArtifactsPath = Path.of(targetPath);
        Path projectPath = Path.of(sourcePath).normalize();
        Path[] executablePathRef = new Path[1];

        // Compile, analyze, and emit (returns project type for validation)
        Boolean isBuildProject;
        try {
            isBuildProject = compileAnalyzeAndEmit(projectPath, miArtifactsPath, printStream, executablePathRef, isConnector);
        } catch (IOException e) {
            printStream.println("ERROR: Failed to create output directories: " + e.getMessage());
            return;
        }

        if (isBuildProject == null) {
            return; // Compilation/Analysis failed
        }

        if (isConnector && isBuildProject) {
            printStream.println("ERROR: Expected a Ballerina connector (Bala) project for 'connector' command, but found a source project.");
            return;
        }
        if (!isConnector && !isBuildProject) {
            printStream.println("ERROR: Expected a Ballerina source project for 'module' command, but found a Bala project.");
            return;
        }
        // Deterministic lifecycle management
        ResourceLifecycleManager lifecycle = new ResourceLifecycleManager();

        try {
            // Generate MI artifacts
            boolean artifactsGenerated = generateMIArtifacts(executablePathRef[0], miArtifactsPath, isBuildProject, printStream);
            if (!artifactsGenerated) {
                return;
            }

            printStream.println("Validating artifacts...");

            boolean isValid = ConnectorValidator.validateConnector(miArtifactsPath);
            if (!isValid) {
                printStream.println("ERROR: MI " + (isBuildProject ? "module" : "connector") + 
                        " generation failed due to validation errors.");
                try {
                    Utils.deleteDirectory(miArtifactsPath);
                } catch (IOException e) {
                    printStream.println("ERROR: Failed to delete invalid MI " + (isBuildProject ?
                            "module" : "connector") + " artifacts at: " + miArtifactsPath);
                }
                return;
            }
            printStream.println("MI " + (isBuildProject ? "module" : "connector") + 
                    " generation completed successfully.");
        } finally {
            // Deterministic cleanup
            lifecycle.cleanup();
            // Remove the intermediate BalConnectors folder from the output directory
            Path balConnectorsDir = miArtifactsPath.resolve("BalConnectors");
            if (Files.exists(balConnectorsDir)) {
                try {
                    Utils.deleteDirectory(balConnectorsDir);
                } catch (IOException e) {
                    printStream.println("WARN: Failed to remove intermediate BalConnectors directory: " + e.getMessage());
                }
            }
        }
    }

    static Boolean compileAnalyzeAndEmit(Path projectPath, Path miArtifactsPath, PrintStream printStream, Path[] executablePathRef, boolean isConnector) throws IOException {
        BuildOptions buildOptions = BuildOptions.builder().setOffline(false).build();
        ProjectLoadResult projectLoadResult;
        try {
            projectLoadResult = ProjectLoader.load(projectPath.toAbsolutePath(), buildOptions);
        } catch (io.ballerina.projects.ProjectException e) {
            printStream.println("ERROR: Valid Ballerina package or bala file not found at " + projectPath.toAbsolutePath() + ". " + e.getMessage());
            return null;
        }
        Project project = projectLoadResult.project();
        Package compilePkg = project.currentPackage();
        boolean isBuildProject = project instanceof BuildProject;

        if (!(project instanceof BuildProject || project instanceof BalaProject)) {
            printStream.println("ERROR: Invalid project path provided");
            return null;
        }

        Analyzer balAnalyzer;
        if (project instanceof BalaProject) {
            balAnalyzer = new BalConnectorAnalyzer();
            Path miConnectorCache = miArtifactsPath.resolve("BalConnectors");
            executablePathRef[0] = miConnectorCache.resolve(compilePkg.descriptor().org().value() +
                    CONNECTOR_NAME_SEPARATOR + compilePkg.descriptor().name().value() +
                    CONNECTOR_NAME_SEPARATOR + compilePkg.descriptor().version().toString() + ".jar" );

            Files.createDirectories(miConnectorCache);
            System.setProperty(Constants.CONNECTOR_TARGET_PATH, executablePathRef[0].toString());
        } else {
            balAnalyzer = new BalModuleAnalyzer();
        }

        PackageCompilation packageCompilation = compilePkg.getCompilation();
        for (Diagnostic diagnostic : packageCompilation.diagnosticResult().diagnostics()) {
            if (!(project instanceof BalaProject) || diagnostic.diagnosticInfo().severity() == DiagnosticSeverity.ERROR) {
                printStream.println(diagnostic.toString());
            }
        }
        if (packageCompilation.diagnosticResult().hasErrors()) {
            printStream.println("ERROR: Ballerina project compilation contains errors");
            return null;
        }

        balAnalyzer.analyze(compilePkg);

        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_21);

        if (isBuildProject) {
            Path bin = miArtifactsPath.resolve("bin");
            createBinFolder(bin);
            executablePathRef[0] = bin.resolve(compilePkg.descriptor().name().value() + ".jar");
        }

        EmitResult emitResult = jBallerinaBackend.emit(JBallerinaBackend.OutputType.EXEC, executablePathRef[0]);
        if (!jBallerinaBackend.conflictedJars().isEmpty()) {
            printStream.println("WARNING: Detected conflicting jar files:");
            for (JBallerinaBackend.JarConflict conflict : jBallerinaBackend.conflictedJars()) {
                printStream.println(conflict.getWarning(project.buildOptions().listConflictedClasses()));
            }
        }

        if (!emitResult.diagnostics().diagnostics().isEmpty()) {
            emitResult.diagnostics().diagnostics().forEach(d -> printStream.println("\n" + d.toString()));
        }

        if (!emitResult.successful()) {
            printStream.println("ERROR: Ballerina project emit failed; artifact generation aborted.");
            return null;
        }

        Connector.getConnector().clearTypeSymbols();

        return isBuildProject;
    }

    public static boolean generateMIArtifacts(Path sourcePath, Path targetPath, boolean isBuildProject, PrintStream printStream) {
        printStream.println("Generating MI " + (isBuildProject ? "module" : "connector") + " artifacts...");

        Connector connector = Connector.getConnector();

        printStream.println("Found " + connector.getComponents().size() + " component(s)");

        if (connector.getComponents().isEmpty()) {
            if (connector.isGenerationAborted()) {
                printStream.println("WARN: Skipping MI " + (isBuildProject ? "module" : "connector")
                        + " artifacts generation. Reason: " + connector.getAbortionReason());
            } else {
                printStream.println("WARN: No components found. MI " + (isBuildProject ? "module" : "connector") + " artifacts will not be generated.");
            }
            return false;
        }

        ConnectorSerializer connectorSerializer = new ConnectorSerializer(sourcePath, targetPath);
        connectorSerializer.serialize(connector);
        return true;
    }

    static void createBinFolder(Path bin) throws IOException {
        if (Files.exists(bin)) {
            Utils.deleteDirectory(bin);
        }
        Files.createDirectories(bin);
    }
}
