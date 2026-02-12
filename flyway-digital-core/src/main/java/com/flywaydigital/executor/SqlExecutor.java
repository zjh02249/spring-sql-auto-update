package com.cbkj.infrastructure.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

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
            connection.setAutoCommit(false);

            LOGGER.info("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:START] Executing script: {}",
                    scriptName, new java.util.Date(), scriptName);

            // 执行SQL
            executeSql(connection, sqlContent, scriptName);

            // 提交事务
            connection.commit();

            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.info("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:SUCCESS] Script executed successfully in {}ms",
                    scriptName, new java.util.Date(), executionTime);

            return executionTime;

        } catch (Exception e) {
            // 回滚事务
            if (connection != null) {
                try {
                    connection.rollback();
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
                    connection.setAutoCommit(originalAutoCommit);
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
            
            // 处理语句分割（分号）
            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment) {
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
     * 执行SQL内容
     */
    private void executeSql(Connection connection, String sqlContent, String scriptName) throws SQLException {
        // 使用智能分割算法处理SQL语句
        String[] statements = splitSqlStatements(sqlContent);
        int statementCount = 0;

        for (String statement : statements) {
            String trimmedStatement = statement.trim();
            if (trimmedStatement.isEmpty()) {
                continue;
            }

            statementCount++;
            
            try (Statement stmt = connection.createStatement()) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Executing statement #{}: {}",
                        scriptName, statementCount, 
                        trimmedStatement.substring(0, Math.min(100, trimmedStatement.length())));
                
                stmt.execute(trimmedStatement);
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
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
