# Claude Code 使用指南：MCP 直连 vs Skill 辅助

本指南面向**新手**，帮助你理解本项目中 MCP 直连和 Skill 两种调用方式的区别、优先级，以及如何在对话中正确触发。

---

## 一、两种方式是什么

### MCP 直连（推荐）

Claude Code 通过 `.claude/settings.json` 直接连接到远端 MCP Server，工具调用就像内置能力一样透明。

```
你说: "帮我搜索电商平台的登录相关测试用例"
         ↓
Claude 自动调用 remote-testcase MCP 的 search_test_case 工具
         ↓
返回结果给你
```

**特点：**
- 无需你关心底层调用细节
- Claude 自动判断调用哪个工具
- 工具参数由 Claude 自动填充
- 服务端需正在运行（`java -jar ... --spring.profiles.active=h2`）

### Skill 辅助（备用/诊断）

`.claude/skills/remote-testcase/SKILL.md` 教会 Claude 如何通过 `curl` 手动发起 JSON-RPC 请求。

```
你说: /remote-testcase 或 "用curl查询电商项目用例"
         ↓
Claude 读取 SKILL.md 中的 curl 示例模板
         ↓
Claude 通过 Bash 工具执行 curl 命令
         ↓
解析响应并返回结果
```

**特点：**
- 不依赖 MCP 协议握手
- 用 curl 直接发 HTTP 请求
- 适合 MCP 连接异常时的诊断或备用

---

## 二、优先级说明

**两者不互斥，可以同时存在。** Claude 按以下逻辑选择：

```
用户发起请求
    │
    ├─ MCP 连接正常 → 优先使用 MCP 直连工具（自动、透明）
    │
    └─ MCP 连接失败 / 用户显式使用 /remote-testcase
              └─ 使用 Skill（curl 方式）
```

> **一句话记忆：MCP 是正常使用的方式，Skill 是兜底和调试的方式。**

---

## 三、如何在对话中触发

### 3.1 触发 MCP 直连（自然语言，最常用）

直接用自然语言描述需求，Claude 会自动识别并调用对应工具：

```
✅ 正确示例：

"列出所有测试项目"
"帮我查一下电商平台有哪些测试用例"
"搜索用户中心的登录相关用例"
"查看支付网关项目的详情，包括版本信息"
"搜索 /api/v1 路径下的测试用例"
"电商平台 v2.0 版本有哪些用例"
```

不需要任何特殊命令，像正常聊天一样说话即可。

### 3.2 触发 Skill（显式命令）

使用斜杠命令直接激活 Skill：

```bash
/remote-testcase
```

或在对话中明确提及 skill 的使用场景：

```
"用 curl 查询电商平台的测试用例"
"MCP 连不上了，帮我用 skill 查一下项目列表"
"直接发 HTTP 请求查一下用例"
```

### 3.3 检查 MCP 连接状态

在 Claude Code 会话内输入：

```bash
/mcp
```

可以查看 `remote-testcase` MCP 服务的连接状态和可用工具列表。

---

## 四、实际对话示例

### 示例 1：查看所有项目（MCP 自动触发）

```
你：有哪些测试项目？

Claude：我来帮你查询所有项目。
[调用 list_projects 工具]
共有 3 个项目：
- 电商平台（5个用例，2个版本）
- 用户中心（3个用例，2个版本）
- 支付网关（2个用例，1个版本）
```

### 示例 2：搜索特定用例（MCP 自动触发）

```
你：帮我搜索电商平台里和登录有关的用例

Claude：[调用 search_test_case，projectName=电商，caseName=登录]
找到 2 个匹配的用例：
1. 用户登录-正常流程（P0，功能测试）
   步骤：1. 输入用户名密码 2. 点击登录
   预期：登录成功，跳转首页

2. 用户登录-密码错误（P0，功能测试）
   ...
```

### 示例 3：显式使用 Skill 诊断

```
你：/remote-testcase 查一下项目列表

Claude：[读取 SKILL.md，执行 curl 命令]
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call",...}'

返回结果：电商平台、用户中心、支付网关
```

### 示例 4：明确要求用哪种方式

```
你：用 MCP 工具帮我查电商平台的项目详情

Claude：[直接调用 get_project_detail MCP 工具]
...

---

你：不用 MCP，用 curl 帮我查电商平台的项目详情

Claude：[读取 Skill，构造 curl 命令执行]
...
```

---

## 五、配置位置速查

| 配置 | 文件位置 | 作用 |
|------|---------|------|
| MCP 服务器地址 | `.claude/settings.json` | Claude Code 自动连接 |
| Skill 指令 | `.claude/skills/remote-testcase/SKILL.md` | curl 调用模板 |
| 项目说明 | `CLAUDE.md` | 告知 Claude 项目基本信息 |

两个配置文件在项目根目录下，**Claude Code 启动时自动加载，无需手动执行任何命令**。

---

## 六、前提：确保服务在运行

无论使用哪种方式，都需要 MCP Server 正在运行：

```bash
# 使用 H2 内存库（开箱即用，无需 MySQL）
java -jar target/mcp-server-remote-1.0.0.jar --spring.profiles.active=h2
```

验证服务是否正常：

```bash
curl http://localhost:8080/health
# 返回 {"status":"UP",...} 即正常
```

---

## 七、常见问题

**Q：说了中文需求，Claude 没有调用工具怎么办？**

检查 `/mcp` 确认连接状态，或重启 Claude Code 会话让配置重新加载。

**Q：MCP 工具被调用了但返回空结果？**

结果为空表示数据库中没有匹配数据，不是错误。换一个更宽泛的关键词再试。

**Q：Skill 和 MCP 同时配置，会不会重复调用？**

不会。Skill 只在你显式调用 `/remote-testcase` 或 Claude 判断需要用 curl 时才激活，正常情况下 Claude 优先使用 MCP 直连工具。

**Q：中文参数用 curl 命令时报 400 错误？**

在 Windows 环境下，curl `-d` 中的中文字面量可能被 shell 损坏。必须用 JSON Unicode 转义：
```
电商 → \u7535\u5546
登录 → \u767b\u5f55
```
