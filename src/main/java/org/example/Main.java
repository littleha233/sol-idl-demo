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
        if (args.length < 5) {
            printUsage();
            return;
        }

        Path idlPath = Path.of(args[0]);
        String instructionName = args[1];
        Path accountsPath = Path.of(args[2]);
        Path ixArgsPath = Path.of(args[3]);
        Path configPath = Path.of(args[4]);

        JsonNode idl = readJson(idlPath);
        JsonNode accounts = readJson(accountsPath);
        JsonNode ixArgs = readJson(ixArgsPath);
        JsonNode configJson = readJson(configPath);

        LegacyTransactionBuilder.BuildConfig config = LegacyTransactionBuilder.BuildConfig.fromJson(configJson);
        LegacyTransactionBuilder builder = new LegacyTransactionBuilder();
        LegacyTransactionBuilder.BuildResult result =
                builder.build(idl, instructionName, ixArgs, accounts, config);

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
                "    <idl.json> <instructionName> <accounts.json> <ixArgs.json> <buildConfig.json>\n\n" +
                "buildConfig.json example:\n" +
                "{\n" +
                "  \"feePayer\": \"YourFeePayerPubkey\",\n" +
                "  \"recentBlockhash\": \"RecentBlockhashBase58\",\n" +
                "  \"computeUnitLimit\": 250000,\n" +
                "  \"computeUnitPriceMicroLamports\": 1000,\n" +
                "  \"nonce\": {\n" +
                "    \"nonceAccount\": \"NonceAccountPubkey\",\n" +
                "    \"nonceAuthority\": \"NonceAuthorityPubkey\",\n" +
                "    \"nonceValue\": \"DurableNonceValueBase58\"\n" +
                "  }\n" +
                "}\n\n" +
                "Notes:\n" +
                "- nonce 配置是可选；如果传了 nonce，会优先使用 nonce.nonceValue 作为 recent_blockhash。\n" +
                "- 该工具只构建 legacy 交易，不签名。\n";
        System.out.println(usage);
    }
}
