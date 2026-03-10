package org.example.sol.idl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.example.sol.BorshEncoder;
import org.example.sol.sdk.AccountMeta;
import org.example.sol.sdk.Instruction;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IdlInstructionBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Instruction buildInstruction(
            Path idlPath,
            String instructionName,
            Map<String, String> accounts,
            Map<String, Object> args
    ) throws Exception {
        JsonNode idlRoot = MAPPER.readTree(Files.readString(idlPath));
        JsonNode instructionNode = findInstruction(idlRoot, instructionName);
        String programId = requireText(idlRoot.get("address"), "IDL program address");

        List<AccountSpec> specs = new ArrayList<AccountSpec>();
        collectAccountSpecs(instructionNode.get("accounts"), "", specs);

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

        byte[] data = encodeInstructionData(instructionNode, args);
        return new Instruction(programId, metas, data);
    }

    private JsonNode findInstruction(JsonNode idlRoot, String instructionName) {
        JsonNode instructions = idlRoot.get("instructions");
        if (instructions == null || !instructions.isArray()) {
            throw new IllegalArgumentException("IDL instructions is missing");
        }
        for (JsonNode ix : instructions) {
            if (instructionName.equals(ix.path("name").asText())) {
                return ix;
            }
        }
        throw new IllegalArgumentException("Instruction not found in IDL: " + instructionName);
    }

    private byte[] encodeInstructionData(JsonNode instructionNode, Map<String, Object> args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BorshEncoder.discriminatorFromIdlOrAnchor(instructionNode));

        JsonNode argsDef = instructionNode.get("args");
        if (argsDef != null && argsDef.isArray()) {
            for (JsonNode argDef : argsDef) {
                String name = requireText(argDef.get("name"), "arg.name");
                JsonNode type = argDef.get("type");
                Object value = args.get(name);
                JsonNode valueNode = value == null ? NullNode.getInstance() : MAPPER.valueToTree(value);
                out.writeBytes(BorshEncoder.encodeType(type, valueNode));
            }
        }
        return out.toByteArray();
    }

    private void collectAccountSpecs(JsonNode accountsNode, String prefix, List<AccountSpec> out) {
        if (accountsNode == null || !accountsNode.isArray()) {
            return;
        }
        for (JsonNode accountNode : accountsNode) {
            JsonNode nested = accountNode.get("accounts");
            String name = requireText(accountNode.get("name"), "account.name");
            if (nested != null && nested.isArray()) {
                collectAccountSpecs(nested, prefix + name + ".", out);
                continue;
            }
            boolean writable = accountNode.has("writable")
                    ? accountNode.get("writable").asBoolean()
                    : accountNode.path("isMut").asBoolean(false);
            boolean signer = accountNode.has("signer")
                    ? accountNode.get("signer").asBoolean()
                    : accountNode.path("isSigner").asBoolean(false);
            String address = accountNode.has("address") ? accountNode.get("address").asText() : null;
            out.add(new AccountSpec(prefix + name, signer, writable, address));
        }
    }

    private String requireText(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be string");
        }
        return node.asText();
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
