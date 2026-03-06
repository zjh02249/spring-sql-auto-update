package com.cbkj.infrastructure.scanner;

import com.cbkj.infrastructure.model.SqlMigration;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SqlScanner 单元测试。
 * 用于验证 classpath、文件系统、普通 JAR 与嵌套 JAR 场景下的扫描行为。
 */
public class SqlScannerTest {

    private ClassLoader originalClassLoader;

    /**
     * 恢复线程上下文类加载器，避免测试之间相互污染。
     */
    @After
    public void tearDown() {
        if (originalClassLoader != null) {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            originalClassLoader = null;
        }
    }

    /**
     * 验证 classpath 扫描会优先读取 migration.index，并按版本顺序返回结果。
     */
    @Test
    public void testScanClasspathLocationUsesIndexAndReturnsSortedMigrations() {
        SqlScanner scanner = new SqlScanner("classpath:db/migration");
        List<SqlMigration> migrations = scanner.scan();

        assertEquals(2, migrations.size());
        assertEquals("V1__init_schema.sql", migrations.get(0).getScript());
        assertEquals("V1.1.1__add_user_index.sql", migrations.get(1).getScript());
    }

    /**
     * 验证混合 location 扫描时会忽略空白项，并合并文件系统与 classpath 结果。
     */
    @Test
    public void testScanSupportsMixedLocationsAndIgnoresBlankEntries() throws Exception {
        Path dir = Files.createTempDirectory("sql-scanner-fs");
        Files.write(dir.resolve("V2__add_table.sql"), "SELECT 2;".getBytes(StandardCharsets.UTF_8));

        SqlScanner scanner = new SqlScanner("classpath:db/migration, , " + dir.toAbsolutePath());
        List<SqlMigration> migrations = scanner.scan();

        assertEquals(3, migrations.size());
        assertEquals("V1__init_schema.sql", migrations.get(0).getScript());
        assertEquals("V1.1.1__add_user_index.sql", migrations.get(1).getScript());
        assertEquals("V2__add_table.sql", migrations.get(2).getScript());
    }

    /**
     * 验证跨 location 出现重复版本时会直接失败，避免重复执行迁移。
     */
    @Test
    public void testScanRejectsDuplicateVersionsAcrossLocations() throws Exception {
        Path dir = Files.createTempDirectory("sql-scanner-dup");
        Files.write(dir.resolve("V1__duplicate.sql"), "SELECT 1;".getBytes(StandardCharsets.UTF_8));

        SqlScanner scanner = new SqlScanner("classpath:db/migration," + dir.toAbsolutePath());
        try {
            scanner.scan();
            fail("Expected duplicate version failure");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage() != null && !ex.getMessage().isEmpty());
        }
    }

    /**
     * 验证在没有 migration.index 时，会回退到目录型 classpath 资源扫描。
     */
    @Test
    public void testScanClasspathFallbackFromDirectoryResource() throws Exception {
        Path root = Files.createTempDirectory("sql-scanner-classpath");
        Path migrationDir = Files.createDirectories(root.resolve("custom/migration"));
        Files.write(migrationDir.resolve("V3__from_dir.sql"), "SELECT 3;".getBytes(StandardCharsets.UTF_8));

        withContextClassLoader(new URLClassLoader(new URL[]{root.toUri().toURL()}, null));

        SqlScanner scanner = new SqlScanner("classpath:custom/migration");
        List<SqlMigration> migrations = scanner.scan();

        assertEquals(1, migrations.size());
        assertEquals("V3__from_dir.sql", migrations.get(0).getScript());
    }

    /**
     * 验证私有 scanJar 方法可以从普通 JAR 中识别 SQL 文件，并忽略非 SQL 资源。
     */
    @Test
    public void testPrivateScanJarReadsSqlEntries() throws Exception {
        File jarFile = createJar(
                "jar-migration/V5__jar.sql", "SELECT 5;",
                "jar-migration/ignored.txt", "ignore");
        SqlScanner scanner = new SqlScanner("classpath:jar-migration");
        Method method = SqlScanner.class.getDeclaredMethod("scanJar", String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<SqlMigration> migrations = (List<SqlMigration>) method.invoke(
                scanner, jarFile.getAbsolutePath(), "jar-migration");

        assertEquals(1, migrations.size());
        assertEquals("V5__jar.sql", migrations.get(0).getScript());
    }

    /**
     * 验证私有 scanNestedJar 方法可以扫描 BOOT-INF/classes 前缀下的 SQL 文件。
     */
    @Test
    public void testPrivateScanNestedJarFindsEntries() throws Exception {
        File jarFile = createJar(
                "BOOT-INF/classes/db/migration/mysql/V6__nested.sql", "SELECT 6;",
                "BOOT-INF/classes/db/migration/mysql/V7__nested.sql", "SELECT 7;");

        SqlScanner scanner = new SqlScanner("classpath:unused");
        Method method = SqlScanner.class.getDeclaredMethod("scanNestedJar", String.class, String.class, String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<SqlMigration> migrations = (List<SqlMigration>) method.invoke(
                scanner, jarFile.getAbsolutePath(), "BOOT-INF/classes/db/migration", "mysql");

        assertEquals(2, migrations.size());
        assertEquals("V6__nested.sql", migrations.get(0).getScript());
    }

    /**
     * 验证私有 handleJarUrl 方法会解析普通 JAR URL，并把结果追加到目标集合。
     */
    @Test
    public void testPrivateHandleJarUrlReadsJarEntries() throws Exception {
        File jarFile = createJar("db/migration/V8__jar_url.sql", "SELECT 8;");
        SqlScanner scanner = new SqlScanner("classpath:db/migration");
        Method method = SqlScanner.class.getDeclaredMethod("handleJarUrl", String.class, String.class, List.class);
        method.setAccessible(true);

        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        method.invoke(scanner, "file:" + jarFile.getAbsolutePath() + "!/db/migration", "db/migration", migrations);

        assertEquals(1, migrations.size());
        assertEquals("V8__jar_url.sql", migrations.get(0).getScript());
    }

    /**
     * 验证私有 handleBootInfNestedJarUrl 方法会将 BOOT-INF/classes URL 路由到嵌套扫描逻辑。
     */
    @Test
    public void testPrivateHandleBootInfNestedJarUrlReadsNestedEntries() throws Exception {
        File jarFile = createJar("BOOT-INF/classes/db/migration/V9__boot.sql", "SELECT 9;");
        SqlScanner scanner = new SqlScanner("classpath:db/migration");
        Method method = SqlScanner.class.getDeclaredMethod(
                "handleBootInfNestedJarUrl", String.class, String.class, List.class);
        method.setAccessible(true);

        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        method.invoke(
                scanner,
                "jar:file:" + jarFile.getAbsolutePath() + "!/BOOT-INF/classes!/db/migration",
                "db/migration",
                migrations);

        assertEquals(1, migrations.size());
        assertEquals("V9__boot.sql", migrations.get(0).getScript());
    }

    /**
     * 验证私有 handleNestedJarUrl 方法可以解析 nested URL，并定位到指定目录中的 SQL 文件。
     */
    @Test
    public void testPrivateHandleNestedJarUrlReadsNestedEntries() throws Exception {
        File jarFile = createJar("BOOT-INF/classes/db/migration/V10__nested_url.sql", "SELECT 10;");
        SqlScanner scanner = new SqlScanner("classpath:db/migration");
        Method method = SqlScanner.class.getDeclaredMethod("handleNestedJarUrl", String.class, String.class, List.class);
        method.setAccessible(true);

        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        method.invoke(
                scanner,
                "nested:" + jarFile.getAbsolutePath() + "!/BOOT-INF/classes!/db/migration",
                "db/migration",
                migrations);

        assertEquals(1, migrations.size());
        assertEquals("V10__nested_url.sql", migrations.get(0).getScript());
    }

    /**
     * 验证非法 nested URL 不会抛出异常，也不会产生误识别的迁移记录。
     */
    @Test
    public void testPrivateHandleNestedJarUrlIgnoresInvalidFormat() throws Exception {
        SqlScanner scanner = new SqlScanner("classpath:db/migration");
        Method method = SqlScanner.class.getDeclaredMethod("handleNestedJarUrl", String.class, String.class, List.class);
        method.setAccessible(true);

        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        method.invoke(scanner, "nested-invalid-format", "db/migration", migrations);

        assertTrue(migrations.isEmpty());
    }

    /**
     * 替换线程上下文类加载器，用于构造自定义 classpath 场景。
     */
    private void withContextClassLoader(ClassLoader classLoader) {
        originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    /**
     * 创建临时测试 JAR，供普通 JAR 与嵌套 JAR 场景复用。
     */
    private File createJar(String... entries) throws Exception {
        Path jarPath = Files.createTempFile("sql-scanner", ".jar");
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
