package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.IdlTemplateTxReq;
import org.example.project.SolIdlProject;
import org.example.sol.LegacyTransactionSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTransactionBuilderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TESTDATA_DIR = Path.of("testdata", "set-safe");

    @Test
    void buildLegacyTxFromRealSetSafeIdlByProjectStyle() throws Exception {
        String instructionName = Files.readString(TESTDATA_DIR.resolve("instruction-name.txt")).trim();
        JsonNode accounts = readJson("accounts.json");
        JsonNode args = readJson("args.json");
        IdlTemplateTxReq request = new IdlTemplateTxReq(
                "8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H",
                TESTDATA_DIR.resolve("idl.json"),
                instructionName,
                toStringMap(accounts),
                toObjectMap(args)
        );
        SolIdlProject project = new SolIdlProject();
        LegacyTransactionSerializer.BuildResult result = project.buildIdlTemplateTx(request);

        assertFalse(result.getMessageBase64().isBlank());
        assertFalse(result.getUnsignedTransactionBase64().isBlank());
        assertEquals(1, result.getRequiredSigners().size());
        assertEquals("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H", result.getRequiredSigners().get(0));
        assertTrue(result.getAccountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
        assertTrue(result.getAccountKeys().contains("G413572PbWwbmEHkZ7WJePXLmaHp8AFnTUn9Hw4iUnLx"));
    }

    private JsonNode readJson(String filename) throws Exception {
        return MAPPER.readTree(Files.readString(TESTDATA_DIR.resolve(filename)));
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asText());
        }
        return out;
    }

    private static Map<String, Object> toObjectMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class));
        }
        return out;
    }
}
