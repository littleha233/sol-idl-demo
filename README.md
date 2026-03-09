# sol-idl-demo

输入 `IDL + instruction 参数 + account 参数 + 交易配置`，构建一笔 **未签名 legacy Solana 交易**。

## Build

```bash
mvn -q package
```

## Run

```bash
java -cp target/sol-idl-demo-1.0-SNAPSHOT.jar org.example.Main \
  /path/to/idl.json \
  grant_permission \
  examples/accounts.template.json \
  examples/ix-args.template.json \
  examples/build-config.template.json
```

## 功能点

- 从 IDL 自动读取 instruction 定义
- 按 IDL args 做 Borsh 编码（含 Anchor discriminator）
- 支持 `computeUnitLimit` / `computeUnitPriceMicroLamports`
- 支持 durable nonce（可选）
- 输出：
  - message base64/base58
  - unsigned legacy transaction base64
  - required signers 和最终 accountKeys
