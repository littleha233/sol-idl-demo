package org.example.project;

import org.example.project.config.SolContractRegistry;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.LegacyTransactionSerializer;
import org.example.sol.idl.IdlInstructionBuilder;
import org.example.sol.sdk.ComputeBudgetProgram;
import org.example.sol.sdk.Instruction;
import org.example.sol.sdk.Message;
import org.example.sol.sdk.SystemProgram;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SolIdlProject {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.-]+)}");
    private static final String MOCK_CONFIG_ACCOUNT = "G413572PbWwbmEHkZ7WJePXLmaHp8AFnTUn9Hw4iUnLx";

    private final IdlInstructionBuilder idlInstructionBuilder = new IdlInstructionBuilder();
    private final LegacyTransactionSerializer serializer = new LegacyTransactionSerializer();
    private final SolContractRegistry contractRegistry;

    public SolIdlProject(Path contractsConfigPath) throws Exception {
        this(SolContractRegistry.load(contractsConfigPath));
    }

    public SolIdlProject(SolContractRegistry contractRegistry) {
        if (contractRegistry == null) {
            throw new IllegalArgumentException("contractRegistry is required");
        }
        this.contractRegistry = contractRegistry;
    }

    public LegacyTransactionSerializer.BuildResult buildTx(BuildTxReq<SolIdlTxBuildExt> req) throws Exception {
        validateReq(req);

        String from = req.getFrom();
        SolIdlTxBuildExt ext = req.getExt();

        SolContractRegistry.ResolvedSolOperation operation =
                contractRegistry.resolve(ext.getTo(), ext.getOperationCode());

        Message message = new Message();

        message.setFeePayer(from);

        NonceInfo nonceInfo = getNonceAccount(from);
        message.addInstruction(SystemProgram.nonceAdvance(nonceInfo.getNonceAccount(), from));
        message.setRecentBlockHash(nonceInfo.getNonceValue());

        int mockedComputeUnitLimit = 200_000;
        long mockedComputeUnitPrice = 1_000L;
        message.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(mockedComputeUnitLimit));
        message.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(mockedComputeUnitPrice));

        Map<String, String> accounts = materializeAccounts(operation.getAccountTemplate(), from, ext.getTo());
        Instruction idlInstruction = idlInstructionBuilder.buildInstruction(
                operation.getIdlPath(),
                operation.getInstructionName(),
                accounts,
                ext.getParamList()
        );
        message.addInstruction(idlInstruction);

        return serializer.serializeUnsigned(message);
    }

    public SolContractRegistry.ResolvedSolOperation resolveOperation(String contractAddress, String operationCode) {
        return contractRegistry.resolve(contractAddress, operationCode);
    }

    protected NonceInfo getNonceAccount(String from) {
        // mock nonce retrieval (replace with real RPC query later).
        return new NonceInfo(
                "6Yq5j8hdkzTpi7yG7r62oV8rB6W4ko29z6hS9kuGBTHP",
                "3smDPkLLW8pUE3NADVQ4tMsANRvDWkfb6xm5ydj5Lc7n"
        );
    }

    private void validateReq(BuildTxReq<SolIdlTxBuildExt> req) {
        if (req == null) {
            throw new IllegalArgumentException("req is required");
        }
        if (isBlank(req.getFrom())) {
            throw new IllegalArgumentException("from is required");
        }

        SolIdlTxBuildExt ext = req.getExt();
        if (ext == null) {
            throw new IllegalArgumentException("ext is required");
        }
        if (isBlank(ext.getTo())) {
            throw new IllegalArgumentException("ext.to (contractAddress) is required");
        }
        if (isBlank(ext.getOperationCode())) {
            throw new IllegalArgumentException("ext.operationCode is required");
        }
        if (ext.getParamList() == null) {
            throw new IllegalArgumentException("ext.paramList is required");
        }
    }

    private Map<String, String> materializeAccounts(Map<String, String> templates, String from, String contractAddress) {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("from", from);
        variables.put("to", contractAddress);
        variables.put("contractAddress", contractAddress);

        Map<String, String> out = new LinkedHashMap<String, String>();
        // Mock runtime account context.
        out.put("authority", from);
        out.put("config", MOCK_CONFIG_ACCOUNT);

        for (Map.Entry<String, String> e : templates.entrySet()) {
            out.put(e.getKey(), renderTemplate(e.getValue(), variables));
        }
        return out;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Unknown account template variable: " + key);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static class NonceInfo {
        private final String nonceAccount;
        private final String nonceValue;

        public NonceInfo(String nonceAccount, String nonceValue) {
            this.nonceAccount = nonceAccount;
            this.nonceValue = nonceValue;
        }

        public String getNonceAccount() {
            return nonceAccount;
        }

        public String getNonceValue() {
            return nonceValue;
        }
    }
}
