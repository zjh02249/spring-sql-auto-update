package com.cbkj.infrastructure.history;

import com.cbkj.infrastructure.model.AppliedMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * History表数据访问层
 * 负责已应用迁移记录的CRUD操作
 */
public class HistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryRepository.class);

    private final DataSource dataSource;
    private final String tableName;

    public HistoryRepository(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    /**
     * 查询所有成功的迁移记录
     *
     * @return 已应用的迁移列表，按installed_rank排序
     */
    public List<AppliedMigration> findAllSuccessful() throws SQLException {
        List<AppliedMigration> migrations = new ArrayList<>();

        String sql = "SELECT installed_rank, version, description, type, script, " +
                     "checksum, installed_by, installed_on, execution_time, success " +
                     "FROM " + tableName + " " +
                     "WHERE success = 1 " +
                     "ORDER BY installed_rank";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AppliedMigration migration = mapResultSetToMigration(rs);
                migrations.add(migration);
            }
        }

        LOGGER.debug("[HistoryRepository] Found {} successful migration(s)", migrations.size());
        return migrations;
    }

    /**
     * 查询所有迁移记录（包括失败的）
     *
     * @return 所有迁移记录
     */
    public List<AppliedMigration> findAll() throws SQLException {
        List<AppliedMigration> migrations = new ArrayList<>();

        String sql = "SELECT installed_rank, version, description, type, script, " +
                     "checksum, installed_by, installed_on, execution_time, success " +
                     "FROM " + tableName + " " +
                     "ORDER BY installed_rank";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AppliedMigration migration = mapResultSetToMigration(rs);
                migrations.add(migration);
            }
        }

        return migrations;
    }

    /**
     * 根据版本号查询迁移记录
     */
    public AppliedMigration findByVersion(String version) throws SQLException {
        String sql = "SELECT installed_rank, version, description, type, script, " +
                     "checksum, installed_by, installed_on, execution_time, success " +
                     "FROM " + tableName + " " +
                     "WHERE version = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, version);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMigration(rs);
                }
            }
        }

        return null;
    }

    /**
     * 保存迁移记录
     */
    public void save(AppliedMigration migration) throws SQLException {
        String sql = "INSERT INTO " + tableName + " " +
                     "(installed_rank, version, description, type, script, checksum, " +
                     "installed_by, installed_on, execution_time, success) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, migration.getInstalledRank());
            stmt.setString(2, migration.getVersion());
            stmt.setString(3, migration.getDescription());
            stmt.setString(4, migration.getType());
            stmt.setString(5, migration.getScript());

            if (migration.getChecksum() != null) {
                stmt.setInt(6, migration.getChecksum());
            } else {
                stmt.setNull(6, java.sql.Types.INTEGER);
            }

            stmt.setString(7, migration.getInstalledBy());
            stmt.setTimestamp(8, migration.getInstalledOn());
            stmt.setInt(9, migration.getExecutionTime());
            stmt.setBoolean(10, migration.isSuccess());

            stmt.executeUpdate();

            LOGGER.debug("[HistoryRepository] Saved migration record: version={}, success={}",
                    migration.getVersion(), migration.isSuccess());
        }
    }

    /**
     * 获取下一个installed_rank
     */
    public int getNextInstalledRank() throws SQLException {
        String sql = "SELECT MAX(installed_rank) FROM " + tableName;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                int maxRank = rs.getInt(1);
                return rs.wasNull() ? 1 : maxRank + 1;
            }
        }

        return 1;
    }

    /**
     * 检查是否存在失败的迁移
     */
    public boolean hasFailedMigrations() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE success = 0";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }

        return false;
    }

    /**
     * 获取当前数据库用户
     */
    public String getCurrentUser() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getUserName();
        }
    }

    /**
     * 将ResultSet映射为AppliedMigration对象
     */
    private AppliedMigration mapResultSetToMigration(ResultSet rs) throws SQLException {
        AppliedMigration migration = new AppliedMigration();
        migration.setInstalledRank(rs.getInt("installed_rank"));
        migration.setVersion(rs.getString("version"));
        migration.setDescription(rs.getString("description"));
        migration.setType(rs.getString("type"));
        migration.setScript(rs.getString("script"));
        
        int checksum = rs.getInt("checksum");
        if (!rs.wasNull()) {
            migration.setChecksum(checksum);
        }
        
        migration.setInstalledBy(rs.getString("installed_by"));
        migration.setInstalledOn(rs.getTimestamp("installed_on"));
        migration.setExecutionTime(rs.getInt("execution_time"));
        migration.setSuccess(rs.getBoolean("success"));
        
        return migration;
    }

    /**
    /**
     * 检查是否存在指定版本的迁移记录（无论成功与否）
     *
     * @param version 版本号
     * @return 如果存在返回true，否则返回false
     */
    public boolean existsByVersion(String version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE version = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, version);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    /**
     * 检查是否存在指定版本和状态的迁移记录
     *
     * @param version 版本号
     * @param success 成功状态（true=成功，false=失败）
     * @return 如果存在返回true，否则返回false
     */
    public boolean existsByVersionAndSuccess(String version, boolean success) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE version = ? AND success = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, version);
            stmt.setBoolean(2, success);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }
}
