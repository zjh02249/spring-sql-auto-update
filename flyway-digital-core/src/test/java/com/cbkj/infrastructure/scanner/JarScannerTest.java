package com.cbkj.infrastructure.scanner;

import com.cbkj.infrastructure.model.SqlMigration;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * JarScanner 单元测试。
 * 用于验证普通 JAR、嵌套路径和异常输入下的扫描行为。
 */
public class JarScannerTest {

    /**
     * 验证普通 JAR 中指定目录下的 SQL 文件会被识别并解析。
     */
    @Test
    public void testScanFindsSqlEntriesInJar() throws Exception {
        File jarFile = createJar(
                "db/migration/V2__later.sql", "SELECT 2;",
                "db/migration/V1__init.sql", "SELECT 1;",
                "db/migration/readme.txt", "ignore");

        JarScanner scanner = new JarScanner();
        List<SqlMigration> migrations = scanner.scan(jarFile.getAbsolutePath(), "db/migration/");

        assertEquals(2, migrations.size());
        assertEquals("V2__later.sql", migrations.get(0).getScript());
        assertEquals("classpath:db/migration/", migrations.get(0).getLocation());
    }

    /**
     * 验证嵌套目录扫描时会正确拼接搜索前缀并过滤目标目录。
     */
    @Test
    public void testScanNestedUsesCombinedPrefix() throws Exception {
        File jarFile = createJar(
                "BOOT-INF/classes/db/migration/mysql/V1__init.sql", "SELECT 1;",
                "BOOT-INF/classes/db/migration/mysql/V2__later.sql", "SELECT 2;",
                "BOOT-INF/classes/db/migration/pgsql/V3__ignored.sql", "SELECT 3;");

        JarScanner scanner = new JarScanner();
        List<SqlMigration> migrations = scanner.scanNested(
                jarFile.getAbsolutePath(), "BOOT-INF/classes/db/migration", "mysql");

        assertEquals(2, migrations.size());
        assertEquals("V1__init.sql", migrations.get(0).getScript());
        assertEquals("classpath:mysql", migrations.get(0).getLocation());
    }

    /**
     * 验证非法 JAR 路径不会抛出异常，而是返回空结果。
     */
    @Test
    public void testScanReturnsEmptyWhenJarIsInvalid() {
        JarScanner scanner = new JarScanner();
        List<SqlMigration> migrations = scanner.scan("missing-file.jar", "db/migration/");
        assertTrue(migrations.isEmpty());
    }

    /**
     * 验证私有前缀拼接逻辑会自动补齐路径分隔符。
     */
    @Test
    public void testBuildSearchPrefixNormalizesSlashes() throws Exception {
        Method method = JarScanner.class.getDeclaredMethod("buildSearchPrefix", String.class, String.class);
        method.setAccessible(true);

        JarScanner scanner = new JarScanner();
        assertEquals("db/migration/mysql/", method.invoke(scanner, "db/migration", "mysql"));
        assertEquals("db/migration/", method.invoke(scanner, "db/migration/", ""));
    }

    /**
     * 创建临时测试 JAR，供扫描器读取。
     */
    private File createJar(String... entries) throws Exception {
        Path jarPath = Files.createTempFile("jar-scanner", ".jar");
        try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            for (int i = 0; i < entries.length; i += 2) {
                JarEntry entry = new JarEntry(entries[i]);
                outputStream.putNextEntry(entry);
                outputStream.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
        }
        return jarPath.toFile();
    }
}
