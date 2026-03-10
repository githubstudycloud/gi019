# MCP Remote Server

生产可用的远端 MCP Server，支持完整 MCP 协议、业务解耦注册、MySQL 数据库可配置映射。

## 架构

```
Framework Layer (固定，不需改动)         Business Layer (自由扩展)
├── McpController (Streamable HTTP/SSE)  ├── TestCaseToolProvider (@Component)
├── McpDispatcher (JSON-RPC 2.0 分发)    │   └── implements McpToolProvider
├── McpRegistry (自动发现所有 Provider)    ├── TestCaseService (JDBC 查询)
├── McpToolProvider (工具接口)            └── 新增业务只需加 @Component
├── McpResourceProvider (资源接口)
└── McpPromptProvider (提示模板接口)
```

新增业务工具 **不需要修改框架代码**，只需：
1. 写一个 `@Component` 类实现 `McpToolProvider` 接口
2. 在 `getToolDefinitions()` 声明工具名称、描述、参数 Schema
3. 在 `callTool()` 实现业务逻辑
4. 框架通过 Spring 自动扫描注册

## 项目结构

```
src/main/java/com/mcp/server/
├── McpRemoteServerApplication.java          # 启动类
├── framework/                                # MCP 框架层（通用，不改）
│   ├── McpController.java                   # HTTP 端点 (POST /mcp, GET /sse)
│   ├── McpDispatcher.java                   # JSON-RPC 方法分发
│   ├── McpRegistry.java                     # Provider 注册表（自动发现）
│   ├── McpToolProvider.java                 # 工具 Provider 接口
│   ├── McpResourceProvider.java             # 资源 Provider 接口
│   ├── McpPromptProvider.java               # 提示模板 Provider 接口
│   └── ToolDefinition.java                  # 工具定义 record
├── config/
│   └── DbTableProperties.java              # 可配置的表名/字段名映射
└── business/testcase/                        # 业务层（示例：测试用例）
    ├── TestCaseToolProvider.java             # MCP 工具定义 + 参数解析
    └── TestCaseService.java                 # 业务逻辑（JDBC 查询）
```

## MCP 协议支持

| 协议 | 端点 | 说明 |
|------|------|------|
| Streamable HTTP | `POST /mcp` | 同步 JSON-RPC 请求/响应（主要） |
| SSE | `GET /sse` + `POST /mcp/messages` | 兼容旧版 MCP 客户端 |

支持的 JSON-RPC 方法：`initialize`, `ping`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, `prompts/get`

## 可用工具

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `search_test_case` | 搜索测试用例 | projectName(模糊), caseName(模糊), versionName(可选), uri(可选) |
| `list_projects` | 列出项目及统计 | keyword(可选, 模糊搜索) |
| `get_project_detail` | 项目详情 | projectName(模糊匹配) |

## 快速启动

### 前置条件

- Java 17+
- Maven 3.6+
- MySQL 5.7+（生产环境）或使用 H2 内存库（本地测试）

### 方式一：H2 内存库（无需 MySQL，开箱即用）

```bash
# 编译
cd mcp-server-remote
mvn package -DskipTests

# 启动（自动建表+灌入测试数据）
java -jar target/mcp-server-remote-1.0.0.jar --spring.profiles.active=h2
```

### 方式二：MySQL 5.7

```bash
# 1. 在 MySQL 中建表
mysql -u root -p testdb < src/main/resources/schema-mysql.sql

# 2. 修改数据库连接配置
vim src/main/resources/application.yml
# 修改 spring.datasource.url / username / password

# 3. 编译启动
mvn package -DskipTests
java -jar target/mcp-server-remote-1.0.0.jar
```

### 数据库配置说明

在 `application.yml` 中配置数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/testdb?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

表名和字段名可自定义（当实际数据库表结构与默认不同时修改）：

```yaml
mcp:
  db:
    tables:
      project: t_project           # 项目表名（改成你的表名）
      version: t_project_version   # 版本表名
      testcase: t_test_case        # 用例表名
    columns:
      project:
        id: id
        name: name                 # 项目名称字段（改成你的字段名）
        description: description
      version:
        id: id
        projectId: project_id      # 关联项目的外键字段
        versionName: version_name
        uri: uri
      testcase:
        id: id
        projectId: project_id
        caseName: case_name        # 用例名称字段
        caseType: case_type
        priority: priority
        precondition: precondition
        steps: steps
        expectedResult: expected_result
        status: status
```

## 测试

### 健康检查

```bash
curl http://localhost:8080/health
```

### MCP 协议测试

```bash
# 初始化握手
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

# 列出所有工具
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 列出项目
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_projects","arguments":{}}}'

# 搜索用例（模糊匹配项目名+用例名）
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"search_test_case","arguments":{"projectName":"电商","caseName":"登录"}}}'

# 获取项目详情
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_project_detail","arguments":{"projectName":"用户"}}}'

# 搜索用例（带版本过滤）
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"search_test_case","arguments":{"projectName":"电商","versionName":"v2"}}}'
```

## Claude Code / Claude Desktop 集成

在 `claude_desktop_config.json` 中添加：

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

配置后 Claude 可直接调用远端工具，例如：
- "帮我搜索电商平台的登录相关用例"
- "列出所有项目"
- "查看用户中心项目的详情"

## 扩展开发

### 添加新业务工具

创建一个新的 `@Component` 实现 `McpToolProvider`：

```java
@Component
public class MyToolProvider implements McpToolProvider {

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return List.of(new ToolDefinition(
            "my_tool",
            "我的工具描述",
            Map.of("type", "object", "properties", Map.of(
                "param1", Map.of("type", "string", "description", "参数1")
            ), "required", List.of("param1"))
        ));
    }

    @Override
    public Object callTool(String name, Map<String, Object> arguments) {
        return Map.of("result", "Hello " + arguments.get("param1"));
    }
}
```

放到任意包下，框架自动发现并注册，无需修改其他代码。

### 添加新资源 / 提示模板

同理实现 `McpResourceProvider` 或 `McpPromptProvider` 接口即可。
