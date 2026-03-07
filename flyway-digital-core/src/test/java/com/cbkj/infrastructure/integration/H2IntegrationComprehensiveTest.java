package com.cbkj.infrastructure.integration;

import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.model.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * H2 综合集成测试。
 * 用于覆盖 baseline、执行顺序、installed_by 等核心迁移流程行为。
 */
public class H2IntegrationComprehensiveTest {

    private DataSource dataSource;
    private Object config;

    @Before
    public void setUp() {
        // 创建 H2 内存数据库。
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        this.dataSource = h2DataSource;
    }

    @After
    public void tearDown() throws Exception {
        // 清理测试表。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS flyway_digital_history");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("DROP TABLE IF EXISTS products");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS order_items");
            stmt.execute("DROP TABLE IF EXISTS customers");
            stmt.execute("DROP TABLE IF EXISTS categories");
        }
    }

    @Test
    public void testBaselineEnabledWithExistingSqlFile() throws Exception {
        // 验证启用 baseline 且目标版本存在时，会写入 baseline 记录。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, true);
        invokeSetter(config, "setBaselineVersion", String.class, "1.1.1");
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        List<MigrationRecord> records = getMigrationRecords(dataSource);
        
        assertTrue("Should have at least 1 record", records.size() >= 1);

        MigrationRecord baselineRecord = null;
        for (MigrationRecord record : records) {
            if ("1.1.1".equals(record.version)) {
                baselineRecord = record;
                break;
            }
        }
        
        assertNotNull("Baseline record should exist", baselineRecord);
        assertEquals("Baseline description should be '<< Flyway Baseline >>'", "<< Flyway Baseline >>", baselineRecord.description);
        assertEquals("Baseline script should be '<< Flyway Baseline >>'", "<< Flyway Baseline >>", baselineRecord.script);
        assertNull("Baseline checksum should be null", baselineRecord.checksum);
        assertEquals("Baseline execution_time should be 0", 0, baselineRecord.executionTime);
        assertTrue("Baseline should be successful", baselineRecord.success);
        assertNotNull("Baseline installed_by should not be null", baselineRecord.installedBy);
    }

    @Test
    public void testBaselineEnabledWithoutExistingSqlFile() throws Exception {
        // 验证即使没有同版本 SQL 文件，也会写入 baseline 记录。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, true);
        invokeSetter(config, "setBaselineVersion", String.class, "1.1.1");
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        List<MigrationRecord> records = getMigrationRecords(dataSource);
        
        // Find baseline record for 1.1.1
        MigrationRecord baselineRecord = null;
        for (MigrationRecord record : records) {
            if ("1.1.1".equals(record.version)) {
                baselineRecord = record;
                break;
            }
        }
        
        assertNotNull("CRITICAL: Baseline record for version 1.1.1 MUST exist even without SQL file! " +
            "This is the core requirement of the baseline feature.", baselineRecord);

        assertEquals("Baseline description", "<< Flyway Baseline >>", baselineRecord.description);
        assertEquals("Baseline script", "<< Flyway Baseline >>", baselineRecord.script);
        assertNull("Baseline checksum must be null", baselineRecord.checksum);
        assertEquals("Baseline execution_time", 0, baselineRecord.executionTime);
        assertTrue("Baseline success", baselineRecord.success);
        assertNotNull("Baseline installed_by must not be null", baselineRecord.installedBy);
    }

    @Test
    public void testBaselineDisabled() throws Exception {
        // 验证关闭 baseline 后，不会生成 baseline 占位记录。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, false);
        invokeSetter(config, "setBaselineVersion", String.class, "1.1.1");
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        List<MigrationRecord> records = getMigrationRecords(dataSource);

        for (MigrationRecord record : records) {
            if ("1.0.0".equals(record.version)) {
                // Should NOT be a baseline record
                assertFalse("V1.0.0 should not be a baseline record", 
                    "<< Flyway Baseline >>".equals(record.description));
            }
            if ("1.1.1".equals(record.version)) {
                // Should NOT be a baseline record (baseline is disabled)
                assertFalse("V1.1.1 should not be a baseline record when baseline is disabled", 
                    "<< Flyway Baseline >>".equals(record.description));
            }
        }
        
    }

    @Test
    public void testExecutionOrderByVersion() throws Exception {
        // 验证执行顺序按版本号排序，而不是按资源发现顺序。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, false);
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        List<MigrationRecord> records = getMigrationRecords(dataSource);

        for (int i = 1; i < records.size(); i++) {
            MigrationVersion prevVersion = MigrationVersion.parse(records.get(i-1).version);
            MigrationVersion currVersion = MigrationVersion.parse(records.get(i).version);
            
            assertTrue("Records must be in version order", 
                prevVersion.compareTo(currVersion) < 0);
        }
    }


    @Test
    public void testFailedHistoryBlocksRetry() throws Exception {
        // 验证历史表中存在失败记录时，会阻断同版本迁移的再次执行。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, false);
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE flyway_digital_history SET success = 0 WHERE version = '1'");
        }

        try {
            newFlywayDigital(config).migrate();
            fail("Expected exception due to failed history record");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("has failed in a previous execution"));
        }
    }

    @Test
    public void testBaselineRecordNotDuplicatedOnSecondRun() throws Exception {
        // 验证 baseline 记录在重复启动场景不会被重复写入。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, true);
        invokeSetter(config, "setBaselineVersion", String.class, "1.1.1");
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();
        flywayDigital.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_digital_history WHERE version = '1.1.1' AND description = '<< Flyway Baseline >>'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    public void testInstalledByNotNull() throws Exception {
        // 验证所有迁移记录都会写入 installed_by。
        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, true);
        invokeSetter(config, "setBaselineVersion", String.class, "1.1.1");
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        List<MigrationRecord> records = getMigrationRecords(dataSource);
        
        for (MigrationRecord record : records) {
            assertNotNull("installed_by must not be null for version " + record.version, 
                record.installedBy);
            assertFalse("installed_by must not be empty for version " + record.version, 
                record.installedBy.isEmpty());
        }
    }

    /**
     * 读取历史表中的迁移记录，用于断言 baseline 和执行顺序。
     */

    private List<MigrationRecord> getMigrationRecords(DataSource dataSource) throws Exception {
        List<MigrationRecord> records = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT installed_rank, version, description, script, checksum, " +
                 "installed_by, installed_on, execution_time, success " +
                 "FROM flyway_digital_history ORDER BY installed_rank")) {
            
            while (rs.next()) {
                MigrationRecord record = new MigrationRecord();
                record.installedRank = rs.getInt("installed_rank");
                record.version = rs.getString("version");
                record.description = rs.getString("description");
                record.script = rs.getString("script");
                record.checksum = rs.getObject("checksum") != null ? rs.getInt("checksum") : null;
                record.installedBy = rs.getString("installed_by");
                record.installedOn = rs.getTimestamp("installed_on");
                record.executionTime = rs.getInt("execution_time");
                record.success = rs.getBoolean("success");
                records.add(record);
            }
        }
        
        return records;
    }

    /**
     * 通过反射创建配置对象，避免配置类包名漂移导致测试编译失败。
     */
    private Object createConfig() {
        try {
            Class<?> configClass = Class.forName("com.cbkj.infrastructure.config.FlywayDigitalConfig");
            return configClass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create FlywayDigitalConfig", ex);
        }
    }

    /**
     * 通过反射设置配置属性，保持测试对业务行为的关注点不变。
     */
    private void invokeSetter(Object target, String methodName, Class<?> parameterType, Object value) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke setter: " + methodName, ex);
        }
    }

    /**
     * 通过 FlywayDigital 的实际构造器创建实例，规避当前构造器签名漂移问题。
     */
    private FlywayDigital newFlywayDigital(Object configObject) {
        try {
            Constructor<?> constructor = FlywayDigital.class.getConstructors()[0];
            return (FlywayDigital) constructor.newInstance(dataSource, configObject);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create FlywayDigital", ex);
        }
    }

    private static class MigrationRecord {
        int installedRank;
        String version;
        String description;
        String script;
        Integer checksum;
        String installedBy;
        java.sql.Timestamp installedOn;
        int executionTime;
        boolean success;
    }
}
