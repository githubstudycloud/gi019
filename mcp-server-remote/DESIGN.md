# MCP Remote Server - 设计文档

## 1. 目标

构建一个生产可用的远端 MCP Server，具备：
- 完整的 MCP 协议实现（Streamable HTTP + SSE + JSON-RPC 2.0）
- 业务代码与框架完全解耦，通过注册机制接入
- 真实 MySQL 数据库连接，表名/字段名可配置
- 直连 Skill 支持 Claude Code 直接调用

## 2. MCP 协议实现

### 2.1 Streamable HTTP Transport (MCP 2025-03-26)

```
POST /mcp
Content-Type: application/json
Accept: application/json, text/event-stream

请求体: JSON-RPC 2.0 单条或批量
响应: 同步 JSON-RPC 或 SSE 流
```

支持的 JSON-RPC 方法:

| 方法 | 方向 | 说明 |
|------|------|------|
| `initialize` | client->server | 握手，交换能力 |
| `notifications/initialized` | client->server | 客户端确认初始化完成 |
| `ping` | 双向 | 心跳 |
| `tools/list` | client->server | 获取工具列表 |
| `tools/call` | client->server | 调用工具 |
| `resources/list` | client->server | 获取资源列表 |
| `resources/read` | client->server | 读取资源 |
| `prompts/list` | client->server | 获取提示模板列表 |
| `prompts/get` | client->server | 获取提示模板 |

### 2.2 SSE Transport（兼容旧客户端）

```
GET  /sse              -> SSE 流，推送 endpoint 事件
POST /mcp/messages     -> JSON-RPC 请求，响应通过 SSE 推送
```

### 2.3 JSON-RPC 2.0 规范

请求:
```json
{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "xxx", "arguments": {}}}
```

响应:
```json
{"jsonrpc": "2.0", "id": 1, "result": {"content": [{"type": "text", "text": "..."}], "isError": false}}
```

错误:
```json
{"jsonrpc": "2.0", "id": 1, "error": {"code": -32000, "message": "..."}}
```

通知（无 id，不需要响应）:
```json
{"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}
```

## 3. 框架与业务解耦架构

```
┌─────────────────────────────────────────────┐
│              MCP Framework Layer             │
│  ┌─────────────────────────────────────┐    │
│  │ McpController (HTTP/SSE 端点)       │    │
│  │ - Streamable HTTP: POST /mcp        │    │
│  │ - SSE: GET /sse + POST /mcp/messages│    │
│  └──────────────┬──────────────────────┘    │
│                 │                            │
│  ┌──────────────▼──────────────────────┐    │
│  │ McpDispatcher (JSON-RPC 分发)       │    │
│  │ - initialize / ping                  │    │
│  │ - tools/* / resources/* / prompts/*  │    │
│  └──────────────┬──────────────────────┘    │
│                 │                            │
│  ┌──────────────▼──────────────────────┐    │
│  │ ToolRegistry / ResourceRegistry     │    │
│  │ - register(name, schema, handler)   │    │
│  │ - 运行时动态注册/注销               │    │
│  └──────────────┬──────────────────────┘    │
└─────────────────┼───────────────────────────┘
                  │ 接口调用
┌─────────────────▼───────────────────────────┐
│           Business Layer (解耦)              │
│                                              │
│  ┌────────────────────┐                      │
│  │ TestCaseToolProvider│ implements McpToolProvider
│  │ - searchTestCase()  │                     │
│  │ - listProjects()    │                     │
│  │ - getProjectDetail()│                     │
│  └────────┬───────────┘                      │
│           │                                  │
│  ┌────────▼───────────┐                      │
│  │ TestCaseService    │                      │
│  │ (JDBC/MySQL)       │                      │
│  └────────────────────┘                      │
│                                              │
│  (更多 Provider 可独立注册...)                │
└──────────────────────────────────────────────┘
```

### 3.1 注册接口

```java
// 业务方实现此接口，框架自动发现并注册
public interface McpToolProvider {
    List<ToolDefinition> getToolDefinitions();
    Object callTool(String name, Map<String, Object> arguments);
}
```

框架通过 Spring 的 `@Component` 扫描自动发现所有 `McpToolProvider` 实现，
无需修改框架代码即可新增业务工具。

## 4. 数据库设计

### 4.1 表结构（默认表名/字段名，均可通过配置覆盖）

**项目表** (`t_project`):
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| name | varchar(200) | 项目名称 |
| description | varchar(500) | 项目描述 |
| created_at | datetime | 创建时间 |

**版本/URI 表** (`t_project_version`):
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| project_id | bigint | 关联项目 |
| version_name | varchar(100) | 版本名称 |
| uri | varchar(500) | 对应 URI |
| created_at | datetime | 创建时间 |

**用例表** (`t_test_case`):
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| project_id | bigint | 关联项目 |
| case_name | varchar(200) | 用例名称 |
| case_type | varchar(50) | 用例类型 |
| priority | varchar(20) | 优先级 |
| precondition | text | 前置条件 |
| steps | text | 操作步骤 |
| expected_result | text | 预期结果 |
| status | varchar(20) | 状态 |
| created_at | datetime | 创建时间 |

### 4.2 可配置映射

```yaml
mcp:
  db:
    tables:
      project: t_project
      version: t_project_version
      testcase: t_test_case
    columns:
      project:
        id: id
        name: name
        description: description
      version:
        id: id
        projectId: project_id
        versionName: version_name
        uri: uri
      testcase:
        id: id
        projectId: project_id
        caseName: case_name
        caseType: case_type
        priority: priority
        precondition: precondition
        steps: steps
        expectedResult: expected_result
        status: status
```

## 5. 业务工具设计

| 工具名 | 描述 | 参数 | 查询逻辑 |
|--------|------|------|---------|
| `search_test_case` | 搜索用例 | projectName(模糊), caseName(模糊), versionName(可选), uri(可选) | 模糊匹配项目名+用例名，可选过滤版本 |
| `list_projects` | 列出项目 | keyword(可选, 模糊) | 列出所有或模糊搜索项目 |
| `get_project_detail` | 项目详情 | projectName(模糊) | 返回项目+版本列表+用例统计 |

## 6. Skill 配置

### 直连远端 MCP

```json
{
  "mcpServers": {
    "remote-testcase": {
      "url": "http://your-server:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```
