package org.example.sol.sdk;

import org.example.sol.Base58;

import java.util.Arrays;

public class PublicKey {
    public static final int PUBLIC_KEY_LENGTH = 32;

    private final byte[] bytes;

    public PublicKey(String base58Value) {
        this(Base58.decode(base58Value));
    }

    public PublicKey(byte[] bytes) {
        if (bytes == null || bytes.length != PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("PublicKey must be 32 bytes");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public String toBase58() {
        return Base58.encode(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PublicKey)) {
            return false;
        }
        PublicKey other = (PublicKey) obj;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
