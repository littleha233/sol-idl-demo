package org.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.example.project.SolIdlProject;
import org.example.project.util.SolIdlConfigUtil;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.Base58;
import org.example.sol.LegacyTransactionSerializer;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return;
        }

        String from = args[0];
        String contractAddress = args[1];
        String operationCode = args[2];
        String paramListResourcePath = args[3];

        SolIdlProject project = new SolIdlProject();
        BuildTxReq<SolIdlTxBuildExt> req = new BuildTxReq<SolIdlTxBuildExt>();
        req.setFrom(from);
        req.setExt(buildExt(contractAddress, operationCode, paramListResourcePath));

        LegacyTransactionSerializer.BuildResult result = project.buildTx(req);
        SolIdlConfigUtil.ResolvedSolOperation operation = project.resolveOperation(contractAddress, operationCode);
        JSONObject idl = readJsonObject(operation.getIdlResourcePath());

        JSONObject output = new JSONObject(true);
        output.put("operationCode", operationCode);
        output.put("instruction", operation.getInstructionName());
        output.put("programId", idl.getString("address"));
        output.put("messageBase64", result.getMessageBase64());
        output.put("messageBase58", Base58.encode(result.getMessageBytes()));
        output.put("unsignedLegacyTransactionBase64", result.getUnsignedTransactionBase64());
        output.put("requiredSignerCount", result.getRequiredSigners().size());
        output.put("requiredSigners", result.getRequiredSigners());
        output.put("accountKeys", result.getAccountKeys());

        System.out.println(JSON.toJSONString(output, true));
    }

    private static SolIdlTxBuildExt buildExt(String contractAddress, String operationCode, String paramListResourcePath) throws Exception {
        JSONArray paramListJson = readJsonArray(paramListResourcePath);
        List<Object> paramList = paramListJson.toJavaList(Object.class);

        SolIdlTxBuildExt ext = new SolIdlTxBuildExt();
        ext.setTo(contractAddress);
        ext.setOperationCode(operationCode);
        ext.setParamList(paramList);
        return ext;
    }

    private static JSONObject readJsonObject(String resourcePath) throws Exception {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            return JSON.parseObject(is.readAllBytes(), JSONObject.class);
        }
    }

    private static JSONArray readJsonArray(String resourcePath) throws Exception {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            return JSON.parseArray(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void printUsage() {
        String usage =
                "Usage:\n" +
                "  java -cp target/sol-idl-demo-1.0-SNAPSHOT.jar org.example.Main \\\n" +
                "    <from> <contractAddress> <operationCode> <param-list-resource>\n\n" +
                "Notes:\n" +
                "- 默认从 classpath 固定读取 idl/contracts-config.json。\n" +
                "- from 通过请求参数传入，不放在配置文件。\n" +
                "- gas 与 nonce 在 SolIdlProject 内部局部 mock。\n" +
                "- param-list-resource 例如 testdata/set-safe/param-list.json。\n" +
                "- 该工具只构建 legacy 交易，不签名。\n";
        System.out.println(usage);
    }
}
