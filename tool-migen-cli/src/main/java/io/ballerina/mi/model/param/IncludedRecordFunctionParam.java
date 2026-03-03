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

package io.ballerina.mi.model.param;

import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.mi.util.Utils;

/**
 * Represents an included record function parameter (using the * spread syntax).
 * <p>
 * In Ballerina, included record parameters allow a function to accept all fields
 * of a record type as individual named parameters at the call site:
 * </p>
 * <pre>
 * // Declaration with named record type
 * function foo(*Options options) { ... }
 *
 * // Declaration with open record (rest field)
 * function bar(*record {|anydata...;|} properties) { ... }
 *
 * // Call with named arguments
 * foo(verbose = true, timeout = 30);
 * </pre>
 * <p>
 * For open records with rest fields (e.g., {@code record {|anydata...;|}}),
 * the parameter is rendered as a table UI allowing users to add key-value pairs.
 * </p>
 *
 * @since 0.7.0
 */
public class IncludedRecordFunctionParam extends RecordFunctionParam {

    private boolean isOpenRecord;
    private TypeSymbol restTypeSymbol;
    private TypeDescKind restTypeKind;
    private String restTypeName;

    public IncludedRecordFunctionParam(String index, String name, String paramType) {
        super(index, name, paramType);
        this.isOpenRecord = false;
    }

    /**
     * Returns true to indicate this is an included record parameter.
     *
     * @return true, as this represents an included record parameter
     */
    public boolean isIncludedRecord() {
        return true;
    }

    /**
     * Returns true if this is an open record with rest fields.
     * Open records should be rendered as tables in the UI.
     *
     * @return true if this record has rest field type
     */
    public boolean isOpenRecord() {
        return isOpenRecord;
    }

    public void setOpenRecord(boolean openRecord) {
        isOpenRecord = openRecord;
    }

    /**
     * Gets the rest field type symbol for open records.
     *
     * @return the rest type symbol, or null if not an open record
     */
    public TypeSymbol getRestTypeSymbol() {
        return restTypeSymbol;
    }

    public void setRestTypeSymbol(TypeSymbol restTypeSymbol) {
        this.restTypeSymbol = restTypeSymbol;
        if (restTypeSymbol != null) {
            this.restTypeKind = Utils.getActualTypeKind(restTypeSymbol);
            this.restTypeName = Utils.getParamTypeName(this.restTypeKind);
        }
    }

    public TypeDescKind getRestTypeKind() {
        return restTypeKind;
    }

    public String getRestTypeName() {
        return restTypeName;
    }

    @Override
    public void clearTypeSymbol() {
        super.clearTypeSymbol();
        this.restTypeSymbol = null;
    }
}
