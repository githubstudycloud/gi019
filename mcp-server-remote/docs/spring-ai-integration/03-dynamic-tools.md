# 核心挑战：动态工具（DynamicToolProvider）的适配方案

动态工具是本项目最有特色的设计——工具定义存在数据库里，Web UI 修改后无需重启立即生效。
这是官方 Spring AI 默认注解模式不能直接覆盖的场景，需要专门设计适配层。

## 1. 现有 DynamicToolProvider 的工作方式

```
数据库 mcp_tool_config
    ↓  (每5分钟自动刷新 or 手动 POST /admin/api/refresh)
DynamicToolProvider.getToolDefinitions()
    ↓  构建 List<ToolDefinition>（工具名/描述/参数/关联QueryView）
McpRegistry.listTools()
    ↓  输出 tools/list 响应

DynamicToolProvider.callTool(name, args)
    ↓
QueryEngine.execute(viewKey, args)
    ↓  SQL 模板渲染 + JDBC 执行
结果返回
```

主要挑战：`@Tool` 注解是编译时静态的，无法动态添加/删除运行时工具。

---

## 2. Spring AI 的动态工具 API

Spring AI 1.0 引入了 `McpSyncServer` 的运行时 API（1.1 GA 已稳定）：

```java
// 注入自动配置的 McpSyncServer
@Autowired
private McpSyncServer mcpSyncServer;

// 动态添加工具
mcpSyncServer.addTool(toolSpecification);

// 动态删除工具
mcpSyncServer.removeTool("tool_name");

// 通知所有已连接的 MCP 客户端（如 Claude）工具列表已变更
mcpSyncServer.notifyToolsListChanged();
```

`SyncToolSpecification` 由 `McpToolUtils.toSyncToolSpecifications(toolCallbacks)` 从
`ToolCallback` 列表转换而来。

参考：[Spring AI Dynamic Tool Updates](https://spring.io/blog/2025/05/04/spring-ai-dynamic-tool-updates-with-mcp/)

---

## 3. 适配方案：保留 Meta-MCP 设计思想

迁移后的 `DynamicToolProvider` 依然从数据库加载工具，只是把"注册到 McpRegistry"改为
"注册到 McpSyncServer"：

```java
/**
 * 适配 Spring AI 的动态工具 Provider。
 * 将 DB 中的 mcp_tool_config 转换为 Spring AI ToolCallback，
 * 通过 McpSyncServer.addTool/removeTool 实现无重启热更新。
 */
@Component
public class DynamicToolRegistrar {

    private final MetaConfigRepo repo;
    private final QueryEngine queryEngine;
    private final McpSyncServer mcpSyncServer;

    // 跟踪当前已注册的动态工具名，用于 diff 更新
    private final Set<String> registeredTools = Collections.synchronizedSet(new HashSet<>());

    public DynamicToolRegistrar(MetaConfigRepo repo, QueryEngine queryEngine,
                                McpSyncServer mcpSyncServer) {
        this.repo = repo;
        this.queryEngine = queryEngine;
        this.mcpSyncServer = mcpSyncServer;
    }

    /** 启动时初始化动态工具 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    /**
     * 热重载：对比 DB 最新工具列表与当前注册状态，
     * 增量 addTool / removeTool，最后发通知。
     * （对应当前 DynamicToolProvider.refresh() + McpRegistry 重注册）
     */
    public synchronized void refresh() {
        List<ToolConfig> dbTools = repo.findAllTools(true);
        Set<String> dbToolKeys = dbTools.stream()
                .map(ToolConfig::getToolKey).collect(Collectors.toSet());

        // 1. 删除已下线的工具
        Set<String> toRemove = new HashSet<>(registeredTools);
        toRemove.removeAll(dbToolKeys);
        toRemove.forEach(key -> {
            mcpSyncServer.removeTool(key);
            registeredTools.remove(key);
            log.info("[DynamicToolRegistrar] 工具已下线: {}", key);
        });

        // 2. 新增或更新工具
        for (ToolConfig tc : dbTools) {
            if (registeredTools.contains(tc.getToolKey())) {
                // 更新：先删再加（McpSyncServer 无 updateTool API）
                mcpSyncServer.removeTool(tc.getToolKey());
            }
            SyncToolSpecification spec = buildToolSpec(tc);
            mcpSyncServer.addTool(spec);
            registeredTools.add(tc.getToolKey());
            log.debug("[DynamicToolRegistrar] 工具已注册: {}", tc.getToolKey());
        }

        // 3. 通知所有已连接的 Claude 客户端
        if (!toRemove.isEmpty() || !dbTools.isEmpty()) {
            mcpSyncServer.notifyToolsListChanged();
        }
        log.info("[DynamicToolRegistrar] 动态工具刷新完成，当前 {} 个", registeredTools.size());
    }

    /** 将 ToolConfig + QueryEngine 封装为 Spring AI SyncToolSpecification */
    private SyncToolSpecification buildToolSpec(ToolConfig tc) {
        // 构建 JSON Schema（复用现有 buildInputSchema 逻辑）
        McpSchema.JsonSchema inputSchema = buildJsonSchema(tc);

        // 工具处理器：接收参数 Map，调用 QueryEngine，返回结果
        SyncToolSpecification.Handler handler = params -> {
            try {
                Map<String, Object> args = params.arguments();
                Object result = queryEngine.execute(tc.getQueryViewId(), args);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(toJson(result))),
                        false);
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("执行失败: " + e.getMessage())),
                        true);
            }
        };

        return SyncToolSpecification.builder()
                .tool(new McpSchema.Tool(tc.getToolKey(), tc.getDescription(), inputSchema))
                .handler(handler)
                .build();
    }
}
```

### Admin API 适配（MetaApiController）

```java
// 原来：POST /admin/api/refresh → dynamicToolProvider.refresh()
// 迁移后：
@PostMapping("/refresh")
public Map<String, Object> refresh() {
    dynamicToolRegistrar.refresh();   // 改为注入 DynamicToolRegistrar
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("message", "动态工具已刷新并通知所有客户端");
    result.put("dynamicToolCount", repo.findAllTools(true).size());
    return result;
}
```

---

## 4. 增量更新 vs 全量替换

| 策略 | 实现复杂度 | 对客户端影响 |
|------|-----------|------------|
| **全量替换**（先删全部，再加全部） | 简单 | 短暂工具列表为空 |
| **增量更新**（diff 后增删）| 中等（需维护 registeredTools 集合） | 最小化影响 ✓ |
| Spring AI 官方推荐 | 增量 | - |

推荐增量更新（如上方代码），避免每次刷新时工具列表短暂为空导致客户端报错。

---

## 5. 对比：迁移前后的 DynamicToolProvider 角色变化

### 迁移前

```
DynamicToolProvider（实现 McpToolProvider 接口）
├── getToolDefinitions() → 输出给 McpRegistry
├── callTool()           → 走 QueryEngine
└── refresh()            → 重置缓存，McpRegistry 下次 listTools 时重新加载
```

### 迁移后

```
DynamicToolRegistrar（不再实现任何自定义接口）
├── onStartup()          → ApplicationReadyEvent 时首次注册
├── refresh()            → 增量 addTool/removeTool + notifyToolsListChanged
└── buildToolSpec()      → 构建 SyncToolSpecification（含 QueryEngine 调用）
```

核心逻辑（从 DB 读工具配置 + QueryEngine 执行 SQL）**完全保留**，
只是注册目标从 `McpRegistry` 改为 `McpSyncServer`。

---

## 6. 额外收益：实时通知客户端

现有方案：客户端（Claude）需要重新发起 `tools/list` 请求才能感知工具变化。

迁移后：`mcpSyncServer.notifyToolsListChanged()` 会通过 SSE 连接主动推送通知，
Claude 立即感知到工具列表已更新，无需手动刷新对话。这是现有自定义框架没有的能力。
