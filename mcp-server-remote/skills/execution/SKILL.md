# Execution 执行用例查询 Skill

通过 curl 调用远端 MCP 服务（JSON-RPC 2.0）查询执行用例和基线数据。

## 连接信息

- **MCP 地址**: `http://localhost:8080/mcp`
- **协议**: Streamable HTTP（POST JSON-RPC 2.0）
- **管理界面**: `http://localhost:8080/admin/index.html`

## 引导式查询流程

> 每步结果包含 `_guide` 字段，提示下一步应调用的工具。

---

### Step 1: `execution_list_projects` — 查项目及业务库（入口）

列出所有项目及其关联的业务库。返回 `projectId`，后续步骤必须使用它。

```bash
# 查所有项目
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_list_projects\",\"arguments\":{}}}"

# 按名称过滤（Windows 需 Unicode 转义）
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_list_projects\",\"arguments\":{\"keyword\":\"\u7535\u5546\"}}}"
```

**参数：** `keyword`(可选) `page`(默认1) `limit`(默认20)

---

### Step 2: `execution_list_baselines` — 查项目基线列表

需要 Step 1 返回的 `projectId`。返回基线名称、用例数量和执行概况（通过率）。

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_list_baselines\",\"arguments\":{\"projectId\":1}}}"
```

**参数：** `projectId`(必填) `page` `limit`

---

### Step 3: `execution_query_baseline` — 查基线用例详情

查看基线中的具体用例，可按类型/优先级/关键字过滤。

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_query_baseline\",\"arguments\":{\"projectId\":1,\"baselineName\":\"v1.0\u57fa\u7ebf\",\"priority\":\"P0\"}}}"
```

**参数：** `projectId`(必填) `baselineName`(必填) `caseType` `priority` `keyword` `page` `limit`

---

### Step 4: `execution_query_results` — 查执行结果

查询某基线的实际执行记录，可按状态/轮次/关键字过滤。

```bash
# 查失败用例
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_query_results\",\"arguments\":{\"projectId\":1,\"baselineName\":\"v1.0\u57fa\u7ebf\",\"executeStatus\":\"fail\"}}}"

# 查第2轮执行
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_query_results\",\"arguments\":{\"projectId\":1,\"executeRound\":\"\u56de\u5f52\"}}}"
```

**参数：** `projectId`(必填) `baselineName` `executeRound` `executeStatus(pass/fail/skip/blocked)` `keyword` `page` `limit`

---

### Step 5: `execution_compare` — 基线 vs 执行对比

对比基线用例与执行结果，输出通过率、覆盖率和每条用例的执行状态。

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_compare\",\"arguments\":{\"projectId\":1,\"baselineName\":\"v1.0\u57fa\u7ebf\"}}}"
```

**参数：** `projectId`(必填) `baselineName`(必填) `executeRound`(可选)

**返回示例：**
```json
{
  "summary": {
    "total": 10, "pass": 7, "fail": 2, "notExecuted": 1,
    "passRate": "70.0%", "coverageRate": "90.0%"
  },
  "_guide": "发现 2 个失败用例。可使用 execution_query_results 按 executeStatus=fail 过滤查看详情"
}
```

---

### Step 6: `execution_stats` — 执行统计汇总

按类型/优先级统计基线，按轮次统计执行情况，输出 Bug 清单。

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_stats\",\"arguments\":{\"projectId\":1,\"baselineName\":\"v1.0\u57fa\u7ebf\"}}}"
```

**参数：** `projectId`(必填) `baselineName`(必填)

---

### Extra: `execution_list_versions` — 查版本关系（公共库）

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"execution_list_versions\",\"arguments\":{\"projectId\":1}}}"
```

---

## 管理功能

```bash
# 查看所有 MCP 工具列表
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# 刷新动态工具缓存（修改 DB 配置后调用）
curl -s -X POST http://localhost:8080/admin/api/refresh

# 生成 Skill 文件
curl -s -X POST http://localhost:8080/admin/api/skill/generate \
  -H "Content-Type: application/json" \
  -d '{"skillKey":"execution","skillTitle":"执行用例查询"}'

# 扫描数据源中的表
curl -s -X POST "http://localhost:8080/admin/api/tables/scan?dsId=2&dsKey=biz1"
```

## 注意事项

- Windows 终端中文参数需 Unicode 转义：`电商` → `\u7535\u5546`
- 每步结果的 `_guide` 字段包含下一步操作提示
- 性能保护：超大表查询会返回 `_perfWarning` 提示扫描行数
- 无业务库映射的项目调用业务接口会返回友好错误提示
- 动态工具无需重启即可新增：在 `/admin/index.html` 配置后调用 `/admin/api/refresh`
