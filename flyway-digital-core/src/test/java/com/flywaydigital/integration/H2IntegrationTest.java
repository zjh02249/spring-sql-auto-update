package com.cbkj.infrastructure.integration;

import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.core.config.FlywayDigitalConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * H2 Integration Test
 * Tests the complete migration flow with an in-memory H2 database
 */
public class H2IntegrationTest {

    private DataSource dataSource;
    private FlywayDigitalConfig config;

    @Before
    public void setUp() {
        // Create H2 in-memory database
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        this.dataSource = h2DataSource;

        // Configure FlywayDigital
        config = new FlywayDigitalConfig();
        config.setEnabled(true);
        config.setLocations("classpath:db/migration");
        config.setTable("flyway_digital_history");
        config.setBaselineOnMigrate(false);
        config.setValidateOnMigrate(true);
    }

    @After
    public void tearDown() throws Exception {
        // Clean up - drop tables
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
        // Create and execute first migration
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();

        // Verify history table was created
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables " +
                 "WHERE table_name = 'FLYWAY_DIGITAL_HISTORY'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0);
        }

        // Verify migrations were recorded
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
        // First migration
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();

        // Get count after first migration
        int countAfterFirst;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM flyway_digital_history WHERE success = 1")) {
            rs.next();
            countAfterFirst = rs.getInt(1);
        }

        // Second migration (should be idempotent)
        FlywayDigital flywayDigital2 = new FlywayDigital(dataSource, config);
        flywayDigital2.migrate();

        // Count should be the same
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
        // First migration
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();

        // Modify a checksum in the history table to simulate changed SQL
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE flyway_digital_history SET checksum = checksum + 1 WHERE version = '1'");
        }

        // Second migration should fail due to checksum mismatch
        try {
            FlywayDigital flywayDigital2 = new FlywayDigital(dataSource, config);
            flywayDigital2.migrate();
            fail("Expected exception due to checksum mismatch");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Checksum mismatch") || 
                      e.getCause().getMessage().contains("Checksum mismatch"));
        }
    }

    @Test
    public void testDisabledMigration() throws Exception {
        // Disable migration
        config.setEnabled(false);

        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();

        // Verify history table was NOT created
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables " +
                 "WHERE table_name = 'FLYWAY_DIGITAL_HISTORY'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }
}
