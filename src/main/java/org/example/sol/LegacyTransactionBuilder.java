package org.example.sol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyTransactionBuilder {
    private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
    private static final String COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111";
    private static final String SYSVAR_RECENT_BLOCKHASHES = "SysvarRecentB1ockHashes11111111111111111111";

    public BuildResult build(BuildRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.idlRoot() == null || !request.idlRoot().isObject()) {
            throw new IllegalArgumentException("IDL JSON is invalid");
        }
        if (request.runtimeOptions() == null) {
            throw new IllegalArgumentException("runtime options are required");
        }
        if (isBlank(request.runtimeOptions().fromAddress())) {
            throw new IllegalArgumentException("fromAddress is required");
        }

        JsonNode ixNode = findInstruction(request.idlRoot(), request.instructionName());
        String programId = requireText(request.idlRoot().get("address"), "IDL program address");
        Instruction contractInstruction = buildIdlInstruction(
                programId,
                ixNode,
                request.accounts(),
                request.arguments()
        );

        MessageDraft messageDraft = newMessage();
        RuntimeOptions runtime = request.runtimeOptions();

        messageDraft.setFeePayer(runtime.fromAddress());
        if (runtime.nonceConfig() != null) {
            messageDraft.setDurableNonceAccount(runtime.nonceConfig());
        } else {
            messageDraft.setRecentBlockhash(runtime.recentBlockhash());
        }
        if (runtime.computeGasLimit() != null) {
            messageDraft.setComputeGasLimit(runtime.computeGasLimit().intValue());
        }
        if (runtime.computeGasPriceMicroLamports() != null) {
            messageDraft.setComputeGasPrice(runtime.computeGasPriceMicroLamports().longValue());
        }

        messageDraft.addContractInstruction(contractInstruction);
        return messageDraft.build();
    }

    public MessageDraft newMessage() {
        return new MessageDraft();
    }

    public final class MessageDraft {
        private final List<Instruction> instructions = new ArrayList<Instruction>();
        private String feePayer;
        private String recentBlockhash;

        public MessageDraft setFeePayer(String feePayer) {
            if (isBlank(feePayer)) {
                throw new IllegalArgumentException("feePayer cannot be blank");
            }
            this.feePayer = feePayer;
            return this;
        }

        public MessageDraft setRecentBlockhash(String recentBlockhash) {
            if (isBlank(recentBlockhash)) {
                throw new IllegalArgumentException("recentBlockhash cannot be blank");
            }
            this.recentBlockhash = recentBlockhash;
            return this;
        }

        public MessageDraft setDurableNonceAccount(NonceConfig nonceConfig) {
            if (nonceConfig == null) {
                throw new IllegalArgumentException("nonceConfig cannot be null");
            }
            instructions.add(advanceNonceInstruction(nonceConfig));
            this.recentBlockhash = nonceConfig.nonceValue();
            return this;
        }

        public MessageDraft setComputeGasLimit(int gasLimit) {
            instructions.add(setComputeUnitLimitInstruction(gasLimit));
            return this;
        }

        public MessageDraft setComputeGasPrice(long gasPriceMicroLamports) {
            instructions.add(setComputeUnitPriceInstruction(gasPriceMicroLamports));
            return this;
        }

        public MessageDraft addContractInstruction(Instruction instruction) {
            if (instruction == null) {
                throw new IllegalArgumentException("instruction cannot be null");
            }
            instructions.add(instruction);
            return this;
        }

        public BuildResult build() {
            if (isBlank(feePayer)) {
                throw new IllegalStateException("feePayer is not set");
            }
            if (isBlank(recentBlockhash)) {
                throw new IllegalStateException("recentBlockhash is not set");
            }
            if (instructions.isEmpty()) {
                throw new IllegalStateException("no instructions in message draft");
            }

            CompiledMessage message = compileLegacyMessage(feePayer, recentBlockhash, instructions);
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
            Map<String, String> accounts,
            Map<String, JsonNode> args
    ) {
        List<AccountSpec> specs = new ArrayList<AccountSpec>();
        collectAccountSpecs(instructionNode.get("accounts"), "", specs);

        List<AccountMeta> metas = new ArrayList<AccountMeta>();
        for (AccountSpec spec : specs) {
            String pubkey = spec.address();
            if (pubkey == null) {
                pubkey = findAccountPubkey(accounts, spec.lookupKey());
            }
            metas.add(new AccountMeta(pubkey, spec.signer(), spec.writable()));
        }

        byte[] data = encodeInstructionData(instructionNode, args);
        return new Instruction(programId, metas, data);
    }

    private byte[] encodeInstructionData(JsonNode instructionNode, Map<String, JsonNode> args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BorshEncoder.discriminatorFromIdlOrAnchor(instructionNode));

        JsonNode argsDef = instructionNode.get("args");
        if (argsDef != null && argsDef.isArray()) {
            for (JsonNode arg : argsDef) {
                String name = requireText(arg.get("name"), "arg.name");
                JsonNode type = arg.get("type");
                JsonNode value = args.get(name);
                if (value == null) {
                    value = NullNode.getInstance();
                }
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

    private String findAccountPubkey(Map<String, String> accounts, String key) {
        String value = accounts.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing account pubkey for: " + key);
        }
        return value;
    }

    private Instruction setComputeUnitLimitInstruction(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("computeGasLimit must be > 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 2);
        data.putInt(units);
        return new Instruction(COMPUTE_BUDGET_PROGRAM, Collections.<AccountMeta>emptyList(), data.array());
    }

    private Instruction setComputeUnitPriceInstruction(long microLamports) {
        if (microLamports < 0) {
            throw new IllegalArgumentException("computeGasPrice must be >= 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 8).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 3);
        data.putLong(microLamports);
        return new Instruction(COMPUTE_BUDGET_PROGRAM, Collections.<AccountMeta>emptyList(), data.array());
    }

    private Instruction advanceNonceInstruction(NonceConfig nonce) {
        if (isBlank(nonce.nonceAccount()) || isBlank(nonce.nonceAuthority()) || isBlank(nonce.nonceValue())) {
            throw new IllegalArgumentException("nonce config requires nonceAccount, nonceAuthority, nonceValue");
        }
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(4);
        List<AccountMeta> metas = new ArrayList<AccountMeta>();
        metas.add(new AccountMeta(nonce.nonceAccount(), false, true));
        metas.add(new AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false));
        metas.add(new AccountMeta(nonce.nonceAuthority(), true, false));
        return new Instruction(SYSTEM_PROGRAM, metas, data.array());
    }

    private CompiledMessage compileLegacyMessage(
            String feePayer,
            String recentBlockhash,
            List<Instruction> instructions
    ) {
        LinkedHashMap<String, MetaFlags> merged = new LinkedHashMap<String, MetaFlags>();
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

        List<String> signedWritable = new ArrayList<String>();
        List<String> signedReadonly = new ArrayList<String>();
        List<String> unsignedWritable = new ArrayList<String>();
        List<String> unsignedReadonly = new ArrayList<String>();

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

        List<String> accountKeys = new ArrayList<String>();
        accountKeys.addAll(signedWritable);
        accountKeys.addAll(signedReadonly);
        accountKeys.addAll(unsignedWritable);
        accountKeys.addAll(unsignedReadonly);

        int numRequiredSignatures = signedWritable.size() + signedReadonly.size();
        int numReadonlySignedAccounts = signedReadonly.size();
        int numReadonlyUnsignedAccounts = unsignedReadonly.size();

        Map<String, Integer> keyIndex = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < accountKeys.size(); i++) {
            keyIndex.put(accountKeys.get(i), i);
        }

        List<CompiledInstruction> compiled = new ArrayList<CompiledInstruction>();
        for (Instruction ix : instructions) {
            Integer pid = keyIndex.get(ix.programId());
            if (pid == null) {
                throw new IllegalStateException("Program id missing from key map: " + ix.programId());
            }
            List<Integer> accountIndexes = new ArrayList<Integer>();
            for (AccountMeta am : ix.accounts()) {
                Integer idx = keyIndex.get(am.pubkey());
                if (idx == null) {
                    throw new IllegalStateException("Account missing from key map: " + am.pubkey());
                }
                accountIndexes.add(idx);
            }
            compiled.add(new CompiledInstruction(pid.intValue(), accountIndexes, ix.data()));
        }

        byte[] messageBytes = serializeMessage(
                numRequiredSignatures,
                numReadonlySignedAccounts,
                numReadonlyUnsignedAccounts,
                accountKeys,
                recentBlockhash,
                compiled
        );

        List<String> requiredSigners = new ArrayList<String>(accountKeys.subList(0, numRequiredSignatures));
        return new CompiledMessage(messageBytes, accountKeys, requiredSigners);
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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String requireText(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return node.asText();
    }

    public static final class BuildRequest {
        private final JsonNode idlRoot;
        private final String instructionName;
        private final Map<String, String> accounts;
        private final Map<String, JsonNode> arguments;
        private final RuntimeOptions runtimeOptions;

        public BuildRequest(
                JsonNode idlRoot,
                String instructionName,
                Map<String, String> accounts,
                Map<String, JsonNode> arguments,
                RuntimeOptions runtimeOptions
        ) {
            this.idlRoot = idlRoot;
            this.instructionName = instructionName;
            this.accounts = accounts;
            this.arguments = arguments;
            this.runtimeOptions = runtimeOptions;
        }

        public static BuildRequest fromJson(
                JsonNode idlRoot,
                String instructionName,
                JsonNode accountsNode,
                JsonNode argsNode,
                RuntimeOptions runtimeOptions
        ) {
            return new BuildRequest(
                    idlRoot,
                    instructionName,
                    flattenStringMap(accountsNode),
                    flattenValueMap(argsNode),
                    runtimeOptions
            );
        }

        private static Map<String, String> flattenStringMap(JsonNode node) {
            Map<String, String> out = new LinkedHashMap<String, String>();
            flattenStringMapRecursive(node, "", out);
            return out;
        }

        private static void flattenStringMapRecursive(JsonNode node, String prefix, Map<String, String> out) {
            if (node == null || node.isNull() || !node.isObject()) {
                return;
            }
            node.fields().forEachRemaining(entry -> {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                JsonNode value = entry.getValue();
                if (value != null && value.isObject()) {
                    flattenStringMapRecursive(value, key, out);
                } else if (value != null && value.isTextual()) {
                    out.put(key, value.asText());
                }
            });
        }

        private static Map<String, JsonNode> flattenValueMap(JsonNode node) {
            if (node == null || node.isNull() || !node.isObject()) {
                return Collections.emptyMap();
            }
            Map<String, JsonNode> out = new LinkedHashMap<String, JsonNode>();
            node.fields().forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue()));
            return out;
        }

        public JsonNode idlRoot() {
            return idlRoot;
        }

        public String instructionName() {
            return instructionName;
        }

        public Map<String, String> accounts() {
            return accounts;
        }

        public Map<String, JsonNode> arguments() {
            return arguments;
        }

        public RuntimeOptions runtimeOptions() {
            return runtimeOptions;
        }
    }

    public static final class RuntimeOptions {
        private final String fromAddress;
        private final String recentBlockhash;
        private final Integer computeGasLimit;
        private final Long computeGasPriceMicroLamports;
        private final NonceConfig nonceConfig;

        public RuntimeOptions(
                String fromAddress,
                String recentBlockhash,
                Integer computeGasLimit,
                Long computeGasPriceMicroLamports,
                NonceConfig nonceConfig
        ) {
            this.fromAddress = fromAddress;
            this.recentBlockhash = recentBlockhash;
            this.computeGasLimit = computeGasLimit;
            this.computeGasPriceMicroLamports = computeGasPriceMicroLamports;
            this.nonceConfig = nonceConfig;
        }

        public String fromAddress() {
            return fromAddress;
        }

        public String recentBlockhash() {
            return recentBlockhash;
        }

        public Integer computeGasLimit() {
            return computeGasLimit;
        }

        public Long computeGasPriceMicroLamports() {
            return computeGasPriceMicroLamports;
        }

        public NonceConfig nonceConfig() {
            return nonceConfig;
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

    public static final class Instruction {
        private final String programId;
        private final List<AccountMeta> accounts;
        private final byte[] data;

        public Instruction(String programId, List<AccountMeta> accounts, byte[] data) {
            this.programId = programId;
            this.accounts = accounts;
            this.data = data;
        }

        public String programId() {
            return programId;
        }

        public List<AccountMeta> accounts() {
            return accounts;
        }

        public byte[] data() {
            return data;
        }
    }

    public static final class AccountMeta {
        private final String pubkey;
        private final boolean signer;
        private final boolean writable;

        public AccountMeta(String pubkey, boolean signer, boolean writable) {
            this.pubkey = pubkey;
            this.signer = signer;
            this.writable = writable;
        }

        public String pubkey() {
            return pubkey;
        }

        public boolean signer() {
            return signer;
        }

        public boolean writable() {
            return writable;
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
