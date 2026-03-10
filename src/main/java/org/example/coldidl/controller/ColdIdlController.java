package org.example.coldidl.controller;

import org.example.coldidl.biz.ColdIdlBiz;
import org.example.coldidl.dto.BuildTxFromMapReq;
import org.example.coldidl.dto.BuildTxResultDto;
import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;

import java.util.List;

public class ColdIdlController {
    private final ColdIdlBiz coldIdlBiz;

    public ColdIdlController(ColdIdlBiz coldIdlBiz) {
        if (coldIdlBiz == null) {
            throw new IllegalArgumentException("coldIdlBiz is required");
        }
        this.coldIdlBiz = coldIdlBiz;
    }

    public List<ContractInfoDto> getContracts() {
        return coldIdlBiz.listContracts();
    }

    public List<OperationInfoDto> getContractOperations(String contractId) {
        return coldIdlBiz.listOperations(contractId);
    }

    public OperationDetailDto getOperationDetail(String contractId, String operationId) {
        return coldIdlBiz.getOperationDetail(contractId, operationId);
    }

    public BuildTxResultDto buildTx(BuildTxFromMapReq req) throws Exception {
        return coldIdlBiz.buildTransaction(req);
    }
}
