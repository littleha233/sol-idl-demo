package org.example.sol;

import org.bouncycastle.math.ec.rfc8032.Ed25519;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PdaUtil {
    private static final byte[] PDA_MARKER = "ProgramDerivedAddress".getBytes(StandardCharsets.UTF_8);

    private PdaUtil() {}

    public static PdaResult findProgramAddress(List<byte[]> seeds, String programIdBase58) {
        if (seeds == null) {
            throw new IllegalArgumentException("seeds is required");
        }
        byte[] programId = Base58.decode(programIdBase58);
        if (programId.length != 32) {
            throw new IllegalArgumentException("programId must be 32 bytes");
        }

        List<byte[]> baseSeeds = new ArrayList<byte[]>(seeds);
        for (int bump = 255; bump >= 0; bump--) {
            List<byte[]> withBump = new ArrayList<byte[]>(baseSeeds);
            withBump.add(new byte[]{(byte) bump});
            byte[] candidate = createProgramAddressBytes(withBump, programId);
            if (!isOnCurve(candidate)) {
                return new PdaResult(Base58.encode(candidate), bump);
            }
        }
        throw new IllegalStateException("Unable to find a viable program address");
    }

    private static byte[] createProgramAddressBytes(List<byte[]> seeds, byte[] programId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] seed : seeds) {
            if (seed == null) {
                throw new IllegalArgumentException("seed must not be null");
            }
            if (seed.length > 32) {
                throw new IllegalArgumentException("Max seed length exceeded: " + seed.length);
            }
            out.writeBytes(seed);
        }
        out.writeBytes(programId);
        out.writeBytes(PDA_MARKER);
        return BorshEncoder.Hash.sha256(out.toByteArray());
    }

    private static boolean isOnCurve(byte[] keyBytes) {
        if (keyBytes.length != 32) {
            return false;
        }
        return Ed25519.validatePublicKeyFull(keyBytes, 0);
    }

    public static class PdaResult {
        private final String address;
        private final int bump;

        public PdaResult(String address, int bump) {
            this.address = address;
            this.bump = bump;
        }

        public String getAddress() {
            return address;
        }

        public int getBump() {
            return bump;
        }
    }
}
