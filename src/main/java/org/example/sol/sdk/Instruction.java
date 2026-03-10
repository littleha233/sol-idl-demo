package org.example.sol.sdk;

import java.util.List;

public class Instruction {
    private final String programId;
    private final List<AccountMeta> accounts;
    private final byte[] data;

    public Instruction(String programId, List<AccountMeta> accounts, byte[] data) {
        this.programId = programId;
        this.accounts = accounts;
        this.data = data;
    }

    public String getProgramId() {
        return programId;
    }

    public List<AccountMeta> getAccounts() {
        return accounts;
    }

    public byte[] getData() {
        return data;
    }
}
