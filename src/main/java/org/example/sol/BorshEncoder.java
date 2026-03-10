package org.example.sol;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class BorshEncoder {
    private BorshEncoder() {}

    public static byte[] encodeType(JsonNode typeNode, JsonNode valueNode) {
        if (typeNode == null) {
            throw new IllegalArgumentException("IDL arg type is missing");
        }
        if (typeNode.isTextual()) {
            return encodePrimitive(typeNode.asText(), valueNode);
        }
        if (typeNode.isObject()) {
            if (typeNode.has("option")) {
                return encodeOption(typeNode.get("option"), valueNode);
            }
            if (typeNode.has("vec")) {
                return encodeVec(typeNode.get("vec"), valueNode);
            }
            if (typeNode.has("array")) {
                return encodeArray(typeNode.get("array"), valueNode);
            }
            if (typeNode.has("defined")) {
                throw new IllegalArgumentException("defined type is not supported in this demo");
            }
        }
        throw new IllegalArgumentException("Unsupported IDL type node: " + typeNode);
    }

    private static byte[] encodeOption(JsonNode innerType, JsonNode valueNode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (valueNode == null || valueNode.isNull()) {
            out.write(0);
            return out.toByteArray();
        }
        out.write(1);
        writeAll(out, encodeType(innerType, valueNode));
        return out.toByteArray();
    }

    private static byte[] encodeVec(JsonNode innerType, JsonNode valueNode) {
        if (valueNode == null || !valueNode.isArray()) {
            throw new IllegalArgumentException("vec expects a JSON array");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, leU32(valueNode.size()));
        for (JsonNode item : valueNode) {
            writeAll(out, encodeType(innerType, item));
        }
        return out.toByteArray();
    }

    private static byte[] encodeArray(JsonNode arrayDef, JsonNode valueNode) {
        if (!arrayDef.isArray() || arrayDef.size() != 2) {
            throw new IllegalArgumentException("array type must be [innerType, length]");
        }
        JsonNode innerType = arrayDef.get(0);
        int length = arrayDef.get(1).asInt();
        if (valueNode == null || !valueNode.isArray() || valueNode.size() != length) {
            throw new IllegalArgumentException("array expects JSON array with fixed length " + length);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (JsonNode item : valueNode) {
            writeAll(out, encodeType(innerType, item));
        }
        return out.toByteArray();
    }

    private static byte[] encodePrimitive(String type, JsonNode valueNode) {
        switch (type) {
            case "bool":
                return new byte[]{(byte) (asBoolean(valueNode) ? 1 : 0)};
            case "u8":
                return new byte[]{(byte) asUnsigned(valueNode, 8).intValueExact()};
            case "i8":
                return new byte[]{(byte) asSigned(valueNode, 8).intValueExact()};
            case "u16":
                return le(asUnsigned(valueNode, 16), 2);
            case "i16":
                return leSigned(asSigned(valueNode, 16), 2);
            case "u32":
                return le(asUnsigned(valueNode, 32), 4);
            case "i32":
                return leSigned(asSigned(valueNode, 32), 4);
            case "u64":
                return le(asUnsigned(valueNode, 64), 8);
            case "i64":
                return leSigned(asSigned(valueNode, 64), 8);
            case "u128":
                return le(asUnsigned(valueNode, 128), 16);
            case "i128":
                return leSigned(asSigned(valueNode, 128), 16);
            case "string":
                return encodeString(asString(valueNode));
            case "pubkey":
            case "publicKey":
                return encodePubkey(asString(valueNode));
            case "bytes":
                return encodeBytesValue(valueNode);
            default:
                throw new IllegalArgumentException("Unsupported primitive type: " + type);
        }
    }

    private static byte[] encodeString(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, leU32(utf8.length));
        writeAll(out, utf8);
        return out.toByteArray();
    }

    private static byte[] encodeBytesValue(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isArray()) {
            throw new IllegalArgumentException("bytes expects an array of numbers");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, leU32(valueNode.size()));
        for (JsonNode n : valueNode) {
            out.write((byte) n.asInt());
        }
        return out.toByteArray();
    }

    public static byte[] encodePubkey(String base58Pubkey) {
        byte[] raw = Base58.decode(base58Pubkey);
        if (raw.length != 32) {
            throw new IllegalArgumentException("Pubkey must decode to 32 bytes: " + base58Pubkey);
        }
        return raw;
    }

    private static boolean asBoolean(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("bool value is missing");
        }
        return node.isBoolean() ? node.asBoolean() : Boolean.parseBoolean(node.asText());
    }

    private static String asString(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("string value is missing");
        }
        return node.asText();
    }

    private static BigInteger asUnsigned(JsonNode node, int bits) {
        BigInteger n = parseBigInt(node);
        if (n.signum() < 0) {
            throw new IllegalArgumentException("unsigned integer cannot be negative: " + n);
        }
        BigInteger max = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        if (n.compareTo(max) > 0) {
            throw new IllegalArgumentException("unsigned integer overflow for u" + bits + ": " + n);
        }
        return n;
    }

    private static BigInteger asSigned(JsonNode node, int bits) {
        BigInteger n = parseBigInt(node);
        BigInteger min = BigInteger.ONE.shiftLeft(bits - 1).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        if (n.compareTo(min) < 0 || n.compareTo(max) > 0) {
            throw new IllegalArgumentException("signed integer overflow for i" + bits + ": " + n);
        }
        return n;
    }

    private static BigInteger parseBigInt(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("numeric value is missing");
        }
        if (node.isIntegralNumber()) {
            return node.bigIntegerValue();
        }
        return new BigInteger(node.asText());
    }

    private static byte[] leU32(int n) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(n);
        return buf.array();
    }

    private static byte[] le(BigInteger n, int size) {
        byte[] out = new byte[size];
        BigInteger value = n;
        for (int i = 0; i < size; i++) {
            out[i] = value.and(BigInteger.valueOf(0xFF)).byteValue();
            value = value.shiftRight(8);
        }
        return out;
    }

    private static byte[] leSigned(BigInteger n, int size) {
        BigInteger modulus = BigInteger.ONE.shiftLeft(size * 8);
        BigInteger val = n.signum() < 0 ? n.add(modulus) : n;
        return le(val, size);
    }

    private static void writeAll(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }

    public static byte[] discriminatorFromIdlOrAnchor(JsonNode instructionNode) {
        JsonNode discrNode = instructionNode.get("discriminator");
        if (discrNode != null && discrNode.isArray() && discrNode.size() > 0) {
            byte[] out = new byte[discrNode.size()];
            for (int i = 0; i < discrNode.size(); i++) {
                out[i] = (byte) discrNode.get(i).asInt();
            }
            return out;
        }
        String name = instructionNode.get("name").asText();
        return AnchorDiscriminator.global(name);
    }

    public static final class AnchorDiscriminator {
        private AnchorDiscriminator() {}

        public static byte[] global(String ixName) {
            String preimage = "global:" + ixName;
            byte[] hash = Hash.sha256(preimage.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[8];
            System.arraycopy(hash, 0, out, 0, 8);
            return out;
        }
    }

    public static final class Hash {
        private Hash() {}

        public static byte[] sha256(byte[] input) {
            try {
                return java.security.MessageDigest.getInstance("SHA-256").digest(input);
            } catch (Exception e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
    }
}
