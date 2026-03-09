package org.example.sol;

import java.util.Arrays;

public final class Base58 {
    private static final char[] ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEXES[ALPHABET[i]] = i;
        }
    }

    private Base58() {}

    public static String encode(byte[] input) {
        if (input.length == 0) {
            return "";
        }
        input = Arrays.copyOf(input, input.length);
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            zeros++;
        }
        int size = input.length * 2;
        char[] encoded = new char[size];
        int outputStart = size;
        int startAt = zeros;
        while (startAt < input.length) {
            int mod = divmod58(input, startAt);
            if (input[startAt] == 0) {
                startAt++;
            }
            encoded[--outputStart] = ALPHABET[mod];
        }
        while (outputStart < size && encoded[outputStart] == ALPHABET[0]) {
            outputStart++;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = ALPHABET[0];
        }
        return new String(encoded, outputStart, size - outputStart);
    }

    public static byte[] decode(String input) {
        if (input.isEmpty()) {
            return new byte[0];
        }
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            int digit = c < 128 ? INDEXES[c] : -1;
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base58 character: '" + c + "'");
            }
            input58[i] = (byte) digit;
        }
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == 0) {
            ++zeros;
        }
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        int startAt = zeros;
        while (startAt < input58.length) {
            int mod = divmod256(input58, startAt);
            if (input58[startAt] == 0) {
                ++startAt;
            }
            decoded[--outputStart] = (byte) mod;
        }
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            ++outputStart;
        }
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    private static int divmod58(byte[] number, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number.length; i++) {
            int digit256 = number[i] & 0xFF;
            int temp = remainder * 256 + digit256;
            number[i] = (byte) (temp / 58);
            remainder = temp % 58;
        }
        return remainder;
    }

    private static int divmod256(byte[] number58, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number58.length; i++) {
            int digit58 = number58[i] & 0xFF;
            int temp = remainder * 58 + digit58;
            number58[i] = (byte) (temp / 256);
            remainder = temp % 256;
        }
        return remainder;
    }
}
