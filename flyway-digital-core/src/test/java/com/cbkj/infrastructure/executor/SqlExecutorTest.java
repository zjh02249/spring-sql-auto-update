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
        // 使用反射访问私有方法 extractNamespace
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("extractNamespace", String.class);
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

    /**
     * 风险验证：当提取到非法 namespace（如包含中划线）时，会在执行前抛出 IllegalArgumentException
     */
    @Test
    public void testExecuteInTransactionFailsFastOnInvalidNamespaceIdentifier() throws Exception {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test_invalid_namespace;MODE=MYSQL;DB_CLOSE_DELAY=-1");

        try (java.sql.Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS PUBLIC");
            stmt.execute("CREATE TABLE IF NOT EXISTS PUBLIC.users (id INT PRIMARY KEY)");
            stmt.execute("MERGE INTO PUBLIC.users KEY(id) VALUES(1)");
        }

        SqlExecutor executor = new SqlExecutor(ds);

        Exception thrown = null;
        try {
            executor.executeInTransaction("UPDATE `my-db`.users SET id = 1 WHERE id = 1;", "risk-invalid-namespace.sql");
        } catch (Exception e) {
            thrown = e;
        }

        assertNotNull("当 namespace 标识符非法时应抛出异常", thrown);
        assertNotNull("应包含包装后的根因异常", thrown.getCause());
        assertTrue("根因异常应为 IllegalArgumentException",
                thrown.getCause() instanceof IllegalArgumentException);
        assertTrue("错误信息应包含非法数据库/schema 名称提示",
                thrown.getCause().getMessage().contains("Invalid database/schema name"));
    }

    /**
     * 风险验证：当 namespace 合法时，不应因 namespace 校验导致失败（H2 上按原 SQL 执行）
     */
    @Test
    public void testExecuteInTransactionSucceedsOnValidNamespaceIdentifier() throws Exception {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test_valid_namespace;MODE=MYSQL;DB_CLOSE_DELAY=-1");

        try (java.sql.Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS mydb");
            stmt.execute("CREATE TABLE IF NOT EXISTS mydb.users (id INT PRIMARY KEY)");
            stmt.execute("MERGE INTO mydb.users KEY(id) VALUES(1)");
        }

        SqlExecutor executor = new SqlExecutor(ds);
        long elapsed = executor.executeInTransaction(
                "UPDATE mydb.users SET id = 2 WHERE id = 1;",
                "risk-valid-namespace.sql");

        assertTrue("执行耗时应为非负数", elapsed >= 0L);

        try (java.sql.Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM mydb.users WHERE id = 2")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
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

    // ==================== 达梦数据库事务相关测试 ====================
    

    @Test
    public void testSetManualCommitModeMethodExists() throws Exception {
        // 验证该方法存在且可被访问
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("setManualCommitMode", java.sql.Connection.class);
        assertNotNull("应存在 setManualCommitMode 方法", method);
    }
    
    @Test
    public void testCommitTransactionMethodExists() throws Exception {
        // 验证该方法存在且可被访问
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("commitTransaction", java.sql.Connection.class);
        assertNotNull("应存在 commitTransaction 方法", method);
    }
    
    @Test
    public void testRollbackTransactionMethodExists() throws Exception {
        // 验证该方法存在且可被访问
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);
        
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("rollbackTransaction", java.sql.Connection.class);
        assertNotNull("应存在 rollbackTransaction 方法", method);
    }
    
    @Test
    public void testRestoreAutoCommitModeMethodExists() throws Exception {
        // 验证该方法存在且可被访问
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;MODE=MYSQL");
        SqlExecutor executor = new SqlExecutor(ds);

        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("restoreAutoCommitMode", java.sql.Connection.class, boolean.class);
        assertNotNull("应存在 restoreAutoCommitMode 方法", method);
    }

    // ==================== DELIMITER 支持测试用例 ====================

    /**
     * 测试 isAtLineStart 方法 - 行首检测
     */
    @Test
    public void testIsAtLineStart() throws Exception {
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("isAtLineStart", String.class, int.class);
        method.setAccessible(true);

        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;");
        SqlExecutor executor = new SqlExecutor(ds);

        // 测试绝对行首（index=0）
        assertTrue("index 0 应为行首", (Boolean) method.invoke(executor, "DELIMITER ;;", 0));

        // 测试换行后的行首
        assertTrue("换行后应为行首", (Boolean) method.invoke(executor, "\nDELIMITER ;;", 1));

        // 测试非行首（中间位置）
        assertFalse("中间位置不应为行首", (Boolean) method.invoke(executor, "DELIMITER ;;", 5));

        // 测试空白后的行首
        assertTrue("空白后应为行首", (Boolean) method.invoke(executor, "   DELIMITER ;;", 3));
    }

    /**
     * 测试 extractDelimiterCommand 方法 - DELIMITER 命令提取
     */
    @Test
    public void testExtractDelimiterCommand() throws Exception {
        java.lang.reflect.Method method = SqlExecutor.class.getDeclaredMethod("extractDelimiterCommand", String.class, int.class);
        method.setAccessible(true);

        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;");
        SqlExecutor executor = new SqlExecutor(ds);

        // 测试 DELIMITER ;;
        String result1 = (String) method.invoke(executor, "DELIMITER ;;", 0);
        assertEquals(";;", result1);

        // 测试 DELIMITER $$ (MySQL 常见)
        String result2 = (String) method.invoke(executor, "DELIMITER $$", 0);
        assertEquals("$$", result2);

        // 测试 DELIMITER ; (恢复默认)
        String result3 = (String) method.invoke(executor, "DELIMITER ;", 0);
        assertEquals(";", result3);

        // 测试非 DELIMITER 语句（返回 null）
        String result4 = (String) method.invoke(executor, "SELECT * FROM t", 0);
        assertNull(result4);

        // 测试大小写不敏感
        String result5 = (String) method.invoke(executor, "delimiter ;;", 0);
        assertEquals(";;", result5);
    }

    // ==================== SQL 标准引号转义测试用例 ====================

    /**
     * 测试 SQL 标准引号转义 - 双单引号表示单引号字符
     */
    @Test
    public void testSqlStandardQuoteEscape() throws Exception {
        // 测试 It's a test
        String sql1 = "INSERT INTO t (name) VALUES ('It''s a test');";
        String[] result1 = splitSqlStatements(sql1);
        assertEquals("应返回 1 条语句", 1, result1.length);
        assertTrue("应包含转义引号", result1[0].contains("'It''s a test'"));

        // 测试 O'Reilly's book
        String sql2 = "INSERT INTO t (msg) VALUES ('O''Reilly''s book');";
        String[] result2 = splitSqlStatements(sql2);
        assertEquals("应返回 1 条语句", 1, result2.length);
        assertTrue("应包含多重转义", result2[0].contains("'O''Reilly''s book'"));

        // 测试混合场景 - 普通语句 + 转义引号
        String sql3 = "SELECT * FROM t; INSERT INTO t (name) VALUES ('It''s test'); DELETE FROM t;";
        String[] result3 = splitSqlStatements(sql3);
        assertEquals("应返回 3 条语句", 3, result3.length);
        assertTrue("第二条应包含转义", result3[1].contains("'It''s test'"));
    }

    // ==================== DELIMITER 分割测试用例 ====================

    /**
     * 测试 MySQL 存储过程定义 - DELIMITER 语法
     */
    @Test
    public void testDelimiterProcedure() throws Exception {
        String sql = "DELIMITER ;;\n" +
                "CREATE PROCEDURE my_proc()\n" +
                "BEGIN\n" +
                "  SELECT * FROM users;\n" +
                "  INSERT INTO logs VALUES (1);\n" +
                "END;;\n" +
                "DELIMITER ;";

        String[] result = splitSqlStatements(sql);

        // Debug: print results
        System.out.println("[DEBUG] Result count: " + result.length);
        for (int i = 0; i < result.length; i++) {
            String stmt = result[i];
            String preview = stmt.length() > 50 ? stmt.substring(0, 50) + "..." : stmt;
            System.out.println("[DEBUG] Statement " + i + " (len=" + stmt.length() + "): [" + preview + "]");
        }

        // DELIMITER 语句作为独立语句（执行时会被过滤）
        assertEquals("应返回 3 条语句（DELIMITER, CREATE PROCEDURE, DELIMITER）", 3, result.length);
        assertTrue("第一条应为 DELIMITER", result[0].toUpperCase().startsWith("DELIMITER"));
        assertTrue("第二条应为 CREATE PROCEDURE", result[1].toUpperCase().contains("CREATE PROCEDURE"));
        assertTrue("CREATE PROCEDURE 应完整", result[1].contains("BEGIN") && result[1].contains("END"));
        assertTrue("第三条应为 DELIMITER", result[2].toUpperCase().startsWith("DELIMITER"));
    }

    /**
     * 测试 MySQL 触发器定义 - DELIMITER 语法
     */
    @Test
    public void testDelimiterTrigger() throws Exception {
        String sql = "DELIMITER $$\n" +
                "CREATE TRIGGER my_trigger\n" +
                "BEFORE INSERT ON t\n" +
                "FOR EACH ROW\n" +
                "BEGIN\n" +
                "  INSERT INTO logs VALUES (NEW.id);\n" +
                "END$$\n" +
                "DELIMITER ;";

        String[] result = splitSqlStatements(sql);

        assertEquals("应返回 3 条语句", 3, result.length);
        assertTrue("第二条应为 CREATE TRIGGER", result[1].toUpperCase().contains("CREATE TRIGGER"));
        assertTrue("CREATE TRIGGER 应完整", result[1].contains("BEGIN") && result[1].contains("END"));
    }

    /**
     * 测试 DELIMITER 恢复后继续正常分割
     */
    @Test
    public void testDelimiterRestore() throws Exception {
        String sql = "DELIMITER ;;\n" +
                "CREATE PROCEDURE p1() BEGIN SELECT 1; END;;\n" +
                "DELIMITER ;\n" +
                "SELECT * FROM users;\n" +
                "INSERT INTO logs VALUES (1);";

        String[] result = splitSqlStatements(sql);

        assertEquals("应返回 5 条语句", 5, result.length);
        assertTrue("第四条应为 SELECT", result[3].toUpperCase().startsWith("SELECT"));
        assertTrue("第五条应为 INSERT", result[4].toUpperCase().startsWith("INSERT"));
    }


}
