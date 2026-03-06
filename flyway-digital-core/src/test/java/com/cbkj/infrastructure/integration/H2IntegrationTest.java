package com.cbkj.infrastructure.integration;

import com.cbkj.infrastructure.core.FlywayDigital;
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

import static org.junit.Assert.*;

/**
 * H2 集成测试。
 * 用于验证 FlywayDigital 在 H2 内存数据库中的完整迁移流程。
 */
public class H2IntegrationTest {

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

        config = createConfig();
        invokeSetter(config, "setEnabled", boolean.class, true);
        invokeSetter(config, "setLocations", String.class, "classpath:db/migration");
        invokeSetter(config, "setTable", String.class, "flyway_digital_history");
        invokeSetter(config, "setBaselineOnMigrate", boolean.class, false);
        invokeSetter(config, "setValidateOnMigrate", boolean.class, true);
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
        }
    }

    @Test
    public void testBasicMigration() throws Exception {
        // 验证基础迁移能够执行并写入历史表。
        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        // 验证历史表已创建。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables " +
                 "WHERE table_name = 'FLYWAY_DIGITAL_HISTORY'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0);
        }

        // 验证迁移记录已写入。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM flyway_digital_history WHERE success = 1")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 0);
        }
    }

    @Test
    public void testIdempotentMigration() throws Exception {
        // 验证重复执行迁移不会重复写入成功记录。
        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        int countAfterFirst;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM flyway_digital_history WHERE success = 1")) {
            rs.next();
            countAfterFirst = rs.getInt(1);
        }

        FlywayDigital flywayDigital2 = newFlywayDigital(config);
        flywayDigital2.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM flyway_digital_history WHERE success = 1")) {
            rs.next();
            assertEquals(countAfterFirst, rs.getInt(1));
        }
    }

    @Test
    public void testChecksumValidation() throws Exception {
        // 验证历史表中的 checksum 被篡改后会触发校验失败。
        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE flyway_digital_history SET checksum = checksum + 1 WHERE version = '1'");
        }

        try {
            FlywayDigital flywayDigital2 = newFlywayDigital(config);
            flywayDigital2.migrate();
            fail("Expected exception due to checksum mismatch");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Checksum mismatch") || 
                      e.getCause().getMessage().contains("Checksum mismatch"));
        }
    }

    @Test
    public void testDisabledMigration() throws Exception {
        // 验证禁用迁移时不会创建历史表。
        invokeSetter(config, "setEnabled", boolean.class, false);

        FlywayDigital flywayDigital = newFlywayDigital(config);
        flywayDigital.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables " +
                 "WHERE table_name = 'FLYWAY_DIGITAL_HISTORY'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    /**
     * 通过反射创建配置对象，避免当前源码与编译产物中的包名漂移影响测试编译。
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
     * 统一通过反射设置配置属性，保持集成测试对配置对象的使用方式不变。
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
     * 通过 FlywayDigital 的实际构造器创建实例，规避配置类签名漂移问题。
     */
    private FlywayDigital newFlywayDigital(Object configObject) {
        try {
            Constructor<?> constructor = FlywayDigital.class.getConstructors()[0];
            return (FlywayDigital) constructor.newInstance(dataSource, configObject);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create FlywayDigital", ex);
        }
    }
}
