package com.cbkj.infrastructure.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL执行器
 * 负责执行SQL脚本和事务控制
 */
public class SqlExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlExecutor.class);

    /**
     * 合法数据库名 / Schema 名标识符
     */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * DDL 语句识别
     */
    private static final Pattern DDL_STATEMENT_PATTERN =
            Pattern.compile("^\\s*(CREATE|ALTER|DROP|TRUNCATE|RENAME)\\s+",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * 支持提取 namespace（数据库/schema）的 SQL 模式
     *
     * 支持：
     * UPDATE db.table
     * UPDATE `db`.table
     * UPDATE `db`.`table`
     * INSERT INTO db.table
     * DELETE FROM db.table
     * REPLACE INTO db.table
     * CREATE TABLE db.table
     * ALTER TABLE db.table
     * DROP TABLE db.table
     * TRUNCATE TABLE db.table
     */
    private static final Pattern[] NAMESPACE_PATTERNS = new Pattern[] {
            Pattern.compile("^\\s*UPDATE\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*UPDATE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*DELETE\\s+FROM\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*DELETE\\s+FROM\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*INSERT\\s+INTO\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*INSERT\\s+INTO\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*REPLACE\\s+INTO\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*REPLACE\\s+INTO\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*CREATE\\s+TABLE\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*CREATE\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*ALTER\\s+TABLE\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*ALTER\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*DROP\\s+TABLE\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*DROP\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE),

            Pattern.compile("^\\s*TRUNCATE\\s+TABLE\\s+`([^`]+)`\\s*\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*TRUNCATE\\s+TABLE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.", Pattern.CASE_INSENSITIVE)
    };

    private final DataSource dataSource;

    public SqlExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 数据库方言
     */
    private enum DatabaseDialect {
        MYSQL,
        POSTGRESQL,
        ORACLE,
        DM,
        UNKNOWN
    }

    /**
     * 提取 namespace（数据库名 / schema）
     *
     * @param sqlStatement SQL语句
     * @return namespace，未识别则返回 null
     */
    private String extractNamespace(String sqlStatement) {
        if (sqlStatement == null || sqlStatement.trim().isEmpty()) {
            return null;
        }

        String trimmedSql = sqlStatement.trim();

        for (Pattern pattern : NAMESPACE_PATTERNS) {
            Matcher matcher = pattern.matcher(trimmedSql);
            if (matcher.find()) {
                String namespace = matcher.group(1);
                LOGGER.debug("[SqlExecutor] Extracted namespace '{}' from SQL: {}",
                        namespace, abbreviateSql(trimmedSql, 80));
                return namespace;
            }
        }

        return null;
    }

    /**
     * 校验数据库名 / schema 名
     */
    private void validateIdentifier(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Database/schema name must not be blank");
        }
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid database/schema name: " + name);
        }
    }

    /**
     * 检测数据库方言
     */
    private DatabaseDialect detectDialect(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData != null ? metaData.getDatabaseProductName() : null;
            String dbNameLower = productName != null ? productName.toLowerCase() : "";

            if (dbNameLower.contains("mysql")) {
                return DatabaseDialect.MYSQL;
            }
            if (dbNameLower.contains("postgres") || dbNameLower.contains("pgsql")) {
                return DatabaseDialect.POSTGRESQL;
            }
            if (dbNameLower.contains("oracle")) {
                return DatabaseDialect.ORACLE;
            }
            if (dbNameLower.contains("dm") || dbNameLower.contains("达梦")) {
                return DatabaseDialect.DM;
            }
            return DatabaseDialect.UNKNOWN;
        } catch (SQLException e) {
            LOGGER.warn("[SqlExecutor] Failed to detect database dialect: {}", e.getMessage());
            return DatabaseDialect.UNKNOWN;
        }
    }

    /**
     * 获取默认 namespace
     * - MySQL: catalog
     * - PostgreSQL/Oracle/达梦: schema
     */
    private String getDefaultNamespace(Connection connection) {
        try {
            DatabaseDialect dialect = detectDialect(connection);
            switch (dialect) {
                case POSTGRESQL:
                case ORACLE:
                case DM:
                    String schema = connection.getSchema();
                    LOGGER.debug("[SqlExecutor] Using getSchema(), default namespace: {}", schema);
                    return schema;
                case MYSQL:
                case UNKNOWN:
                default:
                    String catalog = connection.getCatalog();
                    LOGGER.debug("[SqlExecutor] Using getCatalog(), default namespace: {}", catalog);
                    return catalog;
            }
        } catch (SQLException e) {
            LOGGER.warn("[SqlExecutor] Failed to get default namespace: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 切换执行上下文（数据库 / schema）
     *
     * @param connection 连接
     * @param namespace 目标 namespace
     * @return 是否切换成功
     */
    private boolean switchNamespace(Connection connection, String namespace) throws SQLException {
        if (namespace == null || namespace.trim().isEmpty()) {
            return false;
        }

        validateIdentifier(namespace);

        DatabaseDialect dialect = detectDialect(connection);
        String switchSql = buildSwitchSql(dialect, namespace);

        if (switchSql == null) {
            LOGGER.debug("[SqlExecutor] Dialect '{}' does not support namespace switch, namespace: {}",
                    dialect, namespace);
            return false;
        }

        Savepoint savepoint = null;
        try (Statement stmt = connection.createStatement()) {
            try {
                savepoint = connection.setSavepoint();
            } catch (SQLException e) {
                LOGGER.debug("[SqlExecutor] Failed to create savepoint before namespace switch: {}", e.getMessage());
            }

            stmt.execute(switchSql);

            if (savepoint != null) {
                try {
                    connection.releaseSavepoint(savepoint);
                } catch (SQLException e) {
                    LOGGER.debug("[SqlExecutor] Failed to release savepoint: {}", e.getMessage());
                }
            }

            LOGGER.debug("[SqlExecutor] Successfully switched namespace to '{}' using SQL: {}", namespace, switchSql);
            return true;
        } catch (SQLException e) {
            if (savepoint != null) {
                try {
                    connection.rollback(savepoint);
                    LOGGER.debug("[SqlExecutor] Rolled back failed namespace switch savepoint");
                } catch (SQLException rollbackEx) {
                    LOGGER.warn("[SqlExecutor] Failed to rollback to savepoint: {}", rollbackEx.getMessage());
                }
            }

            LOGGER.debug("[SqlExecutor] Failed to switch namespace '{}' with SQL [{}]: {}. Will try to execute SQL as-is.",
                    namespace, switchSql, e.getMessage());
            return false;
        }
    }

    /**
     * 构造切换 namespace 的 SQL
     */
    private String buildSwitchSql(DatabaseDialect dialect, String namespace) {
        switch (dialect) {
            case MYSQL:
                return "USE " + namespace;
            case POSTGRESQL:
            case DM:
                return "SET SCHEMA " + namespace;
            case ORACLE:
                return "ALTER SESSION SET CURRENT_SCHEMA = " + namespace;
            case UNKNOWN:
            default:
                return null;
        }
    }

    /**
     * 在事务中执行SQL脚本
     *
     * @param sqlContent SQL脚本内容
     * @param scriptName 脚本名称
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

            executeSql(connection, sqlContent, scriptName);

            commitTransaction(connection);

            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.info("[SqlExecutor] [PATH:{}] [TIME:{}] [SQL:SUCCESS] Script executed successfully in {}ms",
                    scriptName, new java.util.Date(), executionTime);

            return executionTime;

        } catch (Exception e) {
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
     * 执行SQL内容
     * 在执行每条SQL语句前，会先恢复到默认 namespace，然后检查是否需要切换到目标 namespace
     * 每条语句执行结束后，无论成功或失败，都会尝试恢复默认 namespace
     */
    private void executeSql(Connection connection, String sqlContent, String scriptName) throws SQLException {
        String defaultNamespace = getDefaultNamespace(connection);
        LOGGER.info("[SqlExecutor] [PATH:{}] Default namespace: {}", scriptName, defaultNamespace);

        String currentNamespace = defaultNamespace;
        String[] statements = splitSqlStatements(sqlContent);
        int statementCount = 0;

        for (String statement : statements) {
            String trimmedStatement = statement.trim();
            if (trimmedStatement.isEmpty()) {
                continue;
            }

            // 跳过 DELIMITER 语句（MySQL 语法，用于存储过程/触发器定义）
            if (trimmedStatement.toUpperCase().startsWith("DELIMITER")) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Skipping DELIMITER statement: {}",
                        scriptName, trimmedStatement);
                continue;
            }

            statementCount++;
            String targetNamespace = extractNamespace(trimmedStatement);
            boolean switchedToTarget = false;

            try {
                if (!Objects.equals(defaultNamespace, currentNamespace) && defaultNamespace != null) {
                    LOGGER.debug("[SqlExecutor] [PATH:{}] Switching back to default namespace before executing statement #{}: {}",
                            scriptName, statementCount, defaultNamespace);
                    boolean restored = switchNamespace(connection, defaultNamespace);
                    if (restored) {
                        currentNamespace = defaultNamespace;
                    }
                }

                if (targetNamespace != null && !Objects.equals(targetNamespace, currentNamespace)) {
                    LOGGER.debug("[SqlExecutor] [PATH:{}] Detected namespace switch from '{}' to '{}' for statement #{}",
                            scriptName, currentNamespace, targetNamespace, statementCount);
                    boolean switched = switchNamespace(connection, targetNamespace);
                    if (switched) {
                        currentNamespace = targetNamespace;
                        switchedToTarget = true;
                    }
                }

                try (Statement stmt = connection.createStatement()) {
                    LOGGER.debug("[SqlExecutor] [PATH:{}] Executing statement #{} on namespace '{}': {}",
                            scriptName,
                            statementCount,
                            currentNamespace,
                            abbreviateSql(trimmedStatement, 100));

                    if (isDdlStatement(trimmedStatement)) {
                        warnIfMySqlDdl(connection, scriptName, trimmedStatement);
                    }

                    stmt.execute(trimmedStatement);
                }

            } catch (SQLException e) {
                LOGGER.error("[SqlExecutor] [PATH:{}] Statement #{} failed: {}",
                        scriptName, statementCount, trimmedStatement, e);
                throw e;

            } finally {
                if (switchedToTarget && defaultNamespace != null && !Objects.equals(defaultNamespace, currentNamespace)) {
                    try {
                        LOGGER.debug("[SqlExecutor] [PATH:{}] Restoring default namespace after statement #{}: {}",
                                scriptName, statementCount, defaultNamespace);
                        boolean restored = switchNamespace(connection, defaultNamespace);
                        if (restored) {
                            currentNamespace = defaultNamespace;
                        }
                    } catch (SQLException restoreEx) {
                        LOGGER.warn("[SqlExecutor] [PATH:{}] Failed to restore default namespace after statement #{}: {}",
                                scriptName, statementCount, restoreEx.getMessage(), restoreEx);
                    }
                }
            }
        }

        LOGGER.info("[SqlExecutor] [PATH:{}] Executed {} SQL statement(s)",
                scriptName, statementCount);
    }

    /**
     * 智能分割SQL语句
     * 能够正确处理：
     * - 字符串中的分号（单引号、双引号）
     * - 注释中的分号（-- 和 /* * /）
     * - 复杂SQL（CREATE PROCEDURE、CREATE FUNCTION、PL/SQL block等）
     */
    private String[] splitSqlStatements(String sqlContent) {
        List<String> statements = new ArrayList<String>();
        StringBuilder currentStatement = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        // PL/SQL block tracking for DECLARE...BEGIN...END blocks
        int plsqlDepth = 0;
        boolean inDeclareSection = false;

        // DELIMITER support for MySQL stored procedures/triggers
        String currentDelimiter = ";";

        for (int i = 0; i < sqlContent.length(); i++) {
            char c = sqlContent.charAt(i);
            char nextChar = (i + 1 < sqlContent.length()) ? sqlContent.charAt(i + 1) : '\0';

            // 检测 DELIMITER 命令（仅在行首，不在字符串或注释中）
            if (!inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment && plsqlDepth == 0) {
                if (isAtLineStart(sqlContent, i)) {
                    String newDelimiter = extractDelimiterCommand(sqlContent, i);
                    if (newDelimiter != null) {
                        // 先分割当前语句（如果有内容）
                        String currentStmt = currentStatement.toString().trim();
                        if (!currentStmt.isEmpty()) {
                            statements.add(currentStmt);
                        }
                        currentStatement = new StringBuilder();

                        currentDelimiter = newDelimiter;
                        // 收集 DELIMITER 行作为独立语句
                        int startIndex = i;
                        while (i < sqlContent.length() && sqlContent.charAt(i) != '\n') {
                            currentStatement.append(sqlContent.charAt(i));
                            i++;
                        }
                        // 不消耗换行符，让下一次循环处理
                        String stmt = currentStatement.toString().trim();
                        if (!stmt.isEmpty()) {
                            statements.add(stmt);
                        }
                        currentStatement = new StringBuilder();
                        // 注意：i 现在停在换行符上，for 循环的 i++ 会跳过换行符
                        continue;
                    }
                }
            }

            // 处理块注释 /* */
            if (!inSingleQuote && !inDoubleQuote && !inLineComment) {
                if (!inBlockComment && c == '/' && nextChar == '*') {
                    inBlockComment = true;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++;
                    continue;
                }
                if (inBlockComment && c == '*' && nextChar == '/') {
                    inBlockComment = false;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++;
                    continue;
                }
            }

            // 处理行注释 --
            if (!inBlockComment && !inSingleQuote && !inDoubleQuote) {
                if (!inLineComment && c == '-' && nextChar == '-') {
                    inLineComment = true;
                    currentStatement.append(c);
                    currentStatement.append(nextChar);
                    i++;
                    continue;
                }
                if (inLineComment && c == '\n') {
                    inLineComment = false;
                    currentStatement.append(c);
                    continue;  // 添加continue，让下一次循环在行首检测DELIMITER
                }
            }

            // 处理单引号字符串（支持 SQL 标准 '' 转义和反斜杠 \' 转义）
            if (!inLineComment && !inBlockComment && !inDoubleQuote) {
                if (c == '\'') {
                    // 检查 SQL 标准转义: '' (两个单引号表示一个单引号字符)
                    if (nextChar == '\'') {
                        // '' 表示转义的单引号，不结束字符串，跳过两个字符
                        currentStatement.append(c);
                        currentStatement.append(nextChar);
                        i++;
                        continue;
                    }
                    // 检查反斜杠转义: \' (部分数据库支持)
                    if (i > 0 && sqlContent.charAt(i - 1) == '\\') {
                        // 已是转义状态，不切换字符串状态
                        currentStatement.append(c);
                        continue;
                    }
                    // 普通单引号，切换字符串状态
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
                String keyword = extractKeywordAt(sqlContent, i);
                if (keyword != null) {
                    if ("DECLARE".equalsIgnoreCase(keyword)) {
                        plsqlDepth = 1;
                        inDeclareSection = true;
                    } else if ("BEGIN".equalsIgnoreCase(keyword)) {
                        if (inDeclareSection) {
                            inDeclareSection = false;
                        } else {
                            plsqlDepth++;
                        }
                    } else if ("END".equalsIgnoreCase(keyword)) {
                        if (isEndOfBlock(sqlContent, i + keyword.length())) {
                            plsqlDepth--;
                            if (plsqlDepth < 0) {
                                plsqlDepth = 0;
                            }
                        }
                    }
                }
            }

            // 处理语句分割（支持自定义 DELIMITER）
            boolean isDelimiterEnd = false;
            if (currentDelimiter.equals(";")) {
                isDelimiterEnd = (c == ';');
            } else {
                // 检查是否匹配自定义分隔符
                if (i + currentDelimiter.length() <= sqlContent.length()) {
                    String potentialDelimiter = sqlContent.substring(i, i + currentDelimiter.length());
                    isDelimiterEnd = potentialDelimiter.equals(currentDelimiter);
                }
            }

            if (isDelimiterEnd
                    && !inSingleQuote
                    && !inDoubleQuote
                    && !inLineComment
                    && !inBlockComment) {
                if (plsqlDepth == 0) {
                    String stmt = currentStatement.toString().trim();
                    if (!stmt.isEmpty()) {
                        statements.add(stmt);
                    }
                    currentStatement = new StringBuilder();
                    // 如果是自定义分隔符，跳过分隔符的剩余字符
                    if (!currentDelimiter.equals(";")) {
                        i += currentDelimiter.length() - 1;
                    }
                    continue;
                }
            }

            currentStatement.append(c);
        }

        String lastStatement = currentStatement.toString().trim();
        if (!lastStatement.isEmpty()) {
            statements.add(lastStatement);
        }

        return statements.toArray(new String[0]);
    }

    /**
     * 从指定位置提取 SQL 关键字（大小写不敏感）
     */
    private String extractKeywordAt(String sqlContent, int index) {
        if (index < 0 || index >= sqlContent.length()) {
            return null;
        }

        char c = sqlContent.charAt(index);
        if (!Character.isLetter(c)) {
            return null;
        }

        if (index > 0) {
            char prev = sqlContent.charAt(index - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_') {
                return null;
            }
        }

        StringBuilder keyword = new StringBuilder();
        int i = index;
        while (i < sqlContent.length()
                && (Character.isLetter(sqlContent.charAt(i)) || sqlContent.charAt(i) == '_')) {
            keyword.append(sqlContent.charAt(i));
            i++;
        }

        String result = keyword.toString();
        if ("DECLARE".equalsIgnoreCase(result)
                || "BEGIN".equalsIgnoreCase(result)
                || "END".equalsIgnoreCase(result)) {
            return result;
        }

        return null;
    }

    /**
     * 检查 END 是否为块结束
     */
    private boolean isEndOfBlock(String sqlContent, int endIndex) {
        int i = endIndex;

        while (i < sqlContent.length() && Character.isWhitespace(sqlContent.charAt(i))) {
            i++;
        }

        if (i >= sqlContent.length()) {
            return true;
        }

        char nextChar = sqlContent.charAt(i);
        if (nextChar == ';') {
            return true;
        }

        if (Character.isLetter(nextChar)) {
            StringBuilder word = new StringBuilder();
            while (i < sqlContent.length() && Character.isLetter(sqlContent.charAt(i))) {
                word.append(sqlContent.charAt(i));
                i++;
            }

            String nextWord = word.toString();
            if ("IF".equalsIgnoreCase(nextWord)
                    || "LOOP".equalsIgnoreCase(nextWord)
                    || "CASE".equalsIgnoreCase(nextWord)
                    || "WHILE".equalsIgnoreCase(nextWord)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查当前位置是否为行首（前面只有空白或换行）
     *
     * @param sqlContent SQL 内容
     * @param index 当前位置
     * @return 是否为行首
     */
    private boolean isAtLineStart(String sqlContent, int index) {
        while (index > 0) {
            char prev = sqlContent.charAt(index - 1);
            if (prev == '\n') {
                return true;
            }
            if (!Character.isWhitespace(prev)) {
                return false;
            }
            index--;
        }
        return true;
    }

    /**
     * 提取 DELIMITER 后的分隔符
     *
     * @param sqlContent SQL 内容
     * @param index 当前位置
     * @return 分隔符字符串，如果不是 DELIMITER 命令则返回 null
     */
    private String extractDelimiterCommand(String sqlContent, int index) {
        // 从当前位置开始，找到行尾
        int lineEnd = index;
        while (lineEnd < sqlContent.length() && sqlContent.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        String line = sqlContent.substring(index, lineEnd).trim();

        if (line.toUpperCase().startsWith("DELIMITER")) {
            // 提取 DELIMITER 后的分隔符（只取第一个词）
            String afterDelimiter = line.substring(9).trim();
            // 分隔符是第一个非空白序列（直到遇到空白或行尾）
            int delimEnd = 0;
            while (delimEnd < afterDelimiter.length() && !Character.isWhitespace(afterDelimiter.charAt(delimEnd))) {
                delimEnd++;
            }
            String delimiter = afterDelimiter.substring(0, delimEnd);
            if (delimiter.isEmpty()) {
                delimiter = ";";
            }
            return delimiter;
        }
        return null;
    }

    /**
     * 是否为 DDL 语句
     */
    private boolean isDdlStatement(String statement) {
        return statement != null && DDL_STATEMENT_PATTERN.matcher(statement).find();
    }

    /**
     * MySQL 下 DDL 会隐式提交，给出警告
     */
    private void warnIfMySqlDdl(Connection connection, String scriptName, String statement) {
        DatabaseDialect dialect = detectDialect(connection);
        if (dialect == DatabaseDialect.MYSQL) {
            LOGGER.warn("[SqlExecutor] [PATH:{}][WARNING] DDL command ({}) detected in MySQL which causes implicit commit and prevents rollback - Subsequent statements in this transaction will not be rolled back if they fail.",
                    scriptName,
                    abbreviateSql(statement, 20).replaceAll("[\\r\\n\\s]+", " "));
        }
    }

    /**
     * 设置事务为手动提交模式
     */
    private void setManualCommitMode(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
    }

    /**
     * 提交事务
     */
    private void commitTransaction(Connection connection) throws SQLException {
        connection.commit();
        LOGGER.debug("[SqlExecutor] Committed transaction for {}",
                connection.getMetaData().getDatabaseProductName());
    }

    /**
     * 回滚事务
     */
    private void rollbackTransaction(Connection connection) throws SQLException {
        connection.rollback();
        LOGGER.debug("[SqlExecutor] Rolled back transaction for {}",
                connection.getMetaData().getDatabaseProductName());
    }

    /**
     * 恢复自动提交模式
     */
    private void restoreAutoCommitMode(Connection connection, boolean originalAutoCommitMode) throws SQLException {
        connection.setAutoCommit(originalAutoCommitMode);
    }

    /**
     * 对外暴露连接（用于非事务操作）
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * SQL 日志裁剪
     */
    private String abbreviateSql(String sql, int maxLen) {
        if (sql == null) {
            return null;
        }
        String normalized = sql.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }
}
