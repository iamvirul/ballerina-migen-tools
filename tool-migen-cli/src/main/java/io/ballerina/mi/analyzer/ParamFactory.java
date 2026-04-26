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

import io.ballerina.compiler.api.impl.symbols.BallerinaUnionTypeSymbol;
import io.ballerina.compiler.api.symbols.ArrayTypeSymbol;
import io.ballerina.compiler.api.symbols.MapTypeSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ParameterKind;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeDescTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.mi.model.param.ArrayFunctionParam;
import io.ballerina.mi.model.param.EnumFunctionParam;
import io.ballerina.mi.model.param.FunctionParam;
import io.ballerina.mi.model.param.IncludedRecordFunctionParam;
import io.ballerina.mi.model.param.MapFunctionParam;
import io.ballerina.mi.model.param.RecordFunctionParam;
import io.ballerina.mi.model.param.UnionFunctionParam;
import io.ballerina.mi.util.Constants;
import io.ballerina.mi.util.Utils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Factory class responsible for creating {@link FunctionParam} instances from Ballerina
 * {@link ParameterSymbol} objects. This factory handles the conversion of various parameter
 * types including simple types and union types.
 *
 * <p>The factory analyzes the type descriptor of each parameter and creates appropriate
 * FunctionParam instances, with special handling for union types that may contain multiple
 * member types.</p>
 *
 * @since 0.4.3
 */
public class ParamFactory {

    /**
     * Creates a {@link FunctionParam} from a {@link ParameterSymbol}.
     *
     * <p>This method analyzes the parameter's type descriptor and creates an appropriate
     * FunctionParam instance.
     *
     * @param parameterSymbol the Ballerina parameter symbol containing type and name information
     * @param index           the zero-based index position of the parameter in the function signature
     * @return an {@link Optional} containing the created {@link FunctionParam} if the parameter
     * type is supported, or {@link Optional#empty()} if the type is not supported
     */
    public static Optional<FunctionParam> createFunctionParam(ParameterSymbol parameterSymbol, int index) {
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        TypeDescKind actualTypeKind = Utils.getActualTypeKind(typeSymbol);
        String paramType = Utils.getParamTypeName(actualTypeKind);

        if (paramType != null) {
            if (actualTypeKind == TypeDescKind.UNION) {
                TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);
                if (actualTypeSymbol instanceof UnionTypeSymbol unionType && isEnumUnion(unionType)) {
                    return createEnumFunctionParam(parameterSymbol, index, unionType);
                }
                return createUnionFunctionParam(parameterSymbol, index);
            }
            if (actualTypeKind == TypeDescKind.RECORD) {
                // Handle included record parameters (*RecordType syntax)
                if (parameterSymbol.paramKind() == ParameterKind.INCLUDED_RECORD) {
                    return createIncludedRecordFunctionParam(parameterSymbol, index);
                }
                return createRecordFunctionParam(parameterSymbol, index);
            }
            if (actualTypeKind == TypeDescKind.MAP) {
                return createMapFunctionParam(parameterSymbol, index);
            }
            if (actualTypeKind == TypeDescKind.ARRAY) {
                return createArrayFunctionParam(parameterSymbol, index);
            }
            if (actualTypeKind == TypeDescKind.TYPEDESC) {
                return createTypedescFunctionParam(parameterSymbol, index);
            }
            FunctionParam functionParam = new FunctionParam(Integer.toString(index), parameterSymbol.getName().orElseThrow(), paramType);
            functionParam.setParamKind(parameterSymbol.paramKind());
            functionParam.setTypeSymbol(typeSymbol);
            return Optional.of(functionParam);
        }
        return Optional.empty();
    }

    // ─── Types skipped inside typedesc<T>
    // These open/erased kinds cannot be represented in the MI UI schema:
    //   - ANY        →  too open, no concrete type to show
    // ANYDATA is handled separately as a json input field.
    // If the typedesc has no type parameter at all, we also skip.
    private static final java.util.Set<TypeDescKind> TYPEDESC_SKIP_KINDS = java.util.Set.of(
            TypeDescKind.ANY
    );

    /**
     * Handles {@code typedesc<T>} parameters.
     *
     * <ul>
     *   <li>{@code typedesc<string>} / {@code typedesc<int>} etc. — plain FunctionParam with the primitive type</li>
     *   <li>{@code typedesc<MyRecord>} — plain FunctionParam with type "string", value = record name</li>
     *   <li>{@code typedesc<RecordA|RecordB>} or {@code typedesc<string|MyRecord>} — UnionFunctionParam combobox</li>
     *   <li>Anydata / erased / unsupported — Optional.empty() (whole operation is skipped)</li>
     * </ul>
     */
    private static Optional<FunctionParam> createTypedescFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        TypeSymbol rawTypeSymbol = parameterSymbol.typeDescriptor();
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(rawTypeSymbol);

        // Resolve the type parameter inside typedesc<T>
        if (!(actualTypeSymbol instanceof TypeDescTypeSymbol typedescTypeSymbol)) {
            // Not a TypeDescTypeSymbol — cannot extract constraint, skip
            return Optional.empty();
        }

        Optional<TypeSymbol> constraintOpt = typedescTypeSymbol.typeParameter();
        if (constraintOpt.isEmpty()) {
            // Erased typedesc<> — skip
            return Optional.empty();
        }

        TypeSymbol constraint = constraintOpt.get();
        TypeDescKind constraintKind = Utils.getActualTypeKind(constraint);

        // Skip open/generic kinds we cannot represent
        if (TYPEDESC_SKIP_KINDS.contains(constraintKind)) {
            return Optional.empty();
        }

        boolean isDefaultable = parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE;

        // ── ANYDATA constraint: plain JSON field — accept any Ballerina plain-data ─
        if (constraintKind == TypeDescKind.ANYDATA) {
            FunctionParam param = new FunctionParam(Integer.toString(index), paramName, Constants.ANYDATA);
            param.setParamKind(parameterSymbol.paramKind());
            param.setTypeSymbol(rawTypeSymbol);
            param.setRequired(!isDefaultable);
            param.setTypeDescriptor(true);
            return Optional.of(param);
        }

        // ── UNION constraint: build a combobox ───────────────────────────────────
        if (constraintKind == TypeDescKind.UNION) {
            TypeSymbol actualConstraint = Utils.getActualTypeSymbol(constraint);
            if (!(actualConstraint instanceof UnionTypeSymbol unionTypeSymbol)) {
                return Optional.empty();
            }
            return buildTypedescUnionParam(paramName, index, unionTypeSymbol, rawTypeSymbol, isDefaultable, parameterSymbol.paramKind());
        }

        // ── RECORD constraint: fixed string field with the record type name ──────
        if (constraintKind == TypeDescKind.RECORD) {
            TypeSymbol actualConstraint = Utils.getActualTypeSymbol(constraint);
            // Try to get name from the original constraint first (for type references like asb:Message)
            // Fall back to actual type symbol name, then parameter name
            String recordName = constraint.getName()
                    .or(() -> actualConstraint.getName())
                    .orElse(paramName);
            FunctionParam param = new FunctionParam(Integer.toString(index), paramName, Constants.STRING);
            param.setParamKind(parameterSymbol.paramKind());
            param.setTypeSymbol(rawTypeSymbol);
            param.setRequired(!isDefaultable);
            param.setTypeDescriptor(true);
            // Default value = the record type name so it is pre-filled in the UI
            if (isDefaultable) {
                param.setDefaultValue(recordName);
            }
            return Optional.of(param);
        }

        // ── Single primitive constraint ───────────────────────────────────────────
        String primitiveType = Utils.getParamTypeName(constraintKind);
        if (primitiveType == null || Constants.TYPEDESC.equals(primitiveType)) {
            // Unsupported or nested typedesc — skip
            return Optional.empty();
        }
        FunctionParam param = new FunctionParam(Integer.toString(index), paramName, primitiveType);
        param.setParamKind(parameterSymbol.paramKind());
        param.setTypeSymbol(rawTypeSymbol);
        param.setRequired(!isDefaultable);
        param.setTypeDescriptor(true);
        return Optional.of(param);
    }

    /**
     * Builds a {@link UnionFunctionParam} combobox from the members of a union inside
     * {@code typedesc<A|B|...>}. Each member becomes one selectable option.
     * <ul>
     *   <li>Primitive members: option label = primitive type name (e.g. "string")</li>
     *   <li>Record members: option label = record type name (e.g. "Message")</li>
     *   <li>Nil / unsupported / skip-kind members are excluded</li>
     * </ul>
     * If only 1 non-nil member survives, the result is simplified to a plain FunctionParam.
     */
    private static Optional<FunctionParam> buildTypedescUnionParam(
            String paramName, int index, UnionTypeSymbol unionTypeSymbol,
            TypeSymbol rawTypeSymbol, boolean isDefaultable, ParameterKind paramKind) {

        UnionFunctionParam unionParam = new UnionFunctionParam(Integer.toString(index), paramName, "union");
        unionParam.setTypeSymbol(rawTypeSymbol);
        unionParam.setRequired(!isDefaultable);
        unionParam.setTypeDescriptor(true);

        java.util.Set<String> seen = new java.util.HashSet<>();
        int memberIndex = 0;

        for (TypeSymbol member : unionTypeSymbol.memberTypeDescriptors()) {
            TypeDescKind memberKind = Utils.getActualTypeKind(member);

            // Skip nil (makes the union optional, but don't show as a selectable type)
            if (memberKind == TypeDescKind.NIL) {
                unionParam.setRequired(false);
                continue;
            }

            // Skip open/erased/unsupported kinds inside the union
            if (TYPEDESC_SKIP_KINDS.contains(memberKind)) {
                continue;
            }

            String optionLabel;
            String optionType;
            if (memberKind == TypeDescKind.RECORD) {
                TypeSymbol actualMember = Utils.getActualTypeSymbol(member);
                optionLabel = member.getName().orElse(actualMember.getName().orElse("Record" + memberIndex));
                optionType = Constants.STRING;
            } else {
                optionType = Utils.getParamTypeName(memberKind);
                if (optionType == null || Constants.TYPEDESC.equals(optionType) || "nil".equals(optionType)) {
                    continue;
                }
                optionLabel = optionType;
            }

            if (seen.contains(optionLabel)) {
                continue;
            }
            seen.add(optionLabel);

            String memberParamName = paramName + org.apache.commons.lang3.StringUtils.capitalize(optionLabel);
            FunctionParam memberParam = new FunctionParam(Integer.toString(memberIndex), memberParamName, optionType);
            memberParam.setTypeSymbol(member);
            memberParam.setDisplayTypeName(optionLabel);
            memberParam.setRequired(!isDefaultable);

            String sanitized = Utils.sanitizeParamName(paramName);
            memberParam.setEnableCondition("[{\"" + sanitized + "DataType\": \"" + optionLabel + "\"}]");

            // Pre-fill the default value for typedesc options so the field carries the type name
            if (unionParam.isTypeDescriptor() || (memberKind == TypeDescKind.RECORD && isDefaultable)) {
                memberParam.setDefaultValue(optionLabel);
            }

            unionParam.addUnionMemberParam(memberParam);
            memberIndex++;
        }

        // Nothing useful survived — skip
        if (unionParam.getUnionMemberParams().isEmpty()) {
            return Optional.empty();
        }

        // Simplify single-member union to a plain FunctionParam
        if (unionParam.getUnionMemberParams().size() == 1
                && !(unionParam.getUnionMemberParams().get(0) instanceof UnionFunctionParam)) {
            FunctionParam single = unionParam.getUnionMemberParams().get(0);
            FunctionParam simplified = new FunctionParam(Integer.toString(index), paramName, single.getParamType());
            simplified.setParamKind(paramKind);
            simplified.setTypeSymbol(rawTypeSymbol);
            simplified.setRequired(unionParam.isRequired());
            simplified.setTypeDescriptor(true);
            if (isDefaultable && single.getDefaultValue() != null) {
                simplified.setDefaultValue(single.getDefaultValue());
            }
            return Optional.of(simplified);
        }

        return Optional.of(unionParam);
    }

    private static Optional<FunctionParam> createRecordFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);

        RecordFunctionParam recordParam = new RecordFunctionParam(Integer.toString(index), paramName, TypeDescKind.RECORD.getName());
        recordParam.setParamKind(parameterSymbol.paramKind());
        recordParam.setTypeSymbol(typeSymbol);
        // Try to get name from the original type symbol first (for type references like ConnectionConfig)
        // Fall back to actual type symbol name, then parameter name
        String recordName = typeSymbol.getName()
                .or(() -> actualTypeSymbol.getName())
                .orElse(paramName);
        recordParam.setRecordName(recordName);

        // Set module info for the record type
        // For external types like time:Civil, the TypeReferenceTypeSymbol has the module info
        // whereas the unwrapped actualTypeSymbol may not
        Optional<ModuleSymbol> moduleOpt = Optional.empty();
        if (typeSymbol instanceof TypeReferenceTypeSymbol typeRef) {
            moduleOpt = typeRef.getModule();
        }
        if (moduleOpt.isEmpty()) {
            moduleOpt = actualTypeSymbol.getModule();
        }
        moduleOpt.ifPresent(moduleSymbol -> {
            recordParam.setRecordOrg(moduleSymbol.id().orgName());
            recordParam.setRecordModule(moduleSymbol.id().moduleName());
            recordParam.setRecordVersion(moduleSymbol.id().version());
        });

        // Set required based on parameter kind
        if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
            recordParam.setRequired(false);
        }

        // Extract record fields if the actual type is a RecordTypeSymbol
        if (actualTypeSymbol instanceof RecordTypeSymbol recordTypeSymbol) {
            // For top-level record params, use empty parent path so field names don't include the param name
            // For example, fields will be "authConfig.token" instead of "config.authConfig.token"
            String parentPath = "";  // Top-level record should not include param name in field paths
            recordParam.setParentParamPath("");  // Top-level has no parent
            populateRecordFieldParams(recordParam, recordTypeSymbol, parentPath);
        }

        return Optional.of(recordParam);
    }

    /**
     * Creates an {@link IncludedRecordFunctionParam} for included record parameters (*RecordType syntax).
     * <p>
     * Included record parameters allow callers to pass record fields as individual named arguments.
     * From the UI perspective, these are treated similarly to regular record parameters with their
     * fields expanded as individual input fields.
     * </p>
     *
     * @param parameterSymbol the Ballerina parameter symbol with INCLUDED_RECORD kind
     * @param index the parameter index
     * @return Optional containing the IncludedRecordFunctionParam, or empty if the type cannot be processed
     */
    private static Optional<FunctionParam> createIncludedRecordFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);

        IncludedRecordFunctionParam includedRecordParam = new IncludedRecordFunctionParam(
                Integer.toString(index), paramName, TypeDescKind.RECORD.getName());
        includedRecordParam.setParamKind(parameterSymbol.paramKind());
        includedRecordParam.setTypeSymbol(typeSymbol);

        // Get record name from type symbol
        String recordName = typeSymbol.getName()
                .or(actualTypeSymbol::getName)
                .orElse(paramName);
        includedRecordParam.setRecordName(recordName);

        // Set required based on parameter kind (same logic as regular record params)
        if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
            includedRecordParam.setRequired(false);
        }

        // Extract record fields if the actual type is a RecordTypeSymbol
        if (actualTypeSymbol instanceof RecordTypeSymbol recordTypeSymbol) {
            // Check for open record (has rest type descriptor like anydata...)
            Optional<TypeSymbol> restTypeOpt = recordTypeSymbol.restTypeDescriptor();
            if (restTypeOpt.isPresent()) {
                includedRecordParam.setOpenRecord(true);
                includedRecordParam.setRestTypeSymbol(restTypeOpt.get());
            }

            String parentPath = "";  // Top-level - no parent path prefix
            includedRecordParam.setParentParamPath("");
            populateRecordFieldParams(includedRecordParam, recordTypeSymbol, parentPath);
        }

        return Optional.of(includedRecordParam);
    }

    /**
     * Entry point for populating record fields. Creates a fresh signature-based visited-types set for cycle detection.
     */
    private static void populateRecordFieldParams(RecordFunctionParam recordParam, RecordTypeSymbol recordTypeSymbol, String parentPath) {
        populateRecordFieldParams(recordParam, recordTypeSymbol, parentPath, new HashSet<>());
    }

    /**
     * Populate record fields recursively with cycle detection.
     * Uses signature() as the key so both named and anonymous/inline record types are tracked correctly,
     * even when the compiler returns different wrapper objects for the same logical type.
     * Uses path-based (backtracking) cycle detection: a type is only blocked if it appears in the
     * current ancestor chain, not merely because it was expanded elsewhere in the tree.
     * This allows the same type (e.g., http:ProxyConfig) to be expanded at multiple sibling paths,
     * while still preventing true cycles (e.g., ClientConfiguration -> auth -> ClientConfiguration).
     */
    private static void populateRecordFieldParams(RecordFunctionParam recordParam, RecordTypeSymbol recordTypeSymbol, String parentPath, Set<String> visitedTypes) {
        // Cycle detection: use signature() so both named and anonymous types are uniquely identified
        // even when the compiler returns different object instances for the same logical type
        String typeSignature = recordTypeSymbol.signature();
        if (visitedTypes.contains(typeSignature)) {
            return; // Cycle detected in current path — stop expanding
        }
        visitedTypes.add(typeSignature);

        Map<String, RecordFieldSymbol> fieldDescriptors = recordTypeSymbol.fieldDescriptors();
        int fieldIndex = 0;

        for (Map.Entry<String, RecordFieldSymbol> entry : fieldDescriptors.entrySet()) {
            String fieldName = entry.getKey();
            RecordFieldSymbol fieldSymbol = entry.getValue();
            TypeSymbol fieldTypeSymbol = fieldSymbol.typeDescriptor();
            TypeDescKind fieldTypeKind = Utils.getActualTypeKind(fieldTypeSymbol);
            String fieldType = Utils.getParamTypeName(fieldTypeKind);

            if (fieldType != null) {
                FunctionParam fieldParam;

                // Build qualified name for this field
                String qualifiedFieldName = buildQualifiedName(parentPath, fieldName);

                // Check if field is optional (has ? suffix) or has a default value
                boolean isOptional = fieldSymbol.isOptional() || fieldSymbol.hasDefaultValue();
                // Propagate parent's optionality: if parent is optional (not required), this field is also optional
                boolean isEffectiveRequired = !isOptional && recordParam.isRequired();

                // Handle different field types appropriately
                if (fieldTypeKind == TypeDescKind.UNION) {
                    TypeSymbol actualEnumCheck = Utils.getActualTypeSymbol(fieldTypeSymbol);
                    if (actualEnumCheck instanceof UnionTypeSymbol enumUnionType && isEnumUnion(enumUnionType)) {
                        List<String> enumValues = extractEnumValues(enumUnionType);
                        EnumFunctionParam enumFieldParam = new EnumFunctionParam(
                                Integer.toString(fieldIndex), qualifiedFieldName, enumValues);
                        enumFieldParam.setTypeSymbol(fieldTypeSymbol);
                        enumFieldParam.setRequired(isEffectiveRequired);
                        fieldParam = enumFieldParam;
                    } else {
                        // Create UnionFunctionParam for union type fields
                        UnionFunctionParam unionFieldParam = new UnionFunctionParam(Integer.toString(fieldIndex), qualifiedFieldName, fieldType);
                        unionFieldParam.setTypeSymbol(fieldTypeSymbol);
                        // Set required status BEFORE recursion so it propagates to members
                        unionFieldParam.setRequired(isEffectiveRequired);

                        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(fieldTypeSymbol);
                        if (actualTypeSymbol instanceof BallerinaUnionTypeSymbol ballerinaUnionTypeSymbol) {
                            // Use qualifiedFieldName to ensure enable conditions are properly scoped for nested fields
                            populateUnionMemberParams(qualifiedFieldName, ballerinaUnionTypeSymbol, unionFieldParam, visitedTypes);
                        }
                        // Skip empty unions (all members are nil or unsupported types)
                        if (unionFieldParam.getUnionMemberParams().isEmpty()) {
                            continue;
                        }
                        // If there's only one non-nil member, convert to a regular FunctionParam instead of UnionFunctionParam
                        if (unionFieldParam.getUnionMemberParams().size() == 1 && !(unionFieldParam.getUnionMemberParams().get(0) instanceof UnionFunctionParam)) {
                            FunctionParam singleMember = unionFieldParam.getUnionMemberParams().get(0);
                            fieldParam = new FunctionParam(Integer.toString(fieldIndex), qualifiedFieldName, singleMember.getParamType());
                            fieldParam.setTypeSymbol(singleMember.getTypeSymbol());
                            fieldParam.setRequired(unionFieldParam.isRequired());
                        } else {
                            fieldParam = unionFieldParam;
                        }
                    }
                } else if (fieldTypeKind == TypeDescKind.RECORD) {
                    // Create RecordFunctionParam for nested record fields
                    TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(fieldTypeSymbol);
                    RecordFunctionParam nestedRecordParam = new RecordFunctionParam(Integer.toString(fieldIndex), qualifiedFieldName, fieldType);
                    nestedRecordParam.setTypeSymbol(fieldTypeSymbol);
                    nestedRecordParam.setRecordName(actualTypeSymbol.getName().orElse(fieldName));
                    nestedRecordParam.setParentParamPath(parentPath);
                    // Set required status BEFORE recursion so it propagates to children
                    nestedRecordParam.setRequired(isEffectiveRequired);

                    if (actualTypeSymbol instanceof RecordTypeSymbol nestedRecordTypeSymbol) {
                        String nestedParentPath = buildQualifiedName(parentPath, fieldName);
                        populateRecordFieldParams(nestedRecordParam, nestedRecordTypeSymbol, nestedParentPath, visitedTypes);
                    }
                    fieldParam = nestedRecordParam;
                } else if (fieldTypeKind == TypeDescKind.MAP) {
                    TypeSymbol actualFieldType = Utils.getActualTypeSymbol(fieldTypeSymbol);
                    MapFunctionParam mapFieldParam = new MapFunctionParam(
                        Integer.toString(fieldIndex), qualifiedFieldName, fieldType);
                    mapFieldParam.setTypeSymbol(fieldTypeSymbol);
                    mapFieldParam.setRequired(isEffectiveRequired);

                    if (actualFieldType instanceof MapTypeSymbol mapTypeSymbol) {
                        Optional<TypeSymbol> valueTypeOpt = mapTypeSymbol.typeParameter();
                        if (valueTypeOpt.isPresent()) {
                            TypeSymbol valueType = valueTypeOpt.get();
                            mapFieldParam.setValueTypeSymbol(valueType);
                            TypeDescKind valueTypeKind = Utils.getActualTypeKind(valueType);
                            boolean shouldRender = shouldMapRenderAsTable(valueType, valueTypeKind);
                            mapFieldParam.setRenderAsTable(shouldRender);
                            if (shouldRender && valueTypeKind == TypeDescKind.RECORD) {
                                TypeSymbol actualValueType = Utils.getActualTypeSymbol(valueType);
                                if (actualValueType instanceof RecordTypeSymbol recordType) {
                                    populateMapValueFields(mapFieldParam, recordType);
                                }
                            }
                        }
                    }
                    fieldParam = mapFieldParam;
                } else if (fieldTypeKind == TypeDescKind.ARRAY) {
                    TypeSymbol actualFieldType = Utils.getActualTypeSymbol(fieldTypeSymbol);
                    ArrayFunctionParam arrayFieldParam = new ArrayFunctionParam(
                        Integer.toString(fieldIndex), qualifiedFieldName, fieldType);
                    arrayFieldParam.setTypeSymbol(fieldTypeSymbol);
                    arrayFieldParam.setRequired(isEffectiveRequired);

                    if (actualFieldType instanceof ArrayTypeSymbol arrayTypeSymbol) {
                        TypeSymbol elementType = arrayTypeSymbol.memberTypeDescriptor();
                        arrayFieldParam.setElementTypeSymbol(elementType);
                        TypeDescKind elementTypeKind = Utils.getActualTypeKind(elementType);
                        boolean shouldRender = shouldArrayRenderAsTable(elementType, elementTypeKind);
                        arrayFieldParam.setRenderAsTable(shouldRender);
                        if (shouldRender && elementTypeKind == TypeDescKind.RECORD) {
                            TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                            if (actualElementType instanceof RecordTypeSymbol recordType) {
                                populateArrayElementFields(arrayFieldParam, recordType);
                                // Enable dual input mode (Table/JSON) for record arrays
                                arrayFieldParam.setSupportsDualInputMode(true);
                            }
                        }
                        if (shouldRender && elementTypeKind == TypeDescKind.ARRAY) {
                            arrayFieldParam.set2DArray(true);
                            TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                            if (actualElementType instanceof ArrayTypeSymbol innerArrayType) {
                                arrayFieldParam.setInnerElementTypeSymbol(innerArrayType.memberTypeDescriptor());
                            }
                        }
                        if (shouldRender && elementTypeKind == TypeDescKind.UNION) {
                            arrayFieldParam.setUnionArray(true);
                            TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                            if (actualElementType instanceof UnionTypeSymbol unionType) {
                                java.util.List<String> memberNames = new java.util.ArrayList<>();
                                for (TypeSymbol member : unionType.memberTypeDescriptors()) {
                                    TypeDescKind memberKind = Utils.getActualTypeKind(member);
                                    if (memberKind != TypeDescKind.NIL) {
                                        String memberName = Utils.getParamTypeName(memberKind);
                                        if (memberName != null && !memberNames.contains(memberName)) {
                                            memberNames.add(memberName);
                                        }
                                    }
                                }
                                arrayFieldParam.setUnionMemberTypeNames(memberNames);
                            }
                        }
                    }
                    fieldParam = arrayFieldParam;
                } else {
                    fieldParam = new FunctionParam(Integer.toString(fieldIndex), qualifiedFieldName, fieldType);
                    fieldParam.setTypeSymbol(fieldTypeSymbol);
                    fieldParam.setRequired(isEffectiveRequired);
                }

                // Get field description from documentation if available
                fieldSymbol.documentation().ifPresent(doc ->
                    doc.description().ifPresent(fieldParam::setDescription));

                recordParam.addRecordFieldParam(fieldParam);
                fieldIndex++;
            }
        }
        // Backtrack: remove from visited so sibling branches can expand this type again.
        // Cycle detection still works because the type is in visitedTypes throughout the
        // entire recursive expansion of its fields (re-entry from a descendant is blocked).
        visitedTypes.remove(typeSignature);
    }

    private static Optional<FunctionParam> createUnionFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        UnionFunctionParam functionParam = new UnionFunctionParam(Integer.toString(index), paramName, TypeDescKind.UNION.getName());
        functionParam.setParamKind(parameterSymbol.paramKind());
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        functionParam.setTypeSymbol(typeSymbol);
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);
        Set<String> visitedTypes = new HashSet<>();
        if (actualTypeSymbol instanceof BallerinaUnionTypeSymbol ballerinaUnionTypeSymbol) {
            populateUnionMemberParams(paramName, ballerinaUnionTypeSymbol, functionParam, visitedTypes);
        }
        functionParam.setTypeSymbol(parameterSymbol.typeDescriptor());
        // Only try the second approach if the first one didn't find any members
        if (functionParam.getUnionMemberParams().isEmpty()) {
            // Resolve type references to get the actual union type symbol
            actualTypeSymbol = Utils.getActualTypeSymbol(parameterSymbol.typeDescriptor());
            // Check for UnionTypeSymbol interface instead of concrete class to handle all union type implementations
            if (actualTypeSymbol instanceof UnionTypeSymbol unionTypeSymbol) {
                populateUnionMemberParams(paramName, unionTypeSymbol, functionParam, visitedTypes);
            }
        }
        // Skip empty unions (all members are nil or unsupported types)
        // Note: Check AFTER both population attempts to ensure we try all possible ways to extract union members
        if (functionParam.getUnionMemberParams().isEmpty()) {
            return Optional.empty();
        }
        // If there's only one non-nil member, return it as a regular FunctionParam instead of a UnionFunctionParam
        // This avoids generating a pointless combobox with a single selectable value (e.g., for optional types like string?)
        // However, if the single member is itself a UnionFunctionParam, we must NOT simplify it to a generic FunctionParam.
        if (functionParam.getUnionMemberParams().size() == 1 && !(functionParam.getUnionMemberParams().get(0) instanceof UnionFunctionParam)) {
            FunctionParam singleMember = functionParam.getUnionMemberParams().get(0);
            // Create a new function param with the original parameter's properties
            FunctionParam simplifiedParam = new FunctionParam(Integer.toString(index), paramName, singleMember.getParamType());
            simplifiedParam.setParamKind(parameterSymbol.paramKind());
            simplifiedParam.setTypeSymbol(singleMember.getTypeSymbol());
            // If the union was optional (nil was one of the members), the result should also be optional
            simplifiedParam.setRequired(functionParam.isRequired());
            return Optional.of(simplifiedParam);
        }
        return Optional.of(functionParam);
    }

    private static void populateUnionMemberParams(String paramName, UnionTypeSymbol unionTypeSymbol, UnionFunctionParam functionParam, Set<String> visitedTypes) {
        int memberIndex = 0;
        java.util.Set<String> seenTypes = new java.util.HashSet<>();  // Track seen actualParamType values to avoid duplicates

        // Pre-scan for Nil type to determine if the union is optional
        // This ensures functionParam.isRequired() is correct before we process any members
        for (TypeSymbol memberTypeSymbol : unionTypeSymbol.memberTypeDescriptors()) {
            TypeDescKind actualTypeKind = Utils.getActualTypeKind(memberTypeSymbol);
            if (actualTypeKind == TypeDescKind.NIL) {
                functionParam.setRequired(false);
                break;
            }
        }

        for (TypeSymbol memberTypeSymbol : unionTypeSymbol.memberTypeDescriptors()) {
            TypeDescKind actualTypeKind = Utils.getActualTypeKind(memberTypeSymbol);
            String paramType = Utils.getParamTypeName(actualTypeKind);
            if (paramType != null) {
                if (TypeDescKind.NIL.getName().equals(paramType)) {
                    functionParam.setRequired(false);
                } else {
                    String actualParamType;
                    if (TypeDescKind.RECORD.getName().equals(paramType)) {
                        // Use record name if available, otherwise use generic "Record" + index
                        actualParamType = memberTypeSymbol.getName().orElse("Record" + memberIndex);
                    } else if (TypeDescKind.UNION.getName().equals(paramType)) {
                        // For union types, try to get the type name (e.g., type alias name)
                        // If no name is available, use generic "Union" + index
                        actualParamType = memberTypeSymbol.getName().orElse("Union" + memberIndex);
                    } else {
                        actualParamType = paramType;
                    }

                    // Skip if we've already added this type (e.g., multiple singleton strings "1", "2" both map to "string")
                    if (seenTypes.contains(actualParamType)) {
                        continue;
                    }
                    seenTypes.add(actualParamType);

                    String memberParamName = paramName + StringUtils.capitalize(actualParamType);

                    // If the member type is itself a union, create a UnionFunctionParam recursively
                    FunctionParam memberParam;
                    TypeSymbol actualMemberTypeSymbol = Utils.getActualTypeSymbol(memberTypeSymbol);
                    if (actualTypeKind == TypeDescKind.UNION && actualMemberTypeSymbol instanceof UnionTypeSymbol memberUnionSymbol) {
                        UnionFunctionParam memberUnionParam = new UnionFunctionParam(Integer.toString(memberIndex), memberParamName, paramType);
                        memberUnionParam.setTypeSymbol(memberTypeSymbol);
                        memberUnionParam.setDisplayTypeName(actualParamType);
                        // Propagate parent's optionality
                        memberUnionParam.setRequired(functionParam.isRequired());
                        populateUnionMemberParams(memberParamName, memberUnionSymbol, memberUnionParam, visitedTypes);
                        memberParam = memberUnionParam;
                    } else if (actualTypeKind == TypeDescKind.RECORD && actualMemberTypeSymbol instanceof RecordTypeSymbol recordTypeSymbol) {
                        RecordFunctionParam recordParam = new RecordFunctionParam(Integer.toString(memberIndex), memberParamName, paramType);
                        recordParam.setTypeSymbol(memberTypeSymbol);
                        recordParam.setDisplayTypeName(actualParamType);
                        recordParam.setRecordName(actualParamType);
                        // Propagate parent's optionality
                        recordParam.setRequired(functionParam.isRequired());
                        // Use original paramName as parent path for fields so they are generated as "paramName.field"
                        populateRecordFieldParams(recordParam, recordTypeSymbol, paramName, visitedTypes);
                        memberParam = recordParam;
                    } else {
                        memberParam = new FunctionParam(Integer.toString(memberIndex), memberParamName, paramType);
                        memberParam.setTypeSymbol(memberTypeSymbol);
                        memberParam.setDisplayTypeName(actualParamType);
                        // Propagate parent's optionality
                        memberParam.setRequired(functionParam.isRequired());
                    }
                    // Use sanitized parameter name in enable condition for consistency
                    String sanitizedParamName = io.ballerina.mi.util.Utils.sanitizeParamName(paramName);
                    memberParam.setEnableCondition("[{\"" + sanitizedParamName + "DataType\": \"" + actualParamType + "\"}]");
                    functionParam.addUnionMemberParam(memberParam);
                    memberIndex++;
                }
            }
        }
    }

    private static Optional<FunctionParam> createMapFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);

        MapFunctionParam mapParam = new MapFunctionParam(Integer.toString(index), paramName, TypeDescKind.MAP.getName());
        mapParam.setParamKind(parameterSymbol.paramKind());
        mapParam.setTypeSymbol(typeSymbol);

        // Set required based on parameter kind
        if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
            mapParam.setRequired(false);
        }

        // Extract key and value type information
        if (actualTypeSymbol instanceof MapTypeSymbol mapTypeSymbol) {
            Optional<TypeSymbol> valueTypeOpt = mapTypeSymbol.typeParameter();
            if (valueTypeOpt.isPresent()) {
                TypeSymbol valueType = valueTypeOpt.get();
                mapParam.setValueTypeSymbol(valueType);

                // Determine if we should render as table
                TypeDescKind valueTypeKind = Utils.getActualTypeKind(valueType);
                boolean shouldRenderAsTable = shouldMapRenderAsTable(valueType, valueTypeKind);
                mapParam.setRenderAsTable(shouldRenderAsTable);

                // If value is a record and we're rendering as table, expand its fields
                if (shouldRenderAsTable && valueTypeKind == TypeDescKind.RECORD) {
                    TypeSymbol actualValueType = Utils.getActualTypeSymbol(valueType);
                    if (actualValueType instanceof RecordTypeSymbol recordTypeSymbol) {
                        populateMapValueFields(mapParam, recordTypeSymbol);
                    }
                }
            }
        }

        return Optional.of(mapParam);
    }

    private static Optional<FunctionParam> createArrayFunctionParam(ParameterSymbol parameterSymbol, int index) {
        String paramName = parameterSymbol.getName().orElseThrow();
        TypeSymbol typeSymbol = parameterSymbol.typeDescriptor();
        TypeSymbol actualTypeSymbol = Utils.getActualTypeSymbol(typeSymbol);

        ArrayFunctionParam arrayParam = new ArrayFunctionParam(Integer.toString(index), paramName, TypeDescKind.ARRAY.getName());
        arrayParam.setParamKind(parameterSymbol.paramKind());
        arrayParam.setTypeSymbol(typeSymbol);

        // Set required based on parameter kind
        if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
            arrayParam.setRequired(false);
        }

        // Extract element type information
        if (actualTypeSymbol instanceof ArrayTypeSymbol arrayTypeSymbol) {
            TypeSymbol elementType = arrayTypeSymbol.memberTypeDescriptor();
            arrayParam.setElementTypeSymbol(elementType);

            // Determine if we should render as table
            TypeDescKind elementTypeKind = Utils.getActualTypeKind(elementType);
            boolean shouldRenderAsTable = shouldArrayRenderAsTable(elementType, elementTypeKind);
            arrayParam.setRenderAsTable(shouldRenderAsTable);

            if (shouldRenderAsTable) {
                if (elementTypeKind == TypeDescKind.RECORD) {
                    // Record array - expand its fields
                    TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                    if (actualElementType instanceof RecordTypeSymbol recordTypeSymbol) {
                        populateArrayElementFields(arrayParam, recordTypeSymbol);
                        // Enable dual input mode (Table/JSON) for record arrays
                        arrayParam.setSupportsDualInputMode(true);
                    }
                } else if (elementTypeKind == TypeDescKind.ARRAY) {
                    // 2D array (e.g., string[][], int[][]) - extract inner element type
                    arrayParam.set2DArray(true);
                    TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                    if (actualElementType instanceof ArrayTypeSymbol innerArrayType) {
                        TypeSymbol innerElementType = innerArrayType.memberTypeDescriptor();
                        arrayParam.setInnerElementTypeSymbol(innerElementType);
                    }
                } else if (elementTypeKind == TypeDescKind.UNION) {
                    // Union type array (e.g., (string|int)[]) - extract union member type names
                    arrayParam.setUnionArray(true);
                    TypeSymbol actualElementType = Utils.getActualTypeSymbol(elementType);
                    if (actualElementType instanceof UnionTypeSymbol unionType) {
                        java.util.List<String> memberNames = new java.util.ArrayList<>();
                        for (TypeSymbol member : unionType.memberTypeDescriptors()) {
                            TypeDescKind memberKind = Utils.getActualTypeKind(member);
                            if (memberKind != TypeDescKind.NIL) {
                                String memberName = Utils.getParamTypeName(memberKind);
                                if (memberName != null && !memberNames.contains(memberName)) {
                                    memberNames.add(memberName);
                                }
                            }
                        }
                        arrayParam.setUnionMemberTypeNames(memberNames);
                    }
                }
            }
        }

        return Optional.of(arrayParam);
    }

    private static boolean shouldMapRenderAsTable(TypeSymbol valueType, TypeDescKind valueTypeKind) {
        // Render as table for simple value types
        if (valueTypeKind == TypeDescKind.STRING ||
            valueTypeKind == TypeDescKind.INT ||
            valueTypeKind == TypeDescKind.BOOLEAN ||
            valueTypeKind == TypeDescKind.FLOAT ||
            valueTypeKind == TypeDescKind.DECIMAL) {
            return true;
        }

        // For record values, render as table if field count is reasonable
        if (valueTypeKind == TypeDescKind.RECORD) {
            TypeSymbol actualType = Utils.getActualTypeSymbol(valueType);
            if (actualType instanceof RecordTypeSymbol recordType) {
                int fieldCount = recordType.fieldDescriptors().size();
                return fieldCount > 0;
            }
        }

        return false;  // Complex types, nested maps, unions, etc. use JSON input
    }

    private static boolean shouldArrayRenderAsTable(TypeSymbol elementType, TypeDescKind elementTypeKind) {
        // Render as table for simple element types
        if (elementTypeKind == TypeDescKind.STRING ||
            elementTypeKind == TypeDescKind.INT ||
            elementTypeKind == TypeDescKind.BOOLEAN ||
            elementTypeKind == TypeDescKind.FLOAT ||
            elementTypeKind == TypeDescKind.DECIMAL) {
            return true;
        }

        // For record elements, render as table if field count is reasonable
        if (elementTypeKind == TypeDescKind.RECORD) {
            TypeSymbol actualType = Utils.getActualTypeSymbol(elementType);
            if (actualType instanceof RecordTypeSymbol recordType) {
                int fieldCount = recordType.fieldDescriptors().size();
                return fieldCount > 0;
            }
        }

        // 2D arrays (e.g., string[][], int[][]) - render as nested table
        if (elementTypeKind == TypeDescKind.ARRAY) {
            return true;
        }

        // Union type arrays (e.g., (string|int)[]) - render as table with type dropdown
        if (elementTypeKind == TypeDescKind.UNION) {
            return true;
        }

        return false;  // Other complex types use JSON input
    }

    private static void populateMapValueFields(MapFunctionParam mapParam, RecordTypeSymbol recordTypeSymbol) {
        Map<String, RecordFieldSymbol> fieldDescriptors = recordTypeSymbol.fieldDescriptors();
        int fieldIndex = 0;

        for (Map.Entry<String, RecordFieldSymbol> entry : fieldDescriptors.entrySet()) {
            String fieldName = entry.getKey();
            RecordFieldSymbol fieldSymbol = entry.getValue();
            TypeSymbol fieldTypeSymbol = fieldSymbol.typeDescriptor();
            TypeDescKind fieldTypeKind = Utils.getActualTypeKind(fieldTypeSymbol);
            String fieldType = Utils.getParamTypeName(fieldTypeKind);

            if (fieldType != null) {
                FunctionParam fieldParam = new FunctionParam(Integer.toString(fieldIndex), fieldName, fieldType);
                fieldParam.setTypeSymbol(fieldTypeSymbol);
                fieldParam.setRequired(!fieldSymbol.isOptional());

                fieldSymbol.documentation().ifPresent(doc ->
                    doc.description().ifPresent(fieldParam::setDescription));

                mapParam.addValueFieldParam(fieldParam);
                fieldIndex++;
            }
        }
    }

    private static void populateArrayElementFields(ArrayFunctionParam arrayParam, RecordTypeSymbol recordTypeSymbol) {
        Map<String, RecordFieldSymbol> fieldDescriptors = recordTypeSymbol.fieldDescriptors();
        int fieldIndex = 0;

        for (Map.Entry<String, RecordFieldSymbol> entry : fieldDescriptors.entrySet()) {
            String fieldName = entry.getKey();
            RecordFieldSymbol fieldSymbol = entry.getValue();
            TypeSymbol fieldTypeSymbol = fieldSymbol.typeDescriptor();
            TypeDescKind fieldTypeKind = Utils.getActualTypeKind(fieldTypeSymbol);
            String fieldType = Utils.getParamTypeName(fieldTypeKind);

            if (fieldType != null) {
                FunctionParam fieldParam = new FunctionParam(Integer.toString(fieldIndex), fieldName, fieldType);
                fieldParam.setTypeSymbol(fieldTypeSymbol);
                fieldParam.setRequired(!fieldSymbol.isOptional());

                fieldSymbol.documentation().ifPresent(doc ->
                    doc.description().ifPresent(fieldParam::setDescription));

                arrayParam.addElementFieldParam(fieldParam);
                fieldIndex++;
            }
        }
    }

    /**
     * Builds qualified parameter name by combining parent path and field name.
     *
     * @param parentPath Parent path (may be empty for top-level)
     * @param fieldName Current field name
     * @return Qualified name using dot notation (e.g., "config.host")
     */
    private static String buildQualifiedName(String parentPath, String fieldName) {
        if (parentPath == null || parentPath.isEmpty()) {
            return fieldName;
        }
        return parentPath + "." + fieldName;
    }

    static boolean isEnumUnion(UnionTypeSymbol unionTypeSymbol) {
        boolean hasNonNilMember = false;
        for (TypeSymbol member : unionTypeSymbol.memberTypeDescriptors()) {
            TypeDescKind kind = Utils.getActualTypeKind(member);
            if (kind == TypeDescKind.NIL) {
                continue;
            }
            if (kind != TypeDescKind.SINGLETON) {
                return false;
            }
            hasNonNilMember = true;
        }
        return hasNonNilMember;
    }

    /**
     * Extracts enum member values from a union of singletons.
     * Singleton signatures are string literals like {@code "queue"} — surrounding quotes are stripped.
     */
    static List<String> extractEnumValues(UnionTypeSymbol unionTypeSymbol) {
        List<String> values = new ArrayList<>();
        for (TypeSymbol member : unionTypeSymbol.memberTypeDescriptors()) {
            TypeDescKind kind = Utils.getActualTypeKind(member);
            if (kind == TypeDescKind.NIL) {
                continue;
            }
            String value = member.signature();
            // Strip surrounding double quotes from string singleton signatures
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            values.add(value);
        }
        return values;
    }

    /**
     * Creates an {@link EnumFunctionParam} for a top-level enum parameter.
     */
    private static Optional<FunctionParam> createEnumFunctionParam(ParameterSymbol parameterSymbol, int index,
                                                                   UnionTypeSymbol unionTypeSymbol) {
        String paramName = parameterSymbol.getName().orElseThrow();
        List<String> enumValues = extractEnumValues(unionTypeSymbol);
        EnumFunctionParam enumParam = new EnumFunctionParam(Integer.toString(index), paramName, enumValues);
        enumParam.setParamKind(parameterSymbol.paramKind());
        enumParam.setTypeSymbol(parameterSymbol.typeDescriptor());
        if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
            enumParam.setRequired(false);
        }
        return Optional.of(enumParam);
    }
}
