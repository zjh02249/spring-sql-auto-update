package com.cbkj.infrastructure.executor;

import org.junit.Test;
import javax.sql.DataSource;

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
        // 创建 H2 内存数据库 DataSource
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("splitSqlStatements", String.class);
        method.setAccessible(true);
        return (String[]) method.invoke(executor, sqlContent);
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
        assertEquals("第二个语句", "INSERT INTO logs VALUES (1)", result[1]);
        assertEquals("第三个语句", "DELETE FROM temp", result[2]);
    }

    /**
     * 测试 4：字符串中包含分号（单引号）- 核心修复场景
     */
    @Test
    public void testSingleQuoteStringWithSemicolon() throws Exception {
        String sql = "INSERT INTO config (key, value) VALUES ('db.url', 'jdbc:mysql://localhost:3306;user=root');";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果（字符串中的分号不应分割）", 1, result.length);
        assertEquals("应保留完整的字符串", 
            "INSERT INTO config (key, value) VALUES ('db.url', 'jdbc:mysql://localhost:3306;user=root')", 
            result[0]);
    }

    /**
     * 测试 5：字符串中包含分号（双引号）
     */
    @Test
    public void testDoubleQuoteStringWithSemicolon() throws Exception {
        String sql = "INSERT INTO logs (message) VALUES (\"Error;Warning;Info\");";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果", 1, result.length);
        assertEquals("应保留完整的字符串", 
            "INSERT INTO logs (message) VALUES (\"Error;Warning;Info\")", 
            result[0]);
    }

    /**
     * 测试 6：注释中包含分号（行注释）
     */
    @Test
    public void testLineCommentWithSemicolon() throws Exception {
        String sql = "-- This is a comment; with semicolon\nSELECT * FROM users;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果", 1, result.length);
        assertTrue("应包含注释", result[0].contains("-- This is a comment; with semicolon"));
    }

    /**
     * 测试 7：注释中包含分号（块注释）
     */
    @Test
    public void testBlockCommentWithSemicolon() throws Exception {
        String sql = "/* Comment; with; semicolons */ SELECT * FROM users;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果", 1, result.length);
        assertTrue("应包含注释", result[0].contains("/* Comment; with; semicolons */"));
    }

    /**
     * 测试 8：原始问题中的复杂 SQL
     */
    @Test
    public void testComplexSqlFromIssue() throws Exception {
        String sql = 
            "INSERT INTO `workflow_operation_params` (`param_id`, `operation_id`, `param_code`, `param_name`, `param_type`, `default_value`, `required`, `description`) " +
            "VALUES (4, 3, 'sql', 'SQL语句', 'textarea', 'SELECT * FROM table', 1, 'SQL查询语句;支持多行');";
        
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果（description 中的分号不应分割）", 1, result.length);
        assertTrue("应包含完整的 VALUES", result[0].contains("VALUES"));
        assertTrue("应包含 description 字段", result[0].contains("'SQL查询语句;支持多行'"));
    }

    /**
     * 测试 9：混合场景 - 多条语句 + 字符串包含分号
     */
    @Test
    public void testMixedScenario() throws Exception {
        String sql = 
            "INSERT INTO config VALUES ('url', 'http://example.com;param=1');" +
            "SELECT * FROM users;" +
            "INSERT INTO logs VALUES ('message;with;semicolons');";
        
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 3 个结果", 3, result.length);
        assertTrue("第一个语句应包含完整字符串", result[0].contains("'http://example.com;param=1'"));
        assertEquals("第二个语句", "SELECT * FROM users", result[1]);
        assertTrue("第三个语句应包含完整字符串", result[2].contains("'message;with;semicolons'"));
    }

    /**
     * 测试 10：空白和格式化
     */
    @Test
    public void testWhitespaceHandling() throws Exception {
        String sql = "   SELECT * FROM users   ;   \n\n   INSERT INTO logs VALUES (1)   ;   ";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 2 个结果", 2, result.length);
        assertEquals("应去除多余空白", "SELECT * FROM users", result[0]);
        assertEquals("应去除多余空白", "INSERT INTO logs VALUES (1)", result[1]);
    }
} 