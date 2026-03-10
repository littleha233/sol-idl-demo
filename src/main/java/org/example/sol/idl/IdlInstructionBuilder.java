package org.example.sol.idl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.sol.BorshEncoder;
import org.example.sol.sdk.AccountMeta;
import org.example.sol.sdk.Instruction;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IdlInstructionBuilder {

    public Instruction buildInstruction(
            Path idlPath,
            String instructionName,
            Map<String, String> accounts,
            List<Object> paramList
    ) throws Exception {
        JSONObject idlRoot = JSON.parseObject(Files.readString(idlPath));
        JSONObject instructionNode = findInstruction(idlRoot, instructionName);
        String programId = requireText(idlRoot.get("address"), "IDL program address");

        List<AccountSpec> specs = new ArrayList<AccountSpec>();
        collectAccountSpecs(instructionNode.getJSONArray("accounts"), "", specs);

        List<AccountMeta> metas = new ArrayList<AccountMeta>();
        for (AccountSpec spec : specs) {
            String pubkey = spec.address;
            if (pubkey == null) {
                pubkey = accounts.get(spec.lookupKey);
            }
            if (pubkey == null) {
                throw new IllegalArgumentException("Missing account pubkey for: " + spec.lookupKey);
            }
            metas.add(new AccountMeta(pubkey, spec.signer, spec.writable));
        }

        byte[] data = encodeInstructionData(instructionNode, paramList);
        return new Instruction(programId, metas, data);
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

    private byte[] encodeInstructionData(JSONObject instructionNode, List<Object> paramList) {
        List<Object> params = paramList == null ? Collections.<Object>emptyList() : paramList;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BorshEncoder.discriminatorFromIdlOrAnchor(instructionNode));

        JSONArray argsDef = instructionNode.getJSONArray("args");
        if (argsDef == null || argsDef.isEmpty()) {
            if (!params.isEmpty()) {
                throw new IllegalArgumentException("paramList must be empty when instruction args are empty");
            }
            return out.toByteArray();
        }
        if (argsDef.size() != params.size()) {
            throw new IllegalArgumentException(
                    "paramList size mismatch, expected=" + argsDef.size() + ", actual=" + params.size()
            );
        }
        for (int i = 0; i < argsDef.size(); i++) {
            JSONObject argDef = argsDef.getJSONObject(i);
            Object type = argDef.get("type");
            Object value = params.get(i);
            out.writeBytes(BorshEncoder.encodeType(type, value));
        }
        return out.toByteArray();
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
            out.add(new AccountSpec(prefix + name, signer, writable, address));
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

        private AccountSpec(String lookupKey, boolean signer, boolean writable, String address) {
            this.lookupKey = lookupKey;
            this.signer = signer;
            this.writable = writable;
            this.address = address;
        }
    }
}
