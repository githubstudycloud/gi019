package com.mcp.server.meta.engine;

import com.mcp.server.meta.domain.PerfRule;
import com.mcp.server.meta.repo.MetaConfigRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 性能保护守卫 —— 查询执行前的安全预检。
 *
 * 防止以下情况把数据库打挂：
 * - 全表扫描超大表（行数预检）
 * - 无 WHERE 条件的查询
 * - 超大结果集返回
 *
 * 策略：warn = 警告但继续 / block = 拒绝执行 / sample = 自动降限
 */
@Component
public class PerfGuard {

    private static final Logger log = LoggerFactory.getLogger(PerfGuard.class);

    private final MetaConfigRepo repo;

    public PerfGuard(MetaConfigRepo repo) {
        this.repo = repo;
    }

    /**
     * 查询执行前预检。
     * @param dsId      数据源 ID（用于匹配规则）
     * @param tableName 主表名（用于匹配 table_pattern）
     * @param countSql  COUNT SQL（用于估算扫描行数）
     * @param jdbc      目标数据源的 JdbcTemplate
     * @param params    COUNT SQL 参数
     * @return 预检结果：{allowed, warning, estimatedRows, maxResultRows}
     */
    public CheckResult check(Long dsId, String tableName, String countSql, JdbcTemplate jdbc, Object[] params) {
        List<PerfRule> rules = repo.findPerfRules(dsId);

        long estimatedRows = 0L;
        try {
            estimatedRows = Boolean.TRUE.equals(jdbc.queryForObject(countSql, Long.class, params))
                ? 0L : safeCount(jdbc, countSql, params);
        } catch (Exception e) {
            log.warn("[PerfGuard] COUNT 预检失败: {}", e.getMessage());
        }

        for (PerfRule rule : rules) {
            if (!rule.matchesTable(tableName)) continue;

            if (estimatedRows > rule.getMaxScanRows()) {
                String msg = String.format("预检警告：表 [%s] 估算扫描行数 %d 超过规则限制 %d",
                        tableName, estimatedRows, rule.getMaxScanRows());
                log.warn("[PerfGuard] {}", msg);

                return switch (rule.getAction()) {
                    case "block" -> CheckResult.blocked(msg, estimatedRows);
                    case "sample" -> CheckResult.sample(msg, estimatedRows, rule.getMaxResultRows());
                    default -> CheckResult.warn(msg, estimatedRows, rule.getMaxResultRows());
                };
            }

            // 未超限：返回该规则的 maxResultRows 作为安全上限
            return CheckResult.ok(estimatedRows, rule.getMaxResultRows());
        }

        // 无匹配规则：默认允许，结果行限 500
        return CheckResult.ok(estimatedRows, 500);
    }

    private long safeCount(JdbcTemplate jdbc, String sql, Object[] params) {
        try {
            Long result = jdbc.queryForObject(sql, Long.class, params);
            return result != null ? result : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    // ----------------------------------------------------------------
    // 预检结果
    // ----------------------------------------------------------------

    public static class CheckResult {
        public final boolean allowed;       // 是否允许执行
        public final boolean hasWarning;    // 是否带警告
        public final String warningMsg;
        public final long estimatedRows;
        public final int maxResultRows;     // 建议的结果行上限
        public final boolean sampling;      // 是否使用采样模式（自动加 LIMIT）

        private CheckResult(boolean allowed, boolean hasWarning, String warningMsg,
                            long estimatedRows, int maxResultRows, boolean sampling) {
            this.allowed = allowed;
            this.hasWarning = hasWarning;
            this.warningMsg = warningMsg;
            this.estimatedRows = estimatedRows;
            this.maxResultRows = maxResultRows;
            this.sampling = sampling;
        }

        static CheckResult ok(long rows, int maxRows) {
            return new CheckResult(true, false, null, rows, maxRows, false);
        }

        static CheckResult warn(String msg, long rows, int maxRows) {
            return new CheckResult(true, true, msg, rows, maxRows, false);
        }

        static CheckResult blocked(String msg, long rows) {
            return new CheckResult(false, true, msg, rows, 0, false);
        }

        static CheckResult sample(String msg, long rows, int maxRows) {
            return new CheckResult(true, true, msg, rows, maxRows, true);
        }

        public Map<String, Object> toWarningPayload() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_perfWarning", warningMsg);
            m.put("_estimatedRows", estimatedRows);
            if (sampling) m.put("_mode", "sample");
            return m;
        }
    }
}
