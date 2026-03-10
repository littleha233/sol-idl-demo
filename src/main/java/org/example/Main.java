package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.project.IdlTemplateTxReq;
import org.example.project.SolIdlProject;
import org.example.sol.Base58;
import org.example.sol.LegacyTransactionSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return;
        }

        Path idlPath = Path.of(args[0]);
        String instructionName = args[1];
        Path accountsPath = Path.of(args[2]);
        Path ixArgsPath = Path.of(args[3]);

        JsonNode accounts = readJson(accountsPath);
        JsonNode ixArgs = readJson(ixArgsPath);

        // local runtime variable, same style as project-level from parameter.
        String fromAddress = accounts.path("authority").asText("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H");
        IdlTemplateTxReq req = new IdlTemplateTxReq(
                fromAddress,
                idlPath,
                instructionName,
                toStringMap(accounts),
                toObjectMap(ixArgs)
        );

        SolIdlProject project = new SolIdlProject();
        LegacyTransactionSerializer.BuildResult result = project.buildIdlTemplateTx(req);

        JsonNode idl = readJson(idlPath);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("instruction", instructionName);
        output.put("programId", idl.path("address").asText());
        output.put("messageBase64", result.getMessageBase64());
        output.put("messageBase58", Base58.encode(result.getMessageBytes()));
        output.put("unsignedLegacyTransactionBase64", result.getUnsignedTransactionBase64());
        output.put("requiredSignerCount", result.getRequiredSigners().size());
        output.set("requiredSigners", MAPPER.valueToTree(result.getRequiredSigners()));
        output.set("accountKeys", MAPPER.valueToTree(result.getAccountKeys()));

        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output));
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
                "    <idl.json> <instructionName> <accounts.json> <ixArgs.json>\n\n" +
                "Notes:\n" +
                "- 运行时参数（nonce、gasPrice、gasLimit）在 SolIdlProject 内部局部变量 mock。\n" +
                "- from 参数由方法参数传递（参考 SolProject 风格），不放在配置文件。\n" +
                "- 该工具只构建 legacy 交易，不签名。\n";
        System.out.println(usage);
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        flattenStringMap(node, "", out);
        return out;
    }

    private static Map<String, Object> toObjectMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (node == null || !node.isObject()) {
            return out;
        }
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class));
        }
        return out;
    }

    private static void flattenStringMap(JsonNode node, String prefix, Map<String, String> out) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() != null && e.getValue().isObject()) {
                flattenStringMap(e.getValue(), key, out);
            } else {
                out.put(key, e.getValue().asText());
            }
        }
    }
}
