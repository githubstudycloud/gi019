# MCP 多层架构设计文档

## 1. 问题分析

### 核心需求
1. **远端 MCP Server** (Java): 提供实际工具能力，部署在服务器端
2. **本地 MCP Proxy** (Go): 快速启动、低资源占用，作为用户端代理
3. **MCP Gateway**: 管理多个远端 MCP 的连接、路由、负载均衡
4. **Skills**: Claude Code 技能，支持直连远端 / 经本地代理连远端
5. **自适应**: 本地 MCP 自动发现并适配远端能力变化

### 关键约束
- 远端不宜长连接（资源限制，防止用户过多拖垮服务）
- 但也要支持长连接场景（SSE 推送）
- 多机部署 + 不同功能拆分部署
- 连接数管控，防止资源耗尽

## 2. 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Claude Code                          │
│  ┌──────────────┐  ┌──────────────────────────────┐     │
│  │ Skill:       │  │ Skill:                       │     │
│  │ direct-mcp   │  │ proxy-mcp                    │     │
│  │ (直连远端)    │  │ (经本地代理)                   │     │
│  └──────┬───────┘  └──────────┬───────────────────┘     │
└─────────┼──────────────────────┼────────────────────────┘
          │                      │
          │ HTTP/SSE             │ stdio/HTTP
          │                      │
          │              ┌───────▼───────────┐
          │              │  Local MCP Proxy  │
          │              │  (Go, 轻量快速)    │
          │              │  - 工具缓存        │
          │              │  - 连接池管理      │
          │              │  - 自适应发现      │
          │              └───────┬───────────┘
          │                      │
          │ HTTP/SSE             │ HTTP/SSE
          │                      │
          │              ┌───────▼───────────┐
          ├─────────────►│   MCP Gateway     │
          │              │   (Go, 网关层)     │
          │              │   - 路由分发       │
          │              │   - 负载均衡       │
          │              │   - 连接池         │
          │              │   - 限流/熔断      │
          │              │   - 工具注册表     │
          │              └───────┬───────────┘
          │                      │
          │            ┌─────────┼─────────┐
          │            │         │         │
     ┌────▼────┐  ┌────▼────┐ ┌─▼───────┐ │
     │Remote   │  │Remote   │ │Remote   │ │
     │MCP-A    │  │MCP-B    │ │MCP-C    │ │
     │(Java)   │  │(Java)   │ │(Java)   │ │
     │数据库工具 │  │文件工具  │ │AI工具   │ │
     └─────────┘  └─────────┘ └─────────┘ │
                                           │
                              (可水平扩展多实例)
```

## 3. 通信协议设计

### 3.1 传输层选择

| 场景 | 协议 | 理由 |
|------|------|------|
| Claude Code <-> Local Proxy | stdio | MCP 原生支持，零网络开销 |
| Claude Code <-> 直连远端 | HTTP+SSE (Streamable HTTP) | MCP 2025-03-26 规范 |
| Local Proxy <-> Gateway | HTTP+SSE | 支持短连接+流式 |
| Gateway <-> Remote MCP | HTTP+SSE | 统一协议，连接池管理 |

### 3.2 连接策略

**短连接模式（默认）**:
- 每次工具调用建立 HTTP 请求
- Gateway 管理连接池复用底层 TCP
- 适合高并发、多用户场景

**长连接模式（可选）**:
- SSE 保持连接，用于需要实时推送的场景
- Gateway 做连接数限制（每用户最多 N 条 SSE）
- 心跳检测 + 空闲超时自动断开

## 4. 各组件详细设计

### 4.1 Remote MCP Server (Java)

```
技术栈: Spring Boot 3 + spring-ai-mcp-server
传输: Streamable HTTP (HTTP+SSE)
部署: 可多实例、按功能拆分

职责:
- 实现具体工具逻辑
- 工具能力声明 (tools/list)
- 健康检查接口
- 按功能域拆分部署（数据库工具、文件工具、AI工具等）
```

### 4.2 Local MCP Proxy (Go)

```
技术栈: Go 1.22+
传输入口: stdio (给 Claude Code 用)
传输出口: HTTP+SSE (连 Gateway 或直连远端)

职责:
- 接收 Claude Code 的 MCP 请求 (stdio)
- 转发到 Gateway 或直接转发到远端
- 工具列表缓存 + 定期刷新
- 连接池管理
- 自适应: 启动时从 Gateway 拉取工具注册表
- 离线降级: Gateway 不可用时尝试直连已知远端
```

### 4.3 MCP Gateway (Go)

```
技术栈: Go 1.22+
传输: HTTP+SSE

职责:
- 工具注册表: 维护所有远端 MCP 的工具清单
- 路由分发: 根据工具名路由到正确的远端 MCP
- 负载均衡: 同一工具多实例时做轮询/加权分发
- 连接池: 复用到远端 MCP 的连接
- 限流: 每用户 QPS 限制
- 熔断: 远端不可用时快速失败
- 自动发现: 定期探测远端 MCP 的 tools/list
- 管理 API: 动态增删远端 MCP 节点
```

### 4.4 Skills

**Skill 1: direct-remote-mcp**
- Claude Code 直连远端 MCP
- 适合单远端、简单场景
- 配置: 远端 MCP 的 URL

**Skill 2: proxy-mcp**
- Claude Code -> Local Proxy -> Gateway -> Remote MCP
- 适合多远端、需要管理的场景
- 配置: 本地 Proxy 的 stdio 命令

## 5. 自适应机制

```
┌─────────────┐     tools/list      ┌─────────────┐
│ Local Proxy │ ──────────────────► │   Gateway   │
│             │ ◄────────────────── │             │
│ 更新本地     │    工具注册表响应    │ 聚合所有远端 │
│ 工具缓存     │                    │ 的工具列表   │
└─────────────┘                    └──────┬──────┘
                                          │ 定期探测
                                   ┌──────▼──────┐
                                   │ Remote MCPs │
                                   │ tools/list  │
                                   └─────────────┘
```

1. Gateway 启动时拉取所有远端 MCP 的工具列表
2. 定期（30s）重新探测，发现变化时更新注册表
3. Local Proxy 启动时从 Gateway 获取完整工具列表
4. Local Proxy 定期（60s）刷新，或收到 Gateway 通知时刷新
5. Claude Code 调用 tools/list 时，Local Proxy 返回缓存的聚合列表

## 6. 资源保护

| 机制 | 实现层 | 策略 |
|------|--------|------|
| 连接数限制 | Gateway | 每用户最多 10 个并发连接 |
| QPS 限流 | Gateway | 令牌桶，每用户 100 QPS |
| 请求超时 | 全链路 | 单次工具调用最长 30s |
| SSE 空闲超时 | Gateway | 无消息 5min 自动断开 |
| 熔断 | Gateway | 连续 5 次失败，熔断 30s |
| 连接池 | Local Proxy + Gateway | 最大空闲连接 50 |

## 7. 多机部署方案

### 方案 A: 按功能拆分
```
机器1: Remote MCP (数据库工具)
机器2: Remote MCP (文件工具)
机器3: Remote MCP (AI工具)
机器4: Gateway (路由分发)
```

### 方案 B: 水平扩展
```
机器1-3: Remote MCP (全功能，3 副本)
机器4: Gateway (负载均衡)
```

### 方案 C: 混合部署
```
机器1-2: Remote MCP-A (数据库工具，2 副本)
机器3: Remote MCP-B (文件工具，1 副本)
机器4: Gateway
```

## 8. 项目结构

```
mcp-architecture/
├── DESIGN.md                    # 本设计文档
├── remote-mcp-server/           # Java 远端 MCP Server
│   ├── pom.xml
│   └── src/
├── local-mcp-proxy/             # Go 本地 MCP 代理
│   ├── go.mod
│   ├── main.go
│   └── proxy/
├── mcp-gateway/                 # Go MCP 网关
│   ├── go.mod
│   ├── main.go
│   └── gateway/
└── skills/                      # Claude Code Skills
    ├── direct-remote-mcp.md
    └── proxy-mcp.md
```
