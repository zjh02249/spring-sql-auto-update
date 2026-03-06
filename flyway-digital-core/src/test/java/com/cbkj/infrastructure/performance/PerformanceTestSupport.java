package com.cbkj.infrastructure.performance;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 性能测试辅助工具。
 * 用于构造批量 migration 数据集、临时 JAR 和 H2 数据源。
 */
final class PerformanceTestSupport {

    private PerformanceTestSupport() {
    }

    /**
     * 创建批量文件系统 migration 数据集。
     *
     * @param rootDir 根目录
     * @param count 文件数量
     * @param nestedLevels 目录层级
     * @return 生成后的 SQL 文件列表
     */
    static List<Path> createFileSystemMigrations(Path rootDir, int count, int nestedLevels) throws IOException {
        return createFileSystemMigrations(rootDir, count, nestedLevels, 1);
    }

    /**
     * 创建带起始版本偏移的批量文件系统 migration 数据集。
     *
     * @param rootDir 根目录
     * @param count 文件数量
     * @param nestedLevels 目录层级
     * @param startVersion 起始版本号
     * @return 生成后的 SQL 文件列表
     */
    static List<Path> createFileSystemMigrations(Path rootDir, int count, int nestedLevels, int startVersion) throws IOException {
        List<Path> paths = new ArrayList<Path>();
        for (int i = 0; i < count; i++) {
            int versionNumber = startVersion + i;
            Path dir = rootDir;
            for (int level = 0; level < nestedLevels; level++) {
                dir = dir.resolve("level-" + level).resolve("bucket-" + (versionNumber % 5));
            }
            Files.createDirectories(dir);

            String version = String.format("V%d", versionNumber);
            Path file = dir.resolve(version + "__perf_case_" + versionNumber + ".sql");
            Files.write(file, buildSqlContent(versionNumber).getBytes(StandardCharsets.UTF_8));
            paths.add(file);
        }
        return paths;
    }

    /**
     * 创建批量 JAR migration 数据集。
     *
     * @param jarPath JAR 文件路径
     * @param prefix JAR 内目录前缀
     * @param count 文件数量
     * @return 生成后的 JAR 文件
     */
    static File createMigrationJar(Path jarPath, String prefix, int count) throws IOException {
        try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            for (int i = 1; i <= count; i++) {
                String entryName = normalizePrefix(prefix) + "V" + i + "__jar_perf_" + i + ".sql";
                JarEntry entry = new JarEntry(entryName);
                outputStream.putNextEntry(entry);
                outputStream.write(buildSqlContent(i).getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
        }
        return jarPath.toFile();
    }

    /**
     * 创建性能测试专用 H2 数据源。
     */
    static DataSource createH2DataSource(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    /**
     * 清理性能测试使用的表，避免不同用例之间相互干扰。
     */
    static void cleanupTables(DataSource dataSource, String historyTable, int count) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + historyTable);
            for (int i = 1; i <= count; i++) {
                statement.execute("DROP TABLE IF EXISTS perf_table_" + i);
            }
        }
    }

    /**
     * 构造单个 migration 的 SQL 内容。
     * 使用简单 DDL + DML 组合，便于观察扫描和执行链路的基础开销。
     */
    static String buildSqlContent(int index) {
        return "CREATE TABLE IF NOT EXISTS perf_table_" + index + " (\n" +
                "    id BIGINT PRIMARY KEY,\n" +
                "    name VARCHAR(64) NOT NULL\n" +
                ");\n" +
                "INSERT INTO perf_table_" + index + " (id, name) VALUES (" + index + ", 'perf-" + index + "');\n";
    }

    /**
     * 标准化 JAR 前缀，确保以斜杠结尾。
     */
    static String normalizePrefix(String prefix) {
        if (prefix.endsWith("/")) {
            return prefix;
        }
        return prefix + "/";
    }
}
