package org.example.sol.sdk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class SystemProgram {
    private static final String PROGRAM_ID = "11111111111111111111111111111111";
    private static final String SYSVAR_RECENT_BLOCKHASHES = "SysvarRecentB1ockHashes11111111111111111111";

    private SystemProgram() {}

    public static Instruction nonceAdvance(String nonceAccount, String nonceAuthority) {
        ByteBuffer data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(4);

        List<AccountMeta> metas = new ArrayList<AccountMeta>();
        metas.add(new AccountMeta(nonceAccount, false, true));
        metas.add(new AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false));
        metas.add(new AccountMeta(nonceAuthority, true, false));
        return new Instruction(PROGRAM_ID, metas, data.array());
    }
}
