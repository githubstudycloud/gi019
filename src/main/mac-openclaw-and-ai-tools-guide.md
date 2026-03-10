# Mac 玩 OpenClaw 教程 & AI 编码工具大全（2026）

---

## 一、OpenClaw 在 Mac 上的完整教程

### 1.1 什么是 OpenClaw？

OpenClaw 是一个开源、自托管的 AI Agent 框架，前身叫 "Clawdbot"（2025年11月），后改名 "Moltbot"，最终定名 OpenClaw。截至2026年2月已获得 200,000+ GitHub Stars。

**它不是一个 AI 模型**，而是给 AI 模型装上"手"的软件——可以读写文件、执行 Shell 命令、安装工具、发消息、管理日历等。

支持连接：WhatsApp、Telegram、Slack、Discord、iMessage 等多个平台。

### 1.2 Mac 安装要求

- **仅支持 Apple Silicon Mac**（M1/M2/M3/M4）
- 需要 Node.js 环境

### 1.3 安装方式

**方式一：一行脚本安装（推荐）**
```bash
# 官方一行安装脚本，自动检测 Node、安装 CLI、启动配置向导
curl -fsSL https://get.openclaw.ai | bash
```

**方式二：通过 Ollama 安装**
```bash
# Ollama 0.17+ 支持一条命令启动
ollama run openclaw
```

**方式三：macOS 原生 App**
- 从官方文档下载：https://docs.openclaw.ai/platforms/macos

### 1.4 配置向导

安装后自动启动交互式配置向导，主要步骤：
1. 选择 AI 模型提供商（推荐 Anthropic）
2. 配置 API Key
3. 设置权限级别
4. 选择连接的消息平台

### 1.5 核心架构文件

| 文件 | 用途 |
|------|------|
| `SOUL.md` | 人格和边界设定，每次会话注入 |
| `AGENTS.md` | 操作指令，每次会话注入 |
| `MEMORY.md` | 长期记忆，仅在主私人会话加载 |
| `memory/*.md` | 每日日志文件 |

### 1.6 记忆系统（三层架构）

1. **上下文窗口（短期）**：当前对话，AI 当前"看到"的内容
2. **每日笔记（中期）**：`memory/2025-07-14.md` 格式的日志，记录每次会话的重要事件
3. **长期记忆（MEMORY.md）**：关于你的提炼知识——姓名、偏好、常用任务等

**高级特性：**
- 语义向量搜索（SQLite + Embeddings）
- 自动 Memory Flush（压缩前保存重要上下文）
- 时间衰减机制（旧笔记权重降低）
- 去重过滤（相似的每日笔记不会重复加载）

### 1.7 Skills 技能系统

Skills 是模块化的功能包，让 Agent 按需调用而非每次都塞进 prompt。

- ClawHub（官方技能市场）截至2026年2月有 **13,729** 个社区技能
- 社区精选列表 awesome-openclaw-skills 收录了 5,494 个

### 1.8 安全注意事项

- 2026年1月发现 CVE-2026-25253（一键远程代码执行漏洞）
- ClawHub 上曾发现 341 个恶意 Skills
- **建议：从最小权限开始，审核每个 Action 后再开放更多能力**

### 1.9 省钱技巧

- 切换到 Kimi K2.5（主） + MiniMax M2.5（备） via OpenRouter
- 可从 $200/月降到 $15/月

---

## 二、Claude Code CLI 教程与技巧

### 2.1 安装

```bash
npm install -g @anthropic-ai/claude-code
```

### 2.2 核心技巧

**提示质量：**
- 具体描述：不要说"修复 bug"，要说"修复 src/components/UserList.tsx 中 users 数组在 API 返回空数据时可能 undefined 的 TypeError"
- 频繁使用 `/clear` 清除历史，减少 token 消耗
- 新对话 = 最佳表现，长对话性能下降

**速度与模式：**
- Fast Mode：Opus 4.6 的高速配置，速度提升 2.5x（v2.1.36+）
- Plan Mode：`/plan` 进入结构化规划
- 后台任务：`/tasks` 或 `/bashes` 查看，`/kill <task-id>` 停止

**权限与安全：**
```bash
claude --allowedTools "Read" "Grep" "LS" "Bash(npm run test:*)"
```
- `Shift+Tab` 切换自动批准模式
- 用 Git 作为安全网，频繁提交

**快捷键：**
- `Escape` 停止 Claude（不是 Ctrl+C）
- `Escape` 按两次：显示历史消息列表
- `Control+V` 粘贴图片（不是 Command+V）

**高级用法：**
- Docker 容器中运行另一个 Claude Code 实例，用 tmux 控制
- 用 Gemini CLI 作为 Claude Code 的"爬虫助手"（Claude 的 WebFetch 不能访问 Reddit 等站点）
- `claude remote-control` 让外部工具驱动 Claude Code
- 插件系统：2026年初公测，已有 9,000+ 插件

### 2.3 CLAUDE.md 配置

- `~/.claude/CLAUDE.md`：全局配置，每次对话加载
- 项目级 `.claude/settings.json`：项目特定权限
- Monorepo 支持多层 CLAUDE.md（祖先 + 后代加载）

---

## 三、OpenAI Codex CLI 教程

### 3.1 安装

```bash
npm i -g @openai/codex
```

首次运行会提示登录，支持 ChatGPT 账号或 API Key。

### 3.2 平台支持

- macOS 和 Linux 完全支持
- Windows 实验性支持（建议用 WSL）

### 3.3 核心功能

- **交互式 TUI**：运行 `codex` 启动终端界面
- `/model` 切换模型（GPT-5.4、GPT-5.3-Codex 等）
- 支持图片输入（截图、设计稿）
- 内置代码审查功能
- 多 Agent 实验性支持
- MCP 集成

### 3.4 安全模式

| 模式 | 说明 |
|------|------|
| Read Only | 只读，最安全 |
| Auto | 自动执行安全操作 |
| Full Access | 完全控制 |

沙箱机制：macOS 用 Seatbelt，Linux 用 Landlock。

### 3.5 推荐模型

- **GPT-5.4**：推荐用于大多数任务
- 结合了 GPT-5.3-Codex 的编码能力和更强的推理能力

---

## 四、Google Gemini CLI 教程

### 4.1 安装

```bash
npm install -g @google/gemini-cli

# 或免安装运行
npx google-gemini/gemini-cli
```

需要 Node.js 18+。

### 4.2 免费额度（重点！）

用个人 Google 账号：
- **60 请求/分钟**
- **1,000 请求/天**
- **完全免费**

### 4.3 认证方式

1. Google 账号登录
2. API Key 环境变量
3. Vertex AI

### 4.4 核心特性

- **1M token 上下文窗口**——远超竞争对手
- 内置工具：Google Search、文件操作、Shell 命令、Web 抓取
- MCP 支持
- 多模态：支持图片和 PDF
- 模型：Gemini 3 Pro 和 Gemini Flash，默认自动路由

### 4.5 配置文件

- `GEMINI.md`：类似 CLAUDE.md 的持久上下文文件
- 支持 Checkpointing（保存/恢复对话）
- Token Caching 优化用量

### 4.6 常用命令

- `/help`：帮助
- `/chat`：聊天模式
- `/path`：加载本地项目
- `/quit` 或 `Ctrl-C` 两次：退出

---

## 五、GitHub CLI（gh）教程与技巧

### 5.1 安装

```bash
# macOS
brew install gh

# Windows
winget install --id GitHub.cli
```

### 5.2 认证

```bash
gh auth login
# 或设置环境变量
export GITHUB_TOKEN=your_token
```

### 5.3 常用命令速查

```bash
# 查看当前状态
gh status

# PR 操作
gh pr create
gh pr list
gh pr list --author @me
gh pr merge

# Issue 操作
gh issue create
gh issue list
gh issue close <number>

# 仓库操作
gh repo clone owner/repo
gh repo create
gh repo fork

# 搜索
gh search prs "author:@me is:merged"
gh search repos "language:go stars:>1000"

# Release
gh release create v1.0.0
gh release list
```

### 5.4 高级技巧

**JSON 输出 + jq 过滤：**
```bash
# 查看仓库详情
gh repo view --json name,stargazerCount,visibility

# 列出 PR 并过滤
gh pr list --json number,title,author | jq '.[] | select(.author.login == "myname")'
```

**自定义别名：**
```bash
gh alias set pv 'pr view'
gh alias set co 'pr checkout'
```

**扩展（Extensions）：**
```bash
gh extension install owner/gh-extension-name
gh extension list
```

---

## 六、多工具组合使用的机巧

### 6.1 角色分工策略

| 工具 | 最佳角色 | 原因 |
|------|----------|------|
| **Claude Code** | 架构师 | 擅长复杂重构、多文件任务、任务分解 |
| **Codex CLI** | 并行工人 | 擅长测试编写、快速代码生成、CI/CD 集成 |
| **Gemini CLI** | 文档+搜索 | 1M上下文窗口、免费、多模态、Google搜索 |
| **GitHub CLI** | 版本管理 | PR/Issue/Release 全流程自动化 |
| **OpenClaw** | 生活助手 | 消息平台集成、日程管理、持久记忆 |

### 6.2 黄金组合工作流

```
1. 用 Gemini CLI 做项目规划（免费 + 大上下文）
2. 用 Claude Code 做核心开发（最强编码能力）
3. 用 Codex CLI 并行写测试
4. 用 GitHub CLI 自动化 PR 流程
5. 用 OpenClaw 接收通知和日常提醒
```

### 6.3 通用最佳实践

1. **投资测试套件**：AI 用测试来自我纠错，你用测试来验证结果
2. **用 git worktree 隔离**：多 Agent 不会互相冲突
3. **写好配置文件**：CLAUDE.md / GEMINI.md / .codex 是最重要的投入
4. **所有工具都支持 MCP**：可以共享工具和上下文
5. **从小处开始**：先集成一个 Agent，审查每个改动，把 Agent 当协作者而非权威

### 6.4 成本对比

| 工具 | 费用 |
|------|------|
| Claude Code | Anthropic API 计费（Max plan $100/200/月） |
| Codex CLI | ChatGPT Plus/Pro/API |
| Gemini CLI | **免费**（个人 Google 账号） |
| GitHub CLI | **免费** |
| OpenClaw | 自托管 + 模型 API 费用 |

---

## 七、参考资源

### OpenClaw
- [Cult of Mac - Mac 上设置 OpenClaw](https://www.cultofmac.com/how-to/set-up-and-run-openclaw-on-mac)
- [Towards AI - OpenClaw 完整教程 2026](https://pub.towardsai.net/openclaw-complete-guide-setup-tutorial-2026-14dd1ae6d1c2)
- [官方文档 - macOS](https://docs.openclaw.ai/platforms/macos)
- [Ollama Blog - 最简安装](https://ollama.com/blog/openclaw-tutorial)
- [SitePoint - Mac Mini 设置指南](https://www.sitepoint.com/how-to-set-up-openclaw-on-a-mac-mini/)
- [OpenClaw 记忆系统文档](https://docs.openclaw.ai/concepts/memory)
- [awesome-openclaw-skills](https://github.com/VoltAgent/awesome-openclaw-skills)
- [12层记忆架构（社区）](https://github.com/coolmanns/openclaw-memory-architecture)

### Claude Code
- [官方快速入门](https://code.claude.com/docs/en/quickstart)
- [zebbern/claude-code-guide](https://github.com/zebbern/claude-code-guide)
- [ykdojo/claude-code-tips（45条技巧）](https://github.com/ykdojo/claude-code-tips)
- [shanraisshan/claude-code-best-practice](https://github.com/shanraisshan/claude-code-best-practice)
- [32 Claude Code Tips（Substack）](https://agenticcoding.substack.com/p/32-claude-code-tips-from-basics-to)
- [SimilarLabs 终极教程 2026](https://similarlabs.com/blog/claude-code-tutorial-guide)

### Codex CLI
- [官方文档](https://developers.openai.com/codex/cli/)
- [快速入门](https://developers.openai.com/codex/quickstart/)
- [功能特性](https://developers.openai.com/codex/cli/features/)
- [SmartScope 综合指南](https://smartscope.blog/en/generative-ai/chatgpt/openai-codex-cli-comprehensive-guide/)

### Gemini CLI
- [GitHub 仓库](https://github.com/google-gemini/gemini-cli)
- [官方文档](https://geminicli.com/docs/get-started/)
- [Google Codelabs 动手教程](https://codelabs.developers.google.com/gemini-cli-hands-on)
- [DataCamp 教程](https://www.datacamp.com/tutorial/gemini-cli)
- [KDnuggets 入门指南](https://www.kdnuggets.com/beginners-guide-to-gemini-cli-install-setup-and-use-it-like-a-pro)

### GitHub CLI
- [官方手册](https://cli.github.com/manual/)
- [GitHub CLI Power Tips 2026](https://onlyutkarsh.com/posts/2026/github-cli-power-tips/)
- [Codecademy 教程](https://www.codecademy.com/article/github-cli-tutorial)
- [官方快速入门](https://docs.github.com/en/github-cli/github-cli/quickstart)

### 多工具对比与组合
- [Educative - Claude Code vs Codex vs Gemini 2026](https://www.educative.io/blog/claude-code-vs-codex-vs-gemini-code-assist)
- [Agentic Coding 2026 完整指南](https://halallens.no/en/blog/agentic-coding-in-2026-the-complete-guide-to-plugins-multi-model-orchestration-and-ai-agent-teams)
- [Claude-Code-Workflow 多Agent框架](https://github.com/catlog22/Claude-Code-Workflow)
- [Optijara - AI 编码 Agent 完整开发者指南](https://www.optijara.ai/en/blog/ai-coding-agents-2026-complete-guide)
