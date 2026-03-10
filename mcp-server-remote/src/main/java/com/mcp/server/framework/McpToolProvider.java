package com.mcp.server.framework;

import java.util.List;
import java.util.Map;

/**
 * 业务工具提供者接口 —— 业务代码实现此接口即可注册进 MCP 框架。
 * 框架通过 Spring 自动扫描所有实现类，无需手动注册。
 */
public interface McpToolProvider {

    /**
     * 声明此 Provider 提供的所有工具定义
     */
    List<ToolDefinition> getToolDefinitions();

    /**
     * 调用指定工具
     * @param name 工具名称（必须是 getToolDefinitions 中声明的）
     * @param arguments 调用参数
     * @return 工具执行结果（会被序列化为 JSON 字符串放入 content[].text）
     */
    Object callTool(String name, Map<String, Object> arguments);

    /**
     * 是否支持指定工具
     */
    default boolean supportsTool(String name) {
        return getToolDefinitions().stream().anyMatch(d -> d.name().equals(name));
    }
}
