# MCP Remote Server 项目

本项目是一个远端 MCP Server，提供测试用例查询工具。

## 启动服务

使用前需要先启动 MCP 服务：
```bash
java -jar target/mcp-server-remote-1.0.0.jar --spring.profiles.active=h2
```

## Skills

项目包含 `remote-testcase` skill（位于 `skills/remote-testcase/SKILL.md`），
用于通过 curl 调用远端 MCP 的 JSON-RPC 接口查询测试用例、项目和版本信息。

当用户想要搜索用例、查看项目时，参考该 skill 中的调用方式执行 curl 命令。

## MCP 服务器连接

也可通过 `.claude/settings.json` 中配置的 mcpServers 直连远端 MCP：
- 服务名: `remote-testcase`
- 地址: `http://localhost:8080/mcp`
- 协议: Streamable HTTP
