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

    /**
     * 测试 11：提取SQL中的数据库名（库名.表名格式）
     *
     * 测试场景：
     * - UPDATE db.table SET ...
     * - DELETE FROM db.table WHERE ...
     * - INSERT INTO db.table VALUES ...
     * - 带反引号的表名：`db`.`table`
     * - 不带库名的语句（应返回null）
     */
    @Test
    public void testExtractDatabaseName() throws Exception {
        // 使用反射访问私有方法 extractDatabaseName
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("extractDatabaseName", String.class);
        method.setAccessible(true);

        // 创建 SqlExecutor 实例
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        SqlExecutor executor = new SqlExecutor(ds);

        // 测试 UPDATE 语句
        String sql1 = "UPDATE cbkj_web_parameter.sys_admin_menu SET menu_name = 'test' WHERE id = 1";
        String result1 = (String) method.invoke(executor, sql1);
        assertEquals("cbkj_web_parameter", result1);

        // 测试 DELETE 语句
        String sql2 = "DELETE FROM mydb.users WHERE id = 1";
        String result2 = (String) method.invoke(executor, sql2);
        assertEquals("mydb", result2);

        // 测试 INSERT 语句
        String sql3 = "INSERT INTO testdb.orders (id, name) VALUES (1, 'test')";
        String result3 = (String) method.invoke(executor, sql3);
        assertEquals("testdb", result3);

        // 测试带反引号的表名
        String sql4 = "UPDATE `mydb`.`table1` SET col = 1";
        String result4 = (String) method.invoke(executor, sql4);
        assertEquals("mydb", result4);

        // 测试不带库名的语句（应返回null）
        String sql5 = "SELECT * FROM users WHERE id = 1";
        String result5 = (String) method.invoke(executor, sql5);
        assertNull(result5);
    }

    // ==================== PL/SQL 块测试用例 ====================

    /**
     * 测试 12：基本的 DECLARE...BEGIN...END 块
     */
    @Test
    public void testDeclareBeginEndBlock() throws Exception {
        String sql = "DECLARE V_CNT INT; BEGIN SELECT 1 INTO V_CNT FROM dual; END;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果（PL/SQL 块不应被分号分割）", 1, result.length);
        assertTrue("应包含完整的 PL/SQL 块", result[0].contains("DECLARE"));
        assertTrue("应包含完整的 PL/SQL 块", result[0].contains("BEGIN"));
        assertTrue("应包含完整的 PL/SQL 块", result[0].contains("END"));
    }

    /**
     * 测试 13：完整的达梦数据库场景（用户提供的实际 SQL）
     */
    @Test
    public void testDmDeclareBlockFullScenario() throws Exception {
        String sql = "DECLARE\n" +
            "  V_CNT INT;\n" +
            "BEGIN\n" +
            "SELECT COUNT(*) INTO V_CNT\n" +
            "FROM ALL_TABLES\n" +
            "WHERE TABLE_NAME = 'T_PERSONAL_PRESCRIPTION_DEPT_MAPPING'\n" +
            "  AND OWNER = 'CBKJ_WEB_API';\n" +
            "\n" +
            "IF V_CNT = 0 THEN\n" +
            "EXECUTE IMMEDIATE '\n" +
            "      CREATE TABLE CBKJ_WEB_API.T_PERSONAL_PRESCRIPTION_DEPT_MAPPING\n" +
            "      (\n" +
            "        ID INTEGER IDENTITY(1,1) NOT NULL,\n" +
            "        APP_ID VARCHAR(32) NOT NULL,\n" +
            "        DEPT_ID_ONE VARCHAR(32) NOT NULL,\n" +
            "        DEPT_ID_TWO VARCHAR(32) NOT NULL,\n" +
            "        CREATE_TIME TIMESTAMP NOT NULL,\n" +
            "        CREATE_USER_NAME VARCHAR(256),\n" +
            "        CREATE_USER_ID VARCHAR(32),\n" +
            "        CONSTRAINT PK_T_P_P_D_M PRIMARY KEY (ID)\n" +
            "      )';\n" +
            "END IF;\n" +
            "END;";
        
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果（达梦 PL/SQL 块不应被分号分割）", 1, result.length);
        assertTrue("应包含 DECLARE", result[0].contains("DECLARE"));
        assertTrue("应包含 BEGIN", result[0].contains("BEGIN"));
        assertTrue("应包含 CREATE TABLE", result[0].contains("CREATE TABLE"));
        assertTrue("应包含 END 关键字", result[0].contains("END"));
    }

    /**
     * 测试 14：混合普通 SQL 和 DECLARE 块
     */
    @Test
    public void testMixedNormalSqlAndDeclareBlock() throws Exception {
        String sql = "CREATE TABLE test (id INT);" +
            "DECLARE v_num INT; BEGIN v_num := 10; END;" +
            "INSERT INTO test VALUES (1);";
        
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 3 个结果", 3, result.length);
        assertTrue("第一个是 CREATE TABLE", result[0].contains("CREATE TABLE"));
        assertTrue("第二个是 DECLARE 块", result[1].contains("DECLARE"));
        assertTrue("第三个是 INSERT", result[2].contains("INSERT"));
    }

    /**
     * 测试 15：不带 DECLARE 的独立 BEGIN...END 块
     */
    @Test
    public void testBeginEndBlockWithoutDeclare() throws Exception {
        String sql = "BEGIN SELECT 1; END;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果", 1, result.length);
        assertTrue("应包含完整的 BEGIN...END 块", result[0].contains("BEGIN"));
        assertTrue("应包含完整的 BEGIN...END 块", result[0].contains("END"));
    }

    /**
     * 测试 16：嵌套的 BEGIN...END 块
     */
    @Test
    public void testNestedBeginEndBlock() throws Exception {
        String sql = "BEGIN BEGIN DBMS_OUTPUT.PUT_LINE('inner'); END; DBMS_OUTPUT.PUT_LINE('outer'); END;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果（嵌套块不应被分割）", 1, result.length);
        assertTrue("应包含完整的嵌套结构", result[0].contains("BEGIN"));
        assertTrue("应包含完整的嵌套结构", result[0].contains("END"));
    }

    /**
     * 测试 17：确保包含 DECLARE_/BEGIN_/END_ 的列名不会被误识别为关键字
     */
    @Test
    public void testDeclareColumnNameNotTreatedAsKeyword() throws Exception {
        String sql = "SELECT DECLARE_COLUMN, BEGIN_COL, END_COL FROM my_table;";
        String[] result = splitSqlStatements(sql);
        
        assertEquals("应返回 1 个结果", 1, result.length);
        assertTrue("应包含列名", result[0].contains("DECLARE_COLUMN"));
        assertTrue("应包含列名", result[0].contains("BEGIN_COL"));
        assertTrue("应包含列名", result[0].contains("END_COL"));
    }

    // ==================== Dameng Database Transaction Tests ====================
    
    @Test
    public void testIsDamengDatabaseMethodExists() throws Exception {
        // Verifies that the method exists and can be accessed
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method isDamengMethod = SqlExecutor.class.getDeclaredMethod("isDamengDatabase", java.sql.Connection.class);
        assertNotNull("Method isDamengDatabase should exist", isDamengMethod);
    }
    
    @Test
    public void testSetManualCommitModeMethodExists() throws Exception {
        // Verifies that the method exists and can be accessed
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("setManualCommitMode", java.sql.Connection.class);
        assertNotNull("Method setManualCommitMode should exist", method);
    }
    
    @Test
    public void testCommitTransactionMethodExists() throws Exception {
        // Verifies that the method exists and can be accessed
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("commitTransaction", java.sql.Connection.class);
        assertNotNull("Method commitTransaction should exist", method);
    }
    
    @Test
    public void testRollbackTransactionMethodExists() throws Exception {
        // Verifies that the method exists and can be accessed
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("rollbackTransaction", java.sql.Connection.class);
        assertNotNull("Method rollbackTransaction should exist", method);
    }
    
    @Test
    public void testRestoreAutoCommitModeMethodExists() throws Exception {
        // Verifies that the method exists and can be accessed
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("restoreAutoCommitMode", java.sql.Connection.class, boolean.class);
        assertNotNull("Method restoreAutoCommitMode should exist", method);
    }


}