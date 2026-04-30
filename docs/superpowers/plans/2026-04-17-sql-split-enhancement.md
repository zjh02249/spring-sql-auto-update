# SQL 分割逻辑增强实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增强 SqlExecutor.splitSqlStatements() 方法，支持 MySQL DELIMITER 语法和 SQL 标准引号转义

**Architecture:** 在现有状态机基础上增加 DELIMITER 检测和引号转义处理，保持向后兼容

**Tech Stack:** Java 8, JDBC, JUnit 4, H2 Database (测试)

---

## 文件结构

| 文件 | 责责 |
|------|------|
| `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java` | 核心分割逻辑，新增 DELIMITER 支持和引号转义 |
| `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java` | 新增测试用例 |

---

## Task 1: 实现 isAtLineStart 辅助方法

**Files:**
- Modify: `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- Test: `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testIsAtLineStart -q`
Expected: FAIL with "NoSuchMethodException" 或 "test failed"

- [ ] **Step 3: 实现最小代码**

在 `SqlExecutor.java` 中添加辅助方法（在 `isEndOfBlock` 方法后添加）：

```java
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
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testIsAtLineStart -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/code/spring-sql-auto-update && git add flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java && git commit -m "feat(sql-split): add isAtLineStart helper method for DELIMITER detection"
```

---

## Task 2: 实现 extractDelimiterCommand 辅助方法

**Files:**
- Modify: `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- Test: `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testExtractDelimiterCommand -q`
Expected: FAIL with "NoSuchMethodException" 或 "test failed"

- [ ] **Step 3: 实现最小代码**

在 `SqlExecutor.java` 中 `isAtLineStart` 方法后添加：

```java
/**
 * 提取 DELIMITER 后的分隔符
 *
 * @param sqlContent SQL 内容
 * @param index 当前位置
 * @return 分隔符字符串，如果不是 DELIMITER 命令则返回 null
 */
private String extractDelimiterCommand(String sqlContent, int index) {
    String remaining = sqlContent.substring(index).trim();
    if (remaining.toUpperCase().startsWith("DELIMITER")) {
        String delimiter = remaining.substring(9).trim();
        if (delimiter.isEmpty()) {
            delimiter = ";";
        }
        return delimiter;
    }
    return null;
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testExtractDelimiterCommand -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/code/spring-sql-auto-update && git add flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java && git commit -m "feat(sql-split): add extractDelimiterCommand helper method"
```

---

## Task 3: 实现 SQL 标准引号转义 ('') 支持

**Files:**
- Modify: `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- Test: `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testSqlStandardQuoteEscape -q`
Expected: FAIL (当前代码将 '' 误判为字符串结束)

- [ ] **Step 3: 实现最小代码**

修改 `SqlExecutor.java` 中的单引号处理逻辑（约第 473-476 行）：

找到以下代码：
```java
// 处理单引号字符串
if (!inLineComment && !inBlockComment && !inDoubleQuote) {
    if (c == '\'' && (i == 0 || sqlContent.charAt(i - 1) != '\\')) {
        inSingleQuote = !inSingleQuote;
    }
}
```

替换为：
```java
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
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testSqlStandardQuoteEscape -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/code/spring-sql-auto-update && git add flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java && git commit -m "feat(sql-split): support SQL standard quote escape (double single quotes)"
```

---

## Task 4: 实现 DELIMITER 分割逻辑

**Files:**
- Modify: `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- Test: `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testDelimiterProcedure,SqlExecutorTest#testDelimiterTrigger,SqlExecutorTest#testDelimiterRestore -q`
Expected: FAIL (存储过程被错误分割)

- [ ] **Step 3: 实现最小代码**

修改 `SqlExecutor.java` 的 `splitSqlStatements` 方法，添加 DELIMITER 支持。

在方法开始处添加分隔符变量（约第 424 行后）：

```java
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
```

在块注释处理之前添加 DELIMITER 检测（约第 440 行前）：

```java
        // 检测 DELIMITER 命令（仅在行首，不在字符串或注释中）
        if (!inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment && plsqlDepth == 0) {
            if (isAtLineStart(sqlContent, i)) {
                String newDelimiter = extractDelimiterCommand(sqlContent, i);
                if (newDelimiter != null) {
                    currentDelimiter = newDelimiter;
                    // 收集 DELIMITER 行作为独立语句
                    while (i < sqlContent.length() && sqlContent.charAt(i) != '\n') {
                        currentStatement.append(sqlContent.charAt(i));
                        i++;
                    }
                    if (i < sqlContent.length() && sqlContent.charAt(i) == '\n') {
                        currentStatement.append('\n');
                        i++;
                    }
                    String stmt = currentStatement.toString().trim();
                    if (!stmt.isEmpty()) {
                        statements.add(stmt);
                    }
                    currentStatement = new StringBuilder();
                    continue;
                }
            }
        }
```

修改语句分割逻辑（约第 511 行），将固定分号检测改为支持自定义分隔符：

找到以下代码：
```java
            // 处理语句分割
            if (c == ';'
                    && !inSingleQuote
                    && !inDoubleQuote
                    && !inLineComment
                    && !inBlockComment
                    && plsqlDepth == 0) {
                String stmt = currentStatement.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                currentStatement = new StringBuilder();
                continue;
            }
```

替换为：
```java
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
                    && !inBlockComment
                    && plsqlDepth == 0) {
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
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testDelimiterProcedure,SqlExecutorTest#testDelimiterTrigger,SqlExecutorTest#testDelimiterRestore -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/code/spring-sql-auto-update && git add flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java && git commit -m "feat(sql-split): implement DELIMITER support for MySQL stored procedures and triggers"
```

---

## Task 5: 实现 DELIMITER 语句过滤（执行阶段）

**Files:**
- Modify: `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- Test: `flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
/**
 * 测试 DELIMITER 语句在执行时被过滤
 */
@Test
public void testDelimiterStatementFiltered() throws Exception {
    org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
    ds.setURL("jdbc:h2:mem:test_delimiter_filter;MODE=MYSQL;DB_CLOSE_DELAY=-1");

    SqlExecutor executor = new SqlExecutor(ds);

    // 创建测试表
    try (java.sql.Connection conn = ds.getConnection();
         java.sql.Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
    }

    // 执行包含 DELIMITER 的脚本
    String sql = "DELIMITER ;;\n" +
            "INSERT INTO test_table VALUES (1);;\n" +
            "DELIMITER ;\n" +
            "INSERT INTO test_table VALUES (2);";

    // DELIMITER 语句应该被过滤，不导致执行失败
    long elapsed = executor.executeInTransaction(sql, "test_delimiter.sql");
    assertTrue("执行耗时应为非负数", elapsed >= 0);

    // 验证数据正确插入
    try (java.sql.Connection conn = ds.getConnection();
         java.sql.Statement stmt = conn.createStatement();
         java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_table")) {
        assertTrue(rs.next());
        assertEquals("应插入 2 条记录", 2, rs.getInt(1));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testDelimiterStatementFiltered -q`
Expected: FAIL (DELIMITER 语句被发送到数据库执行，导致语法错误)

- [ ] **Step 3: 实现最小代码**

修改 `SqlExecutor.java` 的 `executeSql` 方法，添加 DELIMITER 语句过滤。

找到循环执行语句的部分（约第 346-414 行），在 `if (trimmedStatement.isEmpty())` 后添加：

```java
            // 跳过 DELIMITER 语句（MySQL 语法控制命令，不发送到数据库）
            if (trimmedStatement.toUpperCase().startsWith("DELIMITER")) {
                LOGGER.debug("[SqlExecutor] [PATH:{}] Skipping DELIMITER statement: {}",
                        scriptName, trimmedStatement);
                continue;
            }
```

具体修改位置：找到以下代码段：
```java
            for (String statement : statements) {
                String trimmedStatement = statement.trim();
                if (trimmedStatement.isEmpty()) {
                    continue;
                }

                statementCount++;
```

修改为：
```java
            for (String statement : statements) {
                String trimmedStatement = statement.trim();
                if (trimmedStatement.isEmpty()) {
                    continue;
                }

                // 跳过 DELIMITER 语句（MySQL 语法控制命令，不发送到数据库）
                if (trimmedStatement.toUpperCase().startsWith("DELIMITER")) {
                    LOGGER.debug("[SqlExecutor] [PATH:{}] Skipping DELIMITER statement: {}",
                            scriptName, trimmedStatement);
                    continue;
                }

                statementCount++;
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -Dtest=SqlExecutorTest#testDelimiterStatementFiltered -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /d/code/spring-sql-auto-update && git add flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java flyway-digital-core/src/test/java/com/cbkj/infrastructure/executor/SqlExecutorTest.java && git commit -m "feat(sql-split): filter DELIMITER statements during execution"
```

---

## Task 6: 回归测试

**Files:**
- None (测试验证)

- [ ] **Step 1: 运行所有现有测试**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -q`
Expected: 所有测试通过（包括原有 62 个测试）

- [ ] **Step 2: 检查测试数量**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core test -q | grep "Tests run:"`
Expected: Tests run: X, Failures: 0, Errors: 0 (X 应大于原有测试数)

- [ ] **Step 3: 运行集成测试**

Run: `cd /d/code/spring-sql-auto-update && mvn -pl flyway-digital-core verify -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交（如果有遗漏的小修改）**

```bash
cd /d/code/spring-sql-auto-update && git status && git add -A && git commit -m "test(sql-split): ensure all tests pass after DELIMITER support"
```

---

## Task 7: 更新文档

**Files:**
- Modify: `SQL_SPLIT_TEST.md`

- [ ] **Step 1: 添加 DELIMITER 支持文档**

在 `SQL_SPLIT_TEST.md` 文件末尾添加新章节：

```markdown
## 六、DELIMITER 语法支持（MySQL）

### 6.1 功能说明

从版本 1.3.7 开始，支持 MySQL 的 `DELIMITER` 语法，正确处理存储过程和触发器定义。

### 6.2 示例

**存储过程定义**：
```sql
DELIMITER ;;
CREATE PROCEDURE my_proc()
BEGIN
  SELECT * FROM users;
  INSERT INTO logs VALUES (1);
END;;
DELIMITER ;
```

**触发器定义**：
```sql
DELIMITER $$
CREATE TRIGGER my_trigger
BEFORE INSERT ON t
FOR EACH ROW
BEGIN
  INSERT INTO logs VALUES (NEW.id);
END$$
DELIMITER ;
```

### 6.3 处理逻辑

1. 检测行首的 `DELIMITER` 命令
2. 更新当前分隔符（如 `;;`、`$$`）
3. 使用新分隔符分割语句
4. `DELIMITER` 语句本身被过滤，不发送到数据库执行

### 6.4 测试用例

- `testDelimiterProcedure` - MySQL 存储过程定义
- `testDelimiterTrigger` - MySQL 触发器定义
- `testDelimiterRestore` - DELIMITER 恢复后继续正常分割
- `testDelimiterStatementFiltered` - 执行时过滤 DELIMITER 语句

---

## 七、SQL 标准引号转义支持

### 7.1 功能说明

支持 SQL 标准的单引号转义语法：两个单引号 `''` 表示一个单引号字符。

### 7.2 示例

```sql
INSERT INTO t (name) VALUES ('It''s a test');
INSERT INTO t (msg) VALUES ('O''Reilly''s book');
```

### 7.3 处理逻辑

在单引号字符串中，检测到 `''` 时：
- 不结束字符串
- 将两个单引号作为转义字符保留

### 7.4 测试用例

- `testSqlStandardQuoteEscape` - SQL 标准引号转义

---

**更新日期**: 2026-04-17
**更新版本**: 1.3.7
```

- [ ] **Step 2: 提交文档更新**

```bash
cd /d/code/spring-sql-auto-update && git add SQL_SPLIT_TEST.md && git commit -m "docs(sql-split): document DELIMITER and quote escape support"
```

---

## 验证清单

在实施完成后，执行以下验证：

| 验证项 | 命令 | 期望结果 |
|--------|------|----------|
| 单元测试 | `mvn -pl flyway-digital-core test` | Tests run: X, Failures: 0 |
| 集成测试 | `mvn -pl flyway-digital-core verify` | BUILD SUCCESS |
| DELIMITER 分割 | `testDelimiterProcedure` | PASS |
| DELIMITER 执行过滤 | `testDelimiterStatementFiltered` | PASS |
| 引号转义 | `testSqlStandardQuoteEscape` | PASS |
| 回归测试 | 所有原有测试 | 全部通过 |

---

## 自检清单

- [x] **Spec coverage**: 所有设计文档需求已覆盖
- [x] **Placeholder scan**: 无 TBD/TODO/不完整章节
- [x] **Type consistency**: 方法签名一致（isAtLineStart, extractDelimiterCommand）
- [x] **File paths exact**: 所有路径精确指定
- [x] **Code complete**: 每个步骤包含完整代码
- [x] **Commands exact**: 所有命令明确指定