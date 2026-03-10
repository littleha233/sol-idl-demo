# 基于 IDL + 参数构建 SOL 交易的原理与流程

本文描述本项目中“根据 IDL 与参数构建未签名 Solana legacy 交易”的核心机制。对应实现见：

- `src/main/java/org/example/project/SolIdlProject.java`
- `src/main/java/org/example/sol/LegacyTransactionSerializer.java`
- `src/main/java/org/example/sol/idl/IdlInstructionBuilder.java`
- `src/main/java/org/example/sol/sdk/Message.java`
- `src/main/java/org/example/sol/BorshEncoder.java`
- `src/main/java/org/example/sol/ShortVec.java`
- `src/main/java/org/example/sol/Base58.java`

## 1. 核心原理

### 1.1 IDL 决定“要调用什么”

IDL（Anchor IDL）提供：

- 程序地址（`address`）
- 指令名（`instructions[].name`）
- 指令账号列表（`instructions[].accounts`）
- 指令参数定义（`instructions[].args`）
- 指令 discriminator（`instructions[].discriminator`，如存在）

因此构建交易时，先从 IDL 找到目标 instruction，再按该 instruction 的账户和参数规范完成交易数据组装。

### 1.2 参数决定“调用时传什么”

传入 3 类参数：

- `accounts.json`：指令账户的具体公钥
- `args.json`：指令入参（例如 `pubkey/u64/bool/string` 等）
- 运行时局部变量：`fromAddress/recentBlockhash/computeGasLimit/computeGasPrice/nonce`（当前不从配置文件读取）

### 1.3 Borsh 编码决定“data 怎么写”

指令 data 结构：

1. 8 字节 discriminator
2. 按 IDL `args` 定义顺序将参数做 Borsh 编码并拼接

项目中由 `BorshEncoder` 实现基础类型编码（`bool/u8~u128/i8~i128/string/pubkey/vec/array/option`）。

### 1.4 Legacy Message 规则决定“账户和字节结构怎么排”

构建 legacy message 时需按 Solana 规则：

1. 先聚合所有指令使用的账户（含 programId）
2. 合并 signer/writable 标志（同一账户出现多次取并集）
3. 按顺序分组：
   - signer+writable（fee payer 必须在第一位）
   - signer+readonly
   - nonsigner+writable
   - nonsigner+readonly
4. 写入 header（`numRequiredSignatures` 等）
5. 写入账户数组、recent blockhash、compiled instructions（shortvec 长度编码）

`LegacyTransactionSerializer` + `ShortVec` 完成上述序列化。

## 2. 构建流程（项目实际流程）

### Step 1: 读取输入

`Main` 读取：

- `idl.json`
- `instructionName`
- `accounts.json`
- `args.json`
- 运行时变量（局部 mock）

### Step 2: 解析目标 instruction

在 IDL `instructions[]` 中按 `name` 匹配目标指令，并拿到：

- 账户定义
- 参数定义
- discriminator

### Step 3: 生成 instruction data

- discriminator 优先使用 IDL 提供值
- 若 IDL 无 discriminator，回退 Anchor 规则：`sha256("global:<ix_name>")` 前 8 字节
- 按参数顺序做 Borsh 编码并拼接

### Step 4: 生成指令列表

按顺序拼交易指令：

1. 若配置 nonce：先插入 `SystemProgram::AdvanceNonceAccount`
2. 若配置 compute unit limit：插入 `ComputeBudget::SetComputeUnitLimit`
3. 若配置 compute unit price：插入 `ComputeBudget::SetComputeUnitPrice`
4. 最后插入目标 program instruction

### Step 5: 编译 legacy message

- 合并所有账户 meta
- 排序账户并计算 header
- 编译每条 instruction 的 `program_id_index / account_indexes / data`
- 序列化为 message bytes

### Step 6: 生成“未签名交易字节”

未签名 legacy 交易格式：

1. 签名数（shortvec）
2. 对应数量的 64-byte 全 0 签名占位
3. message bytes

最终输出：

- `messageBase64` / `messageBase58`
- `unsignedLegacyTransactionBase64`
- `requiredSigners`
- `accountKeys`

## 3. nonce 与 gas（compute budget）处理说明

### 3.1 nonce

- 若配置 `nonce`，交易会插入 `advance nonce` 指令
- 且 message 的 recent blockhash 使用 `nonce.nonceValue`（而不是普通 recent blockhash）

### 3.2 gas / priority fee

- `computeUnitLimit`：限制本笔交易 CU 上限
- `computeUnitPriceMicroLamports`：设置优先费单价（每 CU 微 lamports）
- 这两条通过 Compute Budget Program 指令注入到交易前部

## 4. 边界与限制（当前实现）

- 当前构建目标是 **legacy transaction**，不包含 v0 address table
- 目前不做链上账户存在性/权限实时校验（仅构建字节）
- IDL 的 `defined` 自定义复杂类型尚未在 demo 中展开
- 产物是“可签名字节”，不负责签名和发送

## 5. 一句话总结

这个流程本质是：**IDL 提供结构模板，参数提供实例值，编码器按 Solana/Anchor 规则把它们变成可签名的 legacy 交易字节。**
