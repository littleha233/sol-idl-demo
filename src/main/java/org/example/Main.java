package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.sol.Base58;
import org.example.sol.LegacyTransactionBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

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

        JsonNode idl = readJson(idlPath);
        JsonNode accounts = readJson(accountsPath);
        JsonNode ixArgs = readJson(ixArgsPath);
        LegacyTransactionBuilder builder = new LegacyTransactionBuilder();

        // Mock runtime variables (you said you'll handle them later).
        String fromAddress = accounts.path("authority").asText("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H");
        String mockedRecentBlockhash = "3smDPkLLW8pUE3NADVQ4tMsANRvDWkfb6xm5ydj5Lc7n";
        Integer mockedComputeGasLimit = Integer.valueOf(200_000);
        Long mockedComputeGasPrice = Long.valueOf(1_000L);

        LegacyTransactionBuilder.RuntimeOptions runtimeOptions =
                new LegacyTransactionBuilder.RuntimeOptions(
                        fromAddress,
                        mockedRecentBlockhash,
                        mockedComputeGasLimit,
                        mockedComputeGasPrice,
                        null
                );

        LegacyTransactionBuilder.BuildRequest request =
                LegacyTransactionBuilder.BuildRequest.fromJson(
                        idl,
                        instructionName,
                        accounts,
                        ixArgs,
                        runtimeOptions
                );

        LegacyTransactionBuilder.BuildResult result = builder.build(request);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("instruction", instructionName);
        output.put("programId", idl.path("address").asText());
        output.put("messageBase64", result.messageBase64());
        output.put("messageBase58", Base58.encode(result.messageBytes()));
        output.put("unsignedLegacyTransactionBase64", result.unsignedTransactionBase64());
        output.put("requiredSignerCount", result.requiredSigners().size());
        output.set("requiredSigners", MAPPER.valueToTree(result.requiredSigners()));
        output.set("accountKeys", MAPPER.valueToTree(result.accountKeys()));

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
                "- 运行时参数（fromAddress、gasLimit、gasPrice、recentBlockhash）目前用 Main 内局部变量 mock。\n" +
                "- 当前不从配置文件读取 gas/from，配置输入只保留 IDL 与调用参数。\n" +
                "- 该工具只构建 legacy 交易，不签名。\n";
        System.out.println(usage);
    }
}
