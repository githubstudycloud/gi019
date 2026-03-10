---
name: remote-testcase
description: 远端测试用例 MCP 服务。搜索测试用例、查看项目列表和详情。当用户想要查询测试用例、搜索用例、查看项目信息时自动激活。
trigger: 当用户提到"用例"、"测试"、"项目"、"搜索用例"、"查找用例"时触发
---

# 远端测试用例 MCP

通过 Streamable HTTP 直连远端 MCP Server，查询测试用例和项目信息。

## 连接配置

在 claude_desktop_config.json 中添加:

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

## 可用工具

### search_test_case
搜索测试用例，支持模糊匹配。
- projectName: 项目名称（模糊，如"电商"）
- caseName: 用例名称（模糊，如"登录"）
- versionName: 版本名称（可选）
- uri: 对应URI（可选）

### list_projects
列出所有项目，可选关键词搜索。
- keyword: 搜索关键词（可选）

### get_project_detail
获取项目详情，包含版本列表和用例统计。
- projectName: 项目名称（模糊匹配）
