package org.example.project.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SolContractRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path configPath;
    private final JsonNode root;

    private SolContractRegistry(Path configPath, JsonNode root) {
        this.configPath = configPath;
        this.root = root;
    }

    public static SolContractRegistry load(Path configPath) throws Exception {
        if (configPath == null) {
            throw new IllegalArgumentException("contracts config path is required");
        }
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("contracts config file not found: " + configPath);
        }
        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        return new SolContractRegistry(configPath.toAbsolutePath().normalize(), root);
    }

    public ResolvedSolOperation resolve(String contractAddress, String operationCode) {
        if (isBlank(contractAddress)) {
            throw new IllegalArgumentException("contract address is required");
        }
        if (isBlank(operationCode)) {
            throw new IllegalArgumentException("operationCode is required");
        }

        JsonNode contracts = root.path("contracts");
        if (!contracts.isArray()) {
            throw new IllegalArgumentException("contracts config missing 'contracts' array");
        }

        for (JsonNode contractNode : contracts) {
            if (!"SOL".equalsIgnoreCase(contractNode.path("chainName").asText())) {
                continue;
            }
            String configuredAddress = contractNode.path("contractAddress").asText();
            if (!contractAddress.equals(configuredAddress)) {
                continue;
            }

            JsonNode operationList = contractNode.path("operationList");
            if (!operationList.isArray()) {
                continue;
            }

            for (JsonNode operationNode : operationList) {
                if (!operationCode.equals(operationNode.path("operationKey").asText())) {
                    continue;
                }
                JsonNode solIdl = operationNode.path("solIdl");
                if (!solIdl.isObject()) {
                    throw new IllegalArgumentException("solIdl block is required for SOL operation: " + operationCode);
                }

                String idlPathRaw = requiredText(solIdl.path("idlPath"), "solIdl.idlPath");
                String instructionName = requiredText(solIdl.path("instructionName"), "solIdl.instructionName");
                Map<String, String> accountTemplate = readAccounts(solIdl.path("accounts"));
                Path idlPath = resolveIdlPath(idlPathRaw);

                return new ResolvedSolOperation(
                        contractNode.path("contractKey").asText(),
                        configuredAddress,
                        operationCode,
                        instructionName,
                        idlPath,
                        accountTemplate
                );
            }
        }

        throw new IllegalArgumentException(
                "SOL operation not found by contractAddress=" + contractAddress + ", operationCode=" + operationCode
        );
    }

    private Path resolveIdlPath(String rawPath) {
        Path raw = Path.of(rawPath);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        Path parent = configPath.getParent();
        if (parent == null) {
            return raw.toAbsolutePath().normalize();
        }
        return parent.resolve(raw).normalize();
    }

    private Map<String, String> readAccounts(JsonNode accountsNode) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (!accountsNode.isObject()) {
            return out;
        }
        accountsNode.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    private String requiredText(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isTextual() || isBlank(node.asText())) {
            throw new IllegalArgumentException(fieldName + " must be non-empty string");
        }
        return node.asText();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static class ResolvedSolOperation {
        private final String contractKey;
        private final String contractAddress;
        private final String operationCode;
        private final String instructionName;
        private final Path idlPath;
        private final Map<String, String> accountTemplate;

        public ResolvedSolOperation(
                String contractKey,
                String contractAddress,
                String operationCode,
                String instructionName,
                Path idlPath,
                Map<String, String> accountTemplate
        ) {
            this.contractKey = contractKey;
            this.contractAddress = contractAddress;
            this.operationCode = operationCode;
            this.instructionName = instructionName;
            this.idlPath = idlPath;
            this.accountTemplate = Collections.unmodifiableMap(new LinkedHashMap<String, String>(accountTemplate));
        }

        public String getContractKey() {
            return contractKey;
        }

        public String getContractAddress() {
            return contractAddress;
        }

        public String getOperationCode() {
            return operationCode;
        }

        public String getInstructionName() {
            return instructionName;
        }

        public Path getIdlPath() {
            return idlPath;
        }

        public Map<String, String> getAccountTemplate() {
            return accountTemplate;
        }
    }
}
