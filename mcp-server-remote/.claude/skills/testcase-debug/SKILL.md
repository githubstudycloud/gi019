# Remote Test Case MCP

连接远端 MCP Server 查询测试用例、项目和版本信息。

## 使用条件

当用户想要查询测试用例、搜索用例、查看项目列表、查看项目详情时使用此 skill。

## MCP 服务器地址

默认地址: `http://localhost:8080/mcp`

传输协议: Streamable HTTP (POST JSON-RPC 2.0)

## 调用方式

通过 `curl` 向远端 MCP 发送 JSON-RPC 请求。所有请求都是 POST 到 `/mcp` 端点。

### 列出所有项目

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_projects","arguments":{}}}'
```

可选参数 `keyword` 用于模糊搜索（中文参数必须使用 `\uXXXX` JSON 转义）:
```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_projects","arguments":{"keyword":"\u7535\u5546"}}}'
```

### 搜索测试用例

支持模糊匹配项目名和用例名，可选版本名和 URI 过滤:

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_test_case","arguments":{"projectName":"\u9879\u76ee\u540d","caseName":"\u7528\u4f8b\u540d"}}}'
```

参数说明:
- `projectName` - 项目名称，模糊匹配（如 `\u7535\u5546` 匹配"电商平台"）
- `caseName` - 用例名称，模糊匹配（如 `\u767b\u5f55` 匹配"用户登录-正常流程"）
- `versionName` - 版本名称，可选，模糊匹配（如"v2"匹配"v2.0.0"）
- `uri` - 对应URI，可选，模糊匹配（如"/api/v1"）

### 获取项目详情

返回项目信息、版本列表和用例统计:

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_project_detail","arguments":{"projectName":"\u9879\u76ee\u540d"}}}'
```

## 响应格式

响应是 JSON-RPC 2.0 格式，工具调用结果在 `result.content[0].text` 中，是一个 JSON 字符串:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "isError": false,
    "content": [{"type": "text", "text": "[{...}, {...}]"}]
  }
}
```

解析 `text` 字段中的 JSON 即可获得业务数据。

## 注意事项

- **中文参数必须使用 JSON `\uXXXX` Unicode 转义**，不能在 curl `-d` 中直接写中文字面量（Windows 环境下会导致 400 Bad Request）
- 中文转 Unicode 转义：每个汉字对应一个 `\uXXXX`，例如 `电商` → `\u7535\u5546`，`登录` → `\u767b\u5f55`
- URI 路径（如 `/api/v1`）和版本号（如 `v1.0`）均为 ASCII，可直接写入，无需转义
- 所有搜索都支持模糊匹配，不需要提供完整名称
- 版本名和 URI 是可选过滤条件，不提供则不过滤
