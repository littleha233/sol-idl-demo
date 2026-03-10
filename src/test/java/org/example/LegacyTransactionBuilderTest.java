package org.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.example.project.SolIdlProject;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.LegacyTransactionSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTransactionBuilderTest {
    private static final Path ROOT_TESTDATA_DIR = Path.of("testdata");
    private static final Path SET_SAFE_TESTDATA_DIR = ROOT_TESTDATA_DIR.resolve("set-safe");

    @Test
    void buildLegacyTxFromSolContractConfigAndIdlParamList() throws Exception {
        BuildTxReq<SolIdlTxBuildExt> request = new BuildTxReq<SolIdlTxBuildExt>();
        request.setFrom("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H");

        SolIdlTxBuildExt ext = new SolIdlTxBuildExt();
        ext.setTo("BHbxLfy5YPYKyrsTXr8cVzBnyKJYY9CGs5ozMzctKxvf");
        ext.setOperationCode("set_safe");
        ext.setParamList(readParamList("param-list.json"));
        request.setExt(ext);

        SolIdlProject project = new SolIdlProject(ROOT_TESTDATA_DIR.resolve("contracts-config.json"));
        LegacyTransactionSerializer.BuildResult result = project.buildTx(request);

        assertFalse(result.getMessageBase64().isBlank());
        assertFalse(result.getUnsignedTransactionBase64().isBlank());
        assertEquals(1, result.getRequiredSigners().size());
        assertEquals("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H", result.getRequiredSigners().get(0));
        assertTrue(result.getAccountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
        assertTrue(result.getAccountKeys().contains("G413572PbWwbmEHkZ7WJePXLmaHp8AFnTUn9Hw4iUnLx"));
    }

    private List<Object> readParamList(String filename) throws Exception {
        JSONArray jsonArray = JSON.parseArray(Files.readString(SET_SAFE_TESTDATA_DIR.resolve(filename)));
        return jsonArray.toJavaList(Object.class);
    }
}
