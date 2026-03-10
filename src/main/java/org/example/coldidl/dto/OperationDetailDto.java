package org.example.coldidl.dto;

import java.util.ArrayList;
import java.util.List;

public class OperationDetailDto {
    private String contractId;
    private String operationId;
    private String operationKey;
    private String displayName;
    private String contractAddress;
    private String programId;
    private String instructionName;
    private List<ArgFieldDto> argFields = new ArrayList<ArgFieldDto>();
    private List<AccountFieldDto> accountFields = new ArrayList<AccountFieldDto>();

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

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

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getProgramId() {
        return programId;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    public String getInstructionName() {
        return instructionName;
    }

    public void setInstructionName(String instructionName) {
        this.instructionName = instructionName;
    }

    public List<ArgFieldDto> getArgFields() {
        return argFields;
    }

    public void setArgFields(List<ArgFieldDto> argFields) {
        this.argFields = argFields;
    }

    public List<AccountFieldDto> getAccountFields() {
        return accountFields;
    }

    public void setAccountFields(List<AccountFieldDto> accountFields) {
        this.accountFields = accountFields;
    }
}
