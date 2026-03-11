package org.example.coldidl.biz.impl;

import org.example.coldidl.biz.ColdIdlBiz;
import org.example.coldidl.dto.BuildTxFromMapReq;
import org.example.coldidl.dto.BuildTxResultDto;
import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;
import org.example.coldidl.service.SolIdlRegistryService;
import org.example.project.SolIdlProject;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.LegacyTransactionSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ColdIdlBizImpl implements ColdIdlBiz {

    @Autowired
    private SolIdlRegistryService solIdlRegistryService;

    @Override
    public List<ContractInfoDto> listContracts() {
        return solIdlRegistryService.listContracts();
    }

    @Override
    public List<OperationInfoDto> listOperations(String contractId) {
        return solIdlRegistryService.listOperations(contractId);
    }

    @Override
    public OperationDetailDto getOperationDetail(String contractId, String operationId) {
        return solIdlRegistryService.getOperationDetail(contractId, operationId);
    }

    @Override
    public BuildTxResultDto buildTransaction(BuildTxFromMapReq req) throws Exception {
        validateReq(req);

        SolIdlRegistryService.BuildMeta buildMeta = solIdlRegistryService.resolveBuildMeta(
                req.getContractId(),
                req.getOperationId()
        );

        List<Object> orderedParamList = solIdlRegistryService.buildOrderedParamList(buildMeta, req.getParamMap());

        BuildTxReq<SolIdlTxBuildExt> txReq = new BuildTxReq<SolIdlTxBuildExt>();
        txReq.setFrom(req.getFrom());

        SolIdlTxBuildExt ext = new SolIdlTxBuildExt();
        ext.setTo(buildMeta.getContractAddress());
        ext.setOperationCode(buildMeta.getOperationKey());
        ext.setParamList(orderedParamList);
        txReq.setExt(ext);

        SolIdlProject solIdlProject = new SolIdlProject(solIdlRegistryService.getConfigPath());
        LegacyTransactionSerializer.BuildResult built = solIdlProject.buildTx(txReq);

        BuildTxResultDto out = new BuildTxResultDto();
        out.setContractId(buildMeta.getContractId());
        out.setOperationId(buildMeta.getOperationId());
        out.setOperationKey(buildMeta.getOperationKey());
        out.setInstructionName(buildMeta.getInstructionName());
        out.setMessageBase64(built.getMessageBase64());
        out.setUnsignedLegacyTransactionBase64(built.getUnsignedTransactionBase64());
        out.setRequiredSigners(built.getRequiredSigners());
        out.setAccountKeys(built.getAccountKeys());
        return out;
    }

    private void validateReq(BuildTxFromMapReq req) {
        if (req == null) {
            throw new IllegalArgumentException("build tx request is required");
        }
        if (isBlank(req.getContractId())) {
            throw new IllegalArgumentException("contractId is required");
        }
        if (isBlank(req.getOperationId())) {
            throw new IllegalArgumentException("operationId is required");
        }
        if (isBlank(req.getFrom())) {
            throw new IllegalArgumentException("from is required");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
