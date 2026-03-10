package org.example.sol.sdk;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private final List<Instruction> instructions = new ArrayList<Instruction>();
    private String feePayer;
    private String recentBlockHash;

    public void addInstruction(Instruction instruction) {
        this.instructions.add(instruction);
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public String getFeePayer() {
        return feePayer;
    }

    public void setFeePayer(String feePayer) {
        this.feePayer = feePayer;
    }

    public String getRecentBlockHash() {
        return recentBlockHash;
    }

    public void setRecentBlockHash(String recentBlockHash) {
        this.recentBlockHash = recentBlockHash;
    }
}
