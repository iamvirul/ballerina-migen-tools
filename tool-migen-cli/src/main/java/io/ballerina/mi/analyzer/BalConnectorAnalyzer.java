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

package io.ballerina.mi.analyzer;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.impl.symbols.BallerinaClassSymbol;
import io.ballerina.compiler.api.symbols.*;
import io.ballerina.compiler.api.symbols.resourcepath.PathSegmentList;
import io.ballerina.compiler.api.symbols.resourcepath.util.PathSegment;
import io.ballerina.compiler.syntax.tree.*;
import io.ballerina.mi.model.Component;
import io.ballerina.mi.model.Connection;
import io.ballerina.mi.model.Connector;
import io.ballerina.mi.model.FunctionType;
import io.ballerina.mi.model.GenerationReport;
import io.ballerina.mi.model.param.EnumFunctionParam;
import io.ballerina.mi.model.param.FunctionParam;
import io.ballerina.mi.model.param.Param;
import io.ballerina.mi.model.param.RecordFunctionParam;
import io.ballerina.mi.model.param.ResourcePathSegment;
import io.ballerina.mi.util.Constants;
import io.ballerina.mi.util.Utils;
import io.ballerina.projects.*;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import org.ballerinalang.diagramutil.connector.models.connector.types.PathParamType;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BalConnectorAnalyzer implements Analyzer {
    private static final String METHOD_NAME = "MethodName";
    private static final String PATH_PARAM_SIZE = "PathParamSize";
    private static final String QUERY_PARAM_SIZE = "QueryParamSize";

    private final PrintStream printStream = System.out;

    @Override
    public void analyze(Package compilePackage) {

        PackageDescriptor descriptor = compilePackage.descriptor();
        Connector connector = Connector.getConnector(descriptor);

        connector.setGenerationReport(new GenerationReport(descriptor.name().value(), descriptor.org().value(),
                descriptor.version().value().toString()));

        // Extract icon from the bala package docs folder
        extractConnectorIcon(compilePackage, connector);

        for (Module module : compilePackage.modules()) {
            analyzeModule(compilePackage, module);
        }
    }

    /**
     * Extracts the connector icon from the bala package docs folder.
     * The icon is typically located at {sourceRoot}/docs/icon.png
     *
     * @param compilePackage The compiled package
     * @param connector The connector to set the icon path on
     */
    private void extractConnectorIcon(Package compilePackage, Connector connector) {
        try {
            Path sourceRoot = compilePackage.project().sourceRoot();
            Path docsIconPath = sourceRoot.resolve("docs").resolve("icon.png");

            if (Files.exists(docsIconPath)) {
                connector.setIconPath(docsIconPath.toString());
                printStream.println("Found connector icon at: " + docsIconPath);
            } else {
                printStream.println("No connector icon found in docs folder. Using default icon.");
            }
        } catch (Exception e) {
            printStream.println("Warning: Could not extract connector icon: " + e.getMessage());
        }
    }

    private void analyzeModule(Package compilePackage, Module module) {
        // Skip sub-modules - only process root-level module clients
        // Sub-modules have non-empty moduleNamePart (e.g., "OAS" in googleapis.gmail.OAS)
        // Root modules have null moduleNamePart
        if (module.moduleName().moduleNamePart() != null && !module.moduleName().moduleNamePart().isEmpty()) {
            printStream.println("Skipping sub-module: " + module.moduleName());
            return;
        }

        SemanticModel semanticModel = compilePackage.getCompilation().getSemanticModel(module.moduleId());
        List<Symbol> moduleSymbols = semanticModel.moduleSymbols();
        List<Symbol> classSymbols = moduleSymbols.stream().filter((s) -> s instanceof BallerinaClassSymbol).toList();

        // Extract class-method param defaults AND record-field defaults in a single syntax tree pass
        Map<String, Map<String, Map<String, String>>> classMethodDefaultValues = new HashMap<>();
        Map<String, Map<String, String>> recordFieldDefaults = new HashMap<>();
        extractAllDefaults(module, semanticModel, classMethodDefaultValues, recordFieldDefaults);

        for (Symbol classSymbol : classSymbols) {
            String className = classSymbol.getName().orElse("");
            Map<String, Map<String, String>> methodDefaultValues = classMethodDefaultValues.getOrDefault(className, Map.of());
            analyzeClass(compilePackage, module, (ClassSymbol) classSymbol, methodDefaultValues, recordFieldDefaults);
        }
    }

    /**
     * Single-pass extraction over the module's syntax trees.
     * Populates both:
     *  - classMethodDefaults: className → functionName → paramName → rawDefaultValue
     *  - recordFieldDefaults: recordTypeName → fieldName → resolvedDefaultValue
     *
     * Record field defaults are resolved immediately via the semantic model so that
     * qualified constant references (e.g. {@code http:HTTP_2_0}) are stored as their
     * actual string values (e.g. {@code "2.0"}) rather than as raw source text.
     */
    private void extractAllDefaults(Module module,
                                    SemanticModel semanticModel,
                                    Map<String, Map<String, Map<String, String>>> classMethodDefaults,
                                    Map<String, Map<String, String>> recordFieldDefaults) {
        for (DocumentId docId : module.documentIds()) {
            Document document = module.document(docId);
            SyntaxTree syntaxTree = document.syntaxTree();
            ModulePartNode modulePartNode = syntaxTree.rootNode();

            for (ModuleMemberDeclarationNode member : modulePartNode.members()) {
                if (member instanceof ClassDefinitionNode classNode) {
                    String className = classNode.className().text();
                    Map<String, Map<String, String>> functionDefaults = new HashMap<>();

                    for (Node classMember : classNode.members()) {
                        if (classMember instanceof FunctionDefinitionNode funcNode) {
                            String functionName = funcNode.functionName().text();
                            if (functionName.equals(Constants.INIT_FUNCTION_NAME)) {
                                functionName = Constants.INIT_FUNCTION_NAME;
                            }
                            Map<String, String> paramDefaults = new HashMap<>();
                            extractFunctionDefaultValues(funcNode, paramDefaults);
                            if (!paramDefaults.isEmpty()) {
                                functionDefaults.put(functionName, paramDefaults);
                            }
                        }
                    }

                    if (!functionDefaults.isEmpty()) {
                        classMethodDefaults.put(className, functionDefaults);
                    }
                } else if (member instanceof TypeDefinitionNode typeDefNode) {
                    Node typeDescNode = typeDefNode.typeDescriptor();
                    if (!(typeDescNode instanceof RecordTypeDescriptorNode recordTypeDesc)) {
                        continue;
                    }
                    String typeName = typeDefNode.typeName().text();
                    Map<String, String> fieldDefaults = new HashMap<>();
                    for (Node fieldNode : recordTypeDesc.fields()) {
                        if (fieldNode instanceof RecordFieldWithDefaultValueNode fieldWithDefault) {
                            String fieldName = fieldWithDefault.fieldName().text();
                            ExpressionNode exprNode = fieldWithDefault.expression();
                            String resolvedDefault = resolveExpressionToString(semanticModel, exprNode);
                            fieldDefaults.put(fieldName, resolvedDefault);
                        }
                    }
                    if (!fieldDefaults.isEmpty()) {
                        recordFieldDefaults.put(typeName, fieldDefaults);
                    }
                }
            }
        }
    }

    /**
     * Resolves a default-value expression to a clean string suitable for use as a combo default.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Try {@code semanticModel.symbol(node)} — handles qualified references like
     *       {@code http:HTTP_2_0} which live in external modules.</li>
     *   <li>Fall back to the raw source text, then strip enclosing quotes if present.</li>
     * </ol>
     */
    private String resolveExpressionToString(SemanticModel semanticModel, ExpressionNode exprNode) {
        try {
            Optional<Symbol> symbolOpt = semanticModel.symbol(exprNode);
            if (symbolOpt.isPresent() && symbolOpt.get() instanceof ConstantSymbol constSym) {
                Optional<String> resolvedOpt = constSym.resolvedValue();
                if (resolvedOpt.isPresent()) {
                    String val = resolvedOpt.get().trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        return val.substring(1, val.length() - 1);
                    }
                    return val;
                }
                Object constVal = constSym.constValue();
                if (constVal != null) {
                    return constVal.toString();
                }
            }
        } catch (Exception e) {
            // Semantic model resolution failed — fall through to raw source
        }
        // Raw source fallback: strip surrounding quotes for string literals
        String raw = exprNode.toSourceCode().trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /**
     * Extracts default values from a function's parameters.
     */
    private void extractFunctionDefaultValues(FunctionDefinitionNode funcNode, Map<String, String> paramDefaults) {
        FunctionSignatureNode signature = funcNode.functionSignature();
        SeparatedNodeList<ParameterNode> parameters = signature.parameters();

        for (ParameterNode paramNode : parameters) {
            if (paramNode instanceof DefaultableParameterNode defaultableParam) {
                String paramName = defaultableParam.paramName().map(Token::text).orElse("");
                String defaultValue = defaultableParam.expression().toSourceCode().trim();
                // Clean up the default value (remove quotes for simple strings if needed)
                paramDefaults.put(paramName, defaultValue);
            }
        }
    }

    private void analyzeClass(Package compilePackage, Module module, ClassSymbol classSymbol,
                              Map<String, Map<String, String>> defaultValues,
                              Map<String, Map<String, String>> recordFieldDefaults) {
        SemanticModel semanticModel = compilePackage.getCompilation().getSemanticModel(module.moduleId());

        if (!isClientClass(classSymbol) || classSymbol.getName().isEmpty()) {
            return;
        }
        String clientClassName = classSymbol.getName().get();
        ModuleSymbol moduleSymbol = classSymbol.getModule().orElseThrow(() -> new IllegalStateException("Client class is outside the module"));
        String moduleName = moduleSymbol.getName().orElseThrow(() -> new IllegalStateException("Module name not defined"));
        String connectionType = String.format("%s_%s", moduleName, clientClassName);

        // Replace dots with underscores in connectionType if module name has dots
        if (moduleName.contains(".")) {
            connectionType = connectionType.replace(".", "_");
        }

        Connector connector = Connector.getConnector();
        Connection connection = new Connection(connector, connectionType, clientClassName, Integer.toString(connector.getConnections().size()));
        GenerationReport.ClientReport clientReport = new GenerationReport.ClientReport(clientClassName, connectionType);

        // Get the connector description
        Optional<PackageReadmeMd> connectorReadMe = compilePackage.readmeMd();
        if (connectorReadMe.isPresent() && !connectorReadMe.get().content().isEmpty()) {
            connector.setDescription(connectorReadMe.get().content());
        } else {
            connector.setDescription(String.format("Ballerina %s connector", connector.getModuleName()));
        }

        // Get the connection description
        Optional<Documentation> optionalMetadataNode = classSymbol.documentation();
        if (optionalMetadataNode.isEmpty()) {
            connection.setDescription(String.format("%s connection for Ballerina %s connector", connectionType,
                    connector.getModuleName()));
        } else {
            connection.setDescription(Utils.getDocString(optionalMetadataNode.get()));
        }

        int i = 0;
        Map<String, MethodSymbol> allMethods = new HashMap<>(classSymbol.methods());
        classSymbol.initMethod().ifPresent(methodSymbol -> allMethods.put(Constants.INIT_FUNCTION_NAME, methodSymbol));

        // Track generated synapse names to handle duplicates
        Map<String, Integer> synapseNameCount = new HashMap<>();

        int totalOperations = 0;
        int skippedOperations = 0;

        for (Map.Entry<String, MethodSymbol> methodEntry : allMethods.entrySet()) {
            MethodSymbol methodSymbol = methodEntry.getValue();
            List<Qualifier> qualifierList = methodSymbol.qualifiers();
            if (!(Utils.containsToken(qualifierList, Qualifier.PUBLIC) ||
                    Utils.containsToken(qualifierList, Qualifier.REMOTE) ||
                    Utils.containsToken(qualifierList, Qualifier.RESOURCE))) {
                continue;
            }

            totalOperations++;

            String functionName = methodSymbol.getName().get();
            FunctionType functionType = Utils.getFunctionType(methodSymbol);
            // For init methods, use Constants.INIT_FUNCTION_NAME as key since we store it that way
            String functionKey = (functionType == FunctionType.INIT) ? Constants.INIT_FUNCTION_NAME : functionName;

            String returnType = Utils.getReturnTypeName(methodSymbol);
            String docString = methodSymbol.documentation().map(Utils::getDocString).orElse("");
            Component component = new Component(functionName, docString, functionType, Integer.toString(i),
                    List.of(), List.of(), returnType);

            // Get default values for this specific function
            Map<String, String> functionParamDefaults = defaultValues.getOrDefault(functionKey, Map.of());

            // Prepare context for synapse name generation
            SynapseNameContext.Builder contextBuilder = SynapseNameContext.builder().module(module);

            // Extract operationId from @openapi:ResourceInfo annotation if present using syntax tree API
            Optional<String> operationIdOpt = Optional.empty();
            try {
                operationIdOpt = Utils.getOpenApiOperationId(methodSymbol, module, semanticModel);
                if (operationIdOpt.isPresent()) {
                    printStream.println("Found operationId: " + operationIdOpt.get() + " for method: " + methodSymbol.getName().orElse("<unknown>"));
                }
            } catch (Exception e) {
                // If syntax tree access fails, continue without operationId
                printStream.println("Error extracting operationId for method: " + methodSymbol.getName().orElse("<unknown>") + " - " + e.getMessage());
            }

            // Add operationId to context if found
            operationIdOpt.ifPresent(contextBuilder::operationId);

            // Extract resource path segments if this is a resource function
            if (functionType == FunctionType.RESOURCE && methodSymbol instanceof ResourceMethodSymbol resourceMethod) {
                try {
                    PathSegmentList resourcePath = (PathSegmentList) resourceMethod.resourcePath();
                    contextBuilder.resourcePathSegments(resourcePath.list());
                } catch (Exception e) {
                    // If path extraction fails, continue without path segments
                }
            }

            SynapseNameContext context = contextBuilder.build();

            // Use priority-based synapse name generation
            SynapseNameGeneratorManager nameGenerator = new SynapseNameGeneratorManager();
            Optional<String> synapseNameOpt = nameGenerator.generateSynapseName(methodSymbol, functionType, context);

            // Fallback to default if no generator succeeded
            String synapseName = synapseNameOpt.orElseGet(() -> Utils.generateSynapseName(methodSymbol, functionType));

            // Replace dots with underscores in synapse name if connector module name has dots
            if (connector.getModuleName().contains(".")) {
                synapseName = synapseName.replace(".", "_");
            }

            // NOTE: Defer duplicate-name bookkeeping until after parameter validation
            // to avoid reserving names for methods that will be skipped
            String finalSynapseName = synapseName;

            // Extract path parameters and path segments from resource path (for resource functions)
            List<PathParamType> pathParams = new ArrayList<>();
            Set<String> pathParamNames = new HashSet<>();
            List<ResourcePathSegment> resourcePathSegments = new ArrayList<>();

            if (functionType == FunctionType.RESOURCE && methodSymbol instanceof ResourceMethodSymbol resourceMethod) {
                try {
                    PathSegmentList resourcePath = (PathSegmentList) resourceMethod.resourcePath();
                    List<PathSegment> segments = resourcePath.list();

                    for (PathSegment segment : segments) {
                        String sig = segment.signature();
                        if (sig != null && sig.startsWith("[") && sig.endsWith("]")) {
                            // This is a path parameter segment (e.g., "[string userId]")
                            String inside = sig.substring(1, sig.length() - 1).trim();
                            String paramName = inside;
                            String paramType = "string"; // default type
                            int lastSpace = inside.lastIndexOf(' ');
                            if (lastSpace >= 0 && lastSpace + 1 < inside.length()) {
                                paramType = inside.substring(0, lastSpace).trim();
                                paramName = inside.substring(lastSpace + 1);
                            }
                            if (!paramName.isEmpty()) {
                                pathParamNames.add(paramName);
                                // Add as a dynamic path segment
                                resourcePathSegments.add(new ResourcePathSegment(paramName, paramType));
                            }
                        } else if (sig != null && !sig.isEmpty()) {
                            // This is a static path segment (e.g., "users", "drafts")
                            // Remove any leading quote (for escaped Ballerina keywords like 'import)
                            String staticSegment = sig.startsWith("'") ? sig.substring(1) : sig;
                            resourcePathSegments.add(new ResourcePathSegment(staticSegment));
                        }
                    }
                } catch (Exception e) {
                    // If path extraction fails, continue without path parameters
                }
            }

            // Now match path parameter names with actual function parameters to get types
            Optional<List<ParameterSymbol>> params = methodSymbol.typeDescriptor().params();
            Map<String, ParameterSymbol> paramMap = new HashMap<>();
            if (params.isPresent()) {
                for (ParameterSymbol paramSymbol : params.get()) {
                    paramSymbol.getName().ifPresent(name -> paramMap.put(name, paramSymbol));
                }
            }

            /*
             * Create PathParamType objects for identified path parameters.
             * In some generated connectors, the path parameter name used in the resource path segment
             * does not have a matching function parameter (for example, when the path param is only
             * used in the path and not re-declared as a separate argument).
             *
             * Previously, such path parameters were silently dropped which resulted in:
             *   - No <property name="pathParam*".../> entries in the Synapse template
             *   - Missing input fields for those path params in the JSON UI schema
             *
             * To avoid losing path parameters, we now:
             *   - Use the actual parameter type when a matching function parameter exists
             *   - Fall back to treating the path parameter as a string when there is no match
             */
            for (String pathParamName : pathParamNames) {
                ParameterSymbol paramSymbol = paramMap.get(pathParamName);
                String paramTypeName;
                if (paramSymbol != null) {
                    paramTypeName = Utils.getParamTypeName(Utils.getActualTypeKind(paramSymbol.typeDescriptor()));
                    // If we cannot resolve a concrete MI type, also fall back to string
                    if (paramTypeName == null) {
                        paramTypeName = Constants.STRING;
                    }
                } else {
                    // No matching parameter symbol - assume string path parameter
                    paramTypeName = Constants.STRING;
                }

                PathParamType pathParam = new PathParamType();
                pathParam.name = pathParamName;
                pathParam.typeName = paramTypeName;
                pathParams.add(pathParam);
            }

            // Collect all parameter names (path params, function params) to check for conflicts
            Set<String> allParamNames = new HashSet<>(pathParamNames);
            if (params.isPresent()) {
                List<ParameterSymbol> parameterSymbols = params.get();
                for (ParameterSymbol parameterSymbol : parameterSymbols) {
                    Optional<String> paramNameOpt = parameterSymbol.getName();
                    if (paramNameOpt.isPresent()) {
                        allParamNames.add(paramNameOpt.get());
                    }
                }
            }

            // Check if synapse name conflicts with any parameter name and make it unique if needed
            Optional<String> methodNameOpt = methodSymbol.getName();
            if (allParamNames.contains(synapseName) ||
                    (methodNameOpt.isPresent() && allParamNames.contains(methodNameOpt.get()))) {
                // Add a suffix to make the synapse name unique and avoid conflicts
                synapseName = synapseName + "_operation";
            }

            // NOW handle duplicate names by appending numeric suffix
            // (All parameters are validated, so this method will not be skipped)
            if (synapseNameCount.containsKey(synapseName)) {
                int count = synapseNameCount.get(synapseName) + 1;
                synapseNameCount.put(synapseName, count);
                finalSynapseName = synapseName + count;
            } else {
                synapseNameCount.put(synapseName, 0);
                finalSynapseName = synapseName;
            }

            component = new Component(finalSynapseName, docString, functionType, Integer.toString(i), pathParams, List.of(), returnType);

            // For resource functions, store the accessor and path segments for invocation
            if (functionType == FunctionType.RESOURCE) {
                component.setResourceAccessor(functionName); // functionName is the accessor (get, post, etc.)
                component.setResourcePathSegments(resourcePathSegments);
            }

            // Store operationId as a parameter if found and mark the component
            if (operationIdOpt.isPresent()) {
                Param operationIdParam = new Param("operationId", operationIdOpt.get());
                component.setParam(operationIdParam);
                component.setHasOperationId(true);
            }


            // Now add all function parameters (we keep them all, synapse name is made unique instead)
            int paramIndex = 0;
            boolean shouldSkipOperation = false;
            if (params.isPresent()) {
                List<ParameterSymbol> parameterSymbols = params.get();
                for (ParameterSymbol parameterSymbol : parameterSymbols) {
                    Optional<FunctionParam> functionParam = ParamFactory.createFunctionParam(parameterSymbol, paramIndex);
                    if (functionParam.isEmpty()) {
                        String paramType = parameterSymbol.typeDescriptor().typeKind().getName();
                        String skipReason = "unsupported parameter type: " + paramType;
                        printStream.println("Skipping function '" + functionName +
                                "' due to " + skipReason);
                        clientReport.addSkipped(functionName, skipReason);
                        skippedOperations++;
                        shouldSkipOperation = true;
                        break; // Exit parameter loop, skip this entire operation
                    }

                    FunctionParam param = functionParam.get();
                    String paramName = parameterSymbol.getName().orElse("");
                    if (functionParamDefaults.containsKey(paramName)) {
                        String defaultValue = functionParamDefaults.get(paramName);
                        if ("<>".equals(defaultValue)) {
                            // typedesc default value `<>` should not override the type name we already set
                            defaultValue = param.getDefaultValue();
                        } else if (defaultValue.startsWith("\"") && defaultValue.endsWith("\"")) {
                            defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                        } else if ("()".equals(defaultValue)) {
                            // Convert Ballerina nil to empty string for UI schema
                            defaultValue = "";
                        } else if (param instanceof EnumFunctionParam enumParam
                                && !enumParam.getEnumValues().contains(defaultValue)
                                && defaultValue.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                            // defaultValue is a bare Ballerina identifier (enum member name, e.g. "DEFAULT"),
                            // not an actual enum value (e.g. "0"). Resolve it via the semantic model.
                            defaultValue = resolveConstantValue(semanticModel, defaultValue)
                                    .orElse(defaultValue);
                        }
                        param.setDefaultValue(defaultValue);
                        param.setRequired(false);
                    }
                    // Apply enum defaults for any enum fields nested inside record params
                    applyEnumFieldDefaults(param, recordFieldDefaults, semanticModel);
                    component.setFunctionParam(param);
                    paramIndex++;
                }

                // Only add component if we didn't skip due to unsupported parameter
                if (!shouldSkipOperation) {
                    Param sizeParam = new Param("paramSize", Integer.toString(paramIndex));
                    Param functionNameParam = new Param("paramFunctionName", component.getName());
                    component.setParam(sizeParam);
                    component.setParam(functionNameParam);
                }
            }

            // Only add the component if it wasn't skipped
            if (!shouldSkipOperation) {
                clientReport.addIncluded(functionName, finalSynapseName, functionType.name());
                if (functionType == FunctionType.INIT) {
                    // objectTypeName is only needed on Connection, not on Component
                    // to avoid duplication in generated XML
                    connection.setInitComponent(component);
                } else {
                    connection.setComponent(component);
                }
                i++;
            }
        }

        // Abort generation only if no operations exist
        if (totalOperations == 0) {
            String message = String.format("WARNING: No operations found in class '%s'. Artifact generation will be skipped.", clientClassName);
            printStream.println(message);
            connector.setGenerationAborted(true, message);
            return;
        }

        // Skip client if no init method exists
        if (connection.getInitComponent() == null) {
            String message = String.format("Skipping client '%s': no init method found.", clientClassName);
            printStream.println(message);
            return;
        }

        if (skippedOperations > 0) {
            String message = String.format("WARNING: %d out of %d operations were skipped due to " +
                            "unsupported parameter types for '%s'.",
                    skippedOperations, totalOperations, clientClassName);
            printStream.println(message);
        }

        GenerationReport report = connector.getGenerationReport();
        if (report != null) {
            report.addClientReport(clientReport);
        }

        connector.setConnection(connection);
    }

    /**
     * Walks the param tree and, for any {@link io.ballerina.mi.model.param.EnumFunctionParam} nested inside a
     * record whose Ballerina type has a declared field default, sets that default value.
     *
     * <p>Two-tier lookup strategy:
     * <ol>
     *   <li>Check the local {@code recordFieldDefaults} map (populated from the connector's own source).</li>
     *   <li>If not found (the record type lives in a dependency like {@code ballerina/oauth2}), read the
     *       bala source file directly to extract the exact default expression at the declared line.</li>
     * </ol>
     */
    private void applyEnumFieldDefaults(FunctionParam param,
                                        Map<String, Map<String, String>> recordFieldDefaults,
                                        SemanticModel semanticModel) {
        // Union members (e.g. OAuth2ClientCredentialsGrantConfig inside an auth union) are stored
        // in getUnionMemberParams(), not getRecordFieldParams(), so we must recurse into them too.
        if (param instanceof io.ballerina.mi.model.param.UnionFunctionParam unionParam) {
            for (FunctionParam memberParam : unionParam.getUnionMemberParams()) {
                applyEnumFieldDefaults(memberParam, recordFieldDefaults, semanticModel);
            }
            return;
        }
        if (!(param instanceof io.ballerina.mi.model.param.RecordFunctionParam recordParam)) {
            return;
        }
        String recordTypeName = recordParam.getRecordName();
        Map<String, String> fieldDefaults = recordFieldDefaults.get(recordTypeName);

        // If not in the current-module map, resolve from the bala source (external dependency).
        // Always cache the result (even if empty) to avoid repeated file scans for the same type.
        if (fieldDefaults == null) {
            fieldDefaults = extractFieldDefaultsFromDependencies(recordParam);
            recordFieldDefaults.put(recordTypeName, fieldDefaults);
        }

        // Lazy-loaded bala defaults for fields introduced by record inclusion (*SomeType).
        // These are NOT present in the local fieldDefaults map because extractAllDefaults only
        // scans direct RecordFieldWithDefaultValueNode declarations, not included type fields.
        Map<String, String> dependencyDefaults = null;

        for (FunctionParam fieldParam : recordParam.getRecordFieldParams()) {
            if (fieldParam instanceof io.ballerina.mi.model.param.EnumFunctionParam enumField
                    && (enumField.getDefaultValue() == null || enumField.getDefaultValue().isEmpty())) {
                String fieldName = lastPathSegment(enumField.getValue());
                String rawDefault = fieldDefaults.get(fieldName);
                // Field not in local defaults — it may come from a record inclusion (*Type).
                // Lazy-load bala defaults for this record and retry.
                if (rawDefault == null || rawDefault.isEmpty()) {
                    if (dependencyDefaults == null) {
                        dependencyDefaults = extractFieldDefaultsFromDependencies(recordParam);
                    }
                    rawDefault = dependencyDefaults.get(fieldName);
                }
                if (rawDefault != null && !rawDefault.isEmpty()) {
                    String resolved = cleanEnumDefault(rawDefault, enumField, semanticModel);
                    if (resolved != null && !resolved.isEmpty()) {
                        enumField.setDefaultValue(resolved);
                    }
                }
            }
            // Recurse so nested records are also covered
            applyEnumFieldDefaults(fieldParam, recordFieldDefaults, semanticModel);
        }
    }

    /**
     * Resolves field default values for a record type defined in a dependency module (bala).
     *
     * <p>For each field with {@code hasDefaultValue() == true}, reads the bala source file
     * directly to get the exact default expression at the declared line.
     */
    private Map<String, String> extractFieldDefaultsFromDependencies(
            io.ballerina.mi.model.param.RecordFunctionParam recordParam) {

        io.ballerina.compiler.api.symbols.TypeSymbol typeSymbol = recordParam.getTypeSymbol();
        if (typeSymbol == null) {
            return Map.of();
        }
        io.ballerina.compiler.api.symbols.TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);
        if (!(actualTypeSymbol instanceof io.ballerina.compiler.api.symbols.RecordTypeSymbol recordTypeSymbol)) {
            return Map.of();
        }

        Map<String, String> defaults = new HashMap<>();

        // Primary: direct fields with defaults
        for (Map.Entry<String, io.ballerina.compiler.api.symbols.RecordFieldSymbol> entry
                : recordTypeSymbol.fieldDescriptors().entrySet()) {
            String fieldName = entry.getKey();
            io.ballerina.compiler.api.symbols.RecordFieldSymbol fieldSymbol = entry.getValue();
            if (!fieldSymbol.hasDefaultValue()) continue;
            resolveFieldDefaultFromBalaSource(fieldSymbol).ifPresent(val -> defaults.put(fieldName, val));
        }

        // Fallback: fields from included types (*SomeType).
        // The included type's RecordFieldSymbol carries the correct bala source location.
        for (io.ballerina.compiler.api.symbols.TypeSymbol includedType : recordTypeSymbol.typeInclusions()) {
            io.ballerina.compiler.api.symbols.TypeSymbol actualIncluded = Utils.getActualTypeSymbol(includedType);
            if (!(actualIncluded instanceof io.ballerina.compiler.api.symbols.RecordTypeSymbol includedRecord)) continue;
            for (Map.Entry<String, io.ballerina.compiler.api.symbols.RecordFieldSymbol> entry
                    : includedRecord.fieldDescriptors().entrySet()) {
                String fieldName = entry.getKey();
                if (defaults.containsKey(fieldName)) continue; // local definition wins
                io.ballerina.compiler.api.symbols.RecordFieldSymbol fieldSymbol = entry.getValue();
                if (!fieldSymbol.hasDefaultValue()) continue;
                resolveFieldDefaultFromBalaSource(fieldSymbol).ifPresent(val -> defaults.put(fieldName, val));
            }
        }
        return defaults;
    }

    /**
     * Resolves the default value of a record field declared in a bala dependency by
     * reading the field's source {@code .bal} file from the local Ballerina bala cache.
     *
     * <p>Steps:
     * <ol>
     *   <li>{@code fieldSymbol.getLocation()} gives the relative file name and line number.</li>
     *   <li>The field type's {@code ModuleSymbol.id()} gives org, module name, and version.</li>
     *   <li>We find the file by searching the bala cache directory for the module.</li>
     *   <li>We read the line, strip the field declaration, and extract the default expression.</li>
     * </ol>
     */
    /**
     * Resolves the default value of an enum record field from the bala source.
     *
     * <p>Uses a text-scan strategy: searches all {@code .bal} files in the field type's
     * module bala for a line matching {@code <TypeName> <fieldName> = <value>;}.
     * This is reliable even for fields that arrive via deep record-inclusion chains where
     * {@code getLocation()} may not point to the correct file.
     */
    private Optional<String> resolveFieldDefaultFromBalaSource(
            io.ballerina.compiler.api.symbols.RecordFieldSymbol fieldSymbol) {
        try {
            String fieldName = fieldSymbol.getName().orElse("");
            if (fieldName.isEmpty()) return Optional.empty();

            // Use typeDescriptor() without unwrapping — TypeReferenceTypeSymbol retains module info.
            io.ballerina.compiler.api.symbols.TypeSymbol fieldType = fieldSymbol.typeDescriptor();
            String fieldTypeName = fieldType.getName().orElse("");

            Optional<io.ballerina.compiler.api.symbols.ModuleSymbol> modOpt = fieldType.getModule();
            if (modOpt.isEmpty()) return Optional.empty();

            io.ballerina.compiler.api.ModuleID modId = modOpt.get().id();
            String orgName    = modId.orgName();
            String moduleName = modId.moduleName();
            String version    = modId.version();
            String pkgName    = moduleName.contains(".") ? moduleName.split("\\.")[0] : moduleName;

            String homeDir = System.getProperty("user.home");
            List<java.nio.file.Path> repoRoots = List.of(
                java.nio.file.Paths.get(homeDir, ".ballerina", "repositories", "central.ballerina.io", "bala", orgName, pkgName, version),
                java.nio.file.Paths.get(homeDir, ".ballerina", "repositories", "local", "bala", orgName, pkgName, version)
            );

            // Pattern: optional whitespace, the type name, whitespace, the field name, optional whitespace, =
            // Handles both "CredentialBearer credentialBearer = AUTH_HEADER_BEARER;" and variants.
            java.util.regex.Pattern pattern = fieldTypeName.isEmpty()
                    ? java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(fieldName) + "\\s*=\\s*(.+?)\\s*[;,]")
                    : java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(fieldTypeName)
                            + "\\s+" + java.util.regex.Pattern.quote(fieldName) + "\\s*=\\s*(.+?)\\s*[;,]");

            for (java.nio.file.Path root : repoRoots) {
                if (!java.nio.file.Files.exists(root)) continue;
                try (java.util.stream.Stream<java.nio.file.Path> pathStream = java.nio.file.Files.walk(root)) {
                    List<java.nio.file.Path> balFiles = pathStream
                            .filter(p -> !java.nio.file.Files.isDirectory(p) && p.toString().endsWith(".bal"))
                            .toList();
                    for (java.nio.file.Path balFile : balFiles) {
                        for (String line : java.nio.file.Files.readAllLines(balFile)) {
                            java.util.regex.Matcher m = pattern.matcher(line);
                            if (m.find()) {
                                String rawVal = m.group(1).trim();
                                // Strip module qualifier: "pkg:CONST" -> "CONST"
                                int colonIdx = rawVal.lastIndexOf(':');
                                if (colonIdx >= 0) rawVal = rawVal.substring(colonIdx + 1).trim();
                                // Strip inline comments
                                int commentIdx = rawVal.indexOf("//");
                                if (commentIdx >= 0) rawVal = rawVal.substring(0, commentIdx).trim();
                                if (!rawVal.isEmpty()) return Optional.of(rawVal);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return Optional.empty();
    }

    /**
     * Reads a specific line from a {@code .bal} file and extracts the default expression.
     *
     * <p>Expected line format: {@code TypeName fieldName = DEFAULT_EXPR;}  <br/>
     * Also handles qualified names: {@code TypeName fieldName = pkg:CONST;}
    /** Strips quotes / resolves identifiers from a raw default-value expression for an enum field. */
    private String cleanEnumDefault(String rawDefault,
                                    io.ballerina.mi.model.param.EnumFunctionParam enumParam,
                                    SemanticModel semanticModel) {
        if (rawDefault.startsWith("\"") && rawDefault.endsWith("\"") && rawDefault.length() >= 2) {
            return rawDefault.substring(1, rawDefault.length() - 1);
        }
        if ("()".equals(rawDefault)) {
            return "";
        }
        // Strip module qualifier (e.g. "http:KEEPALIVE_AUTO" → "KEEPALIVE_AUTO")
        int colonIdx = rawDefault.lastIndexOf(':');
        if (colonIdx >= 0) {
            rawDefault = rawDefault.substring(colonIdx + 1).trim();
        }
        // Value is already a clean enum member — return as-is
        if (enumParam.getEnumValues().contains(rawDefault)) {
            return rawDefault;
        }
        // Bare identifier (unresolved constant name) — resolve via the semantic model
        if (rawDefault.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return resolveConstantValue(semanticModel, rawDefault).orElse(rawDefault);
        }
        return rawDefault;
    }

    private static String lastPathSegment(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return qualifiedName;
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    /**
     * Resolves a Ballerina constant name (e.g. "DEFAULT") to its actual value (e.g. "0") using the semantic model.
     * Returns an empty Optional if the constant cannot be found or its value cannot be determined.
     */
    private Optional<String> resolveConstantValue(SemanticModel semanticModel, String constantName) {
        for (Symbol symbol : semanticModel.moduleSymbols()) {
            if (symbol instanceof ConstantSymbol constantSymbol
                    && constantSymbol.getName().filter(constantName::equals).isPresent()) {
                Optional<String> resolved = constantSymbol.resolvedValue();
                if (resolved.isPresent()) {
                    String value = resolved.get().trim();
                    // resolvedValue() returns quoted strings for string constants, e.g. "\"0\""
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return Optional.of(value);
                }
                // Fall back to constValue() if resolvedValue() is absent
                Object constVal = constantSymbol.constValue();
                if (constVal != null) {
                    return Optional.of(constVal.toString());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isClientClass(ClassSymbol classSymbol) {
        return classSymbol.qualifiers().contains(Qualifier.PUBLIC) && classSymbol.qualifiers().contains(Qualifier.CLIENT);
    }

    /**
     * Counts the number of expanded parameters. For RecordFunctionParam, counts its fields recursively.
     * For other params, returns 1.
     */
    private int countExpandedParams(FunctionParam param) {
        if (param instanceof RecordFunctionParam recordParam && !recordParam.getRecordFieldParams().isEmpty()) {
            int count = 0;
            for (FunctionParam fieldParam : recordParam.getRecordFieldParams()) {
                count += countExpandedParams(fieldParam);
            }
            return count;
        }
        return 1;
    }
}
