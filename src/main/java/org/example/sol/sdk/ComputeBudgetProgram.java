package org.example.sol.sdk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;

public final class ComputeBudgetProgram {
    private static final String PROGRAM_ID = "ComputeBudget111111111111111111111111111111";

    private ComputeBudgetProgram() {}

    public static Instruction setComputeUnitPrice(long microLamports) {
        if (microLamports < 0) {
            throw new IllegalArgumentException("compute unit price must be >= 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 8).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 3);
        data.putLong(microLamports);
        return new Instruction(PROGRAM_ID, Collections.<AccountMeta>emptyList(), data.array());
    }

    public static Instruction setComputeUnitLimit(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("compute unit limit must be > 0");
        }
        ByteBuffer data = ByteBuffer.allocate(1 + 4).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 2);
        data.putInt(units);
        return new Instruction(PROGRAM_ID, Collections.<AccountMeta>emptyList(), data.array());
    }
}
