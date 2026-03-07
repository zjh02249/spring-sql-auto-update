package com.cbkj.infrastructure.core;

import com.cbkj.infrastructure.config.FlywayDigitalConfig;
import com.cbkj.infrastructure.executor.SqlExecutor;
import com.cbkj.infrastructure.history.HistoryRepository;
import com.cbkj.infrastructure.history.HistoryTableManager;
import com.cbkj.infrastructure.model.AppliedMigration;
import com.cbkj.infrastructure.model.MigrationVersion;
import com.cbkj.infrastructure.model.SqlMigration;
import com.cbkj.infrastructure.scanner.SqlScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FlywayDigital 主入口类
 * 提供迁移功能的主要API
 */
public class FlywayDigital {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayDigital.class);

    private final DataSource dataSource;
    private final FlywayDigitalConfig config;

    public FlywayDigital(DataSource dataSource, FlywayDigitalConfig config) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    /**
     * 执行数据库迁移
     */
    public void migrate() throws Exception {
        if (!config.isEnabled()) {
            LOGGER.info("[FlywayDigital] Migration is disabled, skipping.");
            return;
        }

        LOGGER.info("[FlywayDigital] Starting migration...");
        LOGGER.info("[FlywayDigital] Configuration: {}", config);

        // 初始化History表
        HistoryTableManager tableManager = new HistoryTableManager(dataSource, config.getTable());
        tableManager.createTableIfNotExists();

        // 一次性加载历史记录，避免迁移执行期间大量重复查询
        HistoryRepository historyRepository = new HistoryRepository(dataSource, config.getTable());
        List<AppliedMigration> historyMigrations = historyRepository.findAll();
        Map<String, AppliedMigration> successfulByVersion = buildSuccessfulVersionMap(historyMigrations);
        Set<String> successfulVersions = new HashSet<String>(successfulByVersion.keySet());
        Set<String> failedVersions = buildFailedVersionSet(historyMigrations);

        LOGGER.info("[FlywayDigital] Found {} successful migration(s) and {} failed migration(s) in history",
                successfulVersions.size(), failedVersions.size());

        // 扫描SQL文件
        SqlScanner scanner = new SqlScanner(config.getLocations());
        List<SqlMigration> pendingMigrations = scanner.scan();

        // 过滤已执行的迁移并校验checksum
        List<SqlMigration> migrationsToExecute = new ArrayList<SqlMigration>();
        int skippedAppliedCount = 0;
        for (SqlMigration migration : pendingMigrations) {
            String version = migration.getVersion().toString();
            AppliedMigration applied = successfulByVersion.get(version);

            if (applied != null) {
                // 已执行，校验checksum
                if (config.isValidateOnMigrate() && applied.getChecksum() != null) {
                    if (applied.getChecksum() != migration.getChecksum()) {
                        throw new IllegalStateException(
                                "[FlywayDigital] Checksum mismatch for migration " + migration.getVersion() +
                                        ". Applied: " + applied.getChecksum() + ", Current: " + migration.getChecksum() +
                                        ". Script: " + migration.getScript());
                    }
                }
                skippedAppliedCount++;
                LOGGER.debug("[FlywayDigital] Skipping already applied migration: {}", migration.getVersion());
            } else {
                migrationsToExecute.add(migration);
            }
        }

        if (skippedAppliedCount > 0) {
            LOGGER.info("[FlywayDigital] Skipped {} already applied migration(s)", skippedAppliedCount);
        }

        if (migrationsToExecute.isEmpty()) {
            LOGGER.info("[FlywayDigital] No pending migrations to execute. Database is up to date.");
            return;
        }

        LOGGER.info("[FlywayDigital] Found {} pending migration(s) to execute", migrationsToExecute.size());

        // 处理baseline逻辑
        if (config.isBaselineOnMigrate()) {
            MigrationVersion baselineVersion = MigrationVersion.parse(config.getBaselineVersion());
            LOGGER.info("[FlywayDigital] Baseline is enabled, baseline version: {}", baselineVersion);

            // 检查是否已存在 baseline 记录（无论是实际迁移还是 baseline 标记）
            boolean baselineExists = successfulVersions.contains(baselineVersion.toString());
            if (baselineExists) {
                LOGGER.info("[FlywayDigital] Baseline version {} already exists in history", baselineVersion);
            }

            // 如果不存在 baseline 记录，创建一条
            if (!baselineExists) {
                LOGGER.info("[FlywayDigital] Recording baseline version {} to history", baselineVersion);
                recordBaselineMigration(baselineVersion, historyRepository, historyRepository.getNextInstalledRank());
                successfulVersions.add(baselineVersion.toString());
            }

            // 过滤掉低于等于 baseline 版本的迁移
            List<SqlMigration> filteredMigrations = new ArrayList<SqlMigration>();
            for (SqlMigration migration : migrationsToExecute) {
                int comparison = migration.getVersion().compareTo(baselineVersion);
                if (comparison <= 0) {
                    LOGGER.debug("[FlywayDigital] Skipping migration {} (at or below baseline version)", migration.getVersion());
                } else {
                    filteredMigrations.add(migration);
                }
            }

            migrationsToExecute = filteredMigrations;
            LOGGER.info("[FlywayDigital] After baseline filtering, {} migration(s) to execute", migrationsToExecute.size());
        }

        if (migrationsToExecute.isEmpty()) {
            LOGGER.info("[FlywayDigital] No pending migrations to execute. Database is up to date.");
            return;
        }

        // 确保按版本号排序（防止 SqlScanner 返回顺序不一致）
        Collections.sort(migrationsToExecute);

        // 执行迁移
        SqlExecutor sqlExecutor = new SqlExecutor(dataSource);
        int nextRank = historyRepository.getNextInstalledRank();

        for (SqlMigration migration : migrationsToExecute) {
            executeMigration(migration, sqlExecutor, historyRepository, nextRank++, successfulVersions, failedVersions);
        }

        LOGGER.info("[FlywayDigital] Migration completed successfully. Executed {} migration(s)",
                migrationsToExecute.size());
    }

    /**
     * 构建成功迁移版本映射，便于快速查询并执行 checksum 校验。
     */
    private Map<String, AppliedMigration> buildSuccessfulVersionMap(List<AppliedMigration> migrations) {
        Map<String, AppliedMigration> successfulByVersion = new HashMap<String, AppliedMigration>();
        for (AppliedMigration migration : migrations) {
            if (migration.isSuccess()) {
                successfulByVersion.put(migration.getVersion(), migration);
            }
        }
        return successfulByVersion;
    }

    /**
     * 构建失败迁移版本集合，避免迁移执行阶段重复查库。
     */
    private Set<String> buildFailedVersionSet(List<AppliedMigration> migrations) {
        Set<String> failedVersions = new HashSet<String>();
        for (AppliedMigration migration : migrations) {
            if (!migration.isSuccess()) {
                failedVersions.add(migration.getVersion());
            }
        }
        return failedVersions;
    }

    private void executeMigration(SqlMigration migration, SqlExecutor sqlExecutor,
                                  HistoryRepository historyRepository, int installedRank,
                                  Set<String> successfulVersions, Set<String> failedVersions) throws Exception {
        String version = migration.getVersion().toString();
        String script = migration.getScript();

        // 检查是否已有该版本的失败记录
        if (failedVersions.contains(version)) {
            throw new IllegalStateException(
                    "[FlywayDigital] Migration version " + version + " has failed in a previous execution. " +
                            "Please check and delete the failed record from history table before retrying. " +
                            "Table: " + config.getTable() + ", Version: " + version + ", success=0");
        }

        // 检查是否已有该版本的成功记录（保护性检查）
        if (successfulVersions.contains(version)) {
            LOGGER.info("[FlywayDigital] Migration {} already exists in history, skipping execution", version);
            return;
        }

        LOGGER.info("[FlywayDigital] Executing migration: {} - {}", version, script);

        // 获取当前数据库用户
        String installedBy = historyRepository.getCurrentUser();

        AppliedMigration appliedMigration = new AppliedMigration();
        appliedMigration.setInstalledRank(installedRank);
        appliedMigration.setVersion(version);
        appliedMigration.setDescription(migration.getDescription());
        appliedMigration.setType("SQL");
        appliedMigration.setScript(script);
        appliedMigration.setChecksum(migration.getChecksum());
        appliedMigration.setInstalledBy(installedBy);
        appliedMigration.setInstalledOn(new Timestamp(System.currentTimeMillis()));
        appliedMigration.setSuccess(false); // 先标记为失败，成功后再更新

        long executionTime = 0;
        Exception executionException = null;

        try {
            // 执行SQL
            executionTime = sqlExecutor.executeInTransaction(migration.getSqlContent(), script);

            appliedMigration.setSuccess(true);
            appliedMigration.setExecutionTime((int) executionTime);

            LOGGER.info("[FlywayDigital] Migration {} executed successfully in {}ms", version, executionTime);

        } catch (Exception e) {
            executionException = e;
            appliedMigration.setSuccess(false);
            appliedMigration.setExecutionTime((int) (System.currentTimeMillis() -
                    appliedMigration.getInstalledOn().getTime()));

            LOGGER.error("[FlywayDigital] Migration {} failed: {}", version, e.getMessage());
        }

        // 记录到history表
        try {
            historyRepository.save(appliedMigration);
            if (appliedMigration.isSuccess()) {
                successfulVersions.add(version);
            } else {
                failedVersions.add(version);
            }
        } catch (SQLException e) {
            LOGGER.error("[FlywayDigital] Failed to save migration history for version {}", version, e);

            // 尝使用备用方式确傈失败记求被录，特别是对于达梦(DM)等数据库
            // 在事务回滚后可能会影响后续记录的写入
            try {
                // 短暂等待以确保数据库完成其内部事务清理
                Thread.sleep(50);

                // 创建新仓库实例以获得干净的数据库连接
                HistoryRepository freshRepo = new HistoryRepository(dataSource, config.getTable());
                freshRepo.save(appliedMigration);
                if (appliedMigration.isSuccess()) {
                    successfulVersions.add(version);
                } else {
                    failedVersions.add(version);
                }

                LOGGER.info("[FlywayDigital] Success - History recording recovered for version {}" +
                        " via backup repository instance", version);

            } catch (Exception backupException) {
                LOGGER.error("[FlywayDigital] CRITICAL FAILURE: Both primary and backup attempts failed to record migration " +
                        "status for version {} in history table", version);
                LOGGER.error("[FlywayDigital] This could leave your system in an inconsistent state!");

                // 重要：如果原始SQL执行失败，而现在历史记录也失败，应该把两个问题都通知用户
                if (executionException != null) {
                    throw new RuntimeException(
                            String.format(
                                    "Migration execution failed for version %s (%s) AND history recording also failed (%s). " +
                                            "Failed migration was NOT properly recorded in the history table " +
                                            "(this is critical as the failure won't be tracked for retry).",
                                    version, executionException.getMessage(), backupException.getMessage()),
                            executionException);  // 保留原始错误堆栈
                } else {
                    // 如果原始执行成功，而是历史记录本身出问题了（不太可能但保护性检查）
                    throw new RuntimeException(
                            String.format(
                                    "Migration succeeded but history recording failed for version %s, with error: %s. " +
                                            "The successful migration was NOT properly recorded in the history table.",
                                    version, backupException.getMessage()));
                }
            }
        }

        // 如果执行失败，抛出异常
        if (executionException != null) {
            throw new RuntimeException("Migration failed for version " + version + ": " +
                    executionException.getMessage(), executionException);
        }
    }

    /**
     * 记录 baseline 迁移（不执行SQL，仅记录到history表）
     */
    private void recordBaselineMigration(MigrationVersion version, HistoryRepository historyRepository, int installedRank) {
        try {
            AppliedMigration baselineRecord = new AppliedMigration();
            baselineRecord.setInstalledRank(installedRank);
            baselineRecord.setVersion(version.toString());
            baselineRecord.setDescription("<< Flyway Baseline >>");
            baselineRecord.setType("SQL");
            baselineRecord.setScript("<< Flyway Baseline >>");
            baselineRecord.setChecksum(null); // baseline 不计算 checksum
            baselineRecord.setInstalledBy(historyRepository.getCurrentUser());
            baselineRecord.setInstalledOn(new Timestamp(System.currentTimeMillis()));
            baselineRecord.setExecutionTime(0); // baseline 不执行SQL，耗时为0
            baselineRecord.setSuccess(true);

            historyRepository.save(baselineRecord);
            LOGGER.info("[FlywayDigital] Baseline version {} recorded successfully", version);
        } catch (SQLException e) {
            LOGGER.error("[FlywayDigital] Failed to record baseline version {}", version, e);
        }
    }

    /**
     * 验证迁移状态
     */
    public void validate() {
        // TODO: 实现验证逻辑
        LOGGER.info("[FlywayDigital] Validation not yet implemented");
    }
}
