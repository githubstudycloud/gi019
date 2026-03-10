package com.mcp.remote.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Component
public class McpToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private final Map<String, Function<Map<String, Object>, Object>> handlers = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpToolRegistry() {
        registerBuiltinTools();
    }

    private void registerBuiltinTools() {
        // Tool 1: listTables
        register("listTables",
                "查询数据库中的表列表。返回指定数据库的所有表名。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "database", Map.of("type", "string", "description", "数据库名称")
                        ),
                        "required", List.of("database")),
                args -> {
                    String db = (String) args.getOrDefault("database", "default");
                    return Map.of(
                            "database", db,
                            "tables", List.of("users", "orders", "products", "categories"),
                            "timestamp", LocalDateTime.now().toString()
                    );
                });

        // Tool 2: executeQuery
        register("executeQuery",
                "执行 SQL 查询（只读）。返回查询结果集。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "database", Map.of("type", "string", "description", "数据库名称"),
                                "sql", Map.of("type", "string", "description", "SQL 查询语句（仅支持 SELECT）")
                        ),
                        "required", List.of("database", "sql")),
                args -> {
                    String sql = (String) args.getOrDefault("sql", "");
                    if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                        return Map.of("error", "仅支持 SELECT 查询", "sql", sql);
                    }
                    return Map.of(
                            "database", args.getOrDefault("database", "default"),
                            "sql", sql,
                            "columns", List.of("id", "name", "email"),
                            "rows", List.of(
                                    List.of(1, "Alice", "alice@example.com"),
                                    List.of(2, "Bob", "bob@example.com")
                            ),
                            "rowCount", 2,
                            "timestamp", LocalDateTime.now().toString()
                    );
                });

        // Tool 3: describeTable
        register("describeTable",
                "获取表结构信息。返回指定表的列定义。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "database", Map.of("type", "string", "description", "数据库名称"),
                                "table", Map.of("type", "string", "description", "表名")
                        ),
                        "required", List.of("database", "table")),
                args -> Map.of(
                        "database", args.getOrDefault("database", "default"),
                        "table", args.getOrDefault("table", "unknown"),
                        "columns", List.of(
                                Map.of("name", "id", "type", "BIGINT", "nullable", false, "key", "PRIMARY"),
                                Map.of("name", "name", "type", "VARCHAR(255)", "nullable", false, "key", ""),
                                Map.of("name", "email", "type", "VARCHAR(255)", "nullable", true, "key", "UNIQUE"),
                                Map.of("name", "created_at", "type", "TIMESTAMP", "nullable", false, "key", "")
                        ),
                        "timestamp", LocalDateTime.now().toString()
                ));

        // Tool 4: getSystemInfo
        register("getSystemInfo",
                "获取远端服务器的系统信息，包括 CPU、内存、JVM 等。",
                Map.of("type", "object", "properties", Map.of()),
                args -> {
                    Runtime runtime = Runtime.getRuntime();
                    return Map.of(
                            "os", System.getProperty("os.name"),
                            "arch", System.getProperty("os.arch"),
                            "javaVersion", System.getProperty("java.version"),
                            "availableProcessors", runtime.availableProcessors(),
                            "maxMemoryMB", runtime.maxMemory() / (1024 * 1024),
                            "totalMemoryMB", runtime.totalMemory() / (1024 * 1024),
                            "freeMemoryMB", runtime.freeMemory() / (1024 * 1024),
                            "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                            "timestamp", LocalDateTime.now().toString()
                    );
                });

        // Tool 5: healthCheck
        register("healthCheck",
                "检查远端服务的健康状态。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "serviceName", Map.of("type", "string", "description", "要检查的服务名称，留空检查全部")
                        )),
                args -> {
                    String svc = (String) args.getOrDefault("serviceName", "all");
                    return Map.of(
                            "status", "healthy",
                            "service", svc,
                            "checks", Map.of(
                                    "database", "connected",
                                    "cache", "available",
                                    "diskSpace", "sufficient"
                            ),
                            "timestamp", LocalDateTime.now().toString()
                    );
                });
    }

    public void register(String name, String description, Map<String, Object> inputSchema,
                          Function<Map<String, Object>, Object> handler) {
        tools.put(name, new ToolDefinition(name, description, inputSchema));
        handlers.put(name, handler);
    }

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            result.add(Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "inputSchema", tool.inputSchema()
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public Object callTool(String name, Map<String, Object> arguments) {
        Function<Map<String, Object>, Object> handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return handler.apply(arguments != null ? arguments : Map.of());
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}
}
