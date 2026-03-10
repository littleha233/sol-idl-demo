package org.example.sol.sdk;

public class AccountMeta {
    private final String pubkey;
    private final boolean signer;
    private final boolean writable;

    public AccountMeta(String pubkey, boolean signer, boolean writable) {
        this.pubkey = pubkey;
        this.signer = signer;
        this.writable = writable;
    }

    public String getPubkey() {
        return pubkey;
    }

    public boolean isSigner() {
        return signer;
    }

    public boolean isWritable() {
        return writable;
    }
}
