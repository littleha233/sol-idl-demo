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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SolIdlRegistryServiceImpl implements SolIdlRegistryService {

    private static final String DEFAULT_IDL_RESOURCE_PATH = "idl/contracts-config.json";

    @Value("${cold.idl.config-path:" + DEFAULT_IDL_RESOURCE_PATH + "}")
    private String configPath;

    private final Map<String, SolIdlContractMeta> contractMap = new ConcurrentHashMap<String, SolIdlContractMeta>();
    private final Map<String, SolIdlOperationMeta> operationMap = new ConcurrentHashMap<String, SolIdlOperationMeta>();
    private final Map<String, String> operationContractMap = new ConcurrentHashMap<String, String>();
    private final Map<String, JSONObject> idlCache = new ConcurrentHashMap<String, JSONObject>();

    private volatile JSONObject registryRoot;
    private volatile String classpathBaseDir;

    @PostConstruct
    public void init() {
        loadIdlConfig();
    }

    @Override
    public List<ContractInfoDto> listContracts() {
        List<SolIdlContractMeta> contracts = new ArrayList<SolIdlContractMeta>(contractMap.values());
        contracts.sort((a, b) -> a.getContractId().compareTo(b.getContractId()));

        List<ContractInfoDto> out = new ArrayList<ContractInfoDto>(contracts.size());
        for (SolIdlContractMeta contract : contracts) {
            out.add(toContractInfo(contract));
        }
        return out;
    }

    @Override
    public List<OperationInfoDto> listOperations(String contractId) {
        SolIdlContractMeta contract = getContractById(contractId);
        List<SolIdlOperationMeta> operations = contract.getOperationList();
        if (operations == null) {
            return Collections.emptyList();
        }

        List<OperationInfoDto> out = new ArrayList<OperationInfoDto>(operations.size());
        for (SolIdlOperationMeta operation : operations) {
            out.add(toOperationInfo(contract.getContractId(), operation));
        }
        return out;
    }

    @Override
    public OperationDetailDto getOperationDetail(String contractId, String operationId) {
        OpCtx opCtx = getOpCtx(contractId, operationId);
        JSONObject idl = readIdlByPath(opCtx.idlPathRaw);
        JSONObject instruction = findInstruction(idl, opCtx.instructionName);

        OperationDetailDto detail = new OperationDetailDto();
        detail.setContractId(opCtx.contract.getContractId());
        detail.setOperationId(opCtx.operation.getOperationId());
        detail.setOperationKey(opCtx.operation.getOperationKey());
        detail.setDisplayName(opCtx.operation.getDisplayName());
        detail.setContractAddress(opCtx.contract.getContractAddress());
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
        OpCtx opCtx = getOpCtx(contractId, operationId);
        JSONObject idl = readIdlByPath(opCtx.idlPathRaw);
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
                opCtx.contract.getContractId(),
                opCtx.operation.getOperationId(),
                opCtx.operation.getOperationKey(),
                opCtx.instructionName,
                opCtx.contract.getContractAddress(),
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

    private void loadIdlConfig() {
        try {
            byte[] configBytes = loadConfigBytes();
            SolIdlRegistryConfig config = JSON.parseObject(configBytes, SolIdlRegistryConfig.class);
            if (config == null || config.getContracts() == null || config.getContracts().isEmpty()) {
                throw new IllegalStateException("sol idl config is empty, path=" + configPath);
            }

            registryRoot = JSON.parseObject(configBytes, JSONObject.class);

            contractMap.clear();
            operationMap.clear();
            operationContractMap.clear();
            idlCache.clear();

            for (SolIdlContractMeta contract : config.getContracts()) {
                validateContractMeta(contract);
                contractMap.put(contract.getContractId(), contract);

                List<SolIdlOperationMeta> operations = contract.getOperationList();
                if (operations == null) {
                    continue;
                }
                for (SolIdlOperationMeta operation : operations) {
                    validateOperationMeta(contract, operation);
                    String operationCode = buildOperationCode(contract.getContractId(), operation.getOperationId());
                    operation.setOperationCode(operationCode);
                    operationMap.put(operationCode, operation);
                    operationContractMap.put(operationCode, contract.getContractId());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("load sol idl config failed, path=" + configPath, e);
        }
    }

    private byte[] loadConfigBytes() throws Exception {
        ClassPathResource resource = new ClassPathResource(configPath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("config not found in classpath: " + configPath);
        }
        classpathBaseDir = buildClasspathBaseDir(configPath);
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private OpCtx getOpCtx(String contractId, String operationId) {
        String operationCode = buildOperationCode(contractId, operationId);
        SolIdlOperationMeta operation = operationMap.get(operationCode);
        if (operation == null) {
            throw new IllegalArgumentException("operation not found by operationCode=" + operationCode);
        }

        String ownerContractId = operationContractMap.get(operationCode);
        SolIdlContractMeta contract = contractMap.get(ownerContractId);
        if (contract == null) {
            throw new IllegalStateException("contract not found by contractId=" + ownerContractId);
        }

        String idlPathRaw = resolveIdlPathRaw(contract, operation);
        String instructionName = resolveInstructionName(contract, operation);
        return new OpCtx(contract, operation, idlPathRaw, instructionName);
    }

    private SolIdlContractMeta getContractById(String contractId) {
        if (isBlank(contractId)) {
            throw new IllegalArgumentException("contractId is required");
        }
        SolIdlContractMeta contract = contractMap.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("contract not found by contractId=" + contractId);
        }
        return contract;
    }

    private String resolveIdlPathRaw(SolIdlContractMeta contract, SolIdlOperationMeta operation) {
        if (operation.getSolIdl() != null && !isBlank(operation.getSolIdl().getIdlPath())) {
            return operation.getSolIdl().getIdlPath();
        }
        if (isBlank(contract.getIdlPath())) {
            throw new IllegalArgumentException("idlPath is missing for contractId=" + contract.getContractId());
        }
        return contract.getIdlPath();
    }

    private String resolveInstructionName(SolIdlContractMeta contract, SolIdlOperationMeta operation) {
        if (operation.getSolIdl() != null && !isBlank(operation.getSolIdl().getInstructionName())) {
            return operation.getSolIdl().getInstructionName();
        }
        if (isBlank(operation.getOperationKey())) {
            throw new IllegalArgumentException("operationKey is required for contractId=" + contract.getContractId());
        }
        return operation.getOperationKey();
    }

    private JSONObject readIdlByPath(String idlPathRaw) {
        String location = resolveLocation(idlPathRaw);
        JSONObject cached = idlCache.get(location);
        if (cached != null) {
            return cached;
        }
        try {
            JSONObject parsed = JSON.parseObject(readBytesByLocation(location), JSONObject.class);
            idlCache.put(location, parsed);
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read IDL: " + location, e);
        }
    }

    private byte[] readBytesByLocation(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("IDL classpath resource not found: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String resolveLocation(String idlPathRaw) {
        if (isBlank(idlPathRaw)) {
            throw new IllegalArgumentException("idlPath is blank");
        }

        if (idlPathRaw.startsWith("classpath:")) {
            return idlPathRaw.substring("classpath:".length());
        }
        if (idlPathRaw.startsWith("/")) {
            return idlPathRaw.substring(1);
        }
        return classpathBaseDir + idlPathRaw;
    }

    private String buildOperationCode(String contractId, String operationId) {
        return contractId + ":" + operationId;
    }

    private String buildClasspathBaseDir(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }
        return path.substring(0, idx + 1);
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

    private ContractInfoDto toContractInfo(SolIdlContractMeta contract) {
        ContractInfoDto dto = new ContractInfoDto();
        dto.setContractId(contract.getContractId());
        dto.setContractKey(contract.getContractKey());
        dto.setDisplayName(contract.getDisplayName());
        dto.setContractAddress(contract.getContractAddress());
        dto.setCoinId(contract.getCoinId());
        dto.setChainCoinId(contract.getChainCoinId());
        dto.setChainName(contract.getChainName());
        return dto;
    }

    private OperationInfoDto toOperationInfo(String contractId, SolIdlOperationMeta operation) {
        OperationInfoDto dto = new OperationInfoDto();
        dto.setContractId(contractId);
        dto.setOperationId(operation.getOperationId());
        dto.setOperationKey(operation.getOperationKey());
        dto.setDisplayName(operation.getDisplayName());
        return dto;
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

    private void validateContractMeta(SolIdlContractMeta contract) {
        if (contract == null || isBlank(contract.getContractId())) {
            throw new IllegalArgumentException("contractId is required");
        }
        if (isBlank(contract.getContractAddress())) {
            throw new IllegalArgumentException("contractAddress is required, contractId=" + contract.getContractId());
        }
        if (contractMap.containsKey(contract.getContractId())) {
            throw new IllegalArgumentException("duplicate contractId=" + contract.getContractId());
        }
    }

    private void validateOperationMeta(SolIdlContractMeta contract, SolIdlOperationMeta operation) {
        if (operation == null || isBlank(operation.getOperationId())) {
            throw new IllegalArgumentException("operationId is required, contractId=" + contract.getContractId());
        }
        if (isBlank(operation.getOperationKey())) {
            throw new IllegalArgumentException("operationKey is required, contractId=" + contract.getContractId());
        }
        String operationCode = buildOperationCode(contract.getContractId(), operation.getOperationId());
        if (operationMap.containsKey(operationCode)) {
            throw new IllegalArgumentException("duplicate operationCode=" + operationCode);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static class OpCtx {
        private final SolIdlContractMeta contract;
        private final SolIdlOperationMeta operation;
        private final String idlPathRaw;
        private final String instructionName;

        private OpCtx(
                SolIdlContractMeta contract,
                SolIdlOperationMeta operation,
                String idlPathRaw,
                String instructionName
        ) {
            this.contract = contract;
            this.operation = operation;
            this.idlPathRaw = idlPathRaw;
            this.instructionName = instructionName;
        }
    }

    public static class SolIdlRegistryConfig {
        private List<SolIdlContractMeta> contracts;

        public List<SolIdlContractMeta> getContracts() {
            return contracts;
        }

        public void setContracts(List<SolIdlContractMeta> contracts) {
            this.contracts = contracts;
        }
    }

    public static class SolIdlContractMeta {
        private String contractId;
        private String contractKey;
        private String displayName;
        private String contractAddress;
        private Integer coinId;
        private Integer chainCoinId;
        private String chainName;
        private String idlPath;
        private Map<String, String> accounts;
        private List<SolIdlOperationMeta> operationList;

        public String getContractId() {
            return contractId;
        }

        public void setContractId(String contractId) {
            this.contractId = contractId;
        }

        public String getContractKey() {
            return contractKey;
        }

        public void setContractKey(String contractKey) {
            this.contractKey = contractKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getContractAddress() {
            return contractAddress;
        }

        public void setContractAddress(String contractAddress) {
            this.contractAddress = contractAddress;
        }

        public Integer getCoinId() {
            return coinId;
        }

        public void setCoinId(Integer coinId) {
            this.coinId = coinId;
        }

        public Integer getChainCoinId() {
            return chainCoinId;
        }

        public void setChainCoinId(Integer chainCoinId) {
            this.chainCoinId = chainCoinId;
        }

        public String getChainName() {
            return chainName;
        }

        public void setChainName(String chainName) {
            this.chainName = chainName;
        }

        public String getIdlPath() {
            return idlPath;
        }

        public void setIdlPath(String idlPath) {
            this.idlPath = idlPath;
        }

        public Map<String, String> getAccounts() {
            return accounts;
        }

        public void setAccounts(Map<String, String> accounts) {
            this.accounts = accounts;
        }

        public List<SolIdlOperationMeta> getOperationList() {
            return operationList;
        }

        public void setOperationList(List<SolIdlOperationMeta> operationList) {
            this.operationList = operationList;
        }
    }

    public static class SolIdlOperationMeta {
        private String operationId;
        private String operationKey;
        private String displayName;
        private String operationCode;
        private SolIdlOperationConfig solIdl;
        private Map<String, String> accounts;

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public String getOperationKey() {
            return operationKey;
        }

        public void setOperationKey(String operationKey) {
            this.operationKey = operationKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getOperationCode() {
            return operationCode;
        }

        public void setOperationCode(String operationCode) {
            this.operationCode = operationCode;
        }

        public SolIdlOperationConfig getSolIdl() {
            return solIdl;
        }

        public void setSolIdl(SolIdlOperationConfig solIdl) {
            this.solIdl = solIdl;
        }

        public Map<String, String> getAccounts() {
            return accounts;
        }

        public void setAccounts(Map<String, String> accounts) {
            this.accounts = accounts;
        }
    }

    public static class SolIdlOperationConfig {
        private String idlPath;
        private String instructionName;
        private Map<String, String> accounts;

        public String getIdlPath() {
            return idlPath;
        }

        public void setIdlPath(String idlPath) {
            this.idlPath = idlPath;
        }

        public String getInstructionName() {
            return instructionName;
        }

        public void setInstructionName(String instructionName) {
            this.instructionName = instructionName;
        }

        public Map<String, String> getAccounts() {
            return accounts;
        }

        public void setAccounts(Map<String, String> accounts) {
            this.accounts = accounts;
        }
    }
}
