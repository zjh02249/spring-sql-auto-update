package com.cbkj.infrastructure.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.regex.Pattern;


/**
 * SQL执行器
 * 负责执行SQL脚本和事务控制
 */
public class SqlExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlExecutor.class);

    private final DataSource dataSource;

    public SqlExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 提取数据库名称（如果SQL语句包含库名.表名格式）
     * 支持格式：
     * - UPDATE db.table SET ...
     * - UPDATE `db`.`table` SET ...
     * - UPDATE `db.table` SET ...
     * - DELETE FROM db.table WHERE ...
     * - INSERT INTO db.table VALUES ...
     *
     * @param sqlStatement SQL语句
     * @return 数据库名称，如果不包含则返回null
     */
    private String extractDatabaseName(String sqlStatement) {
        if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
            return null;
        }

        // 去掉开头的空白，然后尝试匹配多种格式
        String trimmedSql = sqlStatement.trim();

        // 方案1: 匹配 `db`.table 或 `db`.`table` 格式（反引号包裹的数据库名）
        // 支持 UPDATE `db`.table, DELETE FROM `db`.table, INSERT INTO `db`.table
        java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile(
            "^\\s*(UPDATE|DELETE\\s+FROM|INSERT\\s+INTO)\\s+`([^`]+)`\\.",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher1 = pattern1.matcher(trimmedSql);
        if (matcher1.find()) {
            String dbName = matcher1.group(2);
            LOGGER.debug("[SqlExecutor] Extracted database name '{}' from SQL: {}", dbName,
                trimmedSql.substring(0, Math.min(50, trimmedSql.length())));
            return dbName;
        }

        // 方案2: 匹配 db.table 格式（无引号的数据库名）
        // 支持 UPDATE db.table, DELETE FROM db.table, INSERT INTO db.table
        java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
            "^\\s*(UPDATE|DELETE\\s+FROM|INSERT\\s+INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\.",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher2 = pattern2.matcher(trimmedSql);
        if (matcher2.find()) {
            String dbName = matcher2.group(2);
            LOGGER.debug("[SqlExecutor] Extracted database name '{}' from SQL: {}", dbName,
                trimmedSql.substring(0, Math.min(50, trimmedSql.length())));
            return dbName;
        }

        return null;
    }

    /**
     * 切换数据库（如果可能）
     *
     * @param connection 数据库连接
     * @param databaseName 目标数据库名
     * @return 是否切换成功
     */
    private boolean switchDatabase(Connection connection, String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return false;
        }

        try {
            // 检测数据库类型
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            String dbNameLower = databaseProductName != null ? databaseProductName.toLowerCase() : "";
            
            // 达梦数据库使用 SET SCHEMA 语法
            // 使用 SAVEPOINT 机制来隔离可能失败的 SET SCHEMA 命令
            if (dbNameLower.contains("dm") || dbNameLower.contains("达梦")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SAVEPOINT before_schema_switch");
                    try {
                        String useSql = "SET SCHEMA " + databaseName;
                        stmt.execute(useSql);
                        stmt.execute("RELEASE SAVEPOINT before_schema_switch");
                        LOGGER.debug("[SqlExecutor] Successfully switched to DM schema: {}", databaseName);
                        return true;
                    } catch (SQLException e) {
                        try {
                            stmt.execute("ROLLBACK TO SAVEPOINT before_schema_switch");
                        } catch (SQLException ex) {
                            LOGGER.warn("[SqlExecutor] Failed to rollback to savepoint: {}", ex.getMessage());
                        }
                        LOGGER.debug("[SqlExecutor] Failed to switch to DM schema '{}': {}. Will try to execute SQL as-is.",
                            databaseName, e.getMessage());
                        return false;
                    }
                }
            }
            
            // PostgreSQL 使用 SET SCHEMA 语法
            // 使用 SAVEPOINT 机制来隔离可能失败的 SET SCHEMA 命令
            // 避免事务进入 aborted 状态导致后续 SQL 执行失败
            if (dbNameLower.contains("postgres") || dbNameLower.contains("pgsql")) {
                try (Statement stmt = connection.createStatement()) {
                    // 创建 SAVEPOINT 来保护后续的 SET SCHEMA 操作
                    stmt.execute("SAVEPOINT before_schema_switch");
                    try {
                        String useSql = "SET SCHEMA " + databaseName;
                        stmt.execute(useSql);
                        // 释放 SAVEPOINT，提交这个操作
                        stmt.execute("RELEASE SAVEPOINT before_schema_switch");
                        LOGGER.debug("[SqlExecutor] Successfully switched to PostgreSQL schema: {}", databaseName);
                        return true;
                    } catch (SQLException e) {
                        // SET SCHEMA 失败，回滚到 SAVEPOINT 来恢复事务状态
                        // 这样可以避免事务进入 aborted 状态
                        try {
                            stmt.execute("ROLLBACK TO SAVEPOINT before_schema_switch");
                            LOGGER.debug("[SqlExecutor] Rolled back failed SAVEPOINT for schema switch");
                        } catch (SQLException ex) {
                            LOGGER.warn("[SqlExecutor] Failed to rollback to savepoint: {}", ex.getMessage());
                        }
                        LOGGER.debug("[SqlExecutor] Failed to switch to PostgreSQL schema '{}': {}. Will try to execute SQL as-is.",
                            databaseName, e.getMessage());
                        return false;
                    }
                }
            }
            
            // Oracle 使用 ALTER SESSION SET CURRENT_SCHEMA 语法
            // 使用 SAVEPOINT 机制来隔离可能失败的命令
            if (dbNameLower.contains("oracle")) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SAVEPOINT before_schema_switch");
                    try {
                        String useSql = "ALTER SESSION SET CURRENT_SCHEMA = " + databaseName;
                        stmt.execute(useSql);
                        stmt.execute("RELEASE SAVEPOINT before_schema_switch");
                        LOGGER.debug("[SqlExecutor] Successfully switched to Oracle schema: {}", databaseName);
                        return true;
                    } catch (SQLException e) {
                        try {
                            stmt.execute("ROLLBACK TO SAVEPOINT before_schema_switch");
                        } catch (SQLException ex) {
                            LOGGER.warn("[SqlExecutor] Failed to rollback to savepoint: {}", ex.getMessage());
                        }
                        LOGGER.debug("[SqlExecutor] Failed to switch to Oracle schema '{}': {}. Will try to execute SQL as-is.",
                            databaseName, e.getMessage());
                        return false;
                    }
                }
            }
            
            // MySQL 等数据库使用 USE 语法
            try (Statement stmt = connection.createStatement()) {
                String useSql = "USE " + databaseName;
                stmt.execute(useSql);
                LOGGER.debug("[SqlExecutor] Successfully switched to database: {}", databaseName);
                return true;
            }
        } catch (SQLException e) {
            // 切换失败，记录 debug 日志但继续执行
            LOGGER.debug("[SqlExecutor] Failed to switch to database '{}': {}. Will try to execute SQL as-is.",
                databaseName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取默认数据库/Schema（根据数据库类型选择正确的方式）
     * - MySQL: 使用 getCatalog()
     * - PostgreSQL/Oracle/达梦: 使用 getSchema()
     *
     * @param connection 数据库连接
     * @return 默认数据库或Schema名称
     */
    private String getDefaultDatabaseOrSchema(Connection connection) {
        try {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            String dbNameLower = databaseProductName != null ? databaseProductName.toLowerCase() : "";
            
            // 判断是否为需要使用 Schema 的数据库类型
            boolean useSchema = dbNameLower.contains("postgres") 
                    || dbNameLower.contains("pgsql") 
                    || dbNameLower.contains("oracle")
                    || dbNameLower.contains("dm")
                    || dbNameLower.contains("达梦");
            
            if (useSchema) {
                String schema = connection.getSchema();
                LOGGER.debug("[SqlExecutor] Using getSchema() for database: {}, schema: {}", databaseProductName, schema);
                return schema;
            } else {
                String catalog = connection.getCatalog();
                LOGGER.debug("[SqlExecutor] Using getCatalog() for database: {}, catalog: {}", databaseProductName, catalog);
                return catalog;
            }
        } catch (SQLException e) {
            LOGGER.warn("[SqlExecutor] Failed to get default database/schema: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在事务中执行SQL脚本
     *
     * @param sqlContent SQL脚本内容
     * @param scriptName 脚本名称（用于日志）
     * @return 执行耗时（毫秒）
     * @throws Exception 执行失败时抛出异常
     */
    public long executeInTransaction(String sqlContent, String scriptName) throws Exception {
        Connection connection = null;
        boolean originalAutoCommit = false;
        long startTime = System.currentTimeMillis();

        try {
            connection = dataSource.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            setManualCommitMode(connection);
            LOGGER.info("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:START] Executing script: {}",
                    scriptName, new java.util.Date(), scriptName);

            // 执行SQL
            executeSql(connection, sqlContent, scriptName);

            // 提交事务
            commitTransaction(connection);

            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.info("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:SUCCESS] Script executed successfully in {}ms",
                    scriptName, new java.util.Date(), executionTime);

            return executionTime;

        } catch (Exception e) {
            // 回滚事务
            if (connection != null) {
                try {
                    rollbackTransaction(connection);
                    LOGGER.error("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:ROLLBACK] Transaction rolled back",
                            scriptName, new java.util.Date());
                } catch (SQLException rollbackEx) {
                    LOGGER.error("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:ROLLBACK_FAILED] Failed to rollback transaction",
                            scriptName, new java.util.Date(), rollbackEx);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.error("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:FAILED] Script execution failed after {}ms: {}",
                    scriptName, new java.util.Date(), executionTime, e.getMessage(), e);

            throw new Exception("SQL execution failed for script: " + scriptName, e);

        } finally {
            // 恢复自动提交设置并关闭连接
            if (connection != null) {
                try {
                    restoreAutoCommitMode(connection, originalAutoCommit);
                } catch (SQLException e) {
                    LOGGER.warn("[SqlExecutor] Failed to restore auto-commit setting", e);
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    LOGGER.warn("[SqlExecutor] Failed to close connection", e);
                }
            }
        }
    }

    /**
     * 智能分割SQL语句
     * 能够正确处理：
     * - 字符串中的分号（单引号、双引号）
     * - 注释中的分号（-- 和 /* * /）
     * - 复杂SQL（CREATE PROCEDURE、CREATE FUNCTION等）
     */
    private String[] splitSqlStatements(String sqlContent) {
        java.util.List<String> statements = new java.util.ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        // PL/SQL block tracking for DECLARE...BEGIN...END blocks
        int plsqlDepth = 0;  // Nesting depth of BEGIN/END blocks
        boolean inDeclareSection = false;  // True between DECLARE and BEGIN
        for (int i = 0; i < sqlContent.length(); i++) {
            char c = sqlContent.charAt(i);
            char nextChar = (i + 1 < sqlContent.length()) ? sqlContent.charAt(i + 1) : '\0';
            
            // 处理块注释 /* */
            if (!inSingleQuote && !inDoubleQuote && !inLineComment) {
                if (!inBlockComment && c == '/' && nextChar == '*') {
                    inBlockComment = true;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++; // 跳过下一个字符
                    continue;
                }
                if (inBlockComment && c == '*' && nextChar == '/') {
                    inBlockComment = false;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++; // 跳过下一个字符
                    continue;
                }
            }
            
            // 处理行注释 --
            if (!inBlockComment && !inSingleQuote && !inDoubleQuote) {
                if (!inLineComment && c == '-' && nextChar == '-') {
                    inLineComment = true;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++; // 跳过下一个字符
                    continue;
                }
                if (inLineComment && c == '\n') {
                    inLineComment = false;
                }
            }
            
            // 处理单引号字符串
            if (!inLineComment && !inBlockComment && !inDoubleQuote) {
                if (c == '\'' && (i == 0 || sqlContent.charAt(i - 1) != '\\')) {
                    inSingleQuote = !inSingleQuote;
                }
            }
            
            // 处理双引号字符串
            if (!inLineComment && !inBlockComment && !inSingleQuote) {
                if (c == '"' && (i == 0 || sqlContent.charAt(i - 1) != '\\')) {
                    inDoubleQuote = !inDoubleQuote;
                }
            }
            
            // 检测 PL/SQL 关键字（仅当不在字符串/注释中时）
            if (!inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment) {
                // 检测 DECLARE 关键字
                String keyword = extractKeywordAt(sqlContent, i);
                if (keyword != null) {
                    if (keyword.equalsIgnoreCase("DECLARE")) {
                        plsqlDepth = 1;
                        inDeclareSection = true;
                    } else if (keyword.equalsIgnoreCase("BEGIN")) {
                        if (inDeclareSection) {
                            // DECLARE 后的 BEGIN，不增加深度
                            inDeclareSection = false;
                        } else {
                            // 独立的 BEGIN，增加深度
                            plsqlDepth++;
                        }
                    } else if (keyword.equalsIgnoreCase("END")) {
                        // 检查是否是块结束符（END;）而不是 END IF/LOOP
                        if (isEndOfBlock(sqlContent, i + keyword.length())) {
                            plsqlDepth--;
                            if (plsqlDepth < 0) plsqlDepth = 0;
                        }
                    }
                }
            }
            
            // 处理语句分割（分号）
            // 处理语句分割（分号）
            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment && plsqlDepth == 0) {
                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                currentStatement = new StringBuilder();
                continue;
            }
            
            currentStatement.append(c);
        }
        
        // 处理最后一条语句（可能没有分号结尾）
        String lastStatement = currentStatement.toString().trim();
        if (!lastStatement.isEmpty()) {
            statements.add(lastStatement);
        }
        
        return statements.toArray(new String[0]);
    }
    
    /**
     * 从指定位置提取 SQL 关键字（大小写不敏感）
     * 确保在单词边界处匹配，避免将 DECLARE_VAR 误识别为 DECLARE
     *
     * @param sqlContent SQL 内容
     * @param index 起始位置
     * @return 关键字（如果匹配），否则返回 null
     */
    private String extractKeywordAt(String sqlContent, int index) {
        if (index < 0 || index >= sqlContent.length()) {
            return null;
        }
        
        char c = sqlContent.charAt(index);
        // 必须是字母开头
        if (!Character.isLetter(c)) {
            return null;
        }
        
        // 检查前面是否是非单词字符（单词边界）
        if (index > 0) {
            char prev = sqlContent.charAt(index - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_') {
                return null;
            }
        }
        
        // 提取关键字
        StringBuilder keyword = new StringBuilder();
        int i = index;
        while (i < sqlContent.length() && (Character.isLetter(sqlContent.charAt(i)) || sqlContent.charAt(i) == '_')) {
            keyword.append(sqlContent.charAt(i));
            i++;
        }
        
        String result = keyword.toString();
        
        // 只返回特定关键字
        if (result.equalsIgnoreCase("DECLARE") || result.equalsIgnoreCase("BEGIN") || result.equalsIgnoreCase("END")) {
            return result;
        }
        
        return null;
    }
    
    /**
     * 检查 END 关键字后面是否紧跟着分号（表示块结束符）
     * 而不是 IF/LOOP 等控制结构
     *
     * @param sqlContent SQL 内容
     * @param endIndex END 关键字之后的位置
     * @return true 如果是块结束符，false 如果是 END IF/LOOP 等结构
     */
    private boolean isEndOfBlock(String sqlContent, int endIndex) {
        int i = endIndex;
        // 跳过空白字符
        while (i < sqlContent.length() && Character.isWhitespace(sqlContent.charAt(i))) {
            i++;
        }
        
        if (i >= sqlContent.length()) {
            // END 在文件末尾，假设是块结束
            return true;
        }
        
        char nextChar = sqlContent.charAt(i);
        if (nextChar == ';') {
            return true; // END; 是块结束符
        }
        
        // 检查是否是 END IF 或 END LOOP 等
        if (Character.isLetter(nextChar)) {
            // 提取下一个单词
            StringBuilder word = new StringBuilder();
            while (i < sqlContent.length() && Character.isLetter(sqlContent.charAt(i))) {
                word.append(sqlContent.charAt(i));
                i++;
            }
            
            String nextWord = word.toString();
            // IF, LOOP, CASE 等表示这不是块结束符
            if (nextWord.equalsIgnoreCase("IF") || nextWord.equalsIgnoreCase("LOOP") || 
                nextWord.equalsIgnoreCase("CASE") || nextWord.equalsIgnoreCase("WHILE")) {
                return false;
            }
        }
        
        return true;
    }

    /**
    }

    /**
     * 执行SQL内容
     * 在执行每条SQL语句前，会先切换回默认数据库，然后检查是否需要切换到指定数据库
     * 执行完成后立即切换回默认数据库，确保不影响后续操作
     */
    private void executeSql(Connection connection, String sqlContent, String scriptName) throws SQLException {
        // 获取默认数据库/Schema（根据数据库类型选择正确的方式）
        String defaultDatabase = getDefaultDatabaseOrSchema(connection);
        LOGGER.info("[SqlExecutor] [PATH:{}] Default database/schema: {}", scriptName, defaultDatabase);
        
        // 当前使用的数据库，初始为默认数据库
        String currentDatabase = defaultDatabase;
        
        // 使用智能分割算法处理SQL语句
        String[] statements = splitSqlStatements(sqlContent);
        int statementCount = 0;

        for (String statement : statements) {
            String trimmedStatement = statement.trim();
            if (trimmedStatement.isEmpty()) {
                continue;
            }

            statementCount++;
            
            // 提取数据库名（如果SQL中包含库名.表名格式）
            String targetDatabase = extractDatabaseName(trimmedStatement);
            
            // 重要：每次执行SQL前，先切换回默认数据库
            // 这样可以确保没有指定数据库名的SQL使用默认数据库
            if (!java.util.Objects.equals(defaultDatabase, currentDatabase)) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Switching back to default database before executing statement #{}: {}",
                        scriptName, statementCount, defaultDatabase);
                switchDatabase(connection, defaultDatabase);
                currentDatabase = defaultDatabase;
            }
            
            // 如果当前SQL指定了数据库名，切换到该数据库
            if (targetDatabase != null && !targetDatabase.equals(currentDatabase)) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Detected database switch from '{}' to '{}' for statement #{}",
                        scriptName, currentDatabase, targetDatabase, statementCount);
                boolean switched = switchDatabase(connection, targetDatabase);
                if (switched) {
                    currentDatabase = targetDatabase;
                }
            }
            
            try (Statement stmt = connection.createStatement()) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Executing statement #{} on database '{}': {}",
                        scriptName, statementCount, currentDatabase,
                        trimmedStatement.substring(0, Math.min(100, trimmedStatement.length())));
                
                // 检查是否为 DDL 语句（可能导致 MySQL 事务中断）
                String trimmedUpperStmt = trimmedStatement.toUpperCase().trim();
                if (trimmedUpperStmt.startsWith("CREATE") || 
                    trimmedUpperStmt.startsWith("ALTER") || 
                    trimmedUpperStmt.startsWith("DROP") || 
                    trimmedUpperStmt.startsWith("TRUNCATE") || 
                    trimmedUpperStmt.startsWith("RENAME")) {
                    
                    String databaseProductName = connection.getMetaData().getDatabaseProductName();
                    if (databaseProductName != null && databaseProductName.toLowerCase().contains("mysql")) {
                        LOGGER.warn("[SqlExecutor] [PATH:{}][WARNING] DDL command ({}) detected in MySQL which causes implicit commit and prevents rollback - " +
                                "Subsequent statements in this transaction will not be rolled back if they fail. ",
                                scriptName, trimmedStatement.substring(0, Math.min(20, trimmedStatement.length())).replaceAll("[\\r\\n\\s]+", " "));
                    }
                }
                
                stmt.execute(trimmedStatement);
                
                // 重要：执行完成后立即切换回默认数据库
                // 避免影响后续操作或其他组件使用连接
                if (!java.util.Objects.equals(defaultDatabase, currentDatabase)) {
                    LOGGER.debug("[SqlExecutor] [PATH:{}] Switching back to default database after statement #{}: {}",
                            scriptName, statementCount, defaultDatabase);
                    switchDatabase(connection, defaultDatabase);
                    currentDatabase = defaultDatabase;
                }
            } catch (SQLException e) {
                LOGGER.error("[SqlExecutor] [PATH:{}] Statement #{} failed: {}",
                        scriptName, statementCount, trimmedStatement);
                throw e;
            }
        }

        LOGGER.info("[SqlExecutor] [PATH:{}] Executed {} SQL statement(s)", 
                scriptName, statementCount);
    }

    /**
     * 获取数据库连接（用于非事务操作）
     */

    /**
     * 设置事务为手动提交模式（使用标准JDBC方法）
     * 
     * @param connection 数据库链接
     * @throws SQLException SQL异常
     */
    private void setManualCommitMode(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
    }

    /**
     * 提交事务
     * 
     * @param connection 数据库链接
     * @throws SQLException SQL异常
     */
    private void commitTransaction(Connection connection) throws SQLException {
        connection.commit();
        LOGGER.debug("[SqlExecutor] Committed transaction for {}", connection.getMetaData().getDatabaseProductName());
    }

    /**
     * 回滚事务
     * 
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    private void rollbackTransaction(Connection connection) throws SQLException {
        connection.rollback();
        LOGGER.debug("[SqlExecutor] Rolled back transaction for {}", connection.getMetaData().getDatabaseProductName());
    }

    /**
     * 恢复原始的自动提交模式（使用标准JDBC方法）
     * 
     * @param connection 数据库链接
     * @param originalAutoCommitMode 原始自动提交模式
     * @throws SQLException SQL异常
     */
    private void restoreAutoCommitMode(Connection connection, boolean originalAutoCommitMode) throws SQLException {
connection.setAutoCommit(originalAutoCommitMode);
}

    /**
     * Checks if SQL statements in the content contain DDL statements that cause implicit commits in MySQL
     * @param sqlContent the SQL content to check
     * @return true if contains DDL statements that are problematic in MySQL, false otherwise
     */
    private boolean containsProblematicDdlStatements(String sqlContent) {
        return DDL_STATEMENT_PATTERN.matcher(sqlContent).find();
    }

    private static final Pattern DDL_STATEMENT_PATTERN = Pattern.compile("^\\s*(CREATE|ALTER|DROP|TRUNCATE|RENAME)\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.COMMENTS);

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
