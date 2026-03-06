package com.cbkj.infrastructure.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SqlMigration 单元测试。
 * 用于验证构造约束、排序规则以及模型对象的基础行为。
 */
public class SqlMigrationTest {

    /**
     * 验证字段访问、版本排序、相等性和字符串输出。
     */
    @Test
    public void testGettersCompareToEqualsHashCodeAndToString() {
        SqlMigration migration = new SqlMigration(
                MigrationVersion.parse("1.0.0"),
                "init",
                "V1__init.sql",
                "CREATE TABLE test(id INT);",
                123,
                "classpath:db/migration");
        SqlMigration sameVersion = new SqlMigration(
                MigrationVersion.parse("1.0.0"),
                "other",
                "V1__other.sql",
                "SELECT 1;",
                456,
                "file:/tmp");
        SqlMigration later = new SqlMigration(
                MigrationVersion.parse("2.0.0"),
                "next",
                "V2__next.sql",
                "SELECT 2;",
                789,
                "classpath:db/migration");

        assertEquals("init", migration.getDescription());
        assertEquals("V1__init.sql", migration.getScript());
        assertEquals("CREATE TABLE test(id INT);", migration.getSqlContent());
        assertEquals(123, migration.getChecksum());
        assertEquals("classpath:db/migration", migration.getLocation());
        assertTrue(migration.compareTo(later) < 0);
        assertEquals(migration, sameVersion);
        assertEquals(migration.hashCode(), sameVersion.hashCode());
        assertNotEquals(migration, later);
        assertNotEquals(migration, null);
        assertNotEquals(migration, "migration");
        assertTrue(migration.toString().contains("version=1.0.0"));
        assertTrue(migration.toString().contains("checksum=123"));
    }

    /**
     * 验证构造器会拒绝关键字段为空的非法输入。
     */
    @Test
    public void testConstructorRejectsNullArguments() {
        assertNullRejected("Version cannot be null", null, "desc", "script", "sql", 1, "loc");
        assertNullRejected("Description cannot be null", MigrationVersion.parse("1"), null, "script", "sql", 1, "loc");
        assertNullRejected("Script cannot be null", MigrationVersion.parse("1"), "desc", null, "sql", 1, "loc");
        assertNullRejected("SqlContent cannot be null", MigrationVersion.parse("1"), "desc", "script", null, 1, "loc");
        assertNullRejected("Location cannot be null", MigrationVersion.parse("1"), "desc", "script", "sql", 1, null);
    }

    /**
     * 统一断言不同参数为空时抛出的异常信息。
     */
    private void assertNullRejected(String message,
                                    MigrationVersion version,
                                    String description,
                                    String script,
                                    String sql,
                                    int checksum,
                                    String location) {
        try {
            new SqlMigration(version, description, script, sql, checksum, location);
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            assertEquals(message, ex.getMessage());
        }
    }
}
