package org.example.coldidl.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.coldidl.dto.AccountFieldDto;
import org.example.coldidl.dto.ArgFieldDto;
import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;
import org.example.coldidl.service.SolIdlRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SolIdlRegistryServiceImpl implements SolIdlRegistryService {
    private final Path configPath;
    private final JSONObject root;

    @Autowired
    public SolIdlRegistryServiceImpl(
            @Value("${cold.idl.config-path:testdata/contracts-config.json}") String configPath
    ) throws Exception {
        this(Path.of(configPath));
    }

    public SolIdlRegistryServiceImpl(Path configPath) throws Exception {
        if (configPath == null) {
            throw new IllegalArgumentException("configPath is required");
        }
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("Config file not found: " + configPath);
        }
        this.configPath = configPath.toAbsolutePath().normalize();
        this.root = JSON.parseObject(Files.readString(configPath));
    }

    @Override
    public List<ContractInfoDto> listContracts() {
        JSONArray contracts = root.getJSONArray("contracts");
        if (contracts == null) {
            return Collections.emptyList();
        }
        List<ContractInfoDto> out = new ArrayList<ContractInfoDto>();
        for (int i = 0; i < contracts.size(); i++) {
            JSONObject contract = contracts.getJSONObject(i);
            out.add(toContractInfo(contract));
        }
        return out;
    }

    @Override
    public List<OperationInfoDto> listOperations(String contractId) {
        JSONObject contract = findContractById(contractId);
        JSONArray operations = contract.getJSONArray("operationList");
        if (operations == null) {
            return Collections.emptyList();
        }
        List<OperationInfoDto> out = new ArrayList<OperationInfoDto>();
        for (int i = 0; i < operations.size(); i++) {
            JSONObject op = operations.getJSONObject(i);
            out.add(toOperationInfo(contract.getString("contractId"), op));
        }
        return out;
    }

    @Override
    public OperationDetailDto getOperationDetail(String contractId, String operationId) {
        OpCtx opCtx = findOpCtx(contractId, operationId);
        JSONObject idl = readIdl(opCtx.idlPath);
        JSONObject instruction = findInstruction(idl, opCtx.instructionName);

        OperationDetailDto detail = new OperationDetailDto();
        detail.setContractId(opCtx.contract.getString("contractId"));
        detail.setOperationId(opCtx.operation.getString("operationId"));
        detail.setOperationKey(opCtx.operation.getString("operationKey"));
        detail.setDisplayName(opCtx.operation.getString("displayName"));
        detail.setContractAddress(opCtx.contract.getString("contractAddress"));
        detail.setProgramId(idl.getString("address"));
        detail.setInstructionName(opCtx.instructionName);

        detail.setArgFields(readArgFields(instruction.getJSONArray("args")));
        List<AccountFieldDto> accountFields = new ArrayList<AccountFieldDto>();
        collectAccountFields(instruction.getJSONArray("accounts"), "", accountFields);
        detail.setAccountFields(accountFields);
        return detail;
    }

    @Override
    public BuildMeta resolveBuildMeta(String contractId, String operationId) {
        OpCtx opCtx = findOpCtx(contractId, operationId);
        JSONObject idl = readIdl(opCtx.idlPath);
        JSONObject instruction = findInstruction(idl, opCtx.instructionName);

        JSONArray args = instruction.getJSONArray("args");
        List<String> argNames = new ArrayList<String>();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                JSONObject arg = args.getJSONObject(i);
                argNames.add(arg.getString("name"));
            }
        }
        return new BuildMeta(
                opCtx.contract.getString("contractId"),
                opCtx.operation.getString("operationId"),
                opCtx.operation.getString("operationKey"),
                opCtx.instructionName,
                opCtx.contract.getString("contractAddress"),
                argNames
        );
    }

    @Override
    public List<Object> buildOrderedParamList(BuildMeta buildMeta, Map<String, Object> paramMap) {
        if (buildMeta == null) {
            throw new IllegalArgumentException("buildMeta is required");
        }
        Map<String, Object> args = paramMap == null ? Collections.<String, Object>emptyMap() : paramMap;
        List<Object> ordered = new ArrayList<Object>();
        for (String argName : buildMeta.getArgNames()) {
            if (!args.containsKey(argName)) {
                throw new IllegalArgumentException("Missing required param: " + argName);
            }
            ordered.add(args.get(argName));
        }
        return ordered;
    }

    @Override
    public Path getConfigPath() {
        return configPath;
    }

    private List<ArgFieldDto> readArgFields(JSONArray args) {
        if (args == null) {
            return Collections.emptyList();
        }
        List<ArgFieldDto> out = new ArrayList<ArgFieldDto>();
        for (int i = 0; i < args.size(); i++) {
            JSONObject arg = args.getJSONObject(i);
            ArgFieldDto dto = new ArgFieldDto();
            dto.setName(arg.getString("name"));
            dto.setType(renderType(arg.get("type")));
            dto.setRequired(true);
            out.add(dto);
        }
        return out;
    }

    private void collectAccountFields(JSONArray accounts, String prefix, List<AccountFieldDto> out) {
        if (accounts == null) {
            return;
        }
        for (int i = 0; i < accounts.size(); i++) {
            JSONObject account = accounts.getJSONObject(i);
            String name = account.getString("name");
            JSONArray nested = account.getJSONArray("accounts");
            if (nested != null && !nested.isEmpty()) {
                collectAccountFields(nested, prefix + name + ".", out);
                continue;
            }

            AccountFieldDto dto = new AccountFieldDto();
            dto.setName(prefix + name);
            dto.setSigner(account.containsKey("signer")
                    ? account.getBooleanValue("signer")
                    : account.getBooleanValue("isSigner"));
            dto.setWritable(account.containsKey("writable")
                    ? account.getBooleanValue("writable")
                    : account.getBooleanValue("isMut"));

            if (account.containsKey("address")) {
                dto.setSource("FIXED");
                dto.setValueHint(account.getString("address"));
            } else if (account.containsKey("pda")) {
                dto.setSource("PDA");
                dto.setValueHint(JSON.toJSONString(account.getJSONObject("pda")));
            } else if ("authority".equals(name)) {
                dto.setSource("FROM");
                dto.setValueHint("use request.from");
            } else {
                dto.setSource("INPUT");
                dto.setValueHint("front-end should provide account pubkey");
            }
            out.add(dto);
        }
    }

    private ContractInfoDto toContractInfo(JSONObject contract) {
        ContractInfoDto dto = new ContractInfoDto();
        dto.setContractId(contract.getString("contractId"));
        dto.setContractKey(contract.getString("contractKey"));
        dto.setDisplayName(contract.getString("displayName"));
        dto.setContractAddress(contract.getString("contractAddress"));
        dto.setCoinId(contract.getInteger("coinId"));
        dto.setChainCoinId(contract.getInteger("chainCoinId"));
        dto.setChainName(contract.getString("chainName"));
        return dto;
    }

    private OperationInfoDto toOperationInfo(String contractId, JSONObject operation) {
        OperationInfoDto dto = new OperationInfoDto();
        dto.setContractId(contractId);
        dto.setOperationId(operation.getString("operationId"));
        dto.setOperationKey(operation.getString("operationKey"));
        dto.setDisplayName(operation.getString("displayName"));
        return dto;
    }

    private OpCtx findOpCtx(String contractId, String operationId) {
        JSONObject contract = findContractById(contractId);
        JSONArray operations = contract.getJSONArray("operationList");
        if (operations == null) {
            throw new IllegalArgumentException("operationList is missing for contractId=" + contractId);
        }
        for (int i = 0; i < operations.size(); i++) {
            JSONObject operation = operations.getJSONObject(i);
            if (!operationId.equals(operation.getString("operationId"))) {
                continue;
            }
            Path idlPath = resolveIdlPath(contract, operation);
            String instructionName = resolveInstructionName(contract, operation);
            return new OpCtx(contract, operation, idlPath, instructionName);
        }
        throw new IllegalArgumentException("Operation not found by contractId=" + contractId + ", operationId=" + operationId);
    }

    private JSONObject findContractById(String contractId) {
        if (isBlank(contractId)) {
            throw new IllegalArgumentException("contractId is required");
        }
        JSONArray contracts = root.getJSONArray("contracts");
        if (contracts == null) {
            throw new IllegalArgumentException("contracts array is missing");
        }
        for (int i = 0; i < contracts.size(); i++) {
            JSONObject contract = contracts.getJSONObject(i);
            if (contractId.equals(contract.getString("contractId"))) {
                return contract;
            }
        }
        throw new IllegalArgumentException("contract not found by contractId=" + contractId);
    }

    private Path resolveIdlPath(JSONObject contract, JSONObject operation) {
        JSONObject solIdl = operation.getJSONObject("solIdl");
        String idlPathRaw;
        if (solIdl != null && !isBlank(solIdl.getString("idlPath"))) {
            idlPathRaw = solIdl.getString("idlPath");
        } else {
            idlPathRaw = contract.getString("idlPath");
        }
        if (isBlank(idlPathRaw)) {
            throw new IllegalArgumentException("idlPath is missing for contractId=" + contract.getString("contractId"));
        }

        Path idlPath = Path.of(idlPathRaw);
        if (idlPath.isAbsolute()) {
            return idlPath.normalize();
        }
        Path parent = configPath.getParent();
        if (parent == null) {
            return idlPath.toAbsolutePath().normalize();
        }
        return parent.resolve(idlPath).normalize();
    }

    private String resolveInstructionName(JSONObject contract, JSONObject operation) {
        JSONObject solIdl = operation.getJSONObject("solIdl");
        if (solIdl != null && !isBlank(solIdl.getString("instructionName"))) {
            return solIdl.getString("instructionName");
        }
        String opKey = operation.getString("operationKey");
        if (isBlank(opKey)) {
            throw new IllegalArgumentException("operationKey is required for contractId=" + contract.getString("contractId"));
        }
        return opKey;
    }

    private JSONObject readIdl(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("IDL file not found: " + path);
            }
            return JSON.parseObject(Files.readString(path));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read IDL file: " + path, e);
        }
    }

    private JSONObject findInstruction(JSONObject idl, String instructionName) {
        JSONArray instructions = idl.getJSONArray("instructions");
        if (instructions == null) {
            throw new IllegalArgumentException("IDL instructions missing");
        }
        for (int i = 0; i < instructions.size(); i++) {
            JSONObject instruction = instructions.getJSONObject(i);
            if (instructionName.equals(instruction.getString("name"))) {
                return instruction;
            }
        }
        throw new IllegalArgumentException("Instruction not found in IDL: " + instructionName);
    }

    private String renderType(Object type) {
        if (type == null) {
            return "unknown";
        }
        if (type instanceof String) {
            return (String) type;
        }
        return JSON.toJSONString(type);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static class OpCtx {
        private final JSONObject contract;
        private final JSONObject operation;
        private final Path idlPath;
        private final String instructionName;

        private OpCtx(JSONObject contract, JSONObject operation, Path idlPath, String instructionName) {
            this.contract = contract;
            this.operation = operation;
            this.idlPath = idlPath;
            this.instructionName = instructionName;
        }
    }
}
