package org.example.sol;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BorshEncoder {
    private BorshEncoder() {}

    public static byte[] encodeType(Object typeNode, Object valueNode) {
        if (typeNode == null) {
            throw new IllegalArgumentException("IDL arg type is missing");
        }
        if (typeNode instanceof String) {
            return encodePrimitive((String) typeNode, valueNode);
        }
        if (typeNode instanceof JSONObject) {
            JSONObject objectType = (JSONObject) typeNode;
            if (objectType.containsKey("option")) {
                return encodeOption(objectType.get("option"), valueNode);
            }
            if (objectType.containsKey("vec")) {
                return encodeVec(objectType.get("vec"), valueNode);
            }
            if (objectType.containsKey("array")) {
                return encodeArray(objectType.get("array"), valueNode);
            }
            if (objectType.containsKey("defined")) {
                throw new IllegalArgumentException("defined type is not supported in this demo");
            }
        }
        throw new IllegalArgumentException("Unsupported IDL type node: " + typeNode);
    }

    private static byte[] encodeOption(Object innerType, Object valueNode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (valueNode == null) {
            out.write(0);
            return out.toByteArray();
        }
        out.write(1);
        writeAll(out, encodeType(innerType, valueNode));
        return out.toByteArray();
    }

    private static byte[] encodeVec(Object innerType, Object valueNode) {
        List<Object> list = asList(valueNode, "vec");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, leU32(list.size()));
        for (Object item : list) {
            writeAll(out, encodeType(innerType, item));
        }
        return out.toByteArray();
    }

    private static byte[] encodeArray(Object arrayDef, Object valueNode) {
        if (!(arrayDef instanceof JSONArray)) {
            throw new IllegalArgumentException("array type must be [innerType, length]");
        }
        JSONArray def = (JSONArray) arrayDef;
        if (def.size() != 2) {
            throw new IllegalArgumentException("array type must be [innerType, length]");
        }
        Object innerType = def.get(0);
        int length = asInt(def.get(1), "array length");

        List<Object> values = asList(valueNode, "array");
        if (values.size() != length) {
            throw new IllegalArgumentException("array expects fixed length " + length);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object item : values) {
            writeAll(out, encodeType(innerType, item));
        }
        return out.toByteArray();
    }

    private static byte[] encodePrimitive(String type, Object valueNode) {
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

    private static byte[] encodeBytesValue(Object valueNode) {
        List<Object> list = asList(valueNode, "bytes");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(out, leU32(list.size()));
        for (Object item : list) {
            out.write((byte) asInt(item, "bytes value"));
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

    private static boolean asBoolean(Object node) {
        if (node == null) {
            throw new IllegalArgumentException("bool value is missing");
        }
        if (node instanceof Boolean) {
            return (Boolean) node;
        }
        return Boolean.parseBoolean(String.valueOf(node));
    }

    private static String asString(Object node) {
        if (node == null) {
            throw new IllegalArgumentException("string value is missing");
        }
        return String.valueOf(node);
    }

    private static BigInteger asUnsigned(Object node, int bits) {
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

    private static BigInteger asSigned(Object node, int bits) {
        BigInteger n = parseBigInt(node);
        BigInteger min = BigInteger.ONE.shiftLeft(bits - 1).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        if (n.compareTo(min) < 0 || n.compareTo(max) > 0) {
            throw new IllegalArgumentException("signed integer overflow for i" + bits + ": " + n);
        }
        return n;
    }

    private static BigInteger parseBigInt(Object node) {
        if (node == null) {
            throw new IllegalArgumentException("numeric value is missing");
        }
        if (node instanceof BigInteger) {
            return (BigInteger) node;
        }
        if (node instanceof Number) {
            Number n = (Number) node;
            if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
                return BigInteger.valueOf(n.longValue());
            }
            return new BigInteger(String.valueOf(n));
        }
        return new BigInteger(String.valueOf(node));
    }

    private static int asInt(Object node, String field) {
        if (node == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        if (node instanceof Number) {
            return ((Number) node).intValue();
        }
        return Integer.parseInt(String.valueOf(node));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object node, String field) {
        if (node == null) {
            throw new IllegalArgumentException(field + " expects an array");
        }
        if (node instanceof JSONArray) {
            return (JSONArray) node;
        }
        if (node instanceof List) {
            return (List<Object>) node;
        }
        throw new IllegalArgumentException(field + " expects an array");
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

    public static byte[] discriminatorFromIdlOrAnchor(JSONObject instructionNode) {
        JSONArray discrNode = instructionNode.getJSONArray("discriminator");
        if (discrNode != null && !discrNode.isEmpty()) {
            byte[] out = new byte[discrNode.size()];
            for (int i = 0; i < discrNode.size(); i++) {
                out[i] = (byte) discrNode.getIntValue(i);
            }
            return out;
        }
        String name = instructionNode.getString("name");
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
