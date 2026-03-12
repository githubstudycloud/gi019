# Spring AI 1.1 集成方案

> 本目录分析如何将当前 MCP Server 迁移到官方 Spring AI 1.1 GA，
> 结合现有设计思想最大化代码复用，减少自维护的框架代码量。

## 目录结构

| 文档 | 内容 |
|------|------|
| [01-version-overview.md](01-version-overview.md) | Spring AI 1.1 版本概览与 MCP 支持现状 |
| [02-migration-design.md](02-migration-design.md) | 迁移设计：现有架构 → Spring AI 架构映射 |
| [03-dynamic-tools.md](03-dynamic-tools.md) | 核心挑战：动态工具（DynamicToolProvider）的适配方案 |
| [04-migration-plan.md](04-migration-plan.md) | 分阶段迁移计划与代码量对比 |

## 结论速览

- **当前框架层**：7 文件 / 538 行（McpController、McpDispatcher、McpRegistry 等）可全部删除
- **3 个 ToolProvider**：共 582 行，迁移后减少约 70%（Provider 层变为纯业务代码）
- **动态工具（DynamicToolProvider）**：需要适配，使用 `McpSyncServer.addTool()` API 实现等效热更新
- **总体代码净减**：约 800+ 行，且获得协议自动升级、SSE/Streamable HTTP 双支持

---

*参考版本：Spring AI 1.1.0 GA（2025-11-12 发布）*
