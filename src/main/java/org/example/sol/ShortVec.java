package org.example.sol;

import java.io.ByteArrayOutputStream;

public final class ShortVec {
    private ShortVec() {}

    public static byte[] encodeLength(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int remaining = value;
        while (true) {
            int elem = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining == 0) {
                out.write(elem);
                break;
            }
            out.write(elem | 0x80);
        }
        return out.toByteArray();
    }
}
