package com.cbkj.infrastructure.util;

import com.cbkj.infrastructure.model.MigrationVersion;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * VersionComparator 单元测试。
 * 用于验证空值处理、版本比较顺序以及私有构造器约束。
 */
public class VersionComparatorTest {

    /**
     * 验证比较器对 null 输入的排序行为。
     */
    @Test
    public void testCompareHandlesNulls() {
        assertEquals(0, VersionComparator.INSTANCE.compare(null, null));
        assertTrue(VersionComparator.INSTANCE.compare(null, MigrationVersion.parse("1")) < 0);
        assertTrue(VersionComparator.INSTANCE.compare(MigrationVersion.parse("1"), null) > 0);
    }

    /**
     * 验证比较器会按 MigrationVersion 的语义化版本顺序比较。
     */
    @Test
    public void testCompareDelegatesToVersionOrdering() {
        assertTrue(VersionComparator.INSTANCE.compare(
                MigrationVersion.parse("1.0.0"),
                MigrationVersion.parse("1.0.1")) < 0);
        assertTrue(VersionComparator.INSTANCE.compare(
                MigrationVersion.parse("2"),
                MigrationVersion.parse("1.9.9")) > 0);
    }

    /**
     * 验证工具类式的私有构造器仍可被反射创建，且实例行为与单例一致。
     */
    @Test
    public void testConstructorIsPrivate() throws Exception {
        Constructor<VersionComparator> constructor = VersionComparator.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        VersionComparator instance = constructor.newInstance();
        assertEquals(0, instance.compare(MigrationVersion.parse("1"), MigrationVersion.parse("1")));
    }
}
