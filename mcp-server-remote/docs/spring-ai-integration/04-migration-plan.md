# 分阶段迁移计划

## 1. 代码量对比（迁移前 vs 后）

### 可删除代码

| 文件 | 行数 | 说明 |
|------|------|------|
| `framework/McpController.java` | 183 | HTTP 端点，由 Spring AI 自动配置 |
| `framework/McpDispatcher.java` | 104 | JSON-RPC 路由，由 Spring AI 自动配置 |
| `framework/McpRegistry.java` | 155 | 工具注册中心，由 McpSyncServer 替代 |
| `framework/McpToolProvider.java` | 31 | 接口，由 ToolCallbackProvider 替代 |
| `framework/McpPromptProvider.java` | 24 | 接口，按需使用 Spring AI 接口 |
| `framework/McpResourceProvider.java` | 25 | 接口，按需使用 Spring AI 接口 |
| `framework/ToolDefinition.java` | 16 | record，由 McpSchema.Tool 替代 |
| `business/execution/ExecutionToolProvider.java` | 215 | 迁移为 Service 方法注解 |
| `business/testcase/TestCaseToolProvider.java` | 185 | 迁移为 Service 方法注解 |
| **合计** | **938 行** | |

### 新增代码

| 文件 | 行数 | 说明 |
|------|------|------|
| `config/McpConfig.java`（新建） | ~25 | 注册 ToolCallbackProvider |
| `meta/provider/DynamicToolRegistrar.java`（替换） | ~120 | 适配 McpSyncServer 的动态工具注册 |
| Service 层方法注解 | ~80 | `@Tool` + `@ToolParam` 注解行 |
| `application.yml` MCP 配置 | ~15 | spring.ai.mcp.server.* 配置 |
| **合计** | **~240 行** | |

### 净变化：**-938 + 240 ≈ -700 行**

---

## 2. 保持不变的核心设计

以下是本项目的核心价值，迁移后**完整保留**：

```
✓ meta/ 包 —— 动态配置驱动的完整设计思想
  ├── DynamicDsManager    多数据源运行时管理（HikariCP）
  ├── MetaConfigRepo      所有 meta 表的 JDBC CRUD
  ├── QueryEngine         SQL 模板引擎（#{param} + #{if}...#{/if}）
  ├── PerfGuard           性能预检（COUNT(*) 预检 + warn/block/sample）
  └── SkillGenerator      从 DB 配置生成 SKILL.md

✓ business/ 包 —— 业务服务层（Service 逻辑完整保留）
  ├── ExecutionService    6步引导式查询流程（多库路由）
  └── TestCaseService     测试用例搜索服务

✓ web/ 包 —— Admin REST API（与 MCP 协议无关）
  └── MetaApiController   数据源/表/视图/工具管理 REST API

✓ Admin UI（/admin/index.html）

✓ 所有 SQL 脚本（schema-*.sql / data-*.sql）
```

---

## 3. 分阶段执行计划

### Phase 1：框架层替换（约 1 天）

**目标**：删除 7 个 framework/ 文件，引入 Spring AI，服务正常启动。

```
步骤：
1. pom.xml 添加 spring-ai-bom 1.1.0 + spring-ai-starter-mcp-server-webmvc
2. application.yml 添加 spring.ai.mcp.server.* 配置
3. 创建 McpConfig.java（空 ToolCallbackProvider Bean 占位）
4. 删除 framework/ 目录 7 个文件
5. mvn compile 验证编译通过
6. 启动测试：curl POST /mcp initialize → 验证协议握手
```

**风险**：低。Spring AI 的端点路径默认是 `/mcp`，与当前一致。

---

### Phase 2：静态 ToolProvider 迁移（约 0.5 天）

**目标**：将 TestCaseToolProvider 和 ExecutionToolProvider 迁移为 @Tool 注解。

```
步骤：
1. TestCaseService 方法上加 @Tool + @ToolParam 注解
   （search_test_case、count_test_cases、list_projects、get_project_detail）
2. ExecutionService 方法上加 @Tool + @ToolParam 注解
   （6个工具方法）
3. McpConfig.java 中注册两个 ToolCallbackProvider
4. 删除 TestCaseToolProvider.java 和 ExecutionToolProvider.java
5. 功能测试：tools/list 返回 12 个工具（11个静态 + 1个动态占位）
```

**注意**：`@Tool` 方法名必须与原工具 key 一致，或用 `@Tool(name = "execution_list_projects")`
显式指定（因为 Java 方法名不能含有下划线作为开头，但方法名可以是 executionListProjects 配合 name 属性）。

```java
// 方法命名建议：Java 方法名用 camelCase，@Tool(name) 指定 MCP 工具名
@Tool(name = "execution_list_projects", description = "【第一步】...")
public Map<String, Object> listProjectsWithBiz(
        @ToolParam(description = "关键字") String keyword, ...) { ... }
```

---

### Phase 3：动态工具适配（约 1 天）

**目标**：将 DynamicToolProvider 适配为 DynamicToolRegistrar，接入 McpSyncServer API。

```
步骤：
1. 新建 DynamicToolRegistrar.java（见 03-dynamic-tools.md 中的代码）
2. 删除 DynamicToolProvider.java
3. MetaApiController 中将 dynamicToolProvider.refresh()
   改为 dynamicToolRegistrar.refresh()
4. 测试 POST /admin/api/refresh → 验证工具热更新 + notifyToolsListChanged
```

---

### Phase 4：验证与收尾（约 0.5 天）

```
步骤：
1. 全量功能测试（参考当前 7 个 Bug 修复的测试用例）
2. 验证 tools/list 返回所有工具（静态 11 个 + 动态 N 个）
3. 验证动态工具热更新（Admin UI 改配置 → 刷新 → Claude 立即感知）
4. 验证 Studio 连接（结合 analysis-studio-support.md 的 CORS 配置）
5. 清理：删除无用导入、更新 MEMORY.md
```

---

## 4. pom.xml 变更（具体操作）

```xml
<!-- 在 <properties> 中添加 -->
<spring-ai.version>1.1.0</spring-ai.version>

<!-- 在 <dependencyManagement> 中添加 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- 在 <dependencies> 中添加 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

注意：Spring AI 1.1 要求 Spring Boot 3.3+，当前项目是 3.4.3，**完全兼容**。

---

## 5. 已知风险与应对

| 风险 | 说明 | 应对 |
|------|------|------|
| `@Tool` 方法名与原工具 key 不一致 | Java 方法命名规范 vs MCP tool key | 用 `@Tool(name = "xxx")` 显式指定 |
| DynamicToolRegistrar 与 ToolCallbackProvider 工具名冲突 | 同名工具以第一个为准 | 动态工具 key 不与静态工具重名 |
| `McpSyncServer.addTool` 需要在 connection 初始化后调用 | 文档说明限制 | 用 `ApplicationReadyEvent`，服务完全启动后再调用 |
| spring-ai BOM 引入额外依赖 | 间接依赖体积增加 | 使用 `mvn dependency:analyze` 检查，排除不需要的传递依赖 |
| 端点路径冲突 | Spring AI 默认注册 `/mcp`，与当前相同 | 保持一致，无冲突 |

---

## 6. 总结

Spring AI 1.1 GA 的引入与当前项目的设计思想**高度兼容**：

1. **Meta-MCP 动态配置架构完整保留** —— DB 驱动、Web UI 维护、热更新，这些设计思想不变，
   只是底层注册目标从自定义 McpRegistry 改为官方 McpSyncServer。

2. **业务层代码几乎不变** —— ExecutionService / TestCaseService 只需加注解，
   所有多数据源路由、SQL 模板、性能保护逻辑均不动。

3. **代码量净减约 700 行** —— 删除的是协议层实现（JSON-RPC 路由、工具聚合等）这类
   "胶水代码"，保留的是真正的业务价值。

4. **额外获得** —— 工具列表变更实时通知、请求取消、进度通知、协议自动跟进升级。

**推荐在当前功能稳定后，优先执行 Phase 1（框架替换），验证可行性，再逐步推进 Phase 2-4。**
