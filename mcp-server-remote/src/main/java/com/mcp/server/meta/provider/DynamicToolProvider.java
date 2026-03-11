package com.mcp.server.meta.provider;

import com.mcp.server.framework.McpToolProvider;
import com.mcp.server.framework.ToolDefinition;
import com.mcp.server.meta.domain.ParamConfig;
import com.mcp.server.meta.domain.ToolConfig;
import com.mcp.server.meta.engine.QueryEngine;
import com.mcp.server.meta.repo.MetaConfigRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态工具 Provider —— 从 mcp_tool_config 数据库动态加载 MCP 工具。
 *
 * 特性：
 * 1. 工具定义从 DB 读取，增删改工具无需重启
 * 2. 调用时自动走 QueryEngine 执行关联的 QueryView SQL 模板
 * 3. 支持热重载（调用 refresh() 后立即生效）
 * 4. 工具名/描述/参数全部可在 Web UI 维护
 */
@Component
public class DynamicToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolProvider.class);

    // 缓存超时：5 分钟后自动刷新（防止频繁 DB 查询）
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final MetaConfigRepo repo;
    private final QueryEngine queryEngine;

    private volatile List<ToolConfig> cachedTools = Collections.emptyList();
    private final AtomicLong lastLoadTime = new AtomicLong(0L);

    public DynamicToolProvider(MetaConfigRepo repo, QueryEngine queryEngine) {
        this.repo = repo;
        this.queryEngine = queryEngine;
    }

    /** 手动触发重载（Web UI 保存工具配置后调用） */
    public void refresh() {
        lastLoadTime.set(0L);
        getOrLoadTools();
        log.info("[DynamicToolProvider] 工具配置已重新加载，当前 {} 个动态工具",
                cachedTools.size());
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolConfig> tools = getOrLoadTools();
        List<ToolDefinition> defs = new ArrayList<>();
        for (ToolConfig t : tools) {
            defs.add(new ToolDefinition(
                    t.getToolKey(),
                    t.getDescription(),
                    buildInputSchema(t)
            ));
        }
        return defs;
    }

    @Override
    public boolean supportsTool(String name) {
        return getOrLoadTools().stream().anyMatch(t -> t.getToolKey().equals(name));
    }

    @Override
    public Object callTool(String name, Map<String, Object> args) {
        ToolConfig tool = getOrLoadTools().stream()
                .filter(t -> t.getToolKey().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dynamic tool: " + name));

        if (tool.getQueryViewId() == null) {
            return Map.of("error", "工具 [" + name + "] 未关联查询视图，请在管理界面配置 QueryView");
        }

        // 从视图中获取 dsKey
        String dsKey = repo.findViewById(tool.getQueryViewId())
                .map(v -> v.getPrimaryDsKey())
                .orElse(null);

        // 提取分页参数
        int page  = toInt(args.get("page"),  1);
        int limit = toInt(args.get("limit"), 20);

        // 执行分页查询
        return queryEngine.queryPaged(
                repo.findViewById(tool.getQueryViewId())
                        .map(v -> v.getViewKey())
                        .orElseThrow(() -> new IllegalArgumentException("视图不存在: " + tool.getQueryViewId())),
                dsKey,
                args,
                page,
                limit
        );
    }

    // ----------------------------------------------------------------
    // 私有
    // ----------------------------------------------------------------

    private List<ToolConfig> getOrLoadTools() {
        long now = System.currentTimeMillis();
        if (now - lastLoadTime.get() > CACHE_TTL_MS) {
            try {
                cachedTools = repo.findAllTools(true);
                lastLoadTime.set(now);
                log.debug("[DynamicToolProvider] 从 DB 加载了 {} 个动态工具", cachedTools.size());
            } catch (Exception e) {
                log.warn("[DynamicToolProvider] 加载工具配置失败（使用缓存）: {}", e.getMessage());
            }
        }
        return cachedTools;
    }

    /**
     * 根据 ParamConfig 列表构建 JSON Schema inputSchema。
     * 支持 string / integer / boolean / enum 类型。
     */
    private Map<String, Object> buildInputSchema(ToolConfig tool) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        if (tool.getParams() == null || tool.getParams().isEmpty()) {
            schema.put("properties", Map.of());
            return schema;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParamConfig p : tool.getParams()) {
            Map<String, Object> propSchema = new LinkedHashMap<>();

            // 类型映射
            if ("enum".equals(p.getParamType()) && p.getEnumValues() != null) {
                propSchema.put("type", "string");
                // 简单解析 JSON 数组：["a","b"] → [a, b]
                propSchema.put("enum", parseEnumValues(p.getEnumValues()));
            } else if ("integer".equals(p.getParamType())) {
                propSchema.put("type", "integer");
            } else if ("boolean".equals(p.getParamType())) {
                propSchema.put("type", "boolean");
            } else {
                propSchema.put("type", "string");
            }

            // 描述：优先用 description，其次用 label
            String desc = p.getDescription() != null ? p.getDescription()
                    : (p.getParamLabel() != null ? p.getParamLabel() : "");
            propSchema.put("description", desc);

            if (p.getDefaultValue() != null) {
                propSchema.put("default", p.getDefaultValue());
            }

            properties.put(p.getParamName(), propSchema);
            if (p.isRequired()) required.add(p.getParamName());
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private List<String> parseEnumValues(String json) {
        // 极简解析：去掉 [ " ] 空格，按逗号分割
        return Arrays.asList(
                json.replaceAll("[\\[\\]\"\\s]", "").split(","));
    }

    private int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }
}
