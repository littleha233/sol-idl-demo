package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.sol.LegacyTransactionBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTransactionBuilderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TESTDATA_DIR = Path.of("testdata", "set-safe");

    @Test
    void buildLegacyTxFromRealSetSafeIdl() throws Exception {
        JsonNode idl = readJson("idl.json");
        String instructionName = Files.readString(TESTDATA_DIR.resolve("instruction-name.txt")).trim();
        JsonNode accounts = readJson("accounts.json");
        JsonNode args = readJson("args.json");

        LegacyTransactionBuilder builder = new LegacyTransactionBuilder();
        LegacyTransactionBuilder.RuntimeOptions runtimeOptions =
                new LegacyTransactionBuilder.RuntimeOptions(
                        "8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H",
                        "3smDPkLLW8pUE3NADVQ4tMsANRvDWkfb6xm5ydj5Lc7n",
                        Integer.valueOf(200_000),
                        Long.valueOf(1_000L),
                        null
                );

        LegacyTransactionBuilder.BuildRequest request = LegacyTransactionBuilder.BuildRequest.fromJson(
                idl,
                instructionName,
                accounts,
                args,
                runtimeOptions
        );
        LegacyTransactionBuilder.BuildResult result = builder.build(request);

        assertFalse(result.messageBase64().isBlank());
        assertFalse(result.unsignedTransactionBase64().isBlank());
        assertEquals(1, result.requiredSigners().size());
        assertEquals("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H", result.requiredSigners().get(0));
        assertTrue(result.accountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
        assertTrue(result.accountKeys().contains("G413572PbWwbmEHkZ7WJePXLmaHp8AFnTUn9Hw4iUnLx"));
    }

    private JsonNode readJson(String filename) throws Exception {
        return MAPPER.readTree(Files.readString(TESTDATA_DIR.resolve(filename)));
    }
}
