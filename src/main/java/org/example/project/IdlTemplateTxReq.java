package org.example.project;

import java.nio.file.Path;
import java.util.Map;

public class IdlTemplateTxReq {
    private final String from;
    private final Path idlPath;
    private final String instructionName;
    private final Map<String, String> accounts;
    private final Map<String, Object> args;

    public IdlTemplateTxReq(
            String from,
            Path idlPath,
            String instructionName,
            Map<String, String> accounts,
            Map<String, Object> args
    ) {
        this.from = from;
        this.idlPath = idlPath;
        this.instructionName = instructionName;
        this.accounts = accounts;
        this.args = args;
    }

    public String getFrom() {
        return from;
    }

    public Path getIdlPath() {
        return idlPath;
    }

    public String getInstructionName() {
        return instructionName;
    }

    public Map<String, String> getAccounts() {
        return accounts;
    }

    public Map<String, Object> getArgs() {
        return args;
    }
}
