package com.cbkj.infrastructure.core;

import com.cbkj.infrastructure.core.config.FlywayDigitalConfig;
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
import java.util.*;

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

        // 获取已应用的迁移
        HistoryRepository historyRepository = new HistoryRepository(dataSource, config.getTable());
        List<AppliedMigration> appliedMigrations = historyRepository.findAllSuccessful();

        LOGGER.info("[FlywayDigital] Found {} applied migration(s) in history", appliedMigrations.size());

        // 扫描SQL文件
        SqlScanner scanner = new SqlScanner(config.getLocations());
        List<SqlMigration> pendingMigrations = scanner.scan();

        // 过滤已执行的迁移并校验checksum
        List<SqlMigration> migrationsToExecute = new ArrayList<>();
        for (SqlMigration migration : pendingMigrations) {
            AppliedMigration applied = findAppliedMigration(appliedMigrations, migration.getVersion().toString());
            
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
                LOGGER.info("[FlywayDigital] Skipping already applied migration: {}", migration.getVersion());
            } else {
                migrationsToExecute.add(migration);
            }
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
            boolean baselineExists = false;
            for (AppliedMigration applied : appliedMigrations) {
                if (applied.getVersion().equals(baselineVersion.toString())) {
                    baselineExists = true;
                    LOGGER.info("[FlywayDigital] Baseline version {} already exists in history", baselineVersion);
                    break;
                }
            }
            
            // 如果不存在 baseline 记录，创建一条
            if (!baselineExists) {
                LOGGER.info("[FlywayDigital] Recording baseline version {} to history", baselineVersion);
                recordBaselineMigration(baselineVersion, historyRepository, historyRepository.getNextInstalledRank());
            }
            
            // 过滤掉低于等于 baseline 版本的迁移
            List<SqlMigration> filteredMigrations = new ArrayList<>();
            for (SqlMigration migration : migrationsToExecute) {
                int comparison = migration.getVersion().compareTo(baselineVersion);
                if (comparison <= 0) {
                    // 低于或等于 baseline 版本，跳过
                    LOGGER.info("[FlywayDigital] Skipping migration {} (at or below baseline version)", migration.getVersion());
                } else {
                    // 高于 baseline 版本，正常执行
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
            executeMigration(migration, sqlExecutor, historyRepository, nextRank++);
        }

        LOGGER.info("[FlywayDigital] Migration completed successfully. Executed {} migration(s)", 
            migrationsToExecute.size());
    }

    private AppliedMigration findAppliedMigration(List<AppliedMigration> appliedMigrations, String version) {
        for (AppliedMigration applied : appliedMigrations) {
            if (version.equals(applied.getVersion())) {
                return applied;
            }
        }
        return null;
    }

    private void executeMigration(SqlMigration migration, SqlExecutor sqlExecutor,
                                  HistoryRepository historyRepository, int installedRank) throws Exception {
        String version = migration.getVersion().toString();
        String script = migration.getScript();

        // 检查是否已有该版本的失败记录
        if (historyRepository.existsByVersionAndSuccess(version, false)) {
            throw new IllegalStateException(
                    "[FlywayDigital] Migration version " + version + " has failed in a previous execution. " +
                    "Please check and delete the failed record from history table before retrying. " +
                    "Table: " + config.getTable() + ", Version: " + version + ", success=0");
        }

        // 检查是否已有该版本的记录（无论成功与否）
        // 如果有，则不重复插入
        if (historyRepository.existsByVersion(version)) {
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
