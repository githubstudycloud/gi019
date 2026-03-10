# 用 tmux 榨干 Claude Code —— 多平台安装与并行开发指南

## 文章核心思路分析

原文（公众号「字节笔记本」）提出了一个高效开发范式：**tmux + Claude Code CLI = 全天候多任务并行开发环境**。

### 核心价值

| 特性 | 说明 |
|------|------|
| **会话持久化** | 终端关闭/SSH断开后，Claude Code 会话仍在后台运行，随时恢复 |
| **多窗格并行** | 前端、后端、测试分配到不同窗格，模拟小型团队并行开发 |
| **无人值守** | 结合 cron/watch，定时编译、测试、部署，全天候自动化 |
| **远程开发** | tmux 后台运行 Claude Code，通过 SSH 远程连接使用 |
| **成本监控** | ccusage 实时追踪 token 消耗和 5 小时滚动窗口剩余时长 |

### 可借鉴的架构

```
┌─────────────────────────────────────────────────┐
│  tmux session: workspace                        │
│ ┌─────────────────────┬─────────────────────┐   │
│ │  窗格1: Claude Code │  窗格2: Claude Code │   │
│ │  (前端开发)          │  (后端开发)          │   │
│ ├─────────────────────┼─────────────────────┤   │
│ │  窗格3: watch pytest│  窗格4: ./deploy.sh │   │
│ │  (自动测试)          │  (部署脚本)          │   │
│ └─────────────────────┴─────────────────────┘   │
│  状态栏: [tmux-claude-status] [ccusage 监控]     │
└─────────────────────────────────────────────────┘
```

### 推荐插件生态

| 插件 | 作用 |
|------|------|
| `claude_code_agent_farm` | 批量启动数十个 Claude 代理，文件锁防冲突，仪表盘显示状态 |
| `tmux-claude-status` | 状态栏实时显示 Claude 工作状态 |
| `ccusage` | Token 消耗追踪、成本预估、滚动窗口剩余时长 |

---

## 各平台安装使用指南

### 1. Windows

Windows 本身不原生支持 tmux，有以下方案：

#### 方案 A：WSL2（推荐 ⭐⭐⭐⭐⭐）

```powershell
# 1. 安装 WSL2
wsl --install

# 2. 进入 WSL（默认 Ubuntu）
wsl

# 3. 安装 tmux
sudo apt-get update && sudo apt-get install -y tmux

# 4. 安装 Node.js（Claude Code 依赖）
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
sudo apt-get install -y nodejs

# 5. 安装 Claude Code
npm install -g @anthropic-ai/claude-code

# 6. 启动 tmux + Claude Code
tmux new -s workspace
claude
```

#### 方案 B：Git Bash + Windows Terminal

```bash
# Git Bash 不原生支持 tmux，但可以通过 MSYS2 安装
# 1. 安装 MSYS2 (https://www.msys2.org/)
# 2. 在 MSYS2 终端中：
pacman -S tmux

# 3. 安装 Claude Code
npm install -g @anthropic-ai/claude-code
```

#### 方案 C：直接用 Windows Terminal 多标签（轻量替代）

不用 tmux，利用 Windows Terminal 的多标签 + 分屏功能：
- `Alt+Shift+D` 分屏
- `Ctrl+Shift+T` 新标签
- 每个窗格运行一个 Claude Code 实例

#### 方案 D：VS Code 内置终端分屏

在 VS Code 中直接分屏终端，每个终端运行 Claude Code，结合 Copilot 使用。

---

### 2. Linux（Ubuntu/Debian/CentOS/Arch）

最原生、最推荐的平台。

#### Ubuntu / Debian

```bash
# 安装 tmux
sudo apt-get update && sudo apt-get install -y tmux

# 安装 Node.js
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
sudo apt-get install -y nodejs

# 安装 Claude Code
npm install -g @anthropic-ai/claude-code

# 安装 ccusage 监控
npm install -g ccusage

# 启动并行开发环境
tmux new -s workspace
# Ctrl+b 然后 % 垂直分屏
# Ctrl+b 然后 " 水平分屏
# 每个窗格中运行 claude
```

#### CentOS / RHEL

```bash
sudo yum install -y tmux
# 或
sudo dnf install -y tmux

# Node.js
curl -fsSL https://rpm.nodesource.com/setup_lts.x | sudo bash -
sudo yum install -y nodejs

npm install -g @anthropic-ai/claude-code
```

#### Arch Linux

```bash
sudo pacman -S tmux nodejs npm
npm install -g @anthropic-ai/claude-code
```

#### 一键启动脚本（可复用）

```bash
#!/bin/bash
# save as: ~/start-claude-farm.sh
SESSION="claude-farm"

tmux new-session -d -s $SESSION -n "frontend"
tmux send-keys -t $SESSION:frontend "claude" Enter

tmux new-window -t $SESSION -n "backend"
tmux send-keys -t $SESSION:backend "claude" Enter

tmux new-window -t $SESSION -n "test"
tmux send-keys -t $SESSION:test "watch -n 300 pytest" Enter

tmux new-window -t $SESSION -n "monitor"
tmux send-keys -t $SESSION:monitor "ccusage --plan max5" Enter

tmux attach -t $SESSION
```

---

### 3. macOS

#### 安装步骤

```bash
# 安装 Homebrew（如果还没有）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装 tmux
brew install tmux

# 安装 Node.js
brew install node

# 安装 Claude Code
npm install -g @anthropic-ai/claude-code

# 安装监控工具
npm install -g ccusage

# 启动
tmux new -s workspace
claude
```

#### macOS 特有优化

```bash
# 安装 tmux 插件管理器 TPM
git clone https://github.com/tmux-plugins/tpm ~/.tmux/plugins/tpm

# 在 ~/.tmux.conf 中添加：
cat >> ~/.tmux.conf << 'EOF'
# 插件列表
set -g @plugin 'tmux-plugins/tpm'
set -g @plugin 'tmux-plugins/tmux-sensible'
# Claude 状态插件（如有）
# set -g @plugin 'xxx/tmux-claude-status'

# 启用鼠标支持
set -g mouse on

# 提升颜色支持
set -g default-terminal "screen-256color"

# 初始化 TPM（保持在最后一行）
run '~/.tmux/plugins/tpm/tpm'
EOF

# 重载配置
tmux source-file ~/.tmux.conf
```

---

### 4. Android

Android 无原生终端，但可以通过 **Termux** 实现完整的 Linux 环境。

#### 安装步骤

```bash
# 1. 从 F-Droid 安装 Termux（不要用 Play Store 版本，已过时）
#    https://f-droid.org/packages/com.termux/

# 2. 打开 Termux，更新包
pkg update && pkg upgrade

# 3. 安装 tmux
pkg install tmux

# 4. 安装 Node.js
pkg install nodejs

# 5. 安装 Claude Code
npm install -g @anthropic-ai/claude-code

# 6. 启动
tmux new -s mobile-dev
claude
```

#### Android 使用技巧

- **Termux:Styling** 插件可以自定义字体和配色
- **Termux:Widget** 可以在桌面创建快捷方式一键启动 tmux+Claude
- 使用外接蓝牙键盘体验更佳
- `Ctrl` 键在 Termux 中映射为音量减 + 对应键
- 建议开启 Termux 的 `wake-lock` 防止后台被杀：
  ```bash
  termux-wake-lock
  tmux new -s workspace
  claude
  ```

#### 替代方案：SSH 连接远程服务器

```bash
# 在 Termux 中 SSH 到有 tmux 的服务器
pkg install openssh
ssh user@your-server.com
tmux attach -t workspace  # 恢复远程会话
```

---

### 5. iOS (iPhone / iPad)

iOS 限制更多，但仍有可行方案：

#### 方案 A：iSH + SSH（推荐 ⭐⭐⭐⭐）

```bash
# 1. App Store 安装 iSH（免费 Alpine Linux 模拟器）
# 2. 在 iSH 中安装 SSH 客户端
apk add openssh-client tmux

# 3. SSH 连接到远程 Linux 服务器（服务器上已安装 tmux + Claude Code）
ssh user@your-server.com
tmux attach -t workspace
```

#### 方案 B：a]Shell / Blink Shell（推荐 ⭐⭐⭐⭐⭐）

- **Blink Shell**（付费，最佳体验）：原生 Mosh/SSH 客户端，完美支持 tmux
- **a-Shell**（免费）：提供基础终端环境

```bash
# Blink Shell 中直接 SSH
ssh user@your-server.com
tmux attach -t workspace
```

#### 方案 C：Code Server / VS Code Web

在远程服务器部署 code-server，通过 iPad Safari 访问完整 VS Code 环境：

```bash
# 在服务器上安装
curl -fsSL https://code-server.dev/install.sh | sh
code-server --bind-addr 0.0.0.0:8080

# iPad 浏览器访问 http://your-server:8080
# 在内置终端中使用 tmux + Claude Code
```

---

## 快速对比表

| 平台 | tmux 原生支持 | 推荐方案 | 难度 | 体验评级 |
|------|:---:|----------|:---:|:---:|
| **Windows** | ❌ | WSL2 + tmux | ⭐⭐ | ⭐⭐⭐⭐ |
| **Linux** | ✅ | 原生 tmux | ⭐ | ⭐⭐⭐⭐⭐ |
| **macOS** | ✅ | brew install tmux | ⭐ | ⭐⭐⭐⭐⭐ |
| **Android** | ❌ | Termux + tmux | ⭐⭐ | ⭐⭐⭐ |
| **iOS** | ❌ | SSH + 远程 tmux | ⭐⭐⭐ | ⭐⭐⭐ |

## tmux 常用快捷键速查

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+b %` | 垂直分屏 |
| `Ctrl+b "` | 水平分屏 |
| `Ctrl+b 方向键` | 切换窗格 |
| `Ctrl+b c` | 新建窗口 |
| `Ctrl+b n/p` | 下/上一个窗口 |
| `Ctrl+b d` | 脱离会话（后台运行） |
| `tmux attach -t name` | 重新接入会话 |
| `Ctrl+b z` | 窗格最大化/还原 |
| `Ctrl+b [` | 进入滚动/复制模式 |

## 总结

这篇文章的核心借鉴点：

1. **tmux 是 Claude Code 的最佳搭档**：解决了 CLI 工具没有持久化会话的痛点
2. **多代理并行 = 生产力翻倍**：一个人可以同时跑多个 Claude 实例处理不同模块
3. **自动化测试循环**：watch + pytest 实现修改即测试的闭环
4. **远程开发无缝化**：SSH + tmux 让任何设备都能接入开发环境
5. **可观测性**：ccusage 和状态栏插件让 token 消耗和任务状态一目了然

移动端（Android/iOS）的最佳实践是：**本地设备仅作为终端入口，实际开发环境运行在远程 Linux 服务器上**，通过 SSH + tmux 实现无缝衔接。
