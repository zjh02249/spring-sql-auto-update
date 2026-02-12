package com.cbkj.infrastructure.executor;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SqlExecutor 测试类
 * 
 * 专门测试 SQL 语句分割逻辑，特别是修复字符串中包含分号的 BUG。
 * 
 * 关键测试场景：
 * 1. 字符串中包含分号（如 'a;b;c'）
 * 2. 注释中包含分号
 * 3. 多行字符串
 * 4. 多条语句
 * 5. 原始问题中的复杂 SQL
 * 
 * @author cbkj
 * @since 1.2.2
 */
public class SqlExecutorTest {

    /**
     * 测试工具方法：通过反射调用私有的 splitSqlStatements 方法
     */
    private String[] splitSqlStatements(String sqlContent) throws Exception {
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("splitSqlStatements", String.class);
        method.setAccessible(true);
        return (String[]) method.invoke(null, sqlContent);
    }

    // ==================== 基础测试用例 ====================


    /**
     * 测试 1：单条简单语句（无分号）
     */
    @Test
    public void testSingleStatementWithoutSemicolon() throws Exception {
        String sql = "SELECT * FROM users";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("单条语句应返回 1 个结果", 1, result.length);
        assertEquals("结果应与输入相同", sql, result[0]);
    }

    /**
     * 测试 2：单条语句以分号结尾
     */
    @Test
    public void testSingleStatementWithSemicolon() throws Exception {
        String sql = "SELECT * FROM users;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("单条语句应返回 1 个结果", 1, result.length);
        assertEquals("结果应去除末尾分号", "SELECT * FROM users", result[0]);
    }

    /**
     * 测试 3：多条简单语句
     */
    @Test
    public void testMultipleSimpleStatements() throws Exception {
        String sql = "SELECT * FROM users; INSERT INTO logs VALUES (1); DELETE FROM temp;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 3 个结果", 3, result.length);
        assertEquals("第一个语句", "SELECT * FROM users", result[0]);
        assertEquals("第二个语句", "INSERT INTO logs VALUES (1)", 