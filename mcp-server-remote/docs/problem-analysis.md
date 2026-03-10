# 问题分析与重构记录

> 记录本项目发现的所有问题、根因和解决方案。

---

## 问题一：MCP 和 Skill 都在跑 curl，MCP 直连没有生效

### 现象

用户反映：无论是 MCP 直连还是 Skill，最终 Claude 都在执行 curl 命令。
按理说 MCP 直连（`.claude/settings.json` 的 `url` 配置）应该由 Claude Code 框架原生处理，不经过 curl。

### 根因定位

**根因：Java 编译时编码错误，导致 MCP 工具描述全部乱码。**

定位过程：
```bash
# 调用 tools/list 查看工具描述
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

返回的 `description` 字段包含大量无效 Unicode 字符：
```
"\u93bc\u6ec5\u50a8\u5a34\udc80\u5b2d..."
           ↑
    \udc80 = 孤立代理字符（U+D800~U+DFFF），是无效 Unicode
```

通过 `javap` 反编译已编译的 class 文件确认：
```
#18 = Utf8  ��������������֧��ͨ...   ← 常量池中已经乱码
```

**根本原因**：`pom.xml` 缺少 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`。
在 Windows 上 Maven/javac 默认使用 GBK 编码读取 UTF-8 源文件，导致所有中文字面量在 class 常量池中被错误编码。

**影响链路**：
```
pom.xml 缺少 UTF-8 编码声明
    └→ javac 用 GBK 编译 UTF-8 源文件
    └→ class 文件中字符串常量乱码（含孤立代理字符）
    └→ tools/list 返回乱码描述 + 无效 Unicode
    └→ Claude Code 的 JSON 解析器遇到无效字符，无法正确理解工具
    └→ Claude 无法识别工具用途，放弃调用原生 MCP
    └→ Claude 转而读取 SKILL.md，走 curl 路径
    └→ 表现：MCP 和 Skill 都在跑 curl
```

注意：数据库查询结果（中文）正常，因为 JDBC 有独立的字符集配置，不受 javac 编码影响。

### 解决方案

`pom.xml` 中添加：
```xml
<properties>
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>  <!-- 新增 -->
</properties>
```

然后重新编译：
```bash
mvn clean package -DskipTests
```

---

## 问题二：百万级数据无法有效查询

### 现象

`searchTestCase` 方法硬编码了 `LIMIT 50`，`listProjects` 无任何分页。
实际业务有几百万条测试用例，现有实现存在以下问题：

| 问题 | 影响 |
|------|------|
| `LIMIT 50` 硬编码 | 无法自定义返回条数 |
| 无分页支持 | 无法获取第 2 页以后的数据 |
| 无总数返回 | Claude 不知道匹配了多少条，无法引导用户 |
| 无 COUNT 工具 | "这个项目有多少登录相关用例？"这类问题需要加载数据才能回答 |
| 无上限保护 | 如果用户不填过滤条件，可能触发全表扫描，返回海量数据撑满 context |

### 解决方案

1. **分页参数**：`search_test_case` 和 `list_projects` 工具增加 `page`（页码，默认 1）和 `limit`（每页数量，默认 20，最大 100）参数
2. **总数返回**：每次搜索同时返回 `total`（匹配总数）和 `totalPages`
3. **新增 `count_test_cases` 工具**：只返回计数，不加载数据，适合统计类问题
4. **可配置上限**：`application.yml` 中新增 `mcp.search.default-page-size` 和 `mcp.search.max-page-size`

---

## 问题三：`/mcp` 显示为"项目级 MCP"

### 现象

在 Claude Code 会话中输入 `/mcp`，看到 `remote-testcase` 被标记为"项目级（project）"。

### 说明

这是**正确的**，不是问题。Claude Code 的 MCP 配置有三个作用域：

| 作用域 | 配置文件位置 | 显示标签 | 共享范围 |
|--------|------------|---------|---------|
| 项目级 | `.claude/settings.json` | project | 本项目目录内的会话 |
| 用户级 | `~/.claude.json` | user | 所有项目 |
| 企业级 | 管理员配置 | enterprise | 整个组织 |

本项目配置在 `.claude/settings.json`，因此显示"项目级"。
**如果想让所有项目都能用这个 MCP**，执行：
```bash
claude mcp add --scope user --transport http remote-testcase http://localhost:8080/mcp
```

---

## 问题四：MCP 和 Skill 同名造成概念混淆

### 现象

- MCP 服务器名：`remote-testcase`（`.claude/settings.json`）
- Skill 名：`remote-testcase`（`.claude/skills/remote-testcase/`）

用户说"用 remote-testcase 查一下"时，Claude 无法判断指的是哪条路径。

### 解决方案

Skill 目录改名为 `testcase-debug`，体现其"调试/备用"的真实职责：

```bash
mv .claude/skills/remote-testcase .claude/skills/testcase-debug
mv skills/remote-testcase skills/testcase-debug
```

---

## 修复清单

| # | 问题 | 修复文件 | 状态 |
|---|------|---------|------|
| 1 | 编译编码错误导致 MCP 不可用 | `pom.xml` | ✅ 已修复 |
| 2 | 无分页，百万级数据无法查询 | `TestCaseService.java` | ✅ 已修复 |
| 3 | 工具无分页参数 | `TestCaseToolProvider.java` | ✅ 已修复 |
| 4 | 无 count 工具 | `TestCaseToolProvider.java` | ✅ 已修复 |
| 5 | 无可配置查询上限 | `application.yml` | ✅ 已修复 |
| 6 | Skill 同名混淆 | 目录重命名 | ✅ 已修复 |
