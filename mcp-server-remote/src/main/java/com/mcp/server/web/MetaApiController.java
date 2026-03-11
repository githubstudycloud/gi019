package com.mcp.server.web;

import com.mcp.server.meta.datasource.DynamicDsManager;
import com.mcp.server.meta.domain.*;
import com.mcp.server.meta.provider.DynamicToolProvider;
import com.mcp.server.meta.repo.MetaConfigRepo;
import com.mcp.server.meta.skill.SkillGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Meta-Config 管理 REST API —— 供 Web UI 调用。
 * 所有操作实时生效，无需重启。
 *
 * 路由规则：
 *   GET/POST /admin/api/datasources   数据源管理
 *   GET/POST /admin/api/tables        表注册/统计
 *   GET/POST /admin/api/views         查询视图管理
 *   GET/POST /admin/api/tools         工具配置管理
 *   POST     /admin/api/refresh       刷新缓存
 *   GET      /admin/api/stats         概览统计
 *   POST     /admin/api/skill/generate  生成 Skill
 */
@RestController
@RequestMapping("/admin/api")
public class MetaApiController {

    private static final Logger log = LoggerFactory.getLogger(MetaApiController.class);

    private final MetaConfigRepo repo;
    private final DynamicDsManager dsManager;
    private final DynamicToolProvider dynamicToolProvider;
    private final SkillGenerator skillGenerator;
    private final JdbcTemplate primaryJdbc;

    public MetaApiController(MetaConfigRepo repo, DynamicDsManager dsManager,
                             DynamicToolProvider dynamicToolProvider,
                             SkillGenerator skillGenerator,
                             JdbcTemplate primaryJdbc) {
        this.repo = repo;
        this.dsManager = dsManager;
        this.dynamicToolProvider = dynamicToolProvider;
        this.skillGenerator = skillGenerator;
        this.primaryJdbc = primaryJdbc;
    }

    // ================================================================
    // 概览
    // ================================================================

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = repo.getStats();
        stats.put("loadedDatasources", dsManager.listKeys());
        return stats;
    }

    // ================================================================
    // 数据源管理
    // ================================================================

    @GetMapping("/datasources")
    public List<DsConfig> listDatasources(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return repo.findAllDatasources(enabledOnly);
    }

    @PostMapping("/datasources")
    public ResponseEntity<Map<String, Object>> saveDatasource(@RequestBody DsConfig ds) {
        try {
            repo.saveDatasource(ds);
            // 保存后立即刷新连接池
            dsManager.refresh(ds.getDsKey());
            return ok("数据源 [" + ds.getDsKey() + "] 保存并刷新成功");
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @PostMapping("/datasources/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleDatasource(
            @PathVariable Long id, @RequestParam boolean enabled) {
        repo.toggleDatasource(id, enabled);
        return ok("已" + (enabled ? "启用" : "禁用") + " 数据源 #" + id);
    }

    @GetMapping("/datasources/{dsKey}/health")
    public Map<String, Object> dsHealth(@PathVariable String dsKey) {
        return dsManager.getHealth(dsKey);
    }

    // ================================================================
    // 表元数据管理
    // ================================================================

    @GetMapping("/tables")
    public List<TableMeta> listTables(@RequestParam Long dsId,
                                      @RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly ? repo.findActiveTables(dsId) : repo.findTablesByDsId(dsId);
    }

    /**
     * 扫描数据库中的实际表列表（万张表场景下只扫描未排除的）。
     * 对于已注册的表更新统计，对于新表自动注册（不排除）。
     */
    @PostMapping("/tables/scan")
    public ResponseEntity<Map<String, Object>> scanTables(@RequestParam Long dsId,
                                                           @RequestParam String dsKey) {
        try {
            JdbcTemplate jdbc = dsManager.getTemplate(dsKey);

            // 获取实际表列表（不同 DB 方言）
            List<String> tableNames = getTableNames(jdbc);

            int newCount = 0, updatedCount = 0;
            for (String tableName : tableNames) {
                TableMeta meta = new TableMeta();
                meta.setDsId(dsId);
                meta.setTableName(tableName);
                try {
                    Long rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
                    meta.setRowEstimate(rowCount);
                    meta.setLargeTable(rowCount != null && rowCount > 100000);
                } catch (Exception e) {
                    meta.setRowEstimate(null);
                }
                repo.upsertTableMeta(meta);
                updatedCount++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scanned", tableNames.size());
            result.put("updated", updatedCount);
            result.put("message", "扫描完成，共发现 " + tableNames.size() + " 张表");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return err("扫描失败: " + e.getMessage());
        }
    }

    @PostMapping("/tables/{id}/exclude")
    public ResponseEntity<Map<String, Object>> setExcluded(
            @PathVariable Long id, @RequestParam boolean excluded) {
        repo.setTableExcluded(id, excluded);
        return ok("已" + (excluded ? "排除" : "恢复") + "表 #" + id);
    }

    // ================================================================
    // 查询视图管理
    // ================================================================

    @GetMapping("/views")
    public List<QueryView> listViews(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return repo.findAllViews(enabledOnly);
    }

    @PostMapping("/views")
    public ResponseEntity<Map<String, Object>> saveView(@RequestBody QueryView view) {
        try {
            repo.saveView(view);
            return ok("查询视图 [" + view.getViewKey() + "] 保存成功");
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    /** 测试执行 SQL 模板（预览前 10 行） */
    @PostMapping("/views/test")
    public ResponseEntity<Map<String, Object>> testView(@RequestBody Map<String, Object> body) {
        String dsKey = (String) body.get("dsKey");
        String sql   = (String) body.get("sql");
        if (sql == null || sql.isBlank()) return err("SQL 不能为空");
        if (!sql.trim().toUpperCase().startsWith("SELECT")) return err("仅支持 SELECT 语句");
        try {
            JdbcTemplate jdbc = dsManager.getTemplate(dsKey);
            List<Map<String, Object>> rows = jdbc.queryForList(sql + " LIMIT 10");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows);
            result.put("count", rows.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return err("执行失败: " + e.getMessage());
        }
    }

    // ================================================================
    // 工具配置管理
    // ================================================================

    @GetMapping("/tools")
    public List<ToolConfig> listTools(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return repo.findAllTools(enabledOnly);
    }

    @PostMapping("/tools")
    public ResponseEntity<Map<String, Object>> saveTool(@RequestBody ToolConfig tool) {
        try {
            repo.saveTool(tool);
            // 保存参数
            if (tool.getParams() != null) {
                for (ParamConfig param : tool.getParams()) {
                    param.setToolId(tool.getId());
                    repo.saveParam(param);
                }
            }
            // 刷新动态工具缓存
            dynamicToolProvider.refresh();
            return ok("工具 [" + tool.getToolKey() + "] 保存成功，动态工具已刷新");
        } catch (Exception e) {
            return err(e.getMessage());
        }
    }

    @DeleteMapping("/tools/{id}")
    public ResponseEntity<Map<String, Object>> deleteTool(@PathVariable Long id) {
        repo.deleteTool(id);
        dynamicToolProvider.refresh();
        return ok("工具 #" + id + " 已删除");
    }

    // ================================================================
    // 刷新 & Skill 生成
    // ================================================================

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        dynamicToolProvider.refresh();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "动态工具缓存已刷新");
        result.put("toolCount", repo.findAllTools(true).size());
        return result;
    }

    @PostMapping("/skill/generate")
    public ResponseEntity<Map<String, Object>> generateSkill(@RequestBody Map<String, String> body) {
        String skillKey   = body.getOrDefault("skillKey", "dynamic");
        String skillTitle = body.getOrDefault("skillTitle", "Dynamic MCP Tools");
        try {
            skillGenerator.generateFromDb(skillKey, skillTitle);
            return ok("SKILL.md 生成成功: skills/" + skillKey + "/SKILL.md");
        } catch (Exception e) {
            return err("生成失败: " + e.getMessage());
        }
    }

    // ================================================================
    // 私有工具
    // ================================================================

    private List<String> getTableNames(JdbcTemplate jdbc) {
        try {
            // H2 / MySQL 兼容写法
            return jdbc.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_TYPE='TABLE' OR TABLE_TYPE='BASE TABLE' " +
                "ORDER BY TABLE_NAME",
                String.class);
        } catch (Exception e) {
            // Fallback：直接查 SHOW TABLES（MySQL）
            try {
                return jdbc.queryForList("SHOW TABLES", String.class);
            } catch (Exception e2) {
                log.warn("[MetaApi] 获取表列表失败: {}", e2.getMessage());
                return Collections.emptyList();
            }
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String msg) {
        return ResponseEntity.ok(Map.of("success", true, "message", msg));
    }

    private ResponseEntity<Map<String, Object>> err(String msg) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", msg));
    }
}
