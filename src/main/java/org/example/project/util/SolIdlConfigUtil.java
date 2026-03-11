package org.example.project.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SolIdlConfigUtil {

    private SolIdlConfigUtil() {
    }

    public static ResolvedSolOperation resolve(Path configPath, String contractAddress, String operationCode) throws Exception {
        if (configPath == null) {
            throw new IllegalArgumentException("contracts config path is required");
        }
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("contracts config file not found: " + configPath);
        }
        JSONObject root = JSON.parseObject(Files.readString(configPath));
        return resolve(root, configPath.toAbsolutePath().normalize(), contractAddress, operationCode);
    }

    public static ResolvedSolOperation resolve(
            JSONObject root,
            Path configPath,
            String contractAddress,
            String operationCode
    ) {
        if (isBlank(contractAddress)) {
            throw new IllegalArgumentException("contract address is required");
        }
        if (isBlank(operationCode)) {
            throw new IllegalArgumentException("operationCode is required");
        }
        if (root == null) {
            throw new IllegalArgumentException("contracts config root is required");
        }

        JSONArray contracts = root.getJSONArray("contracts");
        if (contracts == null) {
            throw new IllegalArgumentException("contracts config missing 'contracts' array");
        }

        for (int i = 0; i < contracts.size(); i++) {
            JSONObject contractNode = contracts.getJSONObject(i);
            String configuredAddress = contractNode.getString("contractAddress");
            if (!contractAddress.equals(configuredAddress)) {
                continue;
            }

            JSONArray operationList = contractNode.getJSONArray("operationList");
            if (operationList == null) {
                continue;
            }

            for (int j = 0; j < operationList.size(); j++) {
                JSONObject operationNode = operationList.getJSONObject(j);
                if (!operationCode.equals(operationNode.getString("operationKey"))) {
                    continue;
                }

                JSONObject solIdl = operationNode.getJSONObject("solIdl");
                String idlPathRaw;
                String instructionName;
                Map<String, String> accountTemplate;

                if (solIdl != null) {
                    idlPathRaw = requiredText(solIdl.get("idlPath"), "solIdl.idlPath");
                    instructionName = requiredText(solIdl.get("instructionName"), "solIdl.instructionName");
                    accountTemplate = readAccounts(solIdl.getJSONObject("accounts"));
                } else {
                    idlPathRaw = requiredText(contractNode.get("idlPath"), "idlPath");
                    instructionName = requiredText(operationNode.get("operationKey"), "operationKey");
                    JSONObject operationAccounts = operationNode.getJSONObject("accounts");
                    JSONObject contractAccounts = contractNode.getJSONObject("accounts");
                    accountTemplate = readAccounts(operationAccounts != null ? operationAccounts : contractAccounts);
                }
                Path idlPath = resolveIdlPath(configPath, idlPathRaw);

                return new ResolvedSolOperation(
                        contractNode.getString("contractKey"),
                        configuredAddress,
                        operationCode,
                        instructionName,
                        idlPath,
                        accountTemplate
                );
            }
        }

        throw new IllegalArgumentException(
                "operation not found by contractAddress=" + contractAddress + ", operationCode=" + operationCode
        );
    }

    private static Path resolveIdlPath(Path configPath, String rawPath) {
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

    private static Map<String, String> readAccounts(JSONObject accountsNode) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (accountsNode == null) {
            return out;
        }
        for (String key : accountsNode.keySet()) {
            out.put(key, accountsNode.getString(key));
        }
        return out;
    }

    private static String requiredText(Object node, String fieldName) {
        if (!(node instanceof String) || isBlank((String) node)) {
            throw new IllegalArgumentException(fieldName + " must be non-empty string");
        }
        return (String) node;
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
