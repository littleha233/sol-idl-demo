package org.example.sol.sdk;

import java.util.List;

public class AccountMeta {
    private final PublicKey publicKey;
    private final boolean signer;
    private final boolean writable;

    public AccountMeta(PublicKey publicKey, boolean signer, boolean writable) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey is required");
        }
        this.publicKey = publicKey;
        this.signer = signer;
        this.writable = writable;
    }

    public AccountMeta(String pubkey, boolean signer, boolean writable) {
        this(new PublicKey(pubkey), signer, writable);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public boolean isSigner() {
        return signer;
    }

    public boolean isWritable() {
        return writable;
    }

    public static int findAccountIndex(List<AccountMeta> list, PublicKey key) {
        if (list == null || key == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (key.equals(list.get(i).getPublicKey())) {
                return i;
            }
        }
        return -1;
    }
}
