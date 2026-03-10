# Skill: proxy-mcp

通过本地 MCP Proxy 连接远端，支持网关路由、负载均衡、自适应发现。

## 使用场景
- 生产环境多远端 MCP 部署
- 需要负载均衡和熔断保护
- 需要自适应工具发现
- 多机部署 + 不同功能拆分

## 架构链路
```
Claude Code --stdio--> Local MCP Proxy --HTTP--> MCP Gateway --HTTP--> Remote MCP(s)
```

## 配置方式

### 方式 1: 经 Gateway（推荐）

在 `claude_desktop_config.json` 中添加：

```json
{
  "mcpServers": {
    "mcp-proxy": {
      "command": "path/to/local-mcp-proxy",
      "args": ["--gateway", "http://gateway-host:9090/gateway"],
      "transport": "stdio"
    }
  }
}
```

### 方式 2: 直连远端（降级）

```json
{
  "mcpServers": {
    "mcp-proxy": {
      "command": "path/to/local-mcp-proxy",
      "args": ["--direct", "http://remote1:8080,http://remote2:8081"],
      "transport": "stdio"
    }
  }
}
```

### 方式 3: Gateway + 直连降级

```json
{
  "mcpServers": {
    "mcp-proxy": {
      "command": "path/to/local-mcp-proxy",
      "args": [
        "--gateway", "http://gateway-host:9090/gateway",
        "--direct", "http://remote1:8080"
      ],
      "transport": "stdio"
    }
  }
}
```

## Skill 定义

将以下内容添加到 `.claude/skills/proxy-mcp.md`:

```markdown
---
name: proxy-mcp
description: 通过本地代理连接远端 MCP 集群。当用户想要执行远端工具、查询远端服务、管理远端资源时自动激活。
trigger: 当用户提到"远端"、"远程"、"服务器"相关工具调用时触发
---

# 代理模式远端 MCP

通过本地 MCP Proxy 连接 MCP Gateway，自动发现和路由到后端多个远端 MCP Server。

## 特性
- 自适应工具发现：自动从 Gateway 获取所有可用工具
- 负载均衡：同一工具多实例时自动轮询
- 熔断保护：远端故障时快速失败
- 连接池：复用底层连接
- 降级直连：Gateway 不可用时尝试直连已知远端

## 可用工具
工具列表由远端动态提供，启动时自动发现。
通过 `tools/list` 可获取当前所有可用工具。
```

## Gateway 管理

### 动态添加远端节点
```bash
curl -X POST http://gateway:9090/admin/remotes \
  -H "Content-Type: application/json" \
  -d '{"id":"mcp-db-2","name":"Database MCP 2","url":"http://db-server-2:8080"}'
```

### 查看所有节点
```bash
curl http://gateway:9090/admin/remotes
```

### 移除节点
```bash
curl -X DELETE http://gateway:9090/admin/remotes/mcp-db-2
```
