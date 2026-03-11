package com.mcp.server.meta.engine;

import com.mcp.server.meta.datasource.DynamicDsManager;
import com.mcp.server.meta.domain.QueryView;
import com.mcp.server.meta.repo.MetaConfigRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询引擎 —— 执行 QueryView 中定义的 SQL 模板。
 *
 * SQL 模板语法：
 *   - #{paramName}         → JDBC 参数占位（防注入）
 *   - #{if paramName} ... #{/if}  → 条件块（参数非空时拼入）
 *   - #{limit}  #{offset}  → 内置分页参数（自动注入）
 *
 * 功能：
 *   1. 模板渲染 → 生成 SQL + 参数列表
 *   2. 性能预检（PerfGuard）
 *   3. 分页查询（带总数）
 *   4. 非分页查询
 */
@Component
public class QueryEngine {

    private static final Logger log = LoggerFactory.getLogger(QueryEngine.class);

    // #{if paramName} ... #{/if}
    private static final Pattern IF_BLOCK = Pattern.compile(
            "#\\{if\\s+(\\w+)\\}([\\s\\S]*?)#\\{/if\\}", Pattern.MULTILINE);
    // #{paramName}
    private static final Pattern PARAM_REF = Pattern.compile("#\\{(\\w+)\\}");

    private final MetaConfigRepo repo;
    private final DynamicDsManager dsManager;
    private final PerfGuard perfGuard;

    public QueryEngine(MetaConfigRepo repo, DynamicDsManager dsManager, PerfGuard perfGuard) {
        this.repo = repo;
        this.dsManager = dsManager;
        this.perfGuard = perfGuard;
    }

    /**
     * 执行分页查询（带总数）。
     *
     * @param viewKey   查询视图 key
     * @param dsKey     数据源 key（覆盖视图中的 primaryDsKey）
     * @param userParams 用户传入的参数 Map
     * @param page      页码（从 1 开始）
     * @param limit     每页数量
     */
    public Map<String, Object> queryPaged(String viewKey, String dsKey,
                                          Map<String, Object> userParams,
                                          int page, int limit) {
        QueryView view = repo.findViewByKey(viewKey)
                .orElseThrow(() -> new IllegalArgumentException("查询视图不存在: " + viewKey));

        String resolvedDsKey = (dsKey != null && !dsKey.isBlank()) ? dsKey
                : view.getPrimaryDsKey();
        JdbcTemplate jdbc = dsManager.getTemplate(resolvedDsKey);

        // 安全限制
        int safeLimit = Math.min(limit < 1 ? 20 : limit, view.getMaxRows());
        int safeOffset = (page < 1 ? 0 : page - 1) * safeLimit;

        // 注入分页参数
        Map<String, Object> allParams = new LinkedHashMap<>(userParams);
        allParams.put("limit", safeLimit);
        allParams.put("offset", safeOffset);

        // 渲染 SQL
        RenderResult rendered = render(view.getSqlTemplate(), allParams);
        log.debug("[QueryEngine] SQL: {}", rendered.sql);

        // COUNT SQL
        String countSql = buildCountSql(view, rendered, allParams);
        RenderResult countRendered = render(countSql, allParams);

        // 性能预检
        Long dsId = repo.findAllDatasources(false).stream()
                .filter(d -> resolvedDsKey.equals(d.getDsKey()))
                .map(d -> d.getId())
                .findFirst().orElse(null);
        String mainTable = extractMainTable(rendered.sql);
        PerfGuard.CheckResult check = perfGuard.check(dsId, mainTable, countRendered.sql, jdbc, countRendered.params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();

        if (!check.allowed) {
            result.put("error", check.warningMsg);
            result.put("_blocked", true);
            return result;
        }

        // COUNT
        long total = 0L;
        try {
            Long cnt = jdbc.queryForObject(countRendered.sql, Long.class, countRendered.params.toArray());
            total = cnt != null ? cnt : 0L;
        } catch (Exception e) {
            log.warn("[QueryEngine] COUNT 失败: {}", e.getMessage());
        }

        // 数据查询
        List<Map<String, Object>> items = jdbc.queryForList(rendered.sql, rendered.params.toArray());

        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        result.put("items", items);

        if (check.hasWarning) {
            result.putAll(check.toWarningPayload());
        }

        return result;
    }

    /**
     * 执行不分页查询（返回所有行，受 view.maxRows 限制）。
     */
    public List<Map<String, Object>> queryList(String viewKey, String dsKey,
                                               Map<String, Object> userParams) {
        QueryView view = repo.findViewByKey(viewKey)
                .orElseThrow(() -> new IllegalArgumentException("查询视图不存在: " + viewKey));

        String resolvedDsKey = (dsKey != null && !dsKey.isBlank()) ? dsKey : view.getPrimaryDsKey();
        JdbcTemplate jdbc = dsManager.getTemplate(resolvedDsKey);

        Map<String, Object> allParams = new LinkedHashMap<>(userParams);
        allParams.put("limit", view.getMaxRows());
        allParams.put("offset", 0);

        RenderResult rendered = render(view.getSqlTemplate(), allParams);
        return jdbc.queryForList(rendered.sql, rendered.params.toArray());
    }

    /**
     * 执行任意参数化 SQL（业务代码直接调用）。
     * 仅允许 SELECT，防止误操作。
     */
    public List<Map<String, Object>> execute(String dsKey, String sql, Object... params) {
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            throw new IllegalArgumentException("仅支持 SELECT 查询");
        }
        return dsManager.getTemplate(dsKey).queryForList(sql, params);
    }

    /**
     * 执行带分页的任意 SQL（业务代码直接调用）。
     */
    public Map<String, Object> executePaged(String dsKey, String sql, String countSql,
                                            int page, int limit, Object... params) {
        JdbcTemplate jdbc = dsManager.getTemplate(dsKey);
        int safeLimit = limit < 1 ? 20 : limit;
        int offset = (page < 1 ? 0 : page - 1) * safeLimit;

        long total = 0L;
        try {
            Long cnt = jdbc.queryForObject(countSql, Long.class, params);
            total = cnt != null ? cnt : 0L;
        } catch (Exception e) {
            log.warn("[QueryEngine] COUNT 失败: {}", e.getMessage());
        }

        Object[] dataParams = appendPageParams(params, safeLimit, offset);
        List<Map<String, Object>> items = jdbc.queryForList(sql + " LIMIT ? OFFSET ?", dataParams);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", safeLimit);
        result.put("totalPages", (int) Math.ceil((double) total / safeLimit));
        result.put("items", items);
        return result;
    }

    // ----------------------------------------------------------------
    // SQL 模板渲染
    // ----------------------------------------------------------------

    /** 渲染结果：SQL 字符串 + 有序参数列表 */
    public static class RenderResult {
        public final String sql;
        public final List<Object> params;

        RenderResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /**
     * 渲染 SQL 模板：处理 #{if} 块 + #{param} 占位。
     *
     * 流程：
     * 1. 处理 #{if paramName}...#{/if}：参数非空则保留块内容（去掉标签），否则整块删除
     * 2. 用 ? 替换 #{paramName}，同时将参数值加入列表
     */
    public RenderResult render(String template, Map<String, Object> params) {
        // Step1: 处理条件块
        StringBuffer sb = new StringBuffer();
        Matcher ifMatcher = IF_BLOCK.matcher(template);
        while (ifMatcher.find()) {
            String paramName = ifMatcher.group(1);
            String blockContent = ifMatcher.group(2);
            Object val = params.get(paramName);
            if (val != null && !val.toString().isBlank()) {
                ifMatcher.appendReplacement(sb, Matcher.quoteReplacement(blockContent));
            } else {
                ifMatcher.appendReplacement(sb, "");
            }
        }
        ifMatcher.appendTail(sb);
        String afterIf = sb.toString();

        // Step2: 替换 #{param} → ?，收集参数
        List<Object> paramList = new ArrayList<>();
        StringBuffer sb2 = new StringBuffer();
        Matcher paramMatcher = PARAM_REF.matcher(afterIf);
        while (paramMatcher.find()) {
            String name = paramMatcher.group(1);
            Object val = params.get(name);
            paramMatcher.appendReplacement(sb2, "?");
            // 模糊搜索参数自动加 %（若值本身已含 % 则不重复）
            if (val instanceof String s && !s.contains("%") && name.endsWith("Fuzzy")) {
                paramList.add("%" + s + "%");
            } else {
                paramList.add(val != null ? val : "");
            }
        }
        paramMatcher.appendTail(sb2);

        return new RenderResult(sb2.toString().trim(), paramList);
    }

    // ----------------------------------------------------------------
    // 私有工具
    // ----------------------------------------------------------------

    private String buildCountSql(QueryView view, RenderResult rendered, Map<String, Object> allParams) {
        if (view.getCountSql() != null && !view.getCountSql().isBlank()) {
            return view.getCountSql();
        }
        // 自动包装：去掉 ORDER BY 和 LIMIT 后包装为 COUNT(*)
        String inner = rendered.sql.replaceAll("(?i)\\s+ORDER\\s+BY[^)]+", "")
                .replaceAll("(?i)\\s+LIMIT\\s+\\?\\s+OFFSET\\s+\\?", "");
        return "SELECT COUNT(*) FROM (" + inner + ") _cnt_wrap";
    }

    private String extractMainTable(String sql) {
        // 简单提取 FROM 后第一个表名（用于 PerfGuard 规则匹配）
        Matcher m = Pattern.compile("(?i)FROM\\s+(\\w+)").matcher(sql);
        return m.find() ? m.group(1) : "";
    }

    private Object[] appendPageParams(Object[] original, int limit, int offset) {
        Object[] result = Arrays.copyOf(original, original.length + 2);
        result[original.length] = limit;
        result[original.length + 1] = offset;
        return result;
    }
}
