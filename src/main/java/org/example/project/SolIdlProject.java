package org.example.project;

import org.example.sol.LegacyTransactionSerializer;
import org.example.sol.idl.IdlInstructionBuilder;
import org.example.sol.sdk.ComputeBudgetProgram;
import org.example.sol.sdk.Instruction;
import org.example.sol.sdk.Message;
import org.example.sol.sdk.SystemProgram;

public class SolIdlProject {
    private final IdlInstructionBuilder idlInstructionBuilder = new IdlInstructionBuilder();
    private final LegacyTransactionSerializer serializer = new LegacyTransactionSerializer();

    public LegacyTransactionSerializer.BuildResult buildIdlTemplateTx(IdlTemplateTxReq req) throws Exception {
        Message message = new Message();

        NonceInfo nonceInfo = getNonceAccount(req.getFrom());
        message.addInstruction(SystemProgram.nonceAdvance(nonceInfo.getNonceAccount(), req.getFrom()));
        message.setRecentBlockHash(nonceInfo.getNonceValue());

        // mock gas parameters, keep it local as requested.
        Long mockedGasPrice = Long.valueOf(1_000L);
        Integer mockedGasLimit = Integer.valueOf(200_000);
        message.addInstruction(ComputeBudgetProgram.setComputeUnitPrice(mockedGasPrice.longValue()));
        message.addInstruction(ComputeBudgetProgram.setComputeUnitLimit(mockedGasLimit.intValue()));

        message.setFeePayer(req.getFrom());

        Instruction idlInstruction = idlInstructionBuilder.buildInstruction(
                req.getIdlPath(),
                req.getInstructionName(),
                req.getAccounts(),
                req.getArgs()
        );
        message.addInstruction(idlInstruction);

        return serializer.serializeUnsigned(message);
    }

    protected NonceInfo getNonceAccount(String from) {
        // mock nonce retrieval (replace with real RPC query later).
        return new NonceInfo(
                "6Yq5j8hdkzTpi7yG7r62oV8rB6W4ko29z6hS9kuGBTHP",
                "3smDPkLLW8pUE3NADVQ4tMsANRvDWkfb6xm5ydj5Lc7n"
        );
    }

    public static class NonceInfo {
        private final String nonceAccount;
        private final String nonceValue;

        public NonceInfo(String nonceAccount, String nonceValue) {
            this.nonceAccount = nonceAccount;
            this.nonceValue = nonceValue;
        }

        public String getNonceAccount() {
            return nonceAccount;
        }

        public String getNonceValue() {
            return nonceValue;
        }
    }
}
