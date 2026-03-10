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
 * 性能烟测用例。
 * 这些测试主要验证不同链路的基本耗时采集是否可用，并确保重复执行场景不引入回归。
 */
public class PerformanceSmokeTest {

    /**
     * 验证小规模迁移链路的扫描、首轮执行与重复执行开销都能正常采集。
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
     * 验证数百个文件的文件系统扫描链路可以稳定采集指标。
     */
    @Test
    public void testFileSystemScanBaselineForHundredsOfMigrations() throws Exception {
        assertFileSystemScanScenario("filesystem-scan-100", 100, 2);
        assertFileSystemScanScenario("filesystem-scan-500", 500, 2);
    }

    /**
     * 验证普通 JAR 扫描链路可以稳定采集指标。
     */
    @Test
    public void testJarScanBaselineForHundredsOfMigrations() throws Exception {
        assertJarScanScenario("jar-scan-100", 100, "db/migration");
    }

    /**
     * 验证大批量迁移链路的首轮与重复执行指标。
     */
    @Test
    public void testMigrationChainBaselineForHundredsOfMigrations() throws Exception {
        assertMigrationScenario("migration-chain-100", 100);
        assertMigrationScenario("migration-chain-500", 500);
    }

    /**
     * 验证第二次执行不会重复插入历史记录或业务数据。
     */
    @Test
    public void testSecondRunDoesNotDuplicateHistoryOrData() throws Exception {
        assertSecondRunIdempotent("migration-idempotent-100", 100);
        assertSecondRunIdempotent("migration-idempotent-500", 500);
    }

    /**
     * 验证 classpath 与文件系统混合 location 的扫描指标。
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
     * 采集文件系统扫描指标。
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
     * 采集普通 JAR 扫描指标。
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
     * 验证大批量迁移链路可以完成且历史记录数正确。
     */
    private void assertMigrationScenario(String scenarioName, int migrationCount) throws Exception {
        MigrationScenarioMetrics metrics = runMigrationScenario(scenarioName, migrationCount);

        assertEquals(migrationCount, metrics.migrationCount);
        assertEquals(migrationCount, metrics.historyRowsAfterSecondRun);
        assertTrue(metrics.firstRunMillis >= 0);
        assertTrue(metrics.secondRunMillis >= 0);
    }

    /**
     * 验证重复启动只保留扫描与校验开销，不会重复执行迁移副作用。
     */
    private void assertSecondRunIdempotent(String scenarioName, int migrationCount) throws Exception {
        MigrationScenarioMetrics metrics = runMigrationScenario(scenarioName, migrationCount);

        assertEquals(migrationCount, metrics.historyRowsAfterFirstRun);
        assertEquals(migrationCount, metrics.historyRowsAfterSecondRun);
        assertEquals(migrationCount, metrics.dataRowsAfterFirstRun);
        assertEquals(migrationCount, metrics.dataRowsAfterSecondRun);
    }

    /**
     * 统一采集首轮与重复执行指标，便于比较优化收益。
     */
    private MigrationScenarioMetrics runMigrationScenario(String scenarioName, int migrationCount) throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-migration-chain");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, 2);

        int scenarioSuffix = Math.abs(scenarioName.hashCode());
        DataSource dataSource = PerformanceTestSupport.createH2DataSource("perf_chain_" + migrationCount + "_" + scenarioSuffix);
        String historyTable = "flyway_digital_history_perf_" + migrationCount + "_" + scenarioSuffix;
        FlywayDigitalConfig config = createConfig(migrationDir, historyTable);
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);

        try {
            long scanStart = System.nanoTime();
            List<SqlMigration> migrations = new SqlScanner(migrationDir.toAbsolutePath().toString()).scan();
            long scanMillis = nanosToMillis(System.nanoTime() - scanStart);

            long firstRunStart = System.nanoTime();
            flywayDigital.migrate();
            long firstRunMillis = nanosToMillis(System.nanoTime() - firstRunStart);
            long historyRowsAfterFirstRun = countHistoryRows(dataSource, historyTable);
            long dataRowsAfterFirstRun = countInsertedRows(dataSource, migrationCount);

            long secondRunStart = System.nanoTime();
            flywayDigital.migrate();
            long secondRunMillis = nanosToMillis(System.nanoTime() - secondRunStart);
            long historyRowsAfterSecondRun = countHistoryRows(dataSource, historyTable);
            long dataRowsAfterSecondRun = countInsertedRows(dataSource, migrationCount);

            printMetric(scenarioName, migrations.size(), scanMillis, firstRunMillis, secondRunMillis);

            MigrationScenarioMetrics metrics = new MigrationScenarioMetrics();
            metrics.migrationCount = migrations.size();
            metrics.firstRunMillis = firstRunMillis;
            metrics.secondRunMillis = secondRunMillis;
            metrics.historyRowsAfterFirstRun = historyRowsAfterFirstRun;
            metrics.historyRowsAfterSecondRun = historyRowsAfterSecondRun;
            metrics.dataRowsAfterFirstRun = dataRowsAfterFirstRun;
            metrics.dataRowsAfterSecondRun = dataRowsAfterSecondRun;
            return metrics;
        } finally {
            PerformanceTestSupport.cleanupTables(dataSource, historyTable, migrationCount);
        }
    }

    /**
     * 创建性能场景使用的基础配置。
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
     * 统计历史表记录数。
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
     * 汇总所有业务表记录数，用于检测是否出现重复执行副作用。
     */
    private long countInsertedRows(DataSource dataSource, int migrationCount) throws Exception {
        long totalRows = 0L;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (int i = 1; i <= migrationCount; i++) {
                try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM perf_table_" + i)) {
                    resultSet.next();
                    totalRows += resultSet.getLong(1);
                }
            }
        }
        return totalRows;
    }

    /**
     * 输出迁移链路的关键性能指标。
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
     * 纳秒转毫秒。
     */
    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /**
     * 计算平均每个文件的毫秒成本。
     */
    private double averageMillis(long totalMillis, int count) {
        if (count == 0) {
            return 0D;
        }
        return (double) totalMillis / (double) count;
    }

    private static class MigrationScenarioMetrics {
        private int migrationCount;
        private long firstRunMillis;
        private long secondRunMillis;
        private long historyRowsAfterFirstRun;
        private long historyRowsAfterSecondRun;
        private long dataRowsAfterFirstRun;
        private long dataRowsAfterSecondRun;
    }
}
