package com.cbkj.infrastructure.model;

import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * AppliedMigration 单元测试。
 * 用于验证模型对象的属性读写、相等性和字符串输出。
 */
public class AppliedMigrationTest {

    /**
     * 验证默认构造器配合 setter/getter 可以完整保存和读取字段值。
     */
    @Test
    public void testGettersAndSetters() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        AppliedMigration migration = new AppliedMigration();

        migration.setInstalledRank(1);
        migration.setVersion("1.0.0");
        migration.setDescription("init");
        migration.setType("SQL");
        migration.setScript("V1__init.sql");
        migration.setChecksum(123);
        migration.setInstalledBy("sa");
        migration.setInstalledOn(now);
        migration.setExecutionTime(42);
        migration.setSuccess(true);

        assertEquals(1, migration.getInstalledRank());
        assertEquals("1.0.0", migration.getVersion());
        assertEquals("init", migration.getDescription());
        assertEquals("SQL", migration.getType());
        assertEquals("V1__init.sql", migration.getScript());
        assertEquals(Integer.valueOf(123), migration.getChecksum());
        assertEquals("sa", migration.getInstalledBy());
        assertEquals(now, migration.getInstalledOn());
        assertEquals(42, migration.getExecutionTime());
        assertTrue(migration.isSuccess());
    }

    /**
     * 验证全参构造器、equals/hashCode 和 toString 的核心行为。
     */
    @Test
    public void testAllArgsConstructorEqualsHashCodeAndToString() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        AppliedMigration left = new AppliedMigration(1, "1.0.0", "init", "SQL",
                "V1__init.sql", 123, "sa", now, 10, true);
        AppliedMigration sameIdentity = new AppliedMigration(1, "1.0.0", "other", "SQL",
                "V1__other.sql", 999, "tester", now, 99, false);
        AppliedMigration different = new AppliedMigration(2, "2.0.0", "next", "SQL",
                "V2__next.sql", 222, "sa", now, 11, true);

        assertEquals(left, sameIdentity);
        assertEquals(left.hashCode(), sameIdentity.hashCode());
        assertNotEquals(left, different);
        assertNotEquals(left, null);
        assertNotEquals(left, "migration");
        assertTrue(left.toString().contains("installedRank=1"));
        assertTrue(left.toString().contains("version='1.0.0'"));
        assertTrue(left.toString().contains("success=true"));
    }
}
