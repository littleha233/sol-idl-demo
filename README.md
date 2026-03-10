# sol-idl-demo

输入 `IDL + instruction 参数 + account 参数`，构建一笔 **未签名 legacy Solana 交易**。

当前采用 `SolProject` 风格：

- `from` 作为方法参数传递
- `Message` 先构造，再逐步 `addInstruction`
- nonce/gas 在项目类内部做局部 mock（后续可替换为真实配置或 RPC）

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
  examples/ix-args.template.json
```

## 功能点

- 从 IDL 自动读取 instruction 定义
- 按 IDL args 做 Borsh 编码（含 Anchor discriminator）
- 通过局部变量 mock 运行时参数：`fromAddress`、`computeGasLimit`、`computeGasPrice`
- 支持 durable nonce（可选）
- 输出：
  - message base64/base58
  - unsigned legacy transaction base64
  - required signers 和最终 accountKeys
