package org.example.sol;

import org.example.sol.sdk.AccountMeta;
import org.example.sol.sdk.Instruction;
import org.example.sol.sdk.Message;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyTransactionSerializer {
    public BuildResult serializeUnsigned(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }
        if (isBlank(message.getFeePayer())) {
            throw new IllegalArgumentException("fee payer is required");
        }
        if (isBlank(message.getRecentBlockHash())) {
            throw new IllegalArgumentException("recent blockhash is required");
        }
        if (message.getInstructions().isEmpty()) {
            throw new IllegalArgumentException("message has no instructions");
        }

        CompiledMessage compiledMessage = compileLegacyMessage(message);
        byte[] unsignedTx = buildUnsignedTransaction(compiledMessage);

        return new BuildResult(
                compiledMessage.messageBytes,
                unsignedTx,
                Base64.getEncoder().encodeToString(compiledMessage.messageBytes),
                Base64.getEncoder().encodeToString(unsignedTx),
                compiledMessage.requiredSigners,
                compiledMessage.accountKeys
        );
    }

    private CompiledMessage compileLegacyMessage(Message message) {
        String feePayer = message.getFeePayer();
        LinkedHashMap<String, MetaFlags> merged = new LinkedHashMap<String, MetaFlags>();
        mergeMeta(merged, feePayer, true, true);

        for (Instruction instruction : message.getInstructions()) {
            for (AccountMeta m : instruction.getAccounts()) {
                mergeMeta(merged, m.getPubkey(), m.isSigner(), m.isWritable());
            }
            mergeMeta(merged, instruction.getProgramId(), false, false);
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

        List<CompiledInstruction> compiledInstructions = new ArrayList<CompiledInstruction>();
        for (Instruction ix : message.getInstructions()) {
            Integer pid = keyIndex.get(ix.getProgramId());
            if (pid == null) {
                throw new IllegalStateException("program id not found in account list: " + ix.getProgramId());
            }
            List<Integer> accountIndexes = new ArrayList<Integer>();
            for (AccountMeta account : ix.getAccounts()) {
                Integer idx = keyIndex.get(account.getPubkey());
                if (idx == null) {
                    throw new IllegalStateException("account not found in account list: " + account.getPubkey());
                }
                accountIndexes.add(idx);
            }
            compiledInstructions.add(new CompiledInstruction(pid.intValue(), accountIndexes, ix.getData()));
        }

        byte[] messageBytes = serializeMessage(
                numRequiredSignatures,
                numReadonlySignedAccounts,
                numReadonlyUnsignedAccounts,
                accountKeys,
                message.getRecentBlockHash(),
                compiledInstructions
        );
        List<String> requiredSigners = new ArrayList<String>(accountKeys.subList(0, numRequiredSignatures));
        return new CompiledMessage(messageBytes, requiredSigners, accountKeys);
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
            out.write(ix.programIdIndex);
            out.writeBytes(ShortVec.encodeLength(ix.accountIndexes.size()));
            for (Integer idx : ix.accountIndexes) {
                out.write(idx.intValue());
            }
            out.writeBytes(ShortVec.encodeLength(ix.data.length));
            out.writeBytes(ix.data);
        }
        return out.toByteArray();
    }

    private byte[] buildUnsignedTransaction(CompiledMessage message) {
        int numRequiredSignatures = message.requiredSigners.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ShortVec.encodeLength(numRequiredSignatures));
        for (int i = 0; i < numRequiredSignatures; i++) {
            out.writeBytes(new byte[64]);
        }
        out.writeBytes(message.messageBytes);
        return out.toByteArray();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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

    private static class CompiledInstruction {
        private final int programIdIndex;
        private final List<Integer> accountIndexes;
        private final byte[] data;

        private CompiledInstruction(int programIdIndex, List<Integer> accountIndexes, byte[] data) {
            this.programIdIndex = programIdIndex;
            this.accountIndexes = accountIndexes;
            this.data = data;
        }
    }

    private static class CompiledMessage {
        private final byte[] messageBytes;
        private final List<String> requiredSigners;
        private final List<String> accountKeys;

        private CompiledMessage(byte[] messageBytes, List<String> requiredSigners, List<String> accountKeys) {
            this.messageBytes = messageBytes;
            this.requiredSigners = requiredSigners;
            this.accountKeys = accountKeys;
        }
    }

    private static class MetaFlags {
        private boolean signer;
        private boolean writable;

        private MetaFlags(boolean signer, boolean writable) {
            this.signer = signer;
            this.writable = writable;
        }
    }

    public static class BuildResult {
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

        public byte[] getMessageBytes() {
            return messageBytes;
        }

        public byte[] getUnsignedTransactionBytes() {
            return unsignedTransactionBytes;
        }

        public String getMessageBase64() {
            return messageBase64;
        }

        public String getUnsignedTransactionBase64() {
            return unsignedTransactionBase64;
        }

        public List<String> getRequiredSigners() {
            return requiredSigners;
        }

        public List<String> getAccountKeys() {
            return accountKeys;
        }
    }
}
