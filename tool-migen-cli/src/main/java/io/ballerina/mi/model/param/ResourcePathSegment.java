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

/**
 * Represents a segment in a resource function's path.
 * A segment can be either:
 * - Static: a literal path segment like "users", "drafts", "items"
 * - Dynamic: a path parameter like "[string userId]", "[int id]"
 *
 * This is used to construct the JVM method name for resource function invocation.
 * For example, the resource function:
 *   resource function get users/[string userId]/drafts()
 *
 * Has path segments: ["users", "[string userId]", "drafts"]
 * Which translates to JVM method name: $get$users$$userId$drafts
 */
public class ResourcePathSegment {

    private final String value;
    private final boolean isParameter;
    private final String parameterType;

    /**
     * Creates a static path segment.
     * @param value The literal path segment value (e.g., "users", "drafts")
     */
    public ResourcePathSegment(String value) {
        this.value = value;
        this.isParameter = false;
        this.parameterType = null;
    }

    /**
     * Creates a path parameter segment.
     * @param paramName The parameter name (e.g., "userId", "id")
     * @param paramType The parameter type (e.g., "string", "int")
     */
    public ResourcePathSegment(String paramName, String paramType) {
        this.value = paramName;
        this.isParameter = true;
        this.parameterType = paramType;
    }

    /**
     * Gets the segment value.
     * For static segments, this is the literal path value.
     * For parameter segments, this is the parameter name.
     */
    public String getValue() {
        return value;
    }

    /**
     * Checks if this segment is a path parameter.
     */
    public boolean isParameter() {
        return isParameter;
    }

    /**
     * Gets the parameter type for parameter segments.
     * Returns null for static segments.
     */
    public String getParameterType() {
        return parameterType;
    }

    /**
     * Gets the JVM method name component for this segment.
     * Static segments are prefixed with '$'.
     * Parameter segments are prefixed with '$$' (indicating a path parameter placeholder).
     *
     * @return The JVM method name component
     */
    public String toJvmMethodNameComponent() {
        if (isParameter) {
            // Path parameters use $$ as placeholder in XML, which is replaced by ^ in runtime
            return "$$";
        } else {
            // Static segments use $ prefix, with JVM-reserved characters encoded
            return "$" + encodeForJvm(value);
        }
    }

    /**
     * Encodes characters that are reserved in the JVM method name format.
     * Ballerina uses &XXXX (decimal Unicode) encoding for these characters.
     * Escape backslashes (e.g., \. in Ballerina identifiers) are removed first,
     * matching the Ballerina compiler's unescapeJava step before JVM encoding.
     */
    private static String encodeForJvm(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // First, unescape Ballerina escape sequences: \. -> . , \/ -> / etc.
        String unescaped = unescapeBallerinaIdentifier(name);
        StringBuilder sb = new StringBuilder(unescaped.length());
        for (int i = 0; i < unescaped.length(); i++) {
            char c = unescaped.charAt(i);
            String encoded = getJvmReservedCharEncoding(c);
            if (encoded != null) {
                sb.append("&").append(encoded);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Removes escape backslashes from Ballerina quoted identifiers, only for
     * characters that are valid IdentifierSingleEscape targets per the Ballerina spec.
     * (e.g., auth\.test means the identifier is auth.test).
     * Unsupported escape sequences (e.g., \a) are preserved as-is.
     */
    private static String unescapeBallerinaIdentifier(String name) {
        if (!name.contains("\\")) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\' && i + 1 < name.length()) {
                char next = name.charAt(i + 1);
                if (isIdentifierSingleEscape(next)) {
                    // Valid escape: skip the backslash, take the escaped character
                    sb.append(next);
                    i++;
                } else {
                    // Not a valid escape target: preserve both backslash and next char
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isIdentifierSingleEscape(char c) {
        return c == '.' || c == '/' || c == ':' || c == ';'
                || c == '<' || c == '>' || c == '[' || c == ']' || c == '\\'
                || c == '@';
    }

    private static String getJvmReservedCharEncoding(char c) {
        return switch (c) {
            case '.' -> "0046";
            case '/' -> "0047";
            case ':' -> "0058";
            case ';' -> "0059";
            case '<' -> "0060";
            case '>' -> "0062";
            case '[' -> "0091";
            case '\\' -> "0092";
            case ']' -> "0093";
            default -> null;
        };
    }

    @Override
    public String toString() {
        if (isParameter) {
            return "[" + parameterType + " " + value + "]";
        }
        return value;
    }
}
