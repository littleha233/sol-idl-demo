package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.project.SolIdlProject;
import org.example.project.config.SolContractRegistry;
import org.example.project.dto.BuildTxReq;
import org.example.project.dto.SolIdlTxBuildExt;
import org.example.sol.Base58;
import org.example.sol.LegacyTransactionSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            printUsage();
            return;
        }

        Path contractsConfigPath = Path.of(args[0]);
        String from = args[1];
        String contractAddress = args[2];
        String operationCode = args[3];
        Path paramListPath = Path.of(args[4]);

        SolIdlProject project = new SolIdlProject(contractsConfigPath);
        BuildTxReq<SolIdlTxBuildExt> req = new BuildTxReq<SolIdlTxBuildExt>();
        req.setFrom(from);
        req.setExt(buildExt(contractAddress, operationCode, paramListPath));

        LegacyTransactionSerializer.BuildResult result = project.buildTx(req);
        SolContractRegistry.ResolvedSolOperation operation = project.resolveOperation(contractAddress, operationCode);
        JsonNode idl = readJson(operation.getIdlPath());

        ObjectNode output = MAPPER.createObjectNode();
        output.put("operationCode", operationCode);
        output.put("instruction", operation.getInstructionName());
        output.put("programId", idl.path("address").asText());
        output.put("messageBase64", result.getMessageBase64());
        output.put("messageBase58", Base58.encode(result.getMessageBytes()));
        output.put("unsignedLegacyTransactionBase64", result.getUnsignedTransactionBase64());
        output.put("requiredSignerCount", result.getRequiredSigners().size());
        output.set("requiredSigners", MAPPER.valueToTree(result.getRequiredSigners()));
        output.set("accountKeys", MAPPER.valueToTree(result.getAccountKeys()));

        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    private static SolIdlTxBuildExt buildExt(String contractAddress, String operationCode, Path paramListPath) throws Exception {
        JsonNode paramListNode = readJson(paramListPath);
        if (!paramListNode.isArray()) {
            throw new IllegalArgumentException("paramList json must be an array");
        }
        List<Object> paramList = MAPPER.convertValue(paramListNode, List.class);

        SolIdlTxBuildExt ext = new SolIdlTxBuildExt();
        ext.setTo(contractAddress);
        ext.setOperationCode(operationCode);
        ext.setParamList(paramList);
        return ext;
    }

    private static JsonNode readJson(Path path) throws Exception {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        return MAPPER.readTree(Files.readString(path));
    }

    private static void printUsage() {
        String usage =
                "Usage:\n" +
                "  java -cp target/sol-idl-demo-1.0-SNAPSHOT.jar org.example.Main \\\n" +
                "    <contracts-config.json> <from> <contractAddress> <operationCode> <param-list.json>\n\n" +
                "Notes:\n" +
                "- contracts-config.json 内定义 SOL operation 对应的 idlPath/instructionName/accounts 模板。\n" +
                "- from 通过请求参数传入，不放在配置文件。\n" +
                "- gas 与 nonce 在 SolIdlProject 内部局部 mock。\n" +
                "- param-list.json 为数组，顺序必须与 IDL args 顺序一致。\n" +
                "- 该工具只构建 legacy 交易，不签名。\n";
        System.out.println(usage);
    }
}
