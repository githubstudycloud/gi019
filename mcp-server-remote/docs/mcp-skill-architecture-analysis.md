# MCP 与 Skill 架构深度分析

> 面向新手的完整解答：同名冲突、长连接、curl 本质、是否改名、本地代理方案，以及业界通用做法。

---

## 目录

1. [MCP 直连是长连接吗？](#1-mcp-直连是长连接吗)
2. [远程 MCP 都是一个 /命令 吗？](#2-远程-mcp-都是一个-命令-吗)
3. [MCP 和 Skill 同名有问题吗？要不要改名？](#3-mcp-和-skill-同名有问题吗要不要改名)
4. [本地 Skill + 本地 MCP 代理，体验是否更好？](#4-本地-skill--本地-mcp-代理体验是否更好)
5. [最终调 MCP 都是 curl 命令吗？这是业界通用方式？](#5-最终调-mcp-都是-curl-命令吗这是业界通用方式)
6. [综合建议](#6-综合建议)

---

## 1. MCP 直连是长连接吗？

**结论：本项目用的 Streamable HTTP 不是长连接，每次请求独立。**

MCP 协议支持三种传输方式，连接生命周期完全不同：

### 三种传输方式对比

```
┌─────────────────┬──────────────────────────────┬─────────────────┐
│   传输方式       │        连接特征               │   适用场景       │
├─────────────────┼──────────────────────────────┼─────────────────┤
│ Streamable HTTP │ 无状态，每次 POST 独立连接     │ 云服务器、远端   │
│ (本项目使用)    │ 无持久连接，请求结束即断开     │ 无服务器部署    │
├─────────────────┼──────────────────────────────┼─────────────────┤
│ SSE             │ 有状态，长连接                 │ 旧版客户端兼容  │
│                 │ 服务器主动推送数据             │ ⚠ 已废弃        │
├─────────────────┼──────────────────────────────┼─────────────────┤
│ Stdio           │ 本地进程生命周期               │ 本地工具、命令行│
│                 │ 进程启动到退出的持久连接       │ 系统直接访问    │
└─────────────────┴──────────────────────────────┴─────────────────┘
```

### Streamable HTTP 的调用时序

```
Claude Code                     MCP Server (localhost:8080)
     │                                    │
     │── POST /mcp (initialize) ─────────>│  ← 握手，每次会话只做一次
     │<─ 200 OK (capabilities) ──────────│
     │                                    │
     │── POST /mcp (tools/list) ─────────>│  ← 每次调用都是独立 POST
     │<─ 200 OK (tool definitions) ──────│  ← 请求完成，连接关闭
     │                                    │
     │── POST /mcp (tools/call) ─────────>│  ← 又是一个新的 POST 请求
     │<─ 200 OK (result) ────────────────│  ← 请求完成，连接关闭
     │                                    │
```

**没有保持打开的 TCP 连接**，这也是为什么 Streamable HTTP 适合部署在无服务器平台（云函数、容器按需启停）——空闲时不占资源。

---

## 2. 远程 MCP 都是一个 /命令 吗？

**结论：不是。MCP 工具不会自动变成 /命令。/命令 属于 Skill 的特性。**

这是新手最容易混淆的地方，彻底区分清楚：

### /命令 的来源

```
┌──────────────────────────────────────────────────────────────┐
│                   /命令 (Slash Commands)                      │
├────────────────────┬─────────────────────────────────────────┤
│  来源              │  示例                                    │
├────────────────────┼─────────────────────────────────────────┤
│ Claude Code 内置   │ /mcp  /help  /clear  /memory            │
│ 命令               │                                          │
├────────────────────┼─────────────────────────────────────────┤
│ Skill 文件         │ /remote-testcase  /feishu-helper        │
│ (.claude/skills/)  │ /mysql-manager  /commit                 │
├────────────────────┼─────────────────────────────────────────┤
│ MCP Prompt         │ /mcp__github__list_prs                  │
│ (仅当 MCP 服务端   │ （服务端必须明确定义 prompt 才有）      │
│  定义了 prompts)   │                                          │
└────────────────────┴─────────────────────────────────────────┘

❌ MCP 工具 (tools) 本身不生成 /命令！
```

### 实际情况：MCP 工具是"隐形的能力"

```
配置了 remote-testcase MCP 服务后：

✅ Claude 能自动调用的工具：
   - search_test_case
   - list_projects
   - get_project_detail

❌ 不会出现的命令：
   - /search_test_case      ← 不存在
   - /list_projects         ← 不存在
   - /remote-testcase       ← 这是 Skill 的命令，不是 MCP 的命令！
```

MCP 工具的使用方式是：**你说需求 → Claude 判断 → 自动调用工具**，无需 /命令。

---

## 3. MCP 和 Skill 同名有问题吗？要不要改名？

**结论：技术上没有冲突，但会造成认知混乱。建议改名。**

### 为什么技术上不冲突

```
系统中的 "remote-testcase"：

settings.json 里的 remote-testcase
  → 这是一个 MCP 服务器标识符
  → 出现在 /mcp 面板里
  → Claude 用它调用 3 个工具
  → 你看不见它的 /命令

.claude/skills/remote-testcase/SKILL.md
  → 这是一个 Skill 名称
  → 出现在 /remote-testcase 斜杠命令里
  → Claude 执行 curl 命令
  → 你可以显式调用它
```

两个系统完全独立，互不干扰。

### 为什么建议改名

问题不在于技术，而在于**歧义**。当你说"用 remote-testcase 查一下"，Claude 不知道你指的是：
- MCP 直连工具
- 还是 Skill 的 curl 方式

更重要的是，**Skill 的真正用途是什么**？它是 MCP 连不上时的备用手段，是调试工具。名字应该反映这个用途。

### 改名建议

```
当前名称：remote-testcase（MCP 和 Skill 同名）

建议改为：

方案 A（推荐）：按功能区分
  MCP：    remote-testcase        ← 正式功能，不改
  Skill：  testcase-debug         ← 体现"调试/备用"属性

方案 B：按方式区分
  MCP：    remote-testcase        ← 不改
  Skill：  testcase-curl          ← 体现"curl调用方式"

方案 C：完全删除 Skill
  MCP 正常工作时，Skill 完全多余
  只有在 MCP 断连诊断时才需要临时恢复
```

---

## 4. 本地 Skill + 本地 MCP 代理，体验是否更好？

**结论：本地 MCP 代理（Stdio → HTTP）在特定场景下有价值，但不是"体验更好"，而是"功能更强"。简单使用直连就够了。**

### 你问的"本地 MCP"是什么意思

有两种理解：

**理解 A：本地 Stdio MCP 代理（真正的代理）**

```
Claude Code
    │ Stdio
    ▼
[本地 MCP 代理进程]   ← 你写或安装的本地程序
    │ HTTP
    ▼
[远端 MCP Server]     ← localhost:8080
```

这个本地代理程序接收 Stdio 调用，转换为 HTTP 请求发给远端服务。

**理解 B：Skill（你当前的做法）**

```
Claude Code
    │ Bash 工具
    ▼
curl 命令
    │ HTTP
    ▼
[远端 MCP Server]     ← localhost:8080
```

当前 Skill 就是这种方式——通过 Bash 工具跑 curl。

### 三种方案完整对比

```
┌────────────────────┬────────────┬────────────┬──────────────┐
│ 方案               │ 直连 HTTP  │ Stdio 代理 │ Skill + curl │
├────────────────────┼────────────┼────────────┼──────────────┤
│ 配置复杂度         │ 简单 ★     │ 复杂 ★★★  │ 中等 ★★     │
│ Claude 集成度      │ 原生 ★★★  │ 原生 ★★★  │ 间接 ★      │
│ 错误处理           │ 由框架处理 │ 可自定义   │ 需自己解析   │
│ 可添加缓存/限流    │ 否         │ 可以 ✓     │ 否           │
│ 可添加认证转换     │ 否         │ 可以 ✓     │ 否           │
│ 离线诊断能力       │ 否         │ 否         │ 可以 ✓       │
│ 响应速度           │ 最快       │ 多一跳     │ 慢（最慢）   │
│ 适合新手           │ 是 ✓       │ 否 ✗       │ 部分 ✓       │
└────────────────────┴────────────┴────────────┴──────────────┘
```

### 什么时候需要 Stdio 代理

```
需要 Stdio 代理的场景：

1. 需要在调用间做缓存（减少重复请求）
2. 需要在本地做认证转换（如 token 刷新）
3. 需要对请求/响应做格式转换
4. 远端服务不稳定，需要本地重试逻辑
5. 需要对调用做审计日志

不需要 Stdio 代理的场景（直连就够）：

1. 只是想用 Claude 查询测试用例 ← 你的情况
2. 远端服务稳定可靠
3. 没有特殊中间件需求
```

**对你的建议：** 当前直连 HTTP 方案完全够用，不需要引入额外的本地代理复杂度。

---

## 5. 最终调 MCP 都是 curl 命令吗？这是业界通用方式？

**结论：完全不是。curl 只是 Skill 里的手动备用方式，业界通用做法是原生 MCP 协议。**

这是最重要的一个问题，必须彻底弄清楚。

### 现在项目里的两条路径

```
路径 1：MCP 直连（.claude/settings.json 配置的 url）
────────────────────────────────────────────────────
Claude Code 内部 MCP 客户端
    │ 原生 JSON-RPC over HTTP
    │ 由 Claude Code 框架管理
    ▼
MCP Server (localhost:8080/mcp)

特点：
  ✅ Claude Code 自己实现 HTTP 请求
  ✅ 不经过 Bash 工具
  ✅ 不经过 curl
  ✅ 原生协议，框架级别集成


路径 2：Skill（.claude/skills/remote-testcase/SKILL.md）
────────────────────────────────────────────────────────
Claude 读取 SKILL.md 中的 curl 示例
    │ 通过 Bash 工具执行 shell 命令
    ▼
curl 进程
    │ 手动构造 JSON-RPC 请求
    ▼
MCP Server (localhost:8080/mcp)

特点：
  ⚠️  Claude 调用 Bash 工具
  ⚠️  Bash 工具启动 curl 进程
  ⚠️  手动拼接 JSON-RPC 请求体
  ⚠️  绕过了 MCP 客户端框架
  ⚠️  这是"手工模拟"，不是"原生协议"
```

### 底层本质对比

```
MCP 直连时，Claude Code 做的事：

  1. 建立 HTTP 连接（内部 Java/Node.js HTTP 客户端）
  2. 发送 POST {"jsonrpc":"2.0","method":"initialize",...}
  3. 解析响应，记录服务器能力
  4. 发送 {"jsonrpc":"2.0","method":"tools/list",...}
  5. 解析工具列表，注册为可调用工具
  6. 用户提问 → Claude 判断需要工具 → 发送 tools/call 请求
  → 以上全部由 Claude Code 框架自动完成，你看不到细节

Skill curl 时，Claude 做的事：

  1. 读取 SKILL.md 中的 curl 命令模板
  2. 调用 Bash 工具，执行 curl 命令（你能在对话中看到）
  3. curl 发出 HTTP 请求（与直连发的请求格式相同！）
  4. Claude 读取 curl 的 stdout 输出
  5. Claude 解析 JSON 字符串，提取 result.content[0].text
  → 以上步骤对用户可见，是"手动"的
```

同样的 JSON-RPC 请求，两条路径发的**网络包完全一样**。区别在于谁发出去的：框架（透明）还是 curl（可见）。

### 业界通用做法

```
业界使用 MCP 的标准方式：

1. 服务端：部署 MCP Server（Java/Python/Node.js 等任何语言）
   └─ 实现 JSON-RPC 2.0 协议
   └─ 暴露 tools/resources/prompts

2. 客户端：在 Claude Code / Claude Desktop 配置 MCP 服务器地址
   └─ .mcp.json（项目级，团队共享）
   └─ ~/.claude.json（用户级，个人使用）

3. 使用：自然语言对话，Claude 自动调用工具
   └─ 不需要 /命令
   └─ 不需要 curl
   └─ 不需要手动构造 JSON-RPC

curl 用于：
   └─ 开发调试（验证服务端接口是否正常）
   └─ 文档示例（展示请求格式）
   └─ CI/CD 健康检查
   └─ 不是日常使用方式
```

### 用一个类比理解

```
MCP 直连 ≈ 使用数据库驱动连接 MySQL
  → 应用程序用 JDBC/ORM 直接查询
  → 你不知道底层 TCP 包长什么样
  → 开发者体验好，效率高

Skill curl ≈ 用命令行手敲 SQL 语句
  → 每次手动输入 SQL
  → 你能看到每一步
  → 适合调试，不适合日常
```

---

## 6. 综合建议

### 你现在的架构

```
当前（可工作，但有冗余）：

Claude Code
  ├─ MCP 直连 (settings.json)      ← 正式使用
  │    remote-testcase → localhost:8080/mcp
  │
  └─ Skill (SKILL.md)              ← 备用调试（同名，容易混淆）
       /remote-testcase → curl → localhost:8080/mcp
```

### 推荐的最终架构

```
推荐（清晰，职责分离）：

Claude Code
  ├─ MCP 直连 (settings.json)      ← 主要使用，不改
  │    remote-testcase → localhost:8080/mcp
  │
  └─ Skill（改名后）               ← 诊断备用，职责清晰
       /testcase-debug → curl → localhost:8080/mcp
```

### 改名操作

如果决定改名，只需修改 Skill 目录名：

```bash
# 重命名 skill 目录（两处同步修改）
mv .claude/skills/remote-testcase .claude/skills/testcase-debug
mv skills/remote-testcase skills/testcase-debug
# 然后更新 SKILL.md 中的标题
```

### 不同场景的最佳选择

| 你的需求 | 推荐方案 |
|---------|---------|
| 日常查询测试用例 | MCP 直连，直接说需求 |
| MCP 服务断了要诊断 | Skill（curl）逐步排查 |
| 需要在调用间加缓存/认证 | 引入 Stdio 本地代理 |
| 团队共享配置 | 将 `.claude/settings.json` 提交到版本库 |

---

## 快速参考卡

```
Q: /remote-testcase 是 MCP 的命令吗？
A: 不是。这是 Skill 的命令。MCP 工具没有 /命令。

Q: MCP 每次调用都要重新连接吗？
A: Streamable HTTP 是每次独立 POST，没有持久连接。

Q: Claude 调用 MCP 工具用 curl 吗？
A: 不用。Claude Code 框架原生实现 HTTP 请求，不经过 curl。

Q: Skill 里的 curl 是标准 MCP 调用方式吗？
A: 不是。curl 是手动备用方式，标准方式是 MCP 直连。

Q: 同名的 Skill 和 MCP 会冲突吗？
A: 技术上不冲突，但建议 Skill 改名以区分职责。
```
