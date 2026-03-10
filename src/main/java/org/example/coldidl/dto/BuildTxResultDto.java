package org.example.coldidl.dto;

import java.util.ArrayList;
import java.util.List;

public class BuildTxResultDto {
    private String contractId;
    private String operationId;
    private String operationKey;
    private String instructionName;
    private String messageBase64;
    private String unsignedLegacyTransactionBase64;
    private List<String> requiredSigners = new ArrayList<String>();
    private List<String> accountKeys = new ArrayList<String>();

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

    public String getInstructionName() {
        return instructionName;
    }

    public void setInstructionName(String instructionName) {
        this.instructionName = instructionName;
    }

    public String getMessageBase64() {
        return messageBase64;
    }

    public void setMessageBase64(String messageBase64) {
        this.messageBase64 = messageBase64;
    }

    public String getUnsignedLegacyTransactionBase64() {
        return unsignedLegacyTransactionBase64;
    }

    public void setUnsignedLegacyTransactionBase64(String unsignedLegacyTransactionBase64) {
        this.unsignedLegacyTransactionBase64 = unsignedLegacyTransactionBase64;
    }

    public List<String> getRequiredSigners() {
        return requiredSigners;
    }

    public void setRequiredSigners(List<String> requiredSigners) {
        this.requiredSigners = requiredSigners;
    }

    public List<String> getAccountKeys() {
        return accountKeys;
    }

    public void setAccountKeys(List<String> accountKeys) {
        this.accountKeys = accountKeys;
    }
}
