package org.example.sol;

import org.example.sol.sdk.AccountMeta;
import org.example.sol.sdk.Message;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class LegacyTransactionSerializer {
    public BuildResult serializeUnsigned(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        byte[] messageBytes = message.serialize();
        if (messageBytes.length < 1) {
            throw new IllegalArgumentException("serialized message is empty");
        }

        int numRequiredSignatures = messageBytes[0] & 0xFF;
        List<String> accountKeys = toBase58Keys(message.getAccountKeys());
        if (numRequiredSignatures > accountKeys.size()) {
            throw new IllegalStateException("required signer count exceeds account keys size");
        }

        List<String> requiredSigners = new ArrayList<String>(accountKeys.subList(0, numRequiredSignatures));
        byte[] unsignedTx = buildUnsignedTransaction(messageBytes, numRequiredSignatures);

        return new BuildResult(
                messageBytes,
                unsignedTx,
                Base64.getEncoder().encodeToString(messageBytes),
                Base64.getEncoder().encodeToString(unsignedTx),
                requiredSigners,
                accountKeys
        );
    }

    private List<String> toBase58Keys(List<AccountMeta> accountMetas) {
        List<String> out = new ArrayList<String>();
        for (AccountMeta accountMeta : accountMetas) {
            out.add(accountMeta.getPublicKey().toBase58());
        }
        return out;
    }

    private byte[] buildUnsignedTransaction(byte[] messageBytes, int numRequiredSignatures) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ShortVec.encodeLength(numRequiredSignatures));
        for (int i = 0; i < numRequiredSignatures; i++) {
            out.writeBytes(new byte[64]);
        }
        out.writeBytes(messageBytes);
        return out.toByteArray();
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
