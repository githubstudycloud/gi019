# Spring Boot AI + MCP Server Demo

基于 Spring Boot 3.4 + Spring AI + MCP SDK 的 Model Context Protocol 服务端实现，支持多种传输协议。

## 技术栈

- **Spring Boot 3.4.3** - Web 框架
- **Spring AI 1.0.0-M6** - AI 模型集成 (OpenAI/兼容 API)
- **MCP Java SDK 0.9.0** - Model Context Protocol 实现
- **Java 17+**

## 项目结构

```
src/main/java/com/example/mcpserver/
├── McpServerApplication.java           # 启动类
├── config/
│   ├── McpServerConfig.java            # Stdio 传输配置
│   ├── McpSseConfig.java               # SSE 传输配置 (默认启用)
│   └── McpStreamableHttpConfig.java    # Streamable HTTP 传输配置
├── mcp/
│   ├── McpToolProvider.java            # MCP Tools (4个工具)
│   ├── McpResourceProvider.java        # MCP Resources (3个资源)
│   └── McpPromptProvider.java          # MCP Prompts (3个提示模板)
├── service/
│   └── AiChatService.java              # Spring AI 聊天服务
└── controller/
    ├── ChatController.java             # REST /api/chat
    └── McpInfoController.java          # REST /api/mcp/info
```

## 支持的 MCP 传输协议

| 协议 | 端点 | 适用场景 | 默认状态 |
|------|------|---------|---------|
| **SSE** | `GET /sse` + `POST /mcp/messages` | Web 客户端, 远程连接 | 启用 |
| **Streamable HTTP** | `POST /mcp/stream` | 新 MCP 协议, 双向流式 | 禁用 |
| **Stdio** | 标准输入/输出 | Claude Desktop 子进程模式 | 禁用 |

## 快速开始

### 前置条件

- Java 17+
- Maven 3.6+

### 启动服务

```bash
# 默认 SSE 模式 (端口 18080)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.ai.openai.api-key=YOUR_KEY"

# 同时启用 SSE + Streamable HTTP
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.ai.openai.api-key=YOUR_KEY --mcp.transport.streamable-http.enabled=true"

# Stdio 模式 (用于 Claude Desktop)
mvn spring-boot:run -Dspring-boot.run.profiles=stdio
```

### 测试

```bash
mvn test
```

## MCP 功能

### Tools (工具)

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `get_current_time` | 获取当前时间 | `timezone` (可选) |
| `calculate` | 算术计算 | `expression` (必填) |
| `search_knowledge` | 知识库搜索 | `query` (必填), `limit` (可选) |
| `generate_uuid` | 生成 UUID | 无 |

### Resources (资源)

| URI | 描述 |
|-----|------|
| `config://app/info` | 应用元数据 |
| `config://app/health` | 健康状态 |
| `data://knowledge/topics` | 知识库主题列表 |

### Prompts (提示模板)

| 名称 | 描述 | 参数 |
|------|------|------|
| `code_review` | 代码审查 | `code` (必填), `language` (可选) |
| `explain_concept` | 概念解释 | `concept` (必填), `level` (可选) |
| `generate_test` | 生成测试 | `code` (必填), `framework` (可选) |

## API 测试示例

### 查看 MCP 服务信息

```bash
curl http://localhost:18080/api/mcp/info | python3 -m json.tool
```

### SSE 传输

```bash
# 建立 SSE 连接
curl -N http://localhost:18080/sse
# 返回: event: endpoint
#        data: /mcp/messages?sessionId=xxx
```

### Streamable HTTP 传输

```bash
# 初始化
curl -X POST http://localhost:18080/mcp/stream \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# 列出工具
curl -X POST http://localhost:18080/mcp/stream \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 调用计算工具
curl -X POST http://localhost:18080/mcp/stream \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"calculate","arguments":{"expression":"(2+3)*4"}}}'

# 读取资源
curl -X POST http://localhost:18080/mcp/stream \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"config://app/health"}}'

# 获取提示模板
curl -X POST http://localhost:18080/mcp/stream \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"prompts/get","params":{"name":"explain_concept","arguments":{"concept":"MCP Protocol"}}}'
```

## Claude Desktop 集成

在 Claude Desktop 配置文件中添加：

```json
{
  "mcpServers": {
    "spring-boot-mcp": {
      "command": "java",
      "args": ["-jar", "target/mcp-server-demo-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=stdio"],
      "env": {
        "SPRING_AI_OPENAI_API_KEY": "your-api-key"
      }
    }
  }
}
```

## 配置参考

```yaml
server:
  port: 18080

mcp:
  server:
    name: spring-boot-mcp-server
    version: 1.0.0
  transport:
    sse:
      enabled: true              # SSE 传输 (默认启用)
    streamable-http:
      enabled: false             # Streamable HTTP 传输
    stdio:
      enabled: false             # Stdio 传输

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${AI_MODEL:gpt-4o}
```

## 扩展开发

### 添加新工具

在 `McpToolProvider.java` 的 `getToolSpecifications()` 方法中添加：

```java
new SyncToolSpecification(
    new Tool("my_tool", "My custom tool description",
        new JsonSchema("object",
            Map.of("param1", Map.of("type", "string", "description", "Parameter 1")),
            List.of("param1"), null)),
    (exchange, args) -> {
        String param1 = String.valueOf(args.get("param1"));
        // 实现逻辑
        return new CallToolResult(List.of(new TextContent("Result: " + param1)), false);
    }
)
```

### 添加新资源

在 `McpResourceProvider.java` 的 `getResourceSpecifications()` 方法中添加：

```java
new SyncResourceSpecification(
    new Resource("data://my/resource", "My Resource", "Description", "application/json", null),
    (exchange, request) -> {
        String json = objectMapper.writeValueAsString(Map.of("key", "value"));
        return new ReadResourceResult(List.of(
            new TextResourceContents("data://my/resource", "application/json", json)));
    }
)
```
