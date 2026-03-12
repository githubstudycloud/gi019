# 迁移设计：现有架构 → Spring AI 架构映射

## 1. 当前自定义框架层（可全量删除）

```
framework/
├── McpController.java      183 行   HTTP 端点：POST /mcp、GET /sse
├── McpDispatcher.java      104 行   JSON-RPC 方法路由（initialize/tools/list/tools/call）
├── McpRegistry.java        155 行   工具聚合注册中心
├── McpToolProvider.java     31 行   工具提供者接口
├── McpPromptProvider.java   24 行   Prompt 提供者接口
├── McpResourceProvider.java 25 行   Resource 提供者接口
└── ToolDefinition.java      16 行   工具定义 record
                            ─────
                总计：        538 行  ← 引入 Spring AI 后全部删除
```

这 538 行在功能上与 `spring-ai-starter-mcp-server-webmvc` 完全重合，Spring AI 提供的版本
还额外支持：请求取消、进度通知、工具列表变更通知、AOT 原生编译。

---

## 2. 架构对比

### 现有架构

```
HTTP Request
    ↓
McpController          ← 自定义，183行
    ↓
McpDispatcher          ← 自定义，104行（识别 initialize/tools/list/tools/call）
    ↓
McpRegistry            ← 自定义，155行（聚合所有 McpToolProvider）
    ↓                        ↑
McpToolProvider（接口）       │  所有 Provider 实现注册到此
    ↑─────────────────────────┘
TestCaseToolProvider  ExecutionToolProvider  DynamicToolProvider
```

### 迁移后架构（Spring AI）

```
HTTP Request
    ↓
WebMvcSseServerTransportProvider   ← Spring AI 自动配置，无需编写
    ↓
McpSyncServer（spring-ai-mcp）      ← Spring AI 自动配置，无需编写
    ↓
ToolCallbackProvider（接口）         ← Spring AI 接口，替换 McpToolProvider
    ↑─────────────────────────────────┘
TestCaseService  ExecutionService  McpSyncServer.addTool()（动态工具）
（加 @Tool 注解）（加 @Tool 注解）  （DynamicToolProvider 适配层）
```

---

## 3. ToolProvider 层：迁移前后代码对比

### 现在（ExecutionToolProvider）

```java
// ExecutionToolProvider.java（215行）
@Component
public class ExecutionToolProvider implements McpToolProvider {

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        return List.of(
            new ToolDefinition(
                "execution_list_projects",
                "【第一步】列出所有项目...",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "keyword", strProp("项目名称模糊搜索"),
                        "page",    intProp("页码，默认1"),
                        "limit",   intProp("每页数量，默认20")
                    )
                )
            ),
            // ... 6个工具定义，手写 JSON Schema
        );
    }

    @Override
    public Object callTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "execution_list_projects" -> service.listProjectsWithBiz(
                    str(args, "keyword"), intVal(args, "page"), intVal(args, "limit"));
            // ... 6个 case 分支
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }
    // + 私有工具方法：str/intVal/longVal/strProp/intProp（~30行）
}
```

### 迁移后（@Tool 注解直接在 Service 上）

```java
// ExecutionService.java（不新增文件，直接在 Service 方法上加注解）
@Service
public class ExecutionService {

    @Tool(description = "【第一步】列出所有项目及其关联的业务库信息。" +
          "返回 projectId、projectName、dsKey、bizName。" +
          "这是执行用例查询的入口，后续步骤需要 projectId。")
    public Map<String, Object> execution_list_projects(
            @ToolParam(description = "项目名称模糊搜索关键字，不填返回全部") String keyword,
            @ToolParam(description = "页码，默认1") Integer page,
            @ToolParam(description = "每页数量，默认20，最大100") Integer limit) {
        return listProjectsWithBiz(keyword, page, limit);
    }

    @Tool(description = "【第二步】列出指定项目的所有基线...")
    public Map<String, Object> execution_list_baselines(
            @ToolParam(description = "项目ID（必填）", required = true) Long projectId,
            @ToolParam(description = "页码，默认1") Integer page,
            @ToolParam(description = "每页数量，默认20") Integer limit) {
        return listBaselines(projectId, page, limit);
    }
    // ...其余工具方法同理
}

// McpConfig.java（新增，约20行）
@Configuration
public class McpConfig {
    @Bean
    public ToolCallbackProvider executionTools(ExecutionService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }

    @Bean
    public ToolCallbackProvider testCaseTools(TestCaseService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }
}
```

**效果**：ExecutionToolProvider.java（215行）和 TestCaseToolProvider.java（185行）**整个文件删除**，
换来约 20 行 `McpConfig.java` + Service 方法上的注解。

---

## 4. 参数处理简化

### 现在（手动解析 Map<String, Object>）

```java
// ExecutionToolProvider 中每个工具都要写参数解析
private String str(Map<String, Object> args, String key) {
    Object v = args.get(key);
    return (v == null || v.toString().isBlank()) ? null : v.toString().trim();
}
private Integer intVal(Map<String, Object> args, String key) { ... }
private Long longVal(Map<String, Object> args, String key) { ... }
// 三个 Provider 共写了 ~3×30 = 90 行参数解析代码
```

### 迁移后（框架自动完成类型转换）

```java
// @Tool 方法参数由 Spring AI 自动从 JSON 转换，null 安全，类型安全
@Tool(description = "...")
public Map<String, Object> execution_list_projects(
        String keyword,     // 可以是 null
        Integer page,       // 自动 parseInt
        Integer limit) {    // 自动 parseInt
    // 直接使用，无需手动解析
}
```

所有参数解析代码（~90行）消失。

---

## 5. JSON Schema 生成

### 现在（手写 Map 结构）

```java
new ToolDefinition(
    "tool_name",
    "description",
    Map.of(
        "type", "object",
        "required", List.of("projectId"),
        "properties", Map.of(
            "projectId", Map.of("type", "integer", "description", "项目ID"),
            "keyword",   Map.of("type", "string",  "description", "关键字")
        )
    )
)
// 每个工具约 15-25 行 Map 构造代码
```

### 迁移后（从方法签名自动生成）

```java
@Tool(description = "...")
public Object myTool(
    @ToolParam(description = "项目ID", required = true) Long projectId,
    @ToolParam(description = "关键字") String keyword
) { ... }
// Spring AI 自动生成符合 JSON Schema 的 inputSchema
// 约 3-4 行
```

---

## 6. 配置文件变化

### 需要添加到 application.yml / application-h2.yml

```yaml
spring:
  ai:
    mcp:
      server:
        name: mcp-remote-server
        version: 1.0.0
        type: SYNC
        protocol: STREAMABLE            # 使用 Streamable HTTP（与当前行为一致）
        streamable-http:
          mcp-endpoint: /mcp            # 保持当前路径，客户端无感知
        capabilities:
          tool: true
          resource: false               # 暂不使用 resource
          prompt: false                 # 暂不使用 prompt
        tool-change-notification: true  # 支持动态工具变更通知（替代 DynamicToolProvider.refresh）
```

### 可以从 application.yml 删除

```yaml
# 以下自定义配置可删除（框架层消失后无人引用）
mcp:
  skill:
    output-dir: skills
```

---

## 7. 代码量变化汇总

| 类别 | 现在（行数） | 迁移后（行数） | 变化 |
|------|------------|--------------|------|
| framework/ 7文件 | 538 | 0 | **-538**（全删） |
| *ToolProvider 3文件 | 582 | ~20（McpConfig.java） | **-562** |
| Service 层（加注解） | - | +~60（注解行） | +60 |
| application.yml 配置 | 少量 | +15行 | +15 |
| **合计净减** | | | **约 -1,025 行** |

保留不变的：
- `meta/` 包（DynamicDsManager、MetaConfigRepo、QueryEngine、PerfGuard、SkillGenerator）
- `business/execution/ExecutionService.java`（逻辑保留，加注解）
- `business/testcase/TestCaseService.java`（逻辑保留，加注解）
- `web/MetaApiController.java`（REST Admin API，与 MCP 框架无关）
- 所有 SQL 脚本、静态资源、配置文件主体
