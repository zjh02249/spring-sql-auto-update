package com.cbkj.infrastructure.performance;

import com.cbkj.infrastructure.config.FlywayDigitalConfig;
import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.model.SqlMigration;
import com.cbkj.infrastructure.scanner.JarScanner;
import com.cbkj.infrastructure.scanner.SqlScanner;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 性能烟测。
 * 用于在默认测试流程中记录扫描与迁移主链路的基线耗时，但不设置严格性能阈值。
 */
public class PerformanceSmokeTest {

    /**
     * 验证小规模全链路迁移可以输出扫描、首次执行和二次启动的基线耗时。
     */
    @Test
    public void testSmallScaleMigrationBaseline() throws Exception {
        int migrationCount = 10;
        Path migrationDir = Files.createTempDirectory("perf-small-migration");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, 1);

        DataSource dataSource = PerformanceTestSupport.createH2DataSource("perf_small_chain");
        String historyTable = "flyway_digital_history_perf_small";
        FlywayDigitalConfig config = createConfig(migrationDir, historyTable);
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);

        long scanStart = System.nanoTime();
        List<SqlMigration> migrations = new SqlScanner(migrationDir.toAbsolutePath().toString()).scan();
        long scanMillis = nanosToMillis(System.nanoTime() - scanStart);

        long firstRunStart = System.nanoTime();
        flywayDigital.migrate();
        long firstRunMillis = nanosToMillis(System.nanoTime() - firstRunStart);

        long secondRunStart = System.nanoTime();
        flywayDigital.migrate();
        long secondRunMillis = nanosToMillis(System.nanoTime() - secondRunStart);

        long historyCount = countHistoryRows(dataSource, historyTable);

        printMetric("small-chain", migrationCount, scanMillis, firstRunMillis, secondRunMillis);

        assertEquals(migrationCount, migrations.size());
        assertEquals(migrationCount, historyCount);
        assertTrue(firstRunMillis >= 0);
        assertTrue(secondRunMillis >= 0);

        PerformanceTestSupport.cleanupTables(dataSource, historyTable, migrationCount);
    }

    /**
     * 验证中等规模文件系统扫描可以稳定输出基线耗时与平均单文件耗时。
     */
    @Test
    public void testFileSystemScanBaselineForHundredsOfMigrations() throws Exception {
        assertFileSystemScanScenario("filesystem-scan-100", 100, 2);
        assertFileSystemScanScenario("filesystem-scan-500", 500, 2);
    }

    /**
     * 验证普通 JAR 扫描可以输出批量 migration 文件的基线耗时。
     */
    @Test
    public void testJarScanBaselineForHundredsOfMigrations() throws Exception {
        assertJarScanScenario("jar-scan-100", 100, "db/migration");
    }

    /**
     * 验证大批量全链路迁移与重复启动场景都能输出稳定结果。
     */
    @Test
    public void testMigrationChainBaselineForHundredsOfMigrations() throws Exception {
        assertMigrationScenario("migration-chain-100", 100);
        assertMigrationScenario("migration-chain-500", 500);
    }

    /**
     * 验证混合 classpath 与文件系统 location 的扫描场景可正常工作。
     */
    @Test
    public void testMixedLocationsScanBaseline() throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-mixed-migration");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, 20, 1, 1000);

        SqlScanner scanner = new SqlScanner("classpath:db/migration," + migrationDir.toAbsolutePath());
        long start = System.nanoTime();
        List<SqlMigration> migrations = scanner.scan();
        long elapsedMillis = nanosToMillis(System.nanoTime() - start);

        System.out.println(String.format(
                "[PERF][mixed-scan] migrations=%d totalMs=%d avgPerFileMs=%.2f",
                migrations.size(),
                elapsedMillis,
                averageMillis(elapsedMillis, migrations.size())));

        assertTrue(migrations.size() >= 22);
        assertTrue(elapsedMillis >= 0);
    }

    /**
     * 执行文件系统扫描场景并输出结果。
     */
    private void assertFileSystemScanScenario(String scenarioName, int migrationCount, int nestedLevels) throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-fs-scan");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, nestedLevels);

        long start = System.nanoTime();
        List<SqlMigration> migrations = new SqlScanner(migrationDir.toAbsolutePath().toString()).scan();
        long elapsedMillis = nanosToMillis(System.nanoTime() - start);

        System.out.println(String.format(
                "[PERF][%s] migrations=%d totalMs=%d avgPerFileMs=%.2f",
                scenarioName,
                migrations.size(),
                elapsedMillis,
                averageMillis(elapsedMillis, migrations.size())));

        assertEquals(migrationCount, migrations.size());
        assertTrue(elapsedMillis >= 0);
    }

    /**
     * 执行普通 JAR 扫描场景并输出结果。
     */
    private void assertJarScanScenario(String scenarioName, int migrationCount, String prefix) throws Exception {
        Path jarPath = Files.createTempFile("perf-jar-scan", ".jar");
        File jarFile = PerformanceTestSupport.createMigrationJar(jarPath, prefix, migrationCount);

        JarScanner scanner = new JarScanner();
        long start = System.nanoTime();
        List<SqlMigration> migrations = scanner.scan(jarFile.getAbsolutePath(), PerformanceTestSupport.normalizePrefix(prefix));
        long elapsedMillis = nanosToMillis(System.nanoTime() - start);

        System.out.println(String.format(
                "[PERF][%s] migrations=%d totalMs=%d avgPerFileMs=%.2f",
                scenarioName,
                migrations.size(),
                elapsedMillis,
                averageMillis(elapsedMillis, migrations.size())));

        assertEquals(migrationCount, migrations.size());
        assertTrue(elapsedMillis >= 0);
    }

    /**
     * 执行全链路迁移场景并输出扫描、首次执行和重复启动的基线结果。
     */
    private void assertMigrationScenario(String scenarioName, int migrationCount) throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-migration-chain");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, 2);

        DataSource dataSource = PerformanceTestSupport.createH2DataSource("perf_chain_" + migrationCount);
        String historyTable = "flyway_digital_history_perf_" + migrationCount;
        FlywayDigitalConfig config = createConfig(migrationDir, historyTable);
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);

        long scanStart = System.nanoTime();
        List<SqlMigration> migrations = new SqlScanner(migrationDir.toAbsolutePath().toString()).scan();
        long scanMillis = nanosToMillis(System.nanoTime() - scanStart);

        long firstRunStart = System.nanoTime();
        flywayDigital.migrate();
        long firstRunMillis = nanosToMillis(System.nanoTime() - firstRunStart);

        long secondRunStart = System.nanoTime();
        flywayDigital.migrate();
        long secondRunMillis = nanosToMillis(System.nanoTime() - secondRunStart);

        long historyCount = countHistoryRows(dataSource, historyTable);
        printMetric(scenarioName, migrations.size(), scanMillis, firstRunMillis, secondRunMillis);

        assertEquals(migrationCount, migrations.size());
        assertEquals(migrationCount, historyCount);
        assertTrue(firstRunMillis >= 0);
        assertTrue(secondRunMillis >= 0);

        PerformanceTestSupport.cleanupTables(dataSource, historyTable, migrationCount);
    }

    /**
     * 创建性能测试用配置。
     */
    private FlywayDigitalConfig createConfig(Path migrationDir, String historyTable) {
        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setEnabled(true);
        config.setLocations(migrationDir.toAbsolutePath().toString());
        config.setTable(historyTable);
        config.setBaselineOnMigrate(false);
        config.setValidateOnMigrate(true);
        return config;
    }

    /**
     * 统计历史表中的记录数量，验证迁移确实落库。
     */
    private long countHistoryRows(DataSource dataSource, String historyTable) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + historyTable)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /**
     * 输出全链路场景的统一性能结果。
     */
    private void printMetric(String scenarioName, int migrationCount, long scanMillis, long firstRunMillis, long secondRunMillis) {
        System.out.println(String.format(
                "[PERF][%s] migrations=%d scanMs=%d firstRunMs=%d secondRunMs=%d avgFirstRunPerFileMs=%.2f",
                scenarioName,
                migrationCount,
                scanMillis,
                firstRunMillis,
                secondRunMillis,
                averageMillis(firstRunMillis, migrationCount)));
    }

    /**
     * 将纳秒转换为毫秒。
     */
    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /**
     * 计算平均单文件耗时，便于结果对比。
     */
    private double averageMillis(long totalMillis, int count) {
        if (count == 0) {
            return 0D;
        }
        return (double) totalMillis / (double) count;
    }
}
