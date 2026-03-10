package org.example.sol.idl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.sol.BorshEncoder;
import org.example.sol.PdaUtil;
import org.example.sol.sdk.AccountMeta;
import org.example.sol.sdk.PublicKey;
import org.example.sol.sdk.TransactionInstruction;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IdlInstructionBuilder {

    public TransactionInstruction buildInstruction(
            Path idlPath,
            String instructionName,
            Map<String, String> accounts,
            List<Object> paramList
    ) throws Exception {
        JSONObject idlRoot = JSON.parseObject(Files.readString(idlPath));
        JSONObject instructionNode = findInstruction(idlRoot, instructionName);
        String programId = requireText(idlRoot.get("address"), "IDL program address");

        ArgContext argContext = buildArgContext(instructionNode, paramList);

        List<AccountSpec> specs = new ArrayList<AccountSpec>();
        collectAccountSpecs(instructionNode.getJSONArray("accounts"), "", specs);

        Map<String, String> resolvedAccounts = new LinkedHashMap<String, String>();
        List<AccountMeta> metas = new ArrayList<AccountMeta>();
        for (AccountSpec spec : specs) {
            String pubkey = spec.address;
            if (pubkey == null) {
                pubkey = lookupAccount(spec.lookupKey, accounts, resolvedAccounts);
            }
            if (pubkey == null && spec.pda != null) {
                pubkey = derivePdaAddress(spec.pda, programId, accounts, resolvedAccounts, argContext);
            }
            if (pubkey == null) {
                throw new IllegalArgumentException("Missing account pubkey for: " + spec.lookupKey);
            }

            resolvedAccounts.put(spec.lookupKey, pubkey);
            addLeafAlias(spec.lookupKey, pubkey, resolvedAccounts);
            metas.add(new AccountMeta(new PublicKey(pubkey), spec.signer, spec.writable));
        }

        byte[] data = encodeInstructionData(instructionNode, argContext);
        return new TransactionInstruction(new PublicKey(programId), metas, data);
    }

    private JSONObject findInstruction(JSONObject idlRoot, String instructionName) {
        JSONArray instructions = idlRoot.getJSONArray("instructions");
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("IDL instructions is missing");
        }
        for (int i = 0; i < instructions.size(); i++) {
            JSONObject ix = instructions.getJSONObject(i);
            if (instructionName.equals(ix.getString("name"))) {
                return ix;
            }
        }
        throw new IllegalArgumentException("Instruction not found in IDL: " + instructionName);
    }

    private ArgContext buildArgContext(JSONObject instructionNode, List<Object> paramList) {
        List<Object> params = paramList == null ? Collections.<Object>emptyList() : paramList;
        JSONArray argsDef = instructionNode.getJSONArray("args");
        if (argsDef == null || argsDef.isEmpty()) {
            if (!params.isEmpty()) {
                throw new IllegalArgumentException("paramList must be empty when instruction args are empty");
            }
            return new ArgContext(new LinkedHashMap<String, Object>(), new LinkedHashMap<String, Object>(), argsDef);
        }

        if (argsDef.size() != params.size()) {
            throw new IllegalArgumentException(
                    "paramList size mismatch, expected=" + argsDef.size() + ", actual=" + params.size()
            );
        }

        Map<String, Object> valueByName = new LinkedHashMap<String, Object>();
        Map<String, Object> typeByName = new LinkedHashMap<String, Object>();
        for (int i = 0; i < argsDef.size(); i++) {
            JSONObject argDef = argsDef.getJSONObject(i);
            String name = requireText(argDef.get("name"), "arg.name");
            valueByName.put(name, params.get(i));
            typeByName.put(name, argDef.get("type"));
        }
        return new ArgContext(valueByName, typeByName, argsDef);
    }

    private byte[] encodeInstructionData(JSONObject instructionNode, ArgContext argContext) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BorshEncoder.discriminatorFromIdlOrAnchor(instructionNode));

        JSONArray argsDef = argContext.argsDef;
        if (argsDef == null || argsDef.isEmpty()) {
            return out.toByteArray();
        }
        for (int i = 0; i < argsDef.size(); i++) {
            JSONObject argDef = argsDef.getJSONObject(i);
            String argName = argDef.getString("name");
            Object type = argDef.get("type");
            Object value = argContext.valueByName.get(argName);
            out.writeBytes(BorshEncoder.encodeType(type, value));
        }
        return out.toByteArray();
    }

    private String derivePdaAddress(
            JSONObject pda,
            String programId,
            Map<String, String> requestAccounts,
            Map<String, String> resolvedAccounts,
            ArgContext argContext
    ) {
        JSONArray seeds = pda.getJSONArray("seeds");
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("pda.seeds must not be empty");
        }

        List<byte[]> seedBytes = new ArrayList<byte[]>();
        for (int i = 0; i < seeds.size(); i++) {
            JSONObject seed = seeds.getJSONObject(i);
            String kind = requireText(seed.get("kind"), "pda.seed.kind");
            if ("const".equals(kind)) {
                seedBytes.add(readConstSeed(seed));
            } else if ("account".equals(kind)) {
                String path = requireText(seed.get("path"), "pda.seed.path");
                String accountPubkey = lookupAccount(path, requestAccounts, resolvedAccounts);
                if (accountPubkey == null) {
                    throw new IllegalArgumentException("Cannot resolve PDA account seed path: " + path);
                }
                seedBytes.add(BorshEncoder.encodePubkey(accountPubkey));
            } else if ("arg".equals(kind)) {
                String path = requireText(seed.get("path"), "pda.seed.path");
                seedBytes.add(encodeArgSeed(path, argContext));
            } else {
                throw new IllegalArgumentException("Unsupported PDA seed kind: " + kind);
            }
        }

        PdaUtil.PdaResult pdaResult = PdaUtil.findProgramAddress(seedBytes, programId);
        return pdaResult.getAddress();
    }

    private byte[] encodeArgSeed(String path, ArgContext argContext) {
        Object value = argContext.valueByName.get(path);
        Object type = argContext.typeByName.get(path);
        if (value == null || type == null) {
            throw new IllegalArgumentException("Cannot resolve PDA arg seed path: " + path);
        }
        return BorshEncoder.encodeType(type, value);
    }

    private byte[] readConstSeed(JSONObject seedNode) {
        JSONArray value = seedNode.getJSONArray("value");
        if (value == null) {
            Object strValue = seedNode.get("string");
            if (strValue instanceof String) {
                return ((String) strValue).getBytes(StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("const PDA seed requires 'value' byte array or 'string'");
        }
        byte[] seed = new byte[value.size()];
        for (int i = 0; i < value.size(); i++) {
            seed[i] = (byte) value.getIntValue(i);
        }
        return seed;
    }

    private String lookupAccount(String path, Map<String, String> requestAccounts, Map<String, String> resolvedAccounts) {
        String fromResolved = resolvedAccounts.get(path);
        if (fromResolved != null) {
            return fromResolved;
        }
        String fromRequest = requestAccounts.get(path);
        if (fromRequest != null) {
            return fromRequest;
        }
        if (path.contains(".")) {
            String leaf = path.substring(path.lastIndexOf('.') + 1);
            fromResolved = resolvedAccounts.get(leaf);
            if (fromResolved != null) {
                return fromResolved;
            }
            return requestAccounts.get(leaf);
        }
        return null;
    }

    private void addLeafAlias(String lookupKey, String pubkey, Map<String, String> resolvedAccounts) {
        if (lookupKey == null || pubkey == null || !lookupKey.contains(".")) {
            return;
        }
        String leaf = lookupKey.substring(lookupKey.lastIndexOf('.') + 1);
        resolvedAccounts.putIfAbsent(leaf, pubkey);
    }

    private void collectAccountSpecs(JSONArray accountsNode, String prefix, List<AccountSpec> out) {
        if (accountsNode == null || accountsNode.isEmpty()) {
            return;
        }
        for (int i = 0; i < accountsNode.size(); i++) {
            JSONObject accountNode = accountsNode.getJSONObject(i);
            JSONArray nested = accountNode.getJSONArray("accounts");
            String name = requireText(accountNode.get("name"), "account.name");
            if (nested != null && !nested.isEmpty()) {
                collectAccountSpecs(nested, prefix + name + ".", out);
                continue;
            }

            boolean writable = accountNode.containsKey("writable")
                    ? accountNode.getBooleanValue("writable")
                    : accountNode.getBooleanValue("isMut");
            boolean signer = accountNode.containsKey("signer")
                    ? accountNode.getBooleanValue("signer")
                    : accountNode.getBooleanValue("isSigner");
            String address = accountNode.getString("address");
            JSONObject pda = accountNode.getJSONObject("pda");
            out.add(new AccountSpec(prefix + name, signer, writable, address, pda));
        }
    }

    private String requireText(Object node, String fieldName) {
        if (!(node instanceof String) || ((String) node).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be string");
        }
        return (String) node;
    }

    private static class AccountSpec {
        private final String lookupKey;
        private final boolean signer;
        private final boolean writable;
        private final String address;
        private final JSONObject pda;

        private AccountSpec(String lookupKey, boolean signer, boolean writable, String address, JSONObject pda) {
            this.lookupKey = lookupKey;
            this.signer = signer;
            this.writable = writable;
            this.address = address;
            this.pda = pda;
        }
    }

    private static class ArgContext {
        private final Map<String, Object> valueByName;
        private final Map<String, Object> typeByName;
        private final JSONArray argsDef;

        private ArgContext(Map<String, Object> valueByName, Map<String, Object> typeByName, JSONArray argsDef) {
            this.valueByName = valueByName;
            this.typeByName = typeByName;
            this.argsDef = argsDef;
        }
    }
}
