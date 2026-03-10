package org.example.sol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyTransactionBuilder {
    private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
    private static final String COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111";
    private static final String SYSVAR_RECENT_BLOCKHASHES = "SysvarRecentB1ockHashes11111111111111111111";

    public BuildResult build(
            JsonNode idlRoot,
            String instructionName,
            JsonNode argsNode,
            JsonNode accountsNode,
            BuildConfig config
    ) {
        if (idlRoot == null || !idlRoot.isObject()) {
            throw new IllegalArgumentException("IDL JSON is invalid");
        }
        if (config == null) {
            throw new IllegalArgumentException("build config is required");
        }
        if (config.feePayer() == null || config.feePayer().isBlank()) {
            throw new IllegalArgumentException("feePayer is required");
        }

        JsonNode ixNode = findInstruction(idlRoot, instructionName);
        String programId = requireText(idlRoot.get("address"), "IDL program address");

        List<Instruction> instructions = new ArrayList<>();
        String recentBlockhash = config.recentBlockhash();

        if (config.nonceConfig() != null) {
            NonceConfig nonce = config.nonceConfig();
            instructions.add(advanceNonceInstruction(nonce));
            recentBlockhash = nonce.nonceValue();
        }
        if (recentBlockhash == null || recentBlockhash.isBlank()) {
            throw new IllegalArgumentException("recentBlockhash or nonce.nonceValue is required");
        }

        if (config.computeUnitLimit() != null) {
            instructions.add(setComputeUnitLimitInstruction(config.computeUnitLimit()));
        }
        if (config.computeUnitPriceMicroLamports() != null) {
            instructions.add(setComputeUnitPriceInstruction(config.computeUnitPriceMicroLamports()));
        }

        instructions.add(buildIdlInstruction(programId, ixNode, argsNode, accountsNode));

        CompiledMessage message = compileLegacyMessage(config.feePayer(), recentBlockhash, instructions);
        byte[] unsignedTx = buildUnsignedTransaction(message);

        return new BuildResult(
                message.messageBytes(),
                unsignedTx,
                Base64.getEncoder().encodeToString(message.messageBytes()),
                Base64.getEncoder().encodeToString(unsignedTx),
                message.requiredSigners(),
                message.accountKeys()
        );
    }

    private JsonNode findInstruction(JsonNode idlRoot, String instructionName) {
        JsonNode instructions = idlRoot.get("instructions");
        if (instructions == null || !instructions.isArray()) {
            throw new IllegalArgumentException("IDL instructions is missing");
        }
        for (JsonNode ix : instructions) {
            if (instructionName.equals(ix.path("name").asText())) {
                return ix;
            }
        }
        throw new IllegalArgumentException("Instruction not found in IDL: " + instructionName);
    }

    private Instruction buildIdlInstruction(
            String programId,
            JsonNode instructionNode,
            JsonNode argsNode,
            JsonNode accountsNode
    ) {
        List<AccountSpec> specs = new ArrayList<>();
        collectAccountSpecs(instructionNode.get("accounts"), "", specs);

        List<AccountMeta> metas = new ArrayList<>();
        for (AccountSpec spec : specs) {
            String pubkey = spec.address();
            if (pubkey == null) {
                pubkey = findAccountPubkey(accountsNode, spec.lookupKey());
            }
            metas.add(new AccountMeta(pubkey, spec.signer(), spec.writable()));
        }

        byte[] data = encodeInstructionData(instructionNode, argsNode);
        return new Instruction(programId, metas, data);
    }

    private byte[] encodeInstructionData(JsonNode instructionNode, JsonNode argsNode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BorshEncoder.discriminatorFromIdlOrAnchor(instructionNode));

        JsonNode args = instructionNode.get("args");
        if (args != null && args.isArray()) {
            for (JsonNode arg : args) {
                String name = requireText(arg.get("name"), "arg.name");
                JsonNode type = arg.get("type");
                JsonNode value = argsNode != null && argsNode.has(name) ? argsNode.get(name) : NullNode.getInstance();
                out.writeBytes(BorshEncoder.encodeType(type, value));
            }
        }
        return out.toByteArray();
    }

    private void collectAccountSpecs(JsonNode accountsNode, String prefix, List<AccountSpec> out) {
        if (accountsNode == null || !accountsNode.isArray()) {
            return;
        }
        for (JsonNode accountNode : accountsNode) {
            JsonNode nested = accountNode.get("accounts");
            String name = requireText(accountNode.get("name"), "account.name");
            if (nested != null && nested.isArray()) {
                collectAccountSpecs(nested, prefix + name + ".", out);
                continue;
            }
            boolean writable = accountNode.has("writable")
                    ? accountNode.get("writable").asBoolean()
                    : accountNode.path("isMut").asBoolean(false);
            boolean signer = accountNode.has("signer")
                    ? accountNode.get("signer").asBoolean()
                    : accountNode.path("isSigner").asBoolean(false);
            String address = accountNode.has("address") ? accountNode.get("address").asText() : null;
            out.add(new AccountSpec(prefix + name, signer, writable, address));
        }
    }

    private String findAccountPubkey(JsonNode accountsNode, String key) {
        if (accountsNode == null || accountsNode.isNull()) {
            throw new IllegalArgumentException("accounts JSON is required");
        }
        JsonNode direct = accountsNode.get(key);
        if (direct != null && direct.isTextual()) {
            return direct.asText();
        }
        String[] parts = key.split("\\.");
        JsonNode cur = accountsNode;
        for (String p : parts) {
            if (cur == null || cur.isNull()) {
                break;
            }
            cur = cur.get(p);
        }
        if (cur != null && cur.isTextual()) {
            return cur.asText();
        }
        throw new IllegalArgumentException("Missing account pubkey for: " + key);
    }

    private Instruction setComputeUnitLimitInstruction(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("computeUnitLimit must be > 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 2);
        data.putInt(units);
        return new Instruction(COMPUTE_BUDGET_PROGRAM, List.of(), data.array());
    }

    private Instruction setComputeUnitPriceInstruction(long microLamports) {
        if (microLamports < 0) {
            throw new IllegalArgumentException("computeUnitPriceMicroLamports must be >= 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 8).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 3);
        data.putLong(microLamports);
        return new Instruction(COMPUTE_BUDGET_PROGRAM, List.of(), data.array());
    }

    private Instruction advanceNonceInstruction(NonceConfig nonce) {
        if (nonce.nonceAccount() == null || nonce.nonceAuthority() == null || nonce.nonceValue() == null) {
            throw new IllegalArgumentException("nonce config requires nonceAccount, nonceAuthority, nonceValue");
        }
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(4);
        List<AccountMeta> metas = List.of(
                new AccountMeta(nonce.nonceAccount(), false, true),
                new AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false),
                new AccountMeta(nonce.nonceAuthority(), true, false)
        );
        return new Instruction(SYSTEM_PROGRAM, metas, data.array());
    }

    private CompiledMessage compileLegacyMessage(
            String feePayer,
            String recentBlockhash,
            List<Instruction> instructions
    ) {
        LinkedHashMap<String, MetaFlags> merged = new LinkedHashMap<>();
        mergeMeta(merged, feePayer, true, true);

        for (Instruction instruction : instructions) {
            for (AccountMeta m : instruction.accounts()) {
                mergeMeta(merged, m.pubkey(), m.signer(), m.writable());
            }
            mergeMeta(merged, instruction.programId(), false, false);
        }

        MetaFlags payer = merged.remove(feePayer);
        if (payer == null) {
            payer = new MetaFlags(true, true);
        } else {
            payer.signer = true;
            payer.writable = true;
        }

        List<String> signedWritable = new ArrayList<>();
        List<String> signedReadonly = new ArrayList<>();
        List<String> unsignedWritable = new ArrayList<>();
        List<String> unsignedReadonly = new ArrayList<>();

        signedWritable.add(feePayer);
        for (Map.Entry<String, MetaFlags> e : merged.entrySet()) {
            String key = e.getKey();
            MetaFlags f = e.getValue();
            if (f.signer && f.writable) {
                signedWritable.add(key);
            } else if (f.signer) {
                signedReadonly.add(key);
            } else if (f.writable) {
                unsignedWritable.add(key);
            } else {
                unsignedReadonly.add(key);
            }
        }

        List<String> accountKeys = new ArrayList<>();
        accountKeys.addAll(signedWritable);
        accountKeys.addAll(signedReadonly);
        accountKeys.addAll(unsignedWritable);
        accountKeys.addAll(unsignedReadonly);

        int numRequiredSignatures = signedWritable.size() + signedReadonly.size();
        int numReadonlySignedAccounts = signedReadonly.size();
        int numReadonlyUnsignedAccounts = unsignedReadonly.size();

        Map<String, Integer> keyIndex = new LinkedHashMap<>();
        for (int i = 0; i < accountKeys.size(); i++) {
            keyIndex.put(accountKeys.get(i), i);
        }

        List<CompiledInstruction> compiled = new ArrayList<>();
        for (Instruction ix : instructions) {
            Integer pid = keyIndex.get(ix.programId());
            if (pid == null) {
                throw new IllegalStateException("Program id missing from key map: " + ix.programId());
            }
            List<Integer> accountIndexes = new ArrayList<>();
            for (AccountMeta am : ix.accounts()) {
                Integer idx = keyIndex.get(am.pubkey());
                if (idx == null) {
                    throw new IllegalStateException("Account missing from key map: " + am.pubkey());
                }
                accountIndexes.add(idx);
            }
            compiled.add(new CompiledInstruction(pid, accountIndexes, ix.data()));
        }

        byte[] messageBytes = serializeMessage(
                numRequiredSignatures,
                numReadonlySignedAccounts,
                numReadonlyUnsignedAccounts,
                accountKeys,
                recentBlockhash,
                compiled
        );

        return new CompiledMessage(messageBytes, accountKeys, accountKeys.subList(0, numRequiredSignatures));
    }

    private byte[] serializeMessage(
            int numRequiredSignatures,
            int numReadonlySignedAccounts,
            int numReadonlyUnsignedAccounts,
            List<String> accountKeys,
            String recentBlockhash,
            List<CompiledInstruction> instructions
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(numRequiredSignatures);
        out.write(numReadonlySignedAccounts);
        out.write(numReadonlyUnsignedAccounts);

        out.writeBytes(ShortVec.encodeLength(accountKeys.size()));
        for (String key : accountKeys) {
            out.writeBytes(BorshEncoder.encodePubkey(key));
        }
        out.writeBytes(BorshEncoder.encodePubkey(recentBlockhash));

        out.writeBytes(ShortVec.encodeLength(instructions.size()));
        for (CompiledInstruction ix : instructions) {
            out.write(ix.programIdIndex());
            out.writeBytes(ShortVec.encodeLength(ix.accountIndexes().size()));
            for (int idx : ix.accountIndexes()) {
                out.write(idx);
            }
            out.writeBytes(ShortVec.encodeLength(ix.data().length));
            out.writeBytes(ix.data());
        }
        return out.toByteArray();
    }

    private byte[] buildUnsignedTransaction(CompiledMessage message) {
        int numRequiredSignatures = message.requiredSigners().size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ShortVec.encodeLength(numRequiredSignatures));
        for (int i = 0; i < numRequiredSignatures; i++) {
            out.writeBytes(new byte[64]);
        }
        out.writeBytes(message.messageBytes());
        return out.toByteArray();
    }

    private static void mergeMeta(LinkedHashMap<String, MetaFlags> merged, String key, boolean signer, boolean writable) {
        MetaFlags cur = merged.get(key);
        if (cur == null) {
            merged.put(key, new MetaFlags(signer, writable));
        } else {
            cur.signer = cur.signer || signer;
            cur.writable = cur.writable || writable;
        }
    }

    private static String requireText(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return node.asText();
    }

    public static final class BuildConfig {
        private final String feePayer;
        private final String recentBlockhash;
        private final Integer computeUnitLimit;
        private final Long computeUnitPriceMicroLamports;
        private final NonceConfig nonceConfig;

        public BuildConfig(
                String feePayer,
                String recentBlockhash,
                Integer computeUnitLimit,
                Long computeUnitPriceMicroLamports,
                NonceConfig nonceConfig
        ) {
            this.feePayer = feePayer;
            this.recentBlockhash = recentBlockhash;
            this.computeUnitLimit = computeUnitLimit;
            this.computeUnitPriceMicroLamports = computeUnitPriceMicroLamports;
            this.nonceConfig = nonceConfig;
        }

        public String feePayer() {
            return feePayer;
        }

        public String recentBlockhash() {
            return recentBlockhash;
        }

        public Integer computeUnitLimit() {
            return computeUnitLimit;
        }

        public Long computeUnitPriceMicroLamports() {
            return computeUnitPriceMicroLamports;
        }

        public NonceConfig nonceConfig() {
            return nonceConfig;
        }

        public static BuildConfig fromJson(JsonNode configNode) {
            if (configNode == null || configNode.isNull()) {
                throw new IllegalArgumentException("config JSON is missing");
            }
            String feePayer = textOrNull(configNode.get("feePayer"));
            String recentBlockhash = textOrNull(configNode.get("recentBlockhash"));
            Integer computeUnitLimit = configNode.has("computeUnitLimit")
                    ? configNode.get("computeUnitLimit").asInt()
                    : null;
            Long computeUnitPriceMicroLamports = configNode.has("computeUnitPriceMicroLamports")
                    ? configNode.get("computeUnitPriceMicroLamports").asLong()
                    : null;

            NonceConfig nonce = null;
            JsonNode nonceNode = configNode.get("nonce");
            if (nonceNode != null && nonceNode.isObject()) {
                nonce = new NonceConfig(
                        textOrNull(nonceNode.get("nonceAccount")),
                        textOrNull(nonceNode.get("nonceAuthority")),
                        textOrNull(nonceNode.get("nonceValue"))
                );
            }
            return new BuildConfig(
                    feePayer,
                    recentBlockhash,
                    computeUnitLimit,
                    computeUnitPriceMicroLamports,
                    nonce
            );
        }

        private static String textOrNull(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            return node.asText();
        }
    }

    public static final class NonceConfig {
        private final String nonceAccount;
        private final String nonceAuthority;
        private final String nonceValue;

        public NonceConfig(String nonceAccount, String nonceAuthority, String nonceValue) {
            this.nonceAccount = nonceAccount;
            this.nonceAuthority = nonceAuthority;
            this.nonceValue = nonceValue;
        }

        public String nonceAccount() {
            return nonceAccount;
        }

        public String nonceAuthority() {
            return nonceAuthority;
        }

        public String nonceValue() {
            return nonceValue;
        }
    }

    public static final class BuildResult {
        private final byte[] messageBytes;
        private final byte[] unsignedTransactionBytes;
        private final String messageBase64;
        private final String unsignedTransactionBase64;
        private final List<String> requiredSigners;
        private final List<String> accountKeys;

        public BuildResult(
                byte[] messageBytes,
                byte[] unsignedTransactionBytes,
                String messageBase64,
                String unsignedTransactionBase64,
                List<String> requiredSigners,
                List<String> accountKeys
        ) {
            this.messageBytes = messageBytes;
            this.unsignedTransactionBytes = unsignedTransactionBytes;
            this.messageBase64 = messageBase64;
            this.unsignedTransactionBase64 = unsignedTransactionBase64;
            this.requiredSigners = requiredSigners;
            this.accountKeys = accountKeys;
        }

        public byte[] messageBytes() {
            return messageBytes;
        }

        public byte[] unsignedTransactionBytes() {
            return unsignedTransactionBytes;
        }

        public String messageBase64() {
            return messageBase64;
        }

        public String unsignedTransactionBase64() {
            return unsignedTransactionBase64;
        }

        public List<String> requiredSigners() {
            return requiredSigners;
        }

        public List<String> accountKeys() {
            return accountKeys;
        }
    }

    private static final class AccountSpec {
        private final String lookupKey;
        private final boolean signer;
        private final boolean writable;
        private final String address;

        private AccountSpec(String lookupKey, boolean signer, boolean writable, String address) {
            this.lookupKey = lookupKey;
            this.signer = signer;
            this.writable = writable;
            this.address = address;
        }

        private String lookupKey() {
            return lookupKey;
        }

        private boolean signer() {
            return signer;
        }

        private boolean writable() {
            return writable;
        }

        private String address() {
            return address;
        }
    }

    private static final class AccountMeta {
        private final String pubkey;
        private final boolean signer;
        private final boolean writable;

        private AccountMeta(String pubkey, boolean signer, boolean writable) {
            this.pubkey = pubkey;
            this.signer = signer;
            this.writable = writable;
        }

        private String pubkey() {
            return pubkey;
        }

        private boolean signer() {
            return signer;
        }

        private boolean writable() {
            return writable;
        }
    }

    private static final class Instruction {
        private final String programId;
        private final List<AccountMeta> accounts;
        private final byte[] data;

        private Instruction(String programId, List<AccountMeta> accounts, byte[] data) {
            this.programId = programId;
            this.accounts = accounts;
            this.data = data;
        }

        private String programId() {
            return programId;
        }

        private List<AccountMeta> accounts() {
            return accounts;
        }

        private byte[] data() {
            return data;
        }
    }

    private static final class CompiledInstruction {
        private final int programIdIndex;
        private final List<Integer> accountIndexes;
        private final byte[] data;

        private CompiledInstruction(int programIdIndex, List<Integer> accountIndexes, byte[] data) {
            this.programIdIndex = programIdIndex;
            this.accountIndexes = accountIndexes;
            this.data = data;
        }

        private int programIdIndex() {
            return programIdIndex;
        }

        private List<Integer> accountIndexes() {
            return accountIndexes;
        }

        private byte[] data() {
            return data;
        }
    }

    private static final class CompiledMessage {
        private final byte[] messageBytes;
        private final List<String> accountKeys;
        private final List<String> requiredSigners;

        private CompiledMessage(byte[] messageBytes, List<String> accountKeys, List<String> requiredSigners) {
            this.messageBytes = messageBytes;
            this.accountKeys = accountKeys;
            this.requiredSigners = requiredSigners;
        }

        private byte[] messageBytes() {
            return messageBytes;
        }

        private List<String> accountKeys() {
            return accountKeys;
        }

        private List<String> requiredSigners() {
            return requiredSigners;
        }
    }

    private static final class MetaFlags {
        private boolean signer;
        private boolean writable;

        private MetaFlags(boolean signer, boolean writable) {
            this.signer = signer;
            this.writable = writable;
        }
    }
}
