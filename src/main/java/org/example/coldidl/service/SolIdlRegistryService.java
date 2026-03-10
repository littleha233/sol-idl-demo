package org.example.coldidl.service;

import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface SolIdlRegistryService {
    List<ContractInfoDto> listContracts();

    List<OperationInfoDto> listOperations(String contractId);

    OperationDetailDto getOperationDetail(String contractId, String operationId);

    BuildMeta resolveBuildMeta(String contractId, String operationId);

    List<Object> buildOrderedParamList(BuildMeta buildMeta, Map<String, Object> paramMap);

    Path getConfigPath();

    class BuildMeta {
        private final String contractId;
        private final String operationId;
        private final String operationKey;
        private final String instructionName;
        private final String contractAddress;
        private final List<String> argNames;

        public BuildMeta(
                String contractId,
                String operationId,
                String operationKey,
                String instructionName,
                String contractAddress,
                List<String> argNames
        ) {
            this.contractId = contractId;
            this.operationId = operationId;
            this.operationKey = operationKey;
            this.instructionName = instructionName;
            this.contractAddress = contractAddress;
            this.argNames = argNames;
        }

        public String getContractId() {
            return contractId;
        }

        public String getOperationId() {
            return operationId;
        }

        public String getOperationKey() {
            return operationKey;
        }

        public String getInstructionName() {
            return instructionName;
        }

        public String getContractAddress() {
            return contractAddress;
        }

        public List<String> getArgNames() {
            return argNames;
        }
    }
}
