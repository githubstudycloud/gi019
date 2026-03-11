package com.mcp.server.meta.datasource;

import com.mcp.server.meta.domain.DsConfig;
import com.mcp.server.meta.repo.MetaConfigRepo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源管理器 —— 运行时管理多个数据源连接池。
 *
 * 核心能力：
 * 1. 应用启动时从 mcp_datasource 加载并初始化所有启用的数据源
 * 2. 运行时新增/刷新/关闭数据源（不重启）
 * 3. 支持为 H2 测试数据源自动初始化 Schema 和测试数据
 * 4. 提供 getJdbcTemplate(dsKey) 供业务层使用
 */
@Component
public class DynamicDsManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDsManager.class);

    private final MetaConfigRepo repo;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> templates = new ConcurrentHashMap<>();

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public DynamicDsManager(MetaConfigRepo repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void init() {
        log.info("[DynamicDs] 初始化动态数据源...");
        List<DsConfig> configs = repo.findAllDatasources(true);
        for (DsConfig cfg : configs) {
            try {
                registerDatasource(cfg);
            } catch (Exception e) {
                log.error("[DynamicDs] 数据源 [{}] 初始化失败: {}", cfg.getDsKey(), e.getMessage());
            }
        }
        log.info("[DynamicDs] 已加载 {} 个动态数据源: {}", pools.size(), pools.keySet());
    }

    /**
     * 注册（或重新注册）一个数据源。
     * 如果已存在同 key 的数据源，先关闭旧连接池再重建。
     */
    public synchronized void registerDatasource(DsConfig cfg) {
        // 关闭旧连接池
        if (pools.containsKey(cfg.getDsKey())) {
            HikariDataSource old = pools.remove(cfg.getDsKey());
            templates.remove(cfg.getDsKey());
            try { old.close(); } catch (Exception ignore) {}
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.getUrl());
        hc.setUsername(cfg.getUsername());
        hc.setPassword(cfg.getPassword() != null ? cfg.getPassword() : "");
        String driver = cfg.resolvedDriverClass();
        if (driver != null) hc.setDriverClassName(driver);
        hc.setMaximumPoolSize(cfg.getPoolSize());
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(10_000);
        hc.setPoolName("DynPool-" + cfg.getDsKey());

        HikariDataSource ds = new HikariDataSource(hc);
        JdbcTemplate tpl = new JdbcTemplate(ds);

        // H2 测试模式：初始化业务库 Schema + 数据
        if (isH2Profile() && cfg.getDsKey().startsWith("biz")) {
            initBizH2(tpl, cfg.getDsKey());
        }

        pools.put(cfg.getDsKey(), ds);
        templates.put(cfg.getDsKey(), tpl);
        log.info("[DynamicDs] 数据源 [{}] 注册成功 → {}", cfg.getDsKey(), cfg.getUrl());
    }

    /** 获取指定数据源的 JdbcTemplate，不存在则抛出异常（引导 AI 先查项目确认库名）。 */
    public JdbcTemplate getTemplate(String dsKey) {
        JdbcTemplate tpl = templates.get(dsKey);
        if (tpl == null) {
            throw new IllegalArgumentException(
                "数据源 [" + dsKey + "] 未配置或未启用。请先通过 execution_list_projects 确认项目对应的业务库。");
        }
        return tpl;
    }

    /** 检查数据源是否可用 */
    public boolean isAvailable(String dsKey) {
        return templates.containsKey(dsKey);
    }

    /** 刷新单个数据源（Web UI 修改配置后调用）*/
    public void refresh(String dsKey) {
        repo.findDatasourceByKey(dsKey).ifPresentOrElse(
            this::registerDatasource,
            () -> log.warn("[DynamicDs] 未找到数据源配置: {}", dsKey)
        );
    }

    /** 列出所有已加载的数据源 key */
    public Set<String> listKeys() {
        return Collections.unmodifiableSet(pools.keySet());
    }

    /** 获取数据源健康状态 */
    public Map<String, Object> getHealth(String dsKey) {
        Map<String, Object> info = new LinkedHashMap<>();
        HikariDataSource ds = pools.get(dsKey);
        if (ds == null) {
            info.put("status", "not_loaded");
            return info;
        }
        info.put("status", ds.isRunning() ? "up" : "down");
        info.put("activeConnections", ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getActiveConnections() : -1);
        info.put("idleConnections",   ds.getHikariPoolMXBean() != null ? ds.getHikariPoolMXBean().getIdleConnections() : -1);
        return info;
    }

    @PreDestroy
    public void destroy() {
        pools.values().forEach(ds -> {
            try { ds.close(); } catch (Exception ignore) {}
        });
        log.info("[DynamicDs] 所有动态数据源已关闭");
    }

    // ----------------------------------------------------------------
    // 私有：H2 业务库初始化
    // ----------------------------------------------------------------

    private boolean isH2Profile() {
        return activeProfile.contains("h2");
    }

    private void initBizH2(JdbcTemplate tpl, String dsKey) {
        try {
            // Schema
            new ResourceDatabasePopulator(new ClassPathResource("schema-biz-h2.sql")).execute(tpl.getDataSource());
            log.info("[DynamicDs] [{}] schema 初始化完成", dsKey);
            // 数据
            String dataFile = "data-" + dsKey + "-h2.sql";
            ClassPathResource dataRes = new ClassPathResource(dataFile);
            if (dataRes.exists()) {
                new ResourceDatabasePopulator(dataRes).execute(tpl.getDataSource());
                log.info("[DynamicDs] [{}] 测试数据初始化完成", dsKey);
            }
        } catch (Exception e) {
            log.warn("[DynamicDs] [{}] H2 初始化异常（可能已初始化）: {}", dsKey, e.getMessage());
        }
    }
}
