package com.cbkj.infrastructure.executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * MySQL 5.7 真实数据库集成测试
 * 测试 SQL 分割逻辑在 MySQL DELIMITER 语法上的正确性
 */
public class IntegrationTestMySQL {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3307/flyway_test?useSSL=false&serverTimezone=UTC";
    private static final String USER = "flyway";
    private static final String PASSWORD = "flyway123";

    private Connection connection;
    private SqlExecutor executor;
    private Method splitMethod;

    @Before
    public void setUp() throws Exception {
        connection = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);

        // 创建一个简单的 DataSource 用于构造 SqlExecutor
        javax.sql.DataSource dataSource = new javax.sql.DataSource() {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(JDBC_URL, USER, PASSWORD); }
            @Override public Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
            @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getLogger("test"); }
            @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not supported"); }
            @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
            @Override public void setLoginTimeout(int seconds) throws SQLException { }
            @Override public int getLoginTimeout() throws SQLException { return 0; }
            @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException { }
            @Override public java.io.PrintWriter getLogWriter() throws SQLException { return null; }
        };
        executor = new SqlExecutor(dataSource);
        // 使用反射访问 splitSqlStatements 方法
        splitMethod = SqlExecutor.class.getDeclaredMethod("splitSqlStatements", String.class);
        splitMethod.setAccessible(true);
        resetDatabase();
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void resetDatabase() throws Exception {
        Statement stmt = connection.createStatement();

        // 先收集所有要删除的对象名称
        java.util.List<String> tables = new java.util.ArrayList<>();
        ResultSet rs = stmt.executeQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'flyway_test'"
        );
        while (rs.next()) {
            tables.add(rs.getString(1));
        }
        rs.close();

        java.util.List<String> procs = new java.util.ArrayList<>();
        rs = stmt.executeQuery(
            "SELECT ROUTINE_NAME FROM information_schema.routines WHERE ROUTINE_SCHEMA = 'flyway_test'"
        );
        while (rs.next()) {
            procs.add(rs.getString(1));
        }
        rs.close();

        java.util.List<String> triggers = new java.util.ArrayList<>();
        rs = stmt.executeQuery(
            "SELECT TRIGGER_NAME FROM information_schema.triggers WHERE TRIGGER_SCHEMA = 'flyway_test'"
        );
        while (rs.next()) {
            triggers.add(rs.getString(1));
        }
        rs.close();

        // 然后执行删除
        for (String table : tables) {
            stmt.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        for (String proc : procs) {
            stmt.execute("DROP PROCEDURE IF EXISTS `" + proc + "`");
        }
        for (String trigger : triggers) {
            stmt.execute("DROP TRIGGER IF EXISTS `" + trigger + "`");
        }
        stmt.close();
    }

    private String loadSqlScript(String name) throws Exception {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("integration/mysql/" + name);
        if (is == null) {
            throw new IllegalArgumentException("SQL script not found: " + name);
        }
        return new BufferedReader(new InputStreamReader(is))
            .lines().collect(Collectors.joining("\n"));
    }

    private String[] splitSql(String sqlContent) throws Exception {
        return (String[]) splitMethod.invoke(executor, sqlContent);
    }

    private void executeStatements(String[] statements) throws Exception {
        Statement stmt = connection.createStatement();
        System.out.println("[MySQL] Total statements: " + statements.length);
        for (int i = 0; i < statements.length; i++) {
            String statement = statements[i];
            String trimmed = statement.trim();
            System.out.println("[MySQL] Statement " + i + " (len=" + trimmed.length() + "): [" +
                trimmed.substring(0, Math.min(100, trimmed.length())) + "]");
            if (trimmed.isEmpty()) {
                continue;
            }
            // 跳过纯注释语句（整个语句都是注释，以--开头且没有其他SQL）
            // 检查是否整个语句都是注释行
            String[] lines = trimmed.split("\n");
            boolean allComments = true;
            for (String line : lines) {
                String lineTrimmed = line.trim();
                if (!lineTrimmed.isEmpty() && !lineTrimmed.startsWith("--")) {
                    allComments = false;
                    break;
                }
            }
            if (allComments) {
                System.out.println("[MySQL] -> Skipping pure comment statement");
                continue;
            }
            // 跳过 DELIMITER 语句
            if (trimmed.toUpperCase().startsWith("DELIMITER")) {
                System.out.println("[MySQL] -> Skipping DELIMITER statement");
                continue;
            }
            try {
                stmt.execute(trimmed);
                System.out.println("[MySQL] -> Executed successfully");
            } catch (Exception e) {
                System.err.println("[MySQL] -> FAILED: " + e.getMessage());
                throw e;
            }
        }
        stmt.close();
    }

    @Test
    public void testV1_Init() throws Exception {
        String sql = loadSqlScript("V1__init.sql");
        String[] statements = splitSql(sql);
        System.out.println("[MySQL] V1__init.sql split into " + statements.length + " statements");

        // 打印所有分割结果
        for (int i = 0; i < statements.length; i++) {
            System.out.println("[MySQL] === Statement " + i + " ===");
            System.out.println(statements[i]);
            System.out.println("[MySQL] === END ===");
        }

        executeStatements(statements);

        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        stmt.close();
    }

    @Test
    public void testV2_DelimiterProc() throws Exception {
        // 先初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行存储过程定义
        String procSql = loadSqlScript("V2__delimiter_proc.sql");
        String[] statements = splitSql(procSql);
        System.out.println("[MySQL] V2__delimiter_proc.sql split into " + statements.length + " statements");

        // 验证分割正确：应有 3 个 DELIMITER 语句 + 3 个 CREATE PROCEDURE
        // DELIMITER ;;, CREATE PROCEDURE get_user_count..., DELIMITER ;（循环3次）
        assertEquals(9, statements.length);

        executeStatements(statements);

        // 验证存储过程已创建
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT ROUTINE_NAME FROM information_schema.routines " +
            "WHERE ROUTINE_SCHEMA = 'flyway_test' AND ROUTINE_TYPE = 'PROCEDURE'"
        );

        int procCount = 0;
        while (rs.next()) {
            procCount++;
            System.out.println("[MySQL] Created procedure: " + rs.getString(1));
        }
        assertEquals(3, procCount);
        rs.close();
        stmt.close();
    }

    @Test
    public void testV3_DelimiterTrigger() throws Exception {
        // 初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行触发器定义
        String triggerSql = loadSqlScript("V3__delimiter_trigger.sql");
        String[] statements = splitSql(triggerSql);
        System.out.println("[MySQL] V3__delimiter_trigger.sql split into " + statements.length + " statements");

        executeStatements(statements);

        // 验证触发器已创建
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT TRIGGER_NAME FROM information_schema.triggers " +
            "WHERE TRIGGER_SCHEMA = 'flyway_test'"
        );

        int triggerCount = 0;
        while (rs.next()) {
            triggerCount++;
            System.out.println("[MySQL] Created trigger: " + rs.getString(1));
        }
        assertEquals(3, triggerCount);
        rs.close();
        stmt.close();
    }

    @Test
    public void testV4_QuoteEscape() throws Exception {
        // 初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行引号转义测试
        String escapeSql = loadSqlScript("V4__quote_escape.sql");
        String[] statements = splitSql(escapeSql);
        System.out.println("[MySQL] V4__quote_escape.sql split into " + statements.length + " statements");

        // 验证分割正确：每条 INSERT 是一条语句
        assertEquals(8, statements.length);

        executeStatements(statements);

        // 验证数据插入正确（引号转义）
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY id");
        assertTrue(rs.next());
        assertEquals("It's a test", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("O'Reilly's book", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("John's \"Special\" Name", rs.getString(1));
        rs.close();
        stmt.close();
    }

    @Test
    public void testV5_MixedComplex() throws Exception {
        // 初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行混合复杂场景
        String mixedSql = loadSqlScript("V5__mixed_complex.sql");
        String[] statements = splitSql(mixedSql);
        System.out.println("[MySQL] V5__mixed_complex.sql split into " + statements.length + " statements");

        executeStatements(statements);

        // 验证存储过程创建
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT ROUTINE_NAME FROM information_schema.routines " +
            "WHERE ROUTINE_SCHEMA = 'flyway_test' AND ROUTINE_TYPE = 'PROCEDURE'"
        );

        int procCount = 0;
        while (rs.next()) {
            procCount++;
        }
        assertTrue(procCount >= 3);
        rs.close();

        // 验证数据插入
        rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE name = 'Comment test'");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        rs.close();

        stmt.close();
    }

    @Test
    public void testAllScriptsSequential() throws Exception {
        resetDatabase();

        // 按顺序执行所有脚本
        String[] scripts = {
            "V1__init.sql",
            "V2__delimiter_proc.sql",
            "V3__delimiter_trigger.sql",
            "V4__quote_escape.sql",
            "V5__mixed_complex.sql"
        };

        for (String script : scripts) {
            String sql = loadSqlScript(script);
            String[] statements = splitSql(sql);
            System.out.println("[MySQL] " + script + ": " + statements.length + " statements");
            executeStatements(statements);
        }

        // 最终验证
        Statement stmt = connection.createStatement();

        // 检查表数量
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'flyway_test'"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 3);
        rs.close();

        // 检查存储过程数量
        rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM information_schema.routines " +
            "WHERE ROUTINE_SCHEMA = 'flyway_test' AND ROUTINE_TYPE = 'PROCEDURE'"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 6);
        rs.close();

        // 检查触发器数量
        rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM information_schema.triggers WHERE TRIGGER_SCHEMA = 'flyway_test'"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 3);
        rs.close();

        stmt.close();
        System.out.println("[MySQL] All tests passed!");
    }
}