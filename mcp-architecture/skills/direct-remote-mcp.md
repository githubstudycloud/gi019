# Skill: direct-remote-mcp

直连远端 MCP Server，跳过本地代理和网关，适用于简单的单远端场景。

## 使用场景
- 开发调试时直接连远端 MCP
- 只有一个远端 MCP 不需要网关路由
- 需要最低延迟的场景

## 配置方式

在 `claude_desktop_config.json` 或 `.claude/settings.json` 中添加：

```json
{
  "mcpServers": {
    "remote-db-tools": {
      "url": "http://your-server:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

## Skill 定义

将以下内容添加到 `.claude/skills/direct-remote-mcp.md`:

```markdown
---
name: direct-remote-mcp
description: 直连远端 MCP 服务器，执行远端工具调用。当用户想要直接调用远端数据库查询、系统信息等工具时自动激活。
trigger: 当用户提到"远端工具"、"远程查询"、"服务器工具"时触发
---

# 直连远端 MCP

该 Skill 通过 Streamable HTTP 直接连接远端 MCP Server。

## 连接信息
- 远端 MCP URL: http://localhost:8080/mcp
- 传输协议: Streamable HTTP (HTTP + SSE)

## 可用工具
连接后可自动发现远端提供的所有工具，包括但不限于：
- `listTables`: 查询数据库表列表
- `executeQuery`: 执行 SQL 查询
- `describeTable`: 获取表结构
- `getSystemInfo`: 获取系统信息
- `healthCheck`: 健康检查

## 使用方式
直接通过 MCP 协议调用远端工具即可，Claude Code 会自动处理连接和调用。
```

## 注意事项
- 每次调用都是独立 HTTP 请求，无需维护长连接
- 远端不可用时会直接报错
- 不具备负载均衡和熔断能力
- 适合开发/测试环境
