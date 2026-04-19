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
 * PostgreSQL 真实数据库集成测试
 * 测试 SQL 分割逻辑在 PostgreSQL PL/pgSQL 语法上的正确性
 */
public class IntegrationTestPostgreSQL {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/flyway_test";
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
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
        );
        while (rs.next()) {
            tables.add(rs.getString(1));
        }
        rs.close();

        java.util.List<String[]> functions = new java.util.ArrayList<>();
        rs = stmt.executeQuery(
            "SELECT proname, oidvectortypes(proargtypes) as args " +
            "FROM pg_proc WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')"
        );
        while (rs.next()) {
            functions.add(new String[]{rs.getString(1), rs.getString(2)});
        }
        rs.close();

        // 然后执行删除
        for (String table : tables) {
            stmt.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
        }
        for (String[] func : functions) {
            String funcName = func[0];
            String args = func[1];
            if (args == null || args.isEmpty()) {
                stmt.execute("DROP FUNCTION IF EXISTS " + funcName + " CASCADE");
            } else {
                stmt.execute("DROP FUNCTION IF EXISTS " + funcName + "(" + args + ") CASCADE");
            }
        }
        stmt.close();
    }

    private String loadSqlScript(String name) throws Exception {
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("integration/postgresql/" + name);
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
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 跳过纯注释语句（整个语句都是注释行）
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
                System.out.println("[PostgreSQL] Skipping pure comment statement");
                continue;
            }
            // 跳过 DELIMITER 语句（MySQL语法，PG不需要）
            if (trimmed.toUpperCase().startsWith("DELIMITER")) {
                continue;
            }
            try {
                stmt.execute(trimmed);
            } catch (Exception e) {
                System.err.println("Failed to execute: " + trimmed.substring(0, Math.min(50, trimmed.length())));
                throw e;
            }
        }
        stmt.close();
    }

    @Test
    public void testV1_Init() throws Exception {
        String sql = loadSqlScript("V1__init.sql");
        String[] statements = splitSql(sql);
        System.out.println("[PostgreSQL] V1__init.sql split into " + statements.length + " statements");
        executeStatements(statements);

        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1));
        rs.close();
        stmt.close();
    }

    @Test
    public void testV2_Function() throws Exception {
        // 先初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行函数定义
        String funcSql = loadSqlScript("V2__function.sql");
        String[] statements = splitSql(funcSql);
        System.out.println("[PostgreSQL] V2__function.sql split into " + statements.length + " statements");

        // 验证分割正确：每个函数是一条完整语句
        assertEquals(4, statements.length);

        executeStatements(statements);

        // 验证函数已创建
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM pg_proc " +
            "WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public') " +
            "AND proname IN ('get_user_count', 'add_user', 'complex_func', 'get_all_users')"
        );
        assertTrue(rs.next());
        assertEquals(4, rs.getInt(1));
        rs.close();
        stmt.close();
    }

    @Test
    public void testV3_DoBlock() throws Exception {
        // 初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行 DO 匿名块
        String doSql = loadSqlScript("V3__do_block.sql");
        String[] statements = splitSql(doSql);
        System.out.println("[PostgreSQL] V3__do_block.sql split into " + statements.length + " statements");

        // 验证分割正确：每个 DO 块是一条语句
        assertEquals(5, statements.length);

        executeStatements(statements);

        // 验证 DO 块执行结果
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM logs WHERE action LIKE 'DO%'"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 5);
        rs.close();
        stmt.close();
    }

    @Test
    public void testV4_MixedComplex() throws Exception {
        // 初始化表
        String initSql = loadSqlScript("V1__init.sql");
        executeStatements(splitSql(initSql));

        // 执行混合复杂场景
        String mixedSql = loadSqlScript("V4__mixed_complex.sql");
        String[] statements = splitSql(mixedSql);
        System.out.println("[PostgreSQL] V4__mixed_complex.sql split into " + statements.length + " statements");

        executeStatements(statements);

        // 验证函数创建
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM pg_proc " +
            "WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public') " +
            "AND proname IN ('mixed_func', 'complex_mixed')"
        );
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        rs.close();

        // 验证数据插入
        rs = stmt.executeQuery("SELECT COUNT(*) FROM logs WHERE action LIKE 'SEQ%'");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        rs.close();

        stmt.close();
    }

    @Test
    public void testAllScriptsSequential() throws Exception {
        resetDatabase();

        // 按顺序执行所有脚本
        String[] scripts = {
            "V1__init.sql",
            "V2__function.sql",
            "V3__do_block.sql",
            "V4__mixed_complex.sql"
        };

        for (String script : scripts) {
            String sql = loadSqlScript(script);
            String[] statements = splitSql(sql);
            System.out.println("[PostgreSQL] " + script + ": " + statements.length + " statements");
            executeStatements(statements);
        }

        // 最终验证
        Statement stmt = connection.createStatement();

        // 检查表数量
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public'"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 3);
        rs.close();

        // 检查函数数量
        rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM pg_proc " +
            "WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')"
        );
        assertTrue(rs.next());
        assertTrue(rs.getInt(1) >= 6);
        rs.close();

        stmt.close();
        System.out.println("[PostgreSQL] All tests passed!");
    }
}