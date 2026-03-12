# Spring AI 1.1 版本概览与 MCP 支持

## 1. 版本现状（截至 2025-11）

| 版本 | 发布日期 | 状态 |
|------|----------|------|
| 1.0.0 GA | 2025-05-20 | 稳定 |
| 1.0.3 | 2025-10-01 | 稳定（最新补丁） |
| **1.1.0 GA** | **2025-11-12** | **当前最新稳定版 ✓** |
| 2.0.0-M1 | 开发中 | Spring Boot 4 / Framework 7 目标 |

**结论：现在引入 Spring AI 的最佳版本是 1.1.0 GA，已在 Maven Central 发布，无需 SNAPSHOT 仓库。**

参考：
- [Spring AI 1.1 GA Released](https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/)
- [Spring AI 1.0 GA Released](https://spring.io/blog/2025/05/20/spring-ai-1-0-GA-released/)
- [Maven Central: spring-ai-starter-mcp-server-webmvc](https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-starter-mcp-server-webmvc/1.0.3)

---

## 2. MCP 相关 Starter

Spring AI 提供三个 MCP Server Starter，对应不同传输层：

```
spring-ai-starter-mcp-server           # STDIO（命令行进程模式）
spring-ai-starter-mcp-server-webmvc    # HTTP/SSE（Spring MVC，当前项目栈）✓
spring-ai-starter-mcp-server-webflux   # Reactive HTTP/SSE（WebFlux 栈）
```

我们使用 **`spring-ai-starter-mcp-server-webmvc`**，与当前 Spring MVC 栈完全一致，无需迁移到 WebFlux。

### Maven 引入（使用 BOM 管理版本）

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
  </dependency>
</dependencies>
```

---

## 3. 传输协议：SSE vs Streamable HTTP

当前项目已实现 **Streamable HTTP**（POST /mcp 支持同步 JSON + 可选 SSE 流）。
Spring AI 1.1 WebMVC Starter 同样支持 Streamable HTTP 模式，只需一个配置项：

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE          # 开启 Streamable HTTP（对应当前 /mcp 端点行为）
        name: mcp-remote-server
        version: 1.0.0
        type: SYNC
        streamable-http:
          mcp-endpoint: /mcp          # 保持当前路径不变
```

不配置 `protocol: STREAMABLE` 时，默认使用 SSE 模式（`GET /sse` + `POST /mcp/messages`），
这是旧版协议，当前项目也支持。两种模式均可选。

---

## 4. Spring AI 1.1 MCP 核心改进（相较 1.0）

1.1.0 在 MCP 侧有 850+ 次改进，关键变更：

| 特性 | 1.0 GA | 1.1 GA |
|------|--------|--------|
| MCP Java SDK 版本 | v0.10.x | v0.13.1 |
| 协议版本 | 2024-11-05 | **2025-06-18** |
| `@McpTool` 注解扫描 | 不支持 | 支持（需配置开关） |
| AOT/GraalVM 原生镜像 | 有限支持 | 完整支持 |
| 动态工具通知 | 基础支持 | 稳定支持 |
| 请求取消 / 进度通知 | 无 | 有 |

对当前项目而言，最重要的是：**协议版本升到 2025-06-18（当前项目手写的是 2025-03-26）**，
引入官方 SDK 后协议版本随 spring-ai 升级自动更新，无需手动维护。
