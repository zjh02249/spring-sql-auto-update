package com.cbkj.infrastructure.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * History表管理器
 * 负责创建和维护flyway_digital_history表
 */
public class HistoryTableManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryTableManager.class);

    private final DataSource dataSource;
    private final String tableName;

    public HistoryTableManager(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    /**
     * 检查history表是否存在
     */
    public boolean tableExists() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = null;
            
            // 尝试获取schema
            try {
                schema = connection.getSchema();
            } catch (SQLException | AbstractMethodError e) {
                // JDBC 4.0 (Java 6) 不支持getSchema()
            }

            // 检查表是否存在（尝试多种大小写）
            String[] tableNames = {tableName, tableName.toUpperCase(), tableName.toLowerCase()};
            for (String name : tableNames) {
                try (ResultSet rs = metaData.getTables(catalog, schema, name, null)) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 创建history表（如果不存在）
     */
    public void createTableIfNotExists() throws SQLException {
        if (tableExists()) {
            LOGGER.debug("[HistoryTableManager] History table '{}' already exists", tableName);
            return;
        }

        LOGGER.info("[HistoryTableManager] Creating history table: {}", tableName);

        // 使用通用的DDL语句，兼容多种数据库
        // 使用标准SQL类型，避免数据库特有的语法
        String createTableSql = buildCreateTableSql();

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            
            stmt.execute(createTableSql);
            
            LOGGER.info("[HistoryTableManager] History table '{}' created successfully", tableName);
        } catch (SQLException e) {
            LOGGER.error("[HistoryTableManager] Failed to create history table: {}", tableName, e);
            throw e;
        }
    }

    /**
     * 构建创建表的SQL语句
     * 使用标准SQL，尽可能兼容多种数据库
     */
    private String buildCreateTableSql() {
        StringBuilder sql = new StringBuilder();
        
        sql.append("CREATE TABLE ").append(tableName).append(" (\n");
        sql.append("  installed_rank INT NOT NULL,\n");
        sql.append("  version VARCHAR(50),\n");
        sql.append("  description VARCHAR(200) NOT NULL,\n");
        sql.append("  type VARCHAR(20) NOT NULL,\n");
        sql.append("  script VARCHAR(1000) NOT NULL,\n");
        sql.append("  checksum INT,\n");
        sql.append("  installed_by VARCHAR(100) NOT NULL,\n");
        sql.append("  installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\n");
        sql.append("  execution_time INT NOT NULL,\n");
        sql.append("  success SMALLINT NOT NULL,\n");
        sql.append("  PRIMARY KEY (installed_rank)\n");
        sql.append(")");
        
        return sql.toString();
    }

    /**
     * 删除history表（用于测试）
     */
    public void dropTable() throws SQLException {
        LOGGER.info("[HistoryTableManager] Dropping history table: {}", tableName);

        String dropSql = "DROP TABLE IF EXISTS " + tableName;

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            
            stmt.execute(dropSql);
            
            LOGGER.info("[HistoryTableManager] History table '{}' dropped successfully", tableName);
        } catch (SQLException e) {
            LOGGER.error("[HistoryTableManager] Failed to drop history table: {}", tableName, e);
            throw e;
        }
    }
}
