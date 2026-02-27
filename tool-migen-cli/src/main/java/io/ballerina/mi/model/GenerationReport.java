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

package io.ballerina.mi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Captures the status of a connector generation run.
 * <p>
 * Populated by the connector/module analyzers during analysis and written to a
 * {@code generation-report.log} file by the serializer after artifact generation.
 * The report lists all clients that were found, which operations were included in
 * the connector, and which were skipped along with the reason.
 */
public class GenerationReport {

    /**
     * An operation that was successfully included in the generated connector.
     *
     * @param originalName the Ballerina method/function name
     * @param synapseName  the MI connector operation name (may differ from originalName for
     *                     resource functions or when deduplication suffixes are applied)
     * @param functionType the type of the function (INIT, REMOTE, RESOURCE, FUNCTION)
     */
    public record IncludedOperation(String originalName, String synapseName, String functionType) {
    }

    /**
     * An operation that was skipped during connector generation.
     *
     * @param name   the Ballerina method/function name
     * @param reason human-readable explanation of why the operation was skipped
     */
    public record SkippedOperation(String name, String reason) {
    }

    /**
     * Status report for a single client class (connection).
     */
    public static class ClientReport {
        private final String clientClassName;
        private final String connectionType;
        private final List<IncludedOperation> includedOperations = new ArrayList<>();
        private final List<SkippedOperation> skippedOperations = new ArrayList<>();

        public ClientReport(String clientClassName, String connectionType) {
            this.clientClassName = clientClassName;
            this.connectionType = connectionType;
        }

        public void addIncluded(String originalName, String synapseName, String functionType) {
            includedOperations.add(new IncludedOperation(originalName, synapseName, functionType));
        }

        public void addSkipped(String name, String reason) {
            skippedOperations.add(new SkippedOperation(name, reason));
        }

        public String getClientClassName() {
            return clientClassName;
        }

        public String getConnectionType() {
            return connectionType;
        }

        public List<IncludedOperation> getIncludedOperations() {
            return includedOperations;
        }

        public List<SkippedOperation> getSkippedOperations() {
            return skippedOperations;
        }
    }

    private final String connectorName;
    private final String orgName;
    private final String version;
    private final List<ClientReport> clientReports = new ArrayList<>();

    public GenerationReport(String connectorName, String orgName, String version) {
        this.connectorName = connectorName;
        this.orgName = orgName;
        this.version = version;
    }

    public void addClientReport(ClientReport report) {
        clientReports.add(report);
    }

    public List<ClientReport> getClientReports() {
        return clientReports;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Formats the report as a human-readable text block suitable for console output
     * and writing to {@code generation-report.log}.
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("       Connector Generation Status Report        \n");
        sb.append("=================================================\n");
        sb.append("Connector : ").append(orgName).append("/").append(connectorName)
                .append(" v").append(version).append("\n\n");

        int totalIncluded = 0;
        int totalSkipped = 0;

        for (ClientReport cr : clientReports) {
            sb.append("Client: ").append(cr.getClientClassName());
            if (cr.getConnectionType() != null) {
                sb.append(" (").append(cr.getConnectionType()).append(")");
            }
            sb.append("\n");
            sb.append("-------------------------------------------------\n");

            List<IncludedOperation> included = cr.getIncludedOperations();
            sb.append("  Included operations (").append(included.size()).append("):\n");
            for (IncludedOperation op : included) {
                if (Objects.equals(op.synapseName(), op.originalName())) {
                    sb.append("    - ").append(op.originalName())
                            .append(" [").append(op.functionType()).append("]\n");
                } else {
                    sb.append("    - ").append(op.originalName())
                            .append(" -> ").append(op.synapseName())
                            .append(" [").append(op.functionType()).append("]\n");
                }
            }

            List<SkippedOperation> skipped = cr.getSkippedOperations();
            if (!skipped.isEmpty()) {
                sb.append("\n  Skipped operations (").append(skipped.size()).append("):\n");
                for (SkippedOperation op : skipped) {
                    sb.append("    - ").append(op.name())
                            .append(": ").append(op.reason()).append("\n");
                }
            }

            sb.append("\n  Summary: ").append(included.size()).append(" included, ")
                    .append(skipped.size()).append(" skipped\n\n");

            totalIncluded += included.size();
            totalSkipped += skipped.size();
        }

        sb.append("=================================================\n");
        sb.append("Total: ").append(totalIncluded).append(" included, ")
                .append(totalSkipped).append(" skipped\n");
        sb.append("=================================================\n");
        return sb.toString();
    }
}
