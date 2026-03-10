package org.example.coldidl.biz;

import org.example.coldidl.dto.BuildTxFromMapReq;
import org.example.coldidl.dto.BuildTxResultDto;
import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;

import java.util.List;

public interface ColdIdlBiz {
    List<ContractInfoDto> listContracts();

    List<OperationInfoDto> listOperations(String contractId);

    OperationDetailDto getOperationDetail(String contractId, String operationId);

    BuildTxResultDto buildTransaction(BuildTxFromMapReq req) throws Exception;
}
