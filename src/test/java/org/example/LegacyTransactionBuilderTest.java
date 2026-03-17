package org.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.example.project.SolIdlProject;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.LegacyTransactionSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTransactionBuilderTest {
    private static final String SET_SAFE_TESTDATA_DIR = "testdata/set-safe/";

    @Test
    void buildLegacyTxFromSolContractConfigAndIdlParamList() throws Exception {
        LegacyTransactionSerializer.BuildResult result = buildTx("set_safe", "param-list.json");

        assertFalse(result.getMessageBase64().isBlank());
        assertFalse(result.getUnsignedTransactionBase64().isBlank());
        assertEquals(1, result.getRequiredSigners().size());
        assertEquals("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H", result.getRequiredSigners().get(0));
        assertTrue(result.getAccountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
        assertTrue(result.getAccountKeys().contains("G413572PbWwbmEHkZ7WJePXLmaHp8AFnTUn9Hw4iUnLx"));
    }

    @Test
    void buildLegacyTxForSetOperator() throws Exception {
        LegacyTransactionSerializer.BuildResult result = buildTx("set_operator", "param-list-set-operator.json");
        String rawData = result.getUnsignedTransactionBase64();
        System.out.println(rawData);

        assertFalse(result.getMessageBase64().isBlank());
        assertFalse(result.getUnsignedTransactionBase64().isBlank());
        assertEquals(1, result.getRequiredSigners().size());
        assertEquals("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H", result.getRequiredSigners().get(0));
        assertTrue(result.getAccountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
    }

    private LegacyTransactionSerializer.BuildResult buildTx(String operationCode, String paramListFile) throws Exception {
        BuildTxReq<SolIdlTxBuildExt> request = new BuildTxReq<SolIdlTxBuildExt>();
        request.setFrom("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H");

        SolIdlTxBuildExt ext = new SolIdlTxBuildExt();
        ext.setTo("BHbxLfy5YPYKyrsTXr8cVzBnyKJYY9CGs5ozMzctKxvf");
        ext.setOperationCode(operationCode);
        ext.setParamList(readParamList(paramListFile));
        request.setExt(ext);

        SolIdlProject project = new SolIdlProject();
        return project.buildTx(request);
    }

    private List<Object> readParamList(String filename) throws Exception {
        try (InputStream is = new ClassPathResource(SET_SAFE_TESTDATA_DIR + filename).getInputStream()) {
            JSONArray jsonArray = JSON.parseArray(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            return jsonArray.toJavaList(Object.class);
        }
    }
}
