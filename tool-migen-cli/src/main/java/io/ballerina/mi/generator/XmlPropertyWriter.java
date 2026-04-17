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

import io.ballerina.mi.model.param.ArrayFunctionParam;
import io.ballerina.mi.model.param.FunctionParam;
import io.ballerina.mi.model.param.MapFunctionParam;
import io.ballerina.mi.model.param.RecordFunctionParam;
import io.ballerina.mi.model.param.UnionFunctionParam;
import io.ballerina.mi.util.Utils;
import org.ballerinalang.diagramutil.connector.models.connector.Type;
import org.ballerinalang.diagramutil.connector.models.connector.types.PathParamType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ballerina.mi.util.Constants.RECORD;

/**
 * Writes XML property and parameter elements for connector XML templates.
 * <p>
 * This class extracts all XML property writing logic from ConnectorSerializer,
 * providing clear separation between:
 * <ul>
 *   <li><strong>Path/Query property writing</strong> — for component XML templates</li>
 *   <li><strong>Config property writing</strong> — for connection config XML templates</li>
 *   <li><strong>Record field expansion</strong> — recursively expands nested records to leaf fields</li>
 *   <li><strong>Parameter element writing</strong> — writes parameter elements for XML schemas</li>
 * </ul>
 *
 * @since 0.6.0
 */
public final class XmlPropertyWriter {

    private XmlPropertyWriter() {
        // Utility class — no instantiation
    }

    // ─── Path & Query Properties (Component XML) ──────────────────────────────

    /**
     * Writes XML property elements for a single path parameter.
     */
    static void writeComponentXmlPathProperty(PathParamType parameter, int index, StringBuilder result, boolean isFirstPathParam) {
        if (isFirstPathParam) {
            result.append(String.format("<property name=\"pathParam%d\" value=\"%s\"/>\n", index, parameter.name));
        } else {
            result.append(String.format("        <property name=\"pathParam%d\" value=\"%s\"/>\n", index, parameter.name));
        }
        result.append(String.format("        <property name=\"pathParamType%d\" value=\"%s\"/>\n", index, parameter.typeName));
    }

    /**
     * Writes XML property elements for a single query parameter.
     */
    static void writeComponentXmlQueryProperty(Type parameter, int index, StringBuilder result) {
        switch (parameter.typeName) {
            case "string":
            case "int": case "decimal": case "float":
            case "enum": case "array":
            case "boolean": case "map":
                result.append(String.format("<property name=\"queryParam%d\" value=\"%s\"/>\n", index, parameter.name));
                result.append(String.format("<property name=\"queryParamType%d\" value=\"%s\"/>\n", index, parameter.typeName));
                break;
            case "union":
                result.append(String.format("<property name=\"queryParam%d\" value=\"%s\"/>\n", index, parameter.name));
                result.append(String.format("<property name=\"queryParamType%d\" value=\"%s\"/>\n", index, parameter.typeName));
                result.append(String.format("<property name=\"queryParamDataType%d\" value=\"%s\"/>\n", index,
                        Utils.sanitizeParamName(parameter.name) + "DataType"));
                break;
            case "record":
                result.append(String.format("<property name=\"queryParam%d\" value=\"%s\"/>\n", index, parameter.name));
                result.append(String.format("<property name=\"queryParamType%d\" value=\"%s\"/>\n", index, parameter.typeName));
                if (null != parameter.getTypeInfo()) {
                    result.append(String.format("<property name=\"queryParamRecordName%d\" value=\"%s\"/>\n", index, parameter.typeInfo.name));
                    result.append(String.format("<property name=\"queryParamRecordModule%d\" value=\"%s\"/>\n", index, parameter.typeInfo.moduleName));
                    result.append(String.format("<property name=\"queryParamRecordOrg%d\" value=\"%s\"/>\n", index, parameter.typeInfo.orgName));
                    result.append(String.format("<property name=\"queryParamRecordVersion%d\" value=\"%s\"/>\n", index, parameter.typeInfo.version));
                }
                break;
        }
    }

    // ─── Config Properties (Connection XML) ───────────────────────────────────

    /**
     * Writes XML property elements for a single config/init parameter.
     */
    static void writeConfigXmlProperty(Type parameter, int index, String connectionType, StringBuilder result) {
        switch (parameter.typeName) {
            case "string":
            case "int": case "decimal": case "float":
            case "boolean": case "map":
                result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>\n", connectionType, index, parameter.name));
                result.append(String.format("<property name=\"%s_paramType%d\" value=\"%s\"/>\n", connectionType, index, parameter.typeName));
                break;
            case "record":
                result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>\n", connectionType, index, parameter.name));
                result.append(String.format("<property name=\"%s_paramType%d\" value=\"%s\"/>\n", connectionType, index, parameter.typeName));
                if (parameter.typeInfo != null) {
                    if (parameter.typeInfo.name != null) {
                        result.append(String.format("<property name=\"%s_param%d_recordName\" value=\"%s\"/>\n", connectionType, index, parameter.typeInfo.name));
                    }
                    if (parameter.typeInfo.moduleName != null) {
                        result.append(String.format("<property name=\"%s_param%d_recordModule\" value=\"%s\"/>\n", connectionType, index, parameter.typeInfo.moduleName));
                    }
                    if (parameter.typeInfo.orgName != null) {
                        result.append(String.format("<property name=\"%s_param%d_recordOrg\" value=\"%s\"/>\n", connectionType, index, parameter.typeInfo.orgName));
                    }
                    if (parameter.typeInfo.version != null) {
                        result.append(String.format("<property name=\"%s_param%d_recordVersion\" value=\"%s\"/>\n", connectionType, index, parameter.typeInfo.version));
                    }
                }
                break;
            case "union":
                result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>\n", connectionType, index, parameter.name));
                result.append(String.format("<property name=\"%s_paramType%d\" value=\"%s\"/>\n", connectionType, index, parameter.typeName));
                result.append(String.format("<property name=\"%s_dataType%d\" value=\"%s\"/>\n", connectionType, index,
                        Utils.sanitizeParamName(parameter.name) + "DataType"));
                break;
            case "enum":
                result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>\n", connectionType, index, parameter.name));
                result.append(String.format("<property name=\"%s_paramType%d\" value=\"%s\"/>\n", connectionType, index, parameter.typeName));
                break;
            case "array":
                // TODO: Generate properties for array
        }
    }

    // ─── FunctionParam Properties (XML) ───────────────────────────────────────

    /**
     * Writes XML property elements for function params, expanding record fields with proper indexing.
     * For record parameters, writes the record parameter itself with type="record" and then
     * writes the record fields with the record parameter name prefix.
     */
    static void writeXmlParamProperties(FunctionParam functionParam, String connectionType,
                                        StringBuilder result, int[] indexHolder, boolean[] isFirst) {
        if (functionParam.isTypeDescriptor() && !(functionParam instanceof UnionFunctionParam)) {
            return;
        }

        if (functionParam instanceof RecordFunctionParam recordParam && !recordParam.getRecordFieldParams().isEmpty()) {
            if (!isFirst[0]) {
                result.append("\n        ");
            }
            result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>",
                    connectionType, indexHolder[0], recordParam.getValue()));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    connectionType, indexHolder[0], RECORD));
            result.append(String.format("\n        <property name=\"%s_param%d_recordName\" value=\"%s\"/>",
                    connectionType, indexHolder[0], recordParam.getRecordName()));
            if (recordParam.getRecordOrg() != null) {
                result.append(String.format("\n        <property name=\"%s_param%d_recordOrg\" value=\"%s\"/>",
                        connectionType, indexHolder[0], recordParam.getRecordOrg()));
            }
            if (recordParam.getRecordModule() != null) {
                result.append(String.format("\n        <property name=\"%s_param%d_recordModule\" value=\"%s\"/>",
                        connectionType, indexHolder[0], recordParam.getRecordModule()));
            }
            isFirst[0] = false;
            indexHolder[0]++;

            int[] fieldIndexHolder = {0};
            String recordParamName = recordParam.getValue();
            for (FunctionParam fieldParam : recordParam.getRecordFieldParams()) {
                writeRecordFieldParamProperties(fieldParam, connectionType, recordParamName, result, fieldIndexHolder);
            }
        } else if (functionParam instanceof UnionFunctionParam unionParam && !unionParam.isTypeDescriptor()) {
            // Init-context union parameter (e.g. DestinationConfig|map<string>).
            // Write: paramN / paramTypeN / dataTypeN (discriminator key) then, for each union member,
            // write the member pointer property and — for record members — all flattened field properties.
            if (!isFirst[0]) {
                result.append("\n        ");
            }
            int currentIndex = indexHolder[0];
            String sanitizedParamName = Utils.sanitizeParamName(unionParam.getValue());

            result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>",
                    connectionType, currentIndex, unionParam.getValue()));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    connectionType, currentIndex, unionParam.getParamType()));
            result.append(String.format("\n        <property name=\"%s_dataType%d\" value=\"%s\"/>",
                    connectionType, currentIndex, sanitizedParamName + "DataType"));
            isFirst[0] = false;
            indexHolder[0]++;

            for (FunctionParam memberParam : unionParam.getUnionMemberParams()) {
                if (memberParam instanceof RecordFunctionParam recordMemberParam) {
                    String memberTypeName = recordMemberParam.getDisplayTypeName();
                    if (memberTypeName == null || memberTypeName.isEmpty()) {
                        memberTypeName = recordMemberParam.getRecordName();
                    }
                    if (memberTypeName == null || memberTypeName.isEmpty()) {
                        continue;
                    }
                    String capitalizedTypeName = org.apache.commons.lang3.StringUtils.capitalize(memberTypeName);
                    String recordParamName = sanitizedParamName + "_" + memberTypeName;
                    // e.g. SAP_JCO_CLIENT_param0UnionDestinationConfig = configurations_DestinationConfig
                    result.append(String.format("\n        <property name=\"%s_param%dUnion%s\" value=\"%s\"/>",
                            connectionType, currentIndex, capitalizedTypeName, recordParamName));
                    int[] fieldIndexHolder = {0};
                    for (FunctionParam fieldParam : recordMemberParam.getRecordFieldParams()) {
                        writeRecordFieldParamPropertiesWithUnionMember(fieldParam, connectionType, recordParamName,
                                result, fieldIndexHolder, memberTypeName);
                    }
                } else if ("map".equals(memberParam.getParamType())) {
                    // e.g. SAP_JCO_CLIENT_param0UnionMap = configMap
                    // Value must match the <parameter> name written by writeXmlParameterElements
                    // (sanitizedParamName + "Map") and the uischema field name.
                    result.append(String.format("\n        <property name=\"%s_param%dUnionMap\" value=\"%s\"/>",
                            connectionType, currentIndex, sanitizedParamName + "Map"));
                } else {
                    // Primitive or array union member (string, int, boolean, float, decimal, array).
                    // Emit a pointer so ParamHandler.getUnionParameter() can resolve the selected member
                    // value at runtime: e.g. PREFIX_param0UnionString = configurations_string.
                    String memberType = memberParam.getParamType();
                    if (memberType != null && !memberType.isEmpty()) {
                        String capitalizedType = org.apache.commons.lang3.StringUtils.capitalize(memberType);
                        result.append(String.format("\n        <property name=\"%s_param%dUnion%s\" value=\"%s_%s\"/>",
                                connectionType, currentIndex, capitalizedType, sanitizedParamName, memberType));
                    }
                }
            }
        } else {
            if (!isFirst[0]) {
                result.append("\n        ");
            }
            int currentIndex = indexHolder[0];
            result.append(String.format("<property name=\"%s_param%d\" value=\"%s\"/>",
                    connectionType, currentIndex, functionParam.getValue()));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    connectionType, currentIndex, functionParam.getParamType()));
            
            if (functionParam instanceof RecordFunctionParam recordParam) {
                result.append(String.format("\n        <property name=\"%s_param%d_recordName\" value=\"%s\"/>",
                        connectionType, currentIndex, recordParam.getRecordName()));
                if (recordParam.getRecordModule() != null) {
                    result.append(String.format("\n        <property name=\"%s_param%d_recordModule\" value=\"%s\"/>",
                            connectionType, currentIndex, recordParam.getRecordModule()));
                }
                if (recordParam.getRecordOrg() != null) {
                    result.append(String.format("\n        <property name=\"%s_param%d_recordOrg\" value=\"%s\"/>",
                            connectionType, currentIndex, recordParam.getRecordOrg()));
                }
                // version is often null/empty for local modules
                
                // Recursively write properties for record fields
                int[] fieldIndexHolder = {0};
                String recordParamName = recordParam.getValue();
                for (FunctionParam fieldParam : recordParam.getRecordFieldParams()) {
                    writeRecordFieldParamProperties(fieldParam, connectionType, recordParamName, result, fieldIndexHolder);
                }
            }
            
            isFirst[0] = false;
            indexHolder[0]++;
        }
    }

    // ─── Record Field Expansion (Config context) ──────────────────────────────

    /**
     * Writes XML property elements for record field parameters with connectionType prefix.
     * Delegates to the union-member-aware variant with null unionMemberType.
     */
    static void writeRecordFieldParamProperties(FunctionParam fieldParam, String connectionType,
                                                String recordParamName, StringBuilder result,
                                                int[] fieldIndexHolder) {
        writeRecordFieldParamPropertiesWithUnionMember(fieldParam, connectionType, recordParamName, result,
                fieldIndexHolder, null);
    }

    /**
     * Writes XML properties for record field parameters, with support for tracking union member types.
     * When a field belongs to a union member record, the unionMemberType parameter indicates which
     * union member type this field belongs to, enabling runtime reconstruction of the correct record type.
     */
    static void writeRecordFieldParamPropertiesWithUnionMember(FunctionParam fieldParam, String connectionType,
                                                               String recordParamName, StringBuilder result,
                                                               int[] fieldIndexHolder, String unionMemberType) {
        writeRecordFieldParamPropertiesWithUnionMember(fieldParam, connectionType, recordParamName, result, fieldIndexHolder, unionMemberType, new java.util.HashSet<>());
    }

    static void writeRecordFieldParamPropertiesWithUnionMember(FunctionParam fieldParam, String connectionType,
                                                               String recordParamName, StringBuilder result,
                                                               int[] fieldIndexHolder, String unionMemberType,
                                                               java.util.Set<String> visitedTypes) {
        if (fieldParam instanceof RecordFunctionParam nestedRecordParam && !nestedRecordParam.getRecordFieldParams().isEmpty()) {
            for (FunctionParam nestedFieldParam : nestedRecordParam.getRecordFieldParams()) {
                writeRecordFieldParamPropertiesWithUnionMember(nestedFieldParam, connectionType, recordParamName,
                        result, fieldIndexHolder, unionMemberType, visitedTypes);
            }
        } else if (fieldParam instanceof UnionFunctionParam unionFieldParam) {
            result.append("\n        ");
            String fieldValue = fieldParam.getValue();
            result.append(String.format("<property name=\"%s_%s_param%d\" value=\"%s\"/>",
                    connectionType, recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_%s_paramType%d\" value=\"%s\"/>",
                    connectionType, recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));
            String sanitizedParamName = Utils.sanitizeParamName(fieldValue);
            
            if (!unionFieldParam.isTypeDescriptor()) {
                result.append(String.format("\n        <property name=\"%s_%s_dataType%d\" value=\"%s\"/>",
                        connectionType, recordParamName, fieldIndexHolder[0],
                        sanitizedParamName + "DataType"));
            }

            if (unionMemberType != null) {
                result.append(String.format("\n        <property name=\"%s_%s_unionMember%d\" value=\"%s\"/>",
                        connectionType, recordParamName, fieldIndexHolder[0], unionMemberType));
            }

            fieldIndexHolder[0]++;

            if (!unionFieldParam.isTypeDescriptor()) {
                for (FunctionParam memberParam : unionFieldParam.getUnionMemberParams()) {
                    if (memberParam instanceof RecordFunctionParam recordMemberParam) {
                        String memberTypeName = recordMemberParam.getDisplayTypeName();
                        if (memberTypeName == null || memberTypeName.isEmpty()) {
                            memberTypeName = recordMemberParam.getRecordName();
                        }
                        if (memberTypeName != null && visitedTypes.contains(memberTypeName)) {
                            continue; // Prevent infinite recursion
                        }
                        if (memberTypeName != null) {
                            visitedTypes.add(memberTypeName);
                        }
                        for (FunctionParam recordField : recordMemberParam.getRecordFieldParams()) {
                            writeRecordFieldParamPropertiesWithUnionMember(recordField, connectionType, recordParamName,
                                    result, fieldIndexHolder, memberTypeName, visitedTypes);
                        }
                        if (memberTypeName != null) {
                            visitedTypes.remove(memberTypeName);
                        }
                    }
                }
            }
        } else {
            result.append("\n        ");
            String fieldValue = fieldParam.getValue();
            result.append(String.format("<property name=\"%s_%s_param%d\" value=\"%s\"/>",
                    connectionType, recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_%s_paramType%d\" value=\"%s\"/>",
                    connectionType, recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));

            if (unionMemberType != null) {
                result.append(String.format("\n        <property name=\"%s_%s_unionMember%d\" value=\"%s\"/>",
                        connectionType, recordParamName, fieldIndexHolder[0], unionMemberType));
            }

            fieldIndexHolder[0]++;
        }
    }

    // ─── Record Field Expansion (Function context — no connectionType prefix) ─

    /**
     * Writes XML property elements for function record field parameters without connectionType prefix.
     * Used for regular function parameters (not config/init).
     * Pattern: {recordParamName}_param{fieldIndex} / {recordParamName}_paramType{fieldIndex}
     */
    static void writeFunctionRecordFieldProperties(FunctionParam fieldParam, String recordParamName,
                                                   StringBuilder result, int[] fieldIndexHolder) {
        writeFunctionRecordFieldProperties(fieldParam, recordParamName, result, fieldIndexHolder, new java.util.HashSet<>());
    }

    static void writeFunctionRecordFieldProperties(FunctionParam fieldParam, String recordParamName,
                                                   StringBuilder result, int[] fieldIndexHolder, java.util.Set<String> visitedTypes) {
        if (fieldParam instanceof RecordFunctionParam nestedRecordParam && !nestedRecordParam.getRecordFieldParams().isEmpty()) {
            for (FunctionParam nestedFieldParam : nestedRecordParam.getRecordFieldParams()) {
                writeFunctionRecordFieldProperties(nestedFieldParam, recordParamName, result, fieldIndexHolder, visitedTypes);
            }
        } else if (fieldParam instanceof UnionFunctionParam unionFieldParam) {
            String fieldValue = fieldParam.getValue();
            result.append(String.format("\n        <property name=\"%s_param%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));
            String sanitizedParamName = Utils.sanitizeParamName(fieldValue);
            result.append(String.format("\n        <property name=\"%s_dataType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0],
                    sanitizedParamName + "DataType"));
            fieldIndexHolder[0]++;

            for (FunctionParam memberParam : unionFieldParam.getUnionMemberParams()) {
                if (memberParam instanceof RecordFunctionParam recordMemberParam) {
                    String memberTypeName = recordMemberParam.getDisplayTypeName();
                    if (memberTypeName == null || memberTypeName.isEmpty()) {
                        memberTypeName = recordMemberParam.getRecordName();
                    }
                    if (memberTypeName != null && visitedTypes.contains(memberTypeName)) {
                        continue;
                    }
                    if (memberTypeName != null) {
                        visitedTypes.add(memberTypeName);
                    }
                    for (FunctionParam recordField : recordMemberParam.getRecordFieldParams()) {
                        writeFunctionRecordFieldPropertiesWithUnionMember(recordField, recordParamName,
                                result, fieldIndexHolder, memberTypeName, visitedTypes);
                    }
                    if (memberTypeName != null) {
                        visitedTypes.remove(memberTypeName);
                    }
                }
            }
        } else if (fieldParam instanceof ArrayFunctionParam arrayFieldParam) {
            // Handle array fields - add element type and record field names for table-to-JSON conversion
            String fieldValue = fieldParam.getValue();
            String sanitizedFieldValue = Utils.sanitizeParamName(fieldValue);
            result.append(String.format("\n        <property name=\"%s_param%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));

            // Add array element type
            String elementTypeName = arrayFieldParam.getArrayElementTypeName();
            if (elementTypeName != null && !elementTypeName.isEmpty()) {
                result.append(String.format("\n        <property name=\"%s_arrayElementType%d\" value=\"%s\"/>",
                        recordParamName, fieldIndexHolder[0], elementTypeName));
            }

            // Add record field names for arrays of records (for table-to-JSON conversion)
            List<FunctionParam> elementFields = arrayFieldParam.getElementFieldParams();
            if (elementFields != null && !elementFields.isEmpty()) {
                String fieldNames = elementFields.stream()
                        .map(FunctionParam::getValue)
                        .collect(Collectors.joining(","));
                result.append(String.format("\n        <property name=\"%s_arrayRecordFields%d\" value=\"%s\"/>",
                        recordParamName, fieldIndexHolder[0], fieldNames));

                // Add dual input mode properties for nested record arrays (Table/JSON selector)
                result.append(String.format("\n        <property name=\"%s_param%d_dualMode\" value=\"true\"/>",
                        recordParamName, fieldIndexHolder[0]));
                result.append(String.format("\n        <property name=\"%s_param%d_inputModeField\" value=\"%sInputMode\"/>",
                        recordParamName, fieldIndexHolder[0], sanitizedFieldValue));
                result.append(String.format("\n        <property name=\"%s_param%d_jsonField\" value=\"%sJson\"/>",
                        recordParamName, fieldIndexHolder[0], sanitizedFieldValue));
            }

            fieldIndexHolder[0]++;
        } else {
            String fieldValue = fieldParam.getValue();
            result.append(String.format("\n        <property name=\"%s_param%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));
            fieldIndexHolder[0]++;
        }
    }

    /**
     * Writes XML property elements for function record field parameters with union member tracking.
     */
    static void writeFunctionRecordFieldPropertiesWithUnionMember(FunctionParam fieldParam, String recordParamName,
                                                                  StringBuilder result, int[] fieldIndexHolder,
                                                                  String unionMemberType) {
        writeFunctionRecordFieldPropertiesWithUnionMember(fieldParam, recordParamName, result, fieldIndexHolder, unionMemberType, new java.util.HashSet<>());
    }

    static void writeFunctionRecordFieldPropertiesWithUnionMember(FunctionParam fieldParam, String recordParamName,
                                                                  StringBuilder result, int[] fieldIndexHolder,
                                                                  String unionMemberType, java.util.Set<String> visitedTypes) {
        if (fieldParam instanceof RecordFunctionParam nestedRecordParam && !nestedRecordParam.getRecordFieldParams().isEmpty()) {
            for (FunctionParam nestedFieldParam : nestedRecordParam.getRecordFieldParams()) {
                writeFunctionRecordFieldPropertiesWithUnionMember(nestedFieldParam, recordParamName,
                        result, fieldIndexHolder, unionMemberType, visitedTypes);
            }
        } else if (fieldParam instanceof UnionFunctionParam unionFieldParam) {
            String fieldValue = fieldParam.getValue();
            result.append(String.format("\n        <property name=\"%s_param%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));
            String sanitizedParamName = Utils.sanitizeParamName(fieldValue);
            
            if (!unionFieldParam.isTypeDescriptor()) {
                result.append(String.format("\n        <property name=\"%s_dataType%d\" value=\"%s\"/>",
                        recordParamName, fieldIndexHolder[0],
                        sanitizedParamName + "DataType"));
            }
            if (unionMemberType != null) {
                result.append(String.format("\n        <property name=\"%s_unionMember%d\" value=\"%s\"/>",
                        recordParamName, fieldIndexHolder[0], unionMemberType));
            }
            fieldIndexHolder[0]++;

            if (!unionFieldParam.isTypeDescriptor()) {
                for (FunctionParam memberParam : unionFieldParam.getUnionMemberParams()) {
                    if (memberParam instanceof RecordFunctionParam recordMemberParam) {
                        String memberTypeName = recordMemberParam.getDisplayTypeName();
                        if (memberTypeName == null || memberTypeName.isEmpty()) {
                            memberTypeName = recordMemberParam.getRecordName();
                        }
                        if (memberTypeName != null && visitedTypes.contains(memberTypeName)) {
                            continue; // Prevent infinite recursion
                        }
                        if (memberTypeName != null) {
                            visitedTypes.add(memberTypeName);
                        }
                        for (FunctionParam recordField : recordMemberParam.getRecordFieldParams()) {
                            writeFunctionRecordFieldPropertiesWithUnionMember(recordField, recordParamName,
                                    result, fieldIndexHolder, memberTypeName, visitedTypes);
                        }
                        if (memberTypeName != null) {
                            visitedTypes.remove(memberTypeName);
                        }
                    }
                }
            }
        } else {
            String fieldValue = fieldParam.getValue();
            result.append(String.format("\n        <property name=\"%s_param%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldValue));
            result.append(String.format("\n        <property name=\"%s_paramType%d\" value=\"%s\"/>",
                    recordParamName, fieldIndexHolder[0], fieldParam.getParamType()));
            if (unionMemberType != null) {
                result.append(String.format("\n        <property name=\"%s_unionMember%d\" value=\"%s\"/>",
                        recordParamName, fieldIndexHolder[0], unionMemberType));
            }
            fieldIndexHolder[0]++;
        }
    }

    // ─── Parameter Elements (XML) ─────────────────────────────────────────────

    /**
     * Writes XML parameter elements for function params, expanding record fields as separate parameters.
     * Also expands UnionFunctionParams into their discriminant (DataType) and member fields.
     */
    static void writeXmlParameterElements(FunctionParam functionParam, StringBuilder result,
                                          boolean[] isFirst, Set<String> processedParams) {
        writeXmlParameterElements(functionParam, result, isFirst, processedParams, new HashSet<>(), false);
    }

    static void writeXmlParameterElements(FunctionParam functionParam, StringBuilder result,
                                          boolean[] isFirst, Set<String> processedParams, boolean isConfigContext) {
        writeXmlParameterElements(functionParam, result, isFirst, processedParams, new HashSet<>(), isConfigContext);
    }

    static void writeXmlParameterElements(FunctionParam functionParam, StringBuilder result,
                                          boolean[] isFirst, Set<String> processedParams, Set<String> visitedTypes,
                                          boolean isConfigContext) {
        if (functionParam instanceof RecordFunctionParam recordParam && !recordParam.getRecordFieldParams().isEmpty()) {
            String typeKey = recordParam.getDisplayTypeName();
            if (typeKey == null || typeKey.isEmpty()) {
                typeKey = recordParam.getRecordName();
            }
            if (typeKey != null && visitedTypes.contains(typeKey)) {
                return;
            }
            if (typeKey != null) {
                visitedTypes.add(typeKey);
            }
            // For optional records in config context, add enable_ checkbox parameter
            if (isConfigContext && !recordParam.isRequired()) {
                String sanitizedParamName = Utils.sanitizeParamName(recordParam.getValue());
                String enableParamName = "enable_" + sanitizedParamName;
                if (!processedParams.contains(enableParamName)) {
                    if (!isFirst[0]) result.append("\n    ");
                    result.append(String.format("<parameter name=\"%s\" description=\"\"/>", enableParamName));
                    isFirst[0] = false;
                    processedParams.add(enableParamName);
                }
            }
            // Expand record fields as separate parameters
            for (FunctionParam fieldParam : recordParam.getRecordFieldParams()) {
                writeXmlParameterElements(fieldParam, result, isFirst, processedParams, visitedTypes, isConfigContext);
            }
            if (typeKey != null) {
                visitedTypes.remove(typeKey);
            }
        } else if (functionParam instanceof UnionFunctionParam unionParam) {
            String sanitizedParamName = Utils.sanitizeParamName(unionParam.getValue());

            // Expand Union: Add DataType param and expand members
            String dataTypeParamName = sanitizedParamName + "DataType";

            if (!processedParams.contains(dataTypeParamName)) {
                String description = functionParam.getDescription() != null ? functionParam.getDescription() : "";
                if (!isFirst[0]) {
                    result.append("\n    ");
                }
                result.append(String.format("<parameter name=\"%s\" description=\"%s\"/>",
                        dataTypeParamName, escapeXml(description)));
                isFirst[0] = false;
                processedParams.add(dataTypeParamName);
            }

            // Expand all union members properties
            for (FunctionParam member : unionParam.getUnionMemberParams()) {
                writeXmlParameterElements(member, result, isFirst, processedParams, visitedTypes, isConfigContext);
            }
        } else if (functionParam instanceof ArrayFunctionParam arrayParam) {
            // Handle array fields - add dual mode parameters if supported
            String sanitizedParamName = Utils.sanitizeParamName(functionParam.getValue());

            // Deduplicate: Don't write if already written
            if (processedParams.contains(sanitizedParamName)) {
                return;
            }

            // For optional renderAsTable arrays in config context, add enable_ checkbox parameter
            if (isConfigContext && !arrayParam.isRequired() && arrayParam.isRenderAsTable()) {
                String enableParamName = "enable_" + sanitizedParamName;
                if (!processedParams.contains(enableParamName)) {
                    if (!isFirst[0]) result.append("\n    ");
                    result.append(String.format("<parameter name=\"%s\" description=\"\"/>", enableParamName));
                    isFirst[0] = false;
                    processedParams.add(enableParamName);
                }
            }

            // Add InputMode and Json parameters for dual mode arrays (record arrays)
            List<FunctionParam> elementFields = arrayParam.getElementFieldParams();
            if (elementFields != null && !elementFields.isEmpty()) {
                // Add InputMode parameter
                String inputModeParamName = sanitizedParamName + "InputMode";
                if (!processedParams.contains(inputModeParamName)) {
                    if (!isFirst[0]) {
                        result.append("\n    ");
                    }
                    result.append(String.format("<parameter name=\"%s\" description=\"Input mode for %s: Table or JSON\"/>",
                            inputModeParamName, functionParam.getValue()));
                    isFirst[0] = false;
                    processedParams.add(inputModeParamName);
                }

                // Add Json parameter
                String jsonParamName = sanitizedParamName + "Json";
                if (!processedParams.contains(jsonParamName)) {
                    if (!isFirst[0]) {
                        result.append("\n    ");
                    }
                    result.append(String.format("<parameter name=\"%s\" description=\"JSON array input for %s\"/>",
                            jsonParamName, functionParam.getValue()));
                    isFirst[0] = false;
                    processedParams.add(jsonParamName);
                }
            }

            // Add the array parameter itself
            String description = functionParam.getDescription() != null ? functionParam.getDescription() : "";
            if (!isFirst[0]) {
                result.append("\n    ");
            }
            result.append(String.format("<parameter name=\"%s\" description=\"%s\"/>",
                    sanitizedParamName, escapeXml(description)));
            isFirst[0] = false;
            processedParams.add(sanitizedParamName);
        } else {
            // Generate single parameter element (covers MapFunctionParam and plain FunctionParam)
            String sanitizedParamName = Utils.sanitizeParamName(functionParam.getValue());

            // Deduplicate: Don't write if already written
            if (processedParams.contains(sanitizedParamName)) {
                return;
            }

            // For optional renderAsTable maps in config context, add enable_ checkbox parameter
            if (isConfigContext && !functionParam.isRequired()
                    && functionParam instanceof MapFunctionParam mapParam && mapParam.isRenderAsTable()) {
                String enableParamName = "enable_" + sanitizedParamName;
                if (!processedParams.contains(enableParamName)) {
                    if (!isFirst[0]) result.append("\n    ");
                    result.append(String.format("<parameter name=\"%s\" description=\"\"/>", enableParamName));
                    isFirst[0] = false;
                    processedParams.add(enableParamName);
                }
            }

            String description = functionParam.getDescription() != null ? functionParam.getDescription() : "";
            if (!isFirst[0]) {
                result.append("\n    ");
            }
            result.append(String.format("<parameter name=\"%s\" description=\"%s\"/>",
                    sanitizedParamName, escapeXml(description)));
            isFirst[0] = false;
            processedParams.add(sanitizedParamName);
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Escapes XML special characters for use in XML attribute values.
     * Note: Single quotes (') are NOT escaped because:
     * 1. XML attributes in templates are enclosed in double quotes, so single quotes are safe
     * 2. &amp;apos; is not universally supported (XML 1.0 doesn't include it, only XML 1.1)
     * 3. Ballerina uses single quotes in syntax which should remain as-is
     */
    static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Updates the enable conditions of union members when the union parameter itself is renamed.
     * This ensures that the member conditions (which reference the union param name) remain valid.
     */
    static void updateEnableConditionsForUnionMembers(UnionFunctionParam unionParam, String oldName, String newName) {
        String oldConditionKey = Utils.sanitizeParamName(oldName) + "DataType";
        String newConditionKey = Utils.sanitizeParamName(newName) + "DataType";

        for (FunctionParam member : unionParam.getUnionMemberParams()) {
            String condition = member.getEnableCondition();
            if (condition != null && !condition.isEmpty()) {
                member.setEnableCondition(condition.replace("\"" + oldConditionKey + "\"", "\"" + newConditionKey + "\""));
            }
        }
    }
}
