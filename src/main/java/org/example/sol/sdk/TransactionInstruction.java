package org.example.sol.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransactionInstruction {
    private final PublicKey programId;
    private final List<AccountMeta> keys;
    private final byte[] data;

    public TransactionInstruction(PublicKey programId, List<AccountMeta> keys, byte[] data) {
        if (programId == null) {
            throw new IllegalArgumentException("programId is required");
        }
        if (keys == null) {
            throw new IllegalArgumentException("keys is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
        this.programId = programId;
        this.keys = Collections.unmodifiableList(new ArrayList<AccountMeta>(keys));
        this.data = data.clone();
    }

    public PublicKey getProgramId() {
        return programId;
    }

    public List<AccountMeta> getKeys() {
        return keys;
    }

    public byte[] getData() {
        return data.clone();
    }
}
