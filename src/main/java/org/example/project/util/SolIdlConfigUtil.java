package org.example.project.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SolIdlConfigUtil {
    public static final String DEFAULT_CONFIG_LOCATION = "idl/contracts-config.json";

    private SolIdlConfigUtil() {
    }

    public static ResolvedSolOperation resolve(String configResourcePath, String contractAddress, String operationCode) throws Exception {
        if (isBlank(configResourcePath)) {
            throw new IllegalArgumentException("configResourcePath is required");
        }
        JSONObject root = readJsonObject(configResourcePath);
        String resourceBaseDir = buildClasspathBaseDir(configResourcePath);
        return resolve(root, resourceBaseDir, contractAddress, operationCode);
    }

    public static ResolvedSolOperation resolve(
            JSONObject root,
            String resourceBaseDir,
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
                String idlResourcePath;
                String instructionName;
                Map<String, String> accountTemplate;

                if (solIdl != null) {
                    idlResourcePath = resolveIdlResourcePath(resourceBaseDir, requiredText(solIdl.get("idlPath"), "solIdl.idlPath"));
                    instructionName = requiredText(solIdl.get("instructionName"), "solIdl.instructionName");
                    accountTemplate = readAccounts(solIdl.getJSONObject("accounts"));
                } else {
                    idlResourcePath = resolveIdlResourcePath(resourceBaseDir, requiredText(contractNode.get("idlPath"), "idlPath"));
                    instructionName = requiredText(operationNode.get("operationKey"), "operationKey");
                    JSONObject operationAccounts = operationNode.getJSONObject("accounts");
                    JSONObject contractAccounts = contractNode.getJSONObject("accounts");
                    accountTemplate = readAccounts(operationAccounts != null ? operationAccounts : contractAccounts);
                }

                return new ResolvedSolOperation(
                        contractNode.getString("contractKey"),
                        configuredAddress,
                        operationCode,
                        instructionName,
                        idlResourcePath,
                        accountTemplate
                );
            }
        }

        throw new IllegalArgumentException(
                "operation not found by contractAddress=" + contractAddress + ", operationCode=" + operationCode
        );
    }

    public static JSONObject readJsonObject(String resourcePath) throws Exception {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            return JSON.parseObject(is.readAllBytes(), JSONObject.class);
        }
    }

    public static JSONArray readJsonArray(String resourcePath) throws Exception {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            return JSON.parseArray(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String resolveIdlResourcePath(String resourceBaseDir, String rawPath) {
        if (isBlank(rawPath)) {
            throw new IllegalArgumentException("idlPath is blank");
        }
        if (rawPath.startsWith("classpath:")) {
            return rawPath.substring("classpath:".length());
        }
        if (rawPath.startsWith("/")) {
            return rawPath.substring(1);
        }
        return resourceBaseDir + rawPath;
    }

    private static String buildClasspathBaseDir(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }
        return path.substring(0, idx + 1);
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
        private final String idlResourcePath;
        private final Map<String, String> accountTemplate;

        public ResolvedSolOperation(
                String contractKey,
                String contractAddress,
                String operationCode,
                String instructionName,
                String idlResourcePath,
                Map<String, String> accountTemplate
        ) {
            this.contractKey = contractKey;
            this.contractAddress = contractAddress;
            this.operationCode = operationCode;
            this.instructionName = instructionName;
            this.idlResourcePath = idlResourcePath;
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

        public String getIdlResourcePath() {
            return idlResourcePath;
        }

        public Map<String, String> getAccountTemplate() {
            return accountTemplate;
        }
    }
}
