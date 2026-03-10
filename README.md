# sol-idl-demo

输入 `contracts 配置 + from + contractAddress + operationCode + paramList`，构建一笔 **未签名 legacy Solana 交易**。

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
  testdata/contracts-config.json \
  8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H \
  BHbxLfy5YPYKyrsTXr8cVzBnyKJYY9CGs5ozMzctKxvf \
  set_safe \
  testdata/set-safe/param-list.json
```

## 功能点

- 从统一 `contracts-config.json` 读取 SOL operation，并定位 IDL 元数据
- 按 IDL args 顺序读取 `paramList` 并做 Borsh 编码（含 Anchor discriminator）
- 当账户未显式提供且 IDL 含 `pda.seeds` 时，自动按 seeds 推导 PDA 账户（如 `config`）
- 通过局部变量 mock 运行时参数：`computeGasLimit`、`computeGasPrice`
- 支持 durable nonce（可选）
- 输出：
  - message base64/base58
  - unsigned legacy transaction base64
  - required signers 和最终 accountKeys
