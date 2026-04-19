package com.cbkj.infrastructure.scanner;

import com.cbkj.infrastructure.model.SqlMigration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SqlScanner 缓存测试。
 * 用于验证重复扫描命中缓存，以及文件系统变更后缓存失效。
 */
public class SqlScannerCacheTest {

    @Before
    public void setUp() {
        SqlScanner.clearCacheForTests();
    }

    @After
    public void tearDown() {
        SqlScanner.clearCacheForTests();
    }

    /**
     * 验证同一文件系统 location 在同 JVM 生命周期内重复扫描时会命中缓存。
     */
    @Test
    public void testScanUsesCacheForStableFileSystemLocation() throws Exception {
        Path dir = Files.createTempDirectory("sql-scanner-cache-hit");
        Files.write(dir.resolve("V2__cache_hit.sql"), "SELECT 2;".getBytes(StandardCharsets.UTF_8));

        SqlScanner scanner = new SqlScanner(dir.toAbsolutePath().toString());
        List<SqlMigration> firstScan = scanner.scan();
        long hitCountBeforeSecondScan = SqlScanner.getCacheHitCountForTests();

        List<SqlMigration> secondScan = scanner.scan();

        assertEquals(1, firstScan.size());
        assertEquals(1, secondScan.size());
        assertEquals(hitCountBeforeSecondScan + 1L, SqlScanner.getCacheHitCountForTests());
        assertEquals(1, SqlScanner.getCacheSizeForTests());
        assertEquals(firstScan.get(0).getChecksum(), secondScan.get(0).getChecksum());
    }

    /**
     * 验证文件系统 SQL 内容变化后会失效缓存并重新读取 checksum。
     */
    @Test
    public void testScanInvalidatesCacheWhenFileContentChanges() throws Exception {
        Path dir = Files.createTempDirectory("sql-scanner-cache-invalidate");
        Path sqlFile = dir.resolve("V3__cache_invalidate.sql");
        Files.write(sqlFile, "SELECT 3;".getBytes(StandardCharsets.UTF_8));

        SqlScanner scanner = new SqlScanner(dir.toAbsolutePath().toString());
        List<SqlMigration> firstScan = scanner.scan();
        long hitCountBeforeChange = SqlScanner.getCacheHitCountForTests();

        Files.write(sqlFile, "SELECT 33;".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(sqlFile, FileTime.fromMillis(System.currentTimeMillis() + 2000L));

        List<SqlMigration> secondScan = scanner.scan();

        assertEquals(1, firstScan.size());
        assertEquals(1, secondScan.size());
        assertEquals(hitCountBeforeChange, SqlScanner.getCacheHitCountForTests());
        assertTrue(firstScan.get(0).getChecksum() != secondScan.get(0).getChecksum());
    }
}