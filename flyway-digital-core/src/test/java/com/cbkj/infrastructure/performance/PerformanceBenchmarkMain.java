package com.cbkj.infrastructure.performance;

import com.cbkj.infrastructure.config.FlywayDigitalConfig;
import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.model.SqlMigration;
import com.cbkj.infrastructure.scanner.JarScanner;
import com.cbkj.infrastructure.scanner.SqlScanner;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 本地性能基准入口。
 * 用于在不影响默认测试流程的前提下，手动采集不同规模下的扫描与迁移基线数据。
 */
public class PerformanceBenchmarkMain {

    public static void main(String[] args) throws Exception {
        String sizesProperty = System.getProperty("perf.sizes", "100,500,1000");
        int[] sizes = parseSizes(sizesProperty);

        System.out.println("[PERF] Starting local benchmark with sizes=" + sizesProperty);
        for (int size : sizes) {
            runFileSystemScanBenchmark(size);
            runJarScanBenchmark(size);
            runMigrationBenchmark(size);
        }
    }

    /**
     * 执行文件系统扫描基准。
     */
    private static void runFileSystemScanBenchmark(int migrationCount) throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-benchmark-fs");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, 2);

        long start = System.nanoTime();
        SqlScanner scanner = new SqlScanner(migrationDir.toAbsolutePath().toString());
        int actualSize = scanner.scan().size();
        long elapsedMillis = nanosToMillis(System.nanoTime() - start);

        System.out.println(String.format(
                "[PERF][benchmark][filesystem] migrations=%d totalMs=%d avgPerFileMs=%.2f",
                actualSize,
                elapsedMillis,
                averageMillis(elapsedMillis, actualSize)));
    }

    /**
     * 执行普通 JAR 扫描基准。
     */
    private static void runJarScanBenchmark(int migrationCount) throws Exception {
        Path jarPath = Files.createTempFile("perf-benchmark-jar", ".jar");
        File jarFile = PerformanceTestSupport.createMigrationJar(jarPath, "db/migration", migrationCount);

        long start = System.nanoTime();
        JarScanner scanner = new JarScanner();
        int actualSize = scanner.scan(jarFile.getAbsolutePath(), "db/migration/").size();
        long elapsedMillis = nanosToMillis(System.nanoTime() - start);

        System.out.println(String.format(
                "[PERF][benchmark][jar] migrations=%d totalMs=%d avgPerFileMs=%.2f",
                actualSize,
                elapsedMillis,
                averageMillis(elapsedMillis, actualSize)));
    }

    /**
     * 执行全链路迁移基准。
     */
    private static void runMigrationBenchmark(int migrationCount) throws Exception {
        Path migrationDir = Files.createTempDirectory("perf-benchmark-migration");
        PerformanceTestSupport.createFileSystemMigrations(migrationDir, migrationCount, 2);

        DataSource dataSource = PerformanceTestSupport.createH2DataSource("perf_benchmark_" + migrationCount);
        String historyTable = "flyway_digital_history_benchmark_" + migrationCount;
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, createConfig(migrationDir, historyTable));

        long scanStart = System.nanoTime();
        int actualSize = new SqlScanner(migrationDir.toAbsolutePath().toString()).scan().size();
        long scanMillis = nanosToMillis(System.nanoTime() - scanStart);

        long firstRunStart = System.nanoTime();
        flywayDigital.migrate();
        long firstRunMillis = nanosToMillis(System.nanoTime() - firstRunStart);

        long secondRunStart = System.nanoTime();
        flywayDigital.migrate();
        long secondRunMillis = nanosToMillis(System.nanoTime() - secondRunStart);

        long historyRows = countHistoryRows(dataSource, historyTable);
        System.out.println(String.format(
                "[PERF][benchmark][migration] migrations=%d scanMs=%d firstRunMs=%d secondRunMs=%d historyRows=%d avgFirstRunPerFileMs=%.2f",
                actualSize,
                scanMillis,
                firstRunMillis,
                secondRunMillis,
                historyRows,
                averageMillis(firstRunMillis, actualSize)));

        PerformanceTestSupport.cleanupTables(dataSource, historyTable, migrationCount);
    }

    /**
     * 创建基准测试用配置。
     */
    private static FlywayDigitalConfig createConfig(Path migrationDir, String historyTable) {
        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setEnabled(true);
        config.setLocations(migrationDir.toAbsolutePath().toString());
        config.setTable(historyTable);
        config.setBaselineOnMigrate(false);
        config.setValidateOnMigrate(true);
        return config;
    }

    /**
     * 解析命令行中的规模配置。
     */
    private static int[] parseSizes(String sizesProperty) {
        String[] parts = sizesProperty.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    /**
     * 统计历史表记录数，帮助观察迁移是否完整执行。
     */
    private static long countHistoryRows(DataSource dataSource, String historyTable) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + historyTable)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /**
     * 将纳秒转换为毫秒。
     */
    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /**
     * 计算平均单文件耗时。
     */
    private static double averageMillis(long totalMillis, int count) {
        if (count == 0) {
            return 0D;
        }
        return (double) totalMillis / (double) count;
    }
}
