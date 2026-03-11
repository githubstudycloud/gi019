# 技术分析：官方 MCP SDK 能否简化我们的代码？

> 问题背景：`spring-ai-starter-mcp-server-webmvc` 是否可以替代我们自己手写的 MCP 框架层？

---

## 1. 官方 MCP SDK 是什么

`spring-ai-starter-mcp-server-webmvc` 是 Spring AI 项目提供的官方 MCP Server 支持库，归属于 `org.springframework.ai` 组。它将 MCP（Model Context Protocol）协议的服务端实现完整封装，开发者只需关注业务逻辑。

**核心组件：**

| 类/接口 | 职责 |
|---|---|
| `McpServer` | MCP 服务器生命周期管理，处理 SSE 和 Streamable HTTP 传输 |
| `McpServerFeatures` | 声明服务器能力（tools/resources/prompts） |
| `McpSyncServerExchange` | 工具调用上下文，包含请求信息和响应构建 |
| `ToolCallback` | 单个工具的回调接口 |
| `ToolCallbackProvider` | 工具集提供者接口（对应我们的 `McpToolProvider`） |
| `MethodToolCallbackProvider` | 基于 `@Tool` 注解自动扫描方法并注册工具 |
| `McpSchema.Tool` | 工具定义数据结构（对应我们的 `ToolDefinition`） |

**自动处理的协议细节：**
- `initialize` 握手（能力协商、协议版本）
- `ping` 心跳
- `tools/list`、`tools/call`
- `resources/list`、`resources/read`
- `prompts/list`、`prompts/get`
- SSE 连接管理（session 生命周期）
- JSON-RPC 2.0 封装（`jsonrpc`、`id`、`result`、`error` 字段）

当前协议版本 `2025-03-26` 由 SDK 内部维护，随 spring-ai 版本升级自动跟进。

---

## 2. 可以替换我们哪些代码

### 2.1 当前框架层文件清单

```
src/main/java/com/mcp/server/framework/
├── McpController.java      183 行   HTTP 入口：POST /mcp、GET /sse、POST /mcp/messages
├── McpDispatcher.java      104 行   JSON-RPC 方法分发：initialize/ping/tools/resources/prompts
├── McpRegistry.java        155 行   Provider 聚合器：listTools/callTool/listResources/listPrompts
├── McpToolProvider.java     31 行   工具提供者接口
├── McpPromptProvider.java   24 行   提示模板提供者接口
├── McpResourceProvider.java 25 行   资源提供者接口
└── ToolDefinition.java      16 行   工具定义 record
                          --------
合计                        538 行
```

### 2.2 可完全删除的文件（官方 SDK 等价覆盖）

| 我们的文件 | 对应官方 SDK | 替换说明 |
|---|---|---|
| `McpController.java` | SDK 内置 HTTP/SSE 处理器 | 自动配置，无需手写 |
| `McpDispatcher.java` | `McpServer` 内部路由 | 协议分发逻辑内置 |
| `McpRegistry.java` | Spring Bean 自动扫描 | `ToolCallbackProvider` 列表自动聚合 |
| `McpToolProvider.java` | `ToolCallbackProvider` 接口 | 直接替换接口定义 |
| `McpPromptProvider.java` | SDK 内置 Prompt 支持 | 通过 `McpServerFeatures` 注册 |
| `McpResourceProvider.java` | SDK 内置 Resource 支持 | 通过 `McpServerFeatures` 注册 |
| `ToolDefinition.java` | `McpSchema.Tool` | SDK 提供标准数据类型 |

**结论：上述 7 个文件、538 行代码可全部删除。**

### 2.3 需要适配但不能直接删除的文件

| 我们的文件 | 适配工作 |
|---|---|
| `ExecutionToolProvider.java` | 实现方式从 `McpToolProvider` 改为 `ToolCallbackProvider`，或加 `@Tool` 注解 |
| `TestCaseToolProvider.java` | 同上 |
| `DynamicToolProvider.java` | 需要自定义实现 `ToolCallbackProvider` 接口（见第 5 节） |

---

## 3. 迁移示例

### 3.1 引入依赖

将 `pom.xml` 中替换 `spring-boot-starter-web`，改为引入 MCP starter：

```xml
<!-- 替换原来的 spring-boot-starter-web -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    <version>1.0.0</version>
</dependency>
```

同时在 `<dependencyManagement>` 中引入 BOM（推荐方式）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3.2 工具实现变化

#### 方式 A：注解驱动（推荐用于静态工具）

当前方式——业务逻辑分散在 `XxxToolProvider.callTool()` 的 switch 分支里：

```java
// 当前：ExecutionToolProvider.java（节选）
@Component
public class ExecutionToolProvider implements McpToolProvider {

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return List.of(
            new ToolDefinition(
                "execution_list_projects",
                "【第一步】列出所有项目及其关联的业务库信息...",
                Map.of("type", "object", "properties", Map.of(...))
            ),
            // ... 6 个工具定义，每个都要手写 inputSchema Map
        );
    }

    @Override
    public Object callTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "execution_list_projects" -> service.listProjectsWithBiz(
                    str(args, "keyword"), intVal(args, "page"), intVal(args, "limit"));
            // ... 6 个 case 分支，每个都要手动解析参数类型
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }
}
```

官方 SDK 方式——直接在 `Service` 方法上加注解，框架自动生成工具定义和参数解析：

```java
// 迁移后：ExecutionService.java（节选）
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Service
public class ExecutionService {

    @Tool(description = "【第一步】列出所有项目及其关联的业务库信息。" +
            "返回 projectId、projectName、dsKey（业务库标识）、bizName。" +
            "这是执行用例查询的入口，后续步骤需要 projectId。")
    public Map<String, Object> execution_list_projects(
            @ToolParam(description = "项目名称或描述的模糊搜索关键字，不填返回全部", required = false)
            String keyword,
            @ToolParam(description = "页码，默认1", required = false)
            Integer page,
            @ToolParam(description = "每页数量，默认20，最大100", required = false)
            Integer limit) {
        // 原有业务逻辑不变
        return listProjectsWithBiz(keyword, page, limit);
    }

    @Tool(description = "【第二步】列出指定项目的所有基线，包含用例数量和执行概况。" +
            "需要先调用 execution_list_projects 获取 projectId。")
    public Map<String, Object> execution_list_baselines(
            @ToolParam(description = "项目ID（必填，来自 execution_list_projects 的结果）")
            Long projectId,
            @ToolParam(description = "页码，默认1", required = false) Integer page,
            @ToolParam(description = "每页数量，默认20", required = false) Integer limit) {
        return listBaselines(projectId, page, limit);
    }
    // ... 其余方法同理
}
```

然后通过 `@Bean` 注册给 SDK：

```java
// 新增：MCP 配置类
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider executionTools(ExecutionService executionService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(executionService)
                .build();
    }

    @Bean
    public ToolCallbackProvider testCaseTools(TestCaseService testCaseService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(testCaseService)
                .build();
    }
}
```

注意：`MethodToolCallbackProvider` 会扫描传入对象上的所有 `@Tool` 方法，自动从方法签名和 `@ToolParam` 注解推导 JSON Schema，**不再需要手写 inputSchema Map**。

#### 方式 B：编程式注册（适用于动态工具）

对于 `DynamicToolProvider`（工具定义来自 DB），需要实现 `ToolCallbackProvider` 接口：

```java
// DynamicToolCallbackProvider.java（迁移后）
@Component
public class DynamicToolCallbackProvider implements ToolCallbackProvider {

    private final MetaConfigRepo repo;
    private final QueryEngine queryEngine;

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolConfig> tools = repo.findAllTools(true);
        return tools.stream()
                .map(this::buildCallback)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback buildCallback(ToolConfig toolConfig) {
        McpSchema.Tool toolDef = new McpSchema.Tool(
                toolConfig.getToolKey(),
                toolConfig.getDescription(),
                buildJsonSchema(toolConfig)   // 复用原有 buildInputSchema 逻辑
        );
        return new FunctionToolCallback(toolDef, args -> {
            // 复用原有 callTool 逻辑
            return queryEngine.queryPaged(
                    repo.findViewKey(toolConfig.getQueryViewId()),
                    repo.findDsKey(toolConfig.getQueryViewId()),
                    args, getPage(args), getLimit(args)
            );
        });
    }
}
```

SDK 启动时调用 `getToolCallbacks()` 收集工具定义；热重载时可通过 SDK 提供的 `McpServer.notifyToolsChanged()` 推送变更通知给客户端。

### 3.3 application.yml 配置

原来需要在代码中硬编码 `serverName`、`serverVersion`，迁移后通过标准配置项：

```yaml
# 迁移前（McpDispatcher 中读取）:
# @Value("${mcp.server.name:mcp-remote-server}") String serverName

# 迁移后（application.yml）:
spring:
  ai:
    mcp:
      server:
        name: mcp-remote-server
        version: 1.0.0
        # 以下传输方式自动配置，对应我们原有的 POST /mcp 和 GET /sse
        type: SYNC          # SYNC（同步）或 ASYNC（响应式）
```

端点 `/mcp`（Streamable HTTP）和 `/sse`（SSE Transport）由 SDK 的 `McpHttpServerTransportProvider` 自动注册，路径可配置。

---

## 4. 收益分析

### 4.1 代码量变化

| 维度 | 当前 | 迁移后 | 变化 |
|---|---|---|---|
| 框架层代码（7 文件） | 538 行 | 0 行 | -538 行（全部删除） |
| 配置类（新增） | 0 行 | ~30 行 | +30 行 |
| ToolProvider 适配层 | ~250 行（2 个静态 Provider） | ~10 行（仅 `@Bean` 声明） | -240 行 |
| 业务 Service 注解 | 0 行 | ~60 行（`@Tool`/`@ToolParam`） | +60 行 |
| **净减少** | | | **约 -688 行（含适配层）** |

### 4.2 协议合规性提升

我们当前的框架层存在若干已知隐患：

- `McpController` 的 SSE 会话管理（`ConcurrentHashMap<String, SseEmitter>`）在高并发下存在 session 泄漏风险
- `McpDispatcher` 硬编码协议版本字符串 `"2025-03-26"`，协议更新时需手动修改
- `McpRegistry.callTool()` 的错误响应格式（`isError: true`）依赖人工维护与协议规范的一致性

官方 SDK 已通过 MCP 官方测试套件验证，上述细节均由 SDK 负责。

### 4.3 新特性免费获得

| 特性 | 当前状态 | SDK 提供 |
|---|---|---|
| 工具变更通知（`notifications/tools/list_changed`） | 不支持 | 自动支持 |
| 取消请求（`$/cancelRequest`） | 不支持 | 自动支持 |
| 进度通知（`$/progress`） | 不支持 | 内置支持 |
| Streamable HTTP 支持 | 手写实现 | 内置 |
| 协议版本协商 | 硬编码 | 自动协商 |

---

## 5. 风险和注意事项

### 5.1 SDK 版本稳定性

Spring AI MCP SDK 在 2025 年仍处于快速迭代阶段。`spring-ai-starter-mcp-server-webmvc` 的 API 在 `0.x` 到 `1.0.x` 之间曾有多次破坏性变更（例如 `ToolCallback` 接口签名、`@Tool` 注解所属包路径）。

建议：锁定到 `1.0.0` GA 版本后再迁移，避免使用 SNAPSHOT 版本进入生产环境。

### 5.2 DynamicToolProvider 的特殊性

`DynamicToolProvider` 从数据库动态加载工具定义，这是官方 `MethodToolCallbackProvider`（基于注解反射）**无法**直接覆盖的场景。

迁移方案：实现 `ToolCallbackProvider` 接口（方式 B），大部分逻辑（`buildInputSchema`、`getOrLoadTools`、`queryEngine.queryPaged`）可以直接复用，只需更换包装层。工作量约 0.5 天。

### 5.3 热重载机制变化

当前 `DynamicToolProvider.refresh()` 只需清空本地缓存即可，下次 `tools/list` 请求自动拿到新数据。

迁移后，SDK 会在启动时缓存工具列表，需要通过 `McpSyncServer.notifyToolsListChanged()` 主动推送变更通知（当客户端支持 `listChanged` 能力时）。`MetaApiController` 中调用 `refresh()` 的地方需同步更新。

### 5.4 端点路径变化

| 传输方式 | 当前路径 | SDK 默认路径 | 可否配置 |
|---|---|---|---|
| Streamable HTTP | `POST /mcp` | `POST /mcp` | 可配置 |
| SSE 建立连接 | `GET /sse` | `GET /sse` | 可配置 |
| SSE 消息 | `POST /mcp/messages` | `POST /mcp/messages` | 可配置 |

默认路径与我们当前一致，`.claude/settings.json` 和 skill 中的 curl 调用无需修改。

### 5.5 健康检查端点

当前 `McpController` 提供 `GET /health` 端点。迁移后该端点随框架层删除，需在单独的 Controller 中保留，或改用 `spring-boot-starter-actuator` 的 `/actuator/health`（项目已引入）。

---

## 6. 迁移成本估算

| 任务 | 工作量 | 说明 |
|---|---|---|
| 删除 framework/ 层 7 个文件 | 0.5 天 | 删除代码，调整 import |
| 引入 spring-ai BOM + starter 依赖 | 0.5 天 | 含版本兼容性验证 |
| `ExecutionToolProvider` 适配 | 0.5 天 | 改用 `@Tool` 注解方式 |
| `TestCaseToolProvider` 适配 | 0.5 天 | 改用 `@Tool` 注解方式 |
| `DynamicToolProvider` 适配 | 0.5 天 | 实现 `ToolCallbackProvider` |
| `/health` 端点迁移 | 0.25 天 | 保留或切换到 Actuator |
| `McpRegistry.listTools()` 调用替换 | 0.25 天 | `MetaApiController` 等调用点 |
| 集成测试验证 | 1 天 | 用 curl / Claude Code 验证全部工具 |
| **合计** | **4 天** | |

---

## 7. 结论与建议

**推荐在下一个版本迭代中迁移。**

理由如下：

1. **收益明确、代价有限**：删除 538 行自维护框架代码，换来协议合规性保障和未来特性免费升级，4 天工作量换取长期维护成本的持续降低，ROI 合理。

2. **现阶段不建议立即迁移**：项目当前仍在功能开发阶段（动态工具、QueryEngine 等），在 sprint 中途切换框架层风险较大；建议在当前功能趋于稳定后，作为独立的重构 sprint 执行。

3. **DynamicToolProvider 是唯一需要额外设计的点**：其他所有工具均可用注解方式无缝迁移，`DynamicToolProvider` 需要封装 `ToolCallbackProvider` 实现类，但核心查询逻辑完全复用，改动仅在包装层。

4. **迁移前置条件**：
   - 确认 `spring-ai 1.0.0` GA 已正式发布（截至 2026 年 3 月，应已发布）
   - 在开发环境完成全量工具的端到端验证后再合并到主分支

**最终建议**：当前自定义框架运行稳定，无需紧急迁移；将迁移列入下一季度技术债清单，在功能稳定后用 1 个工作周完成，届时可同步验证 `spring-ai 1.0.x` 在该场景下的生产可用性。
