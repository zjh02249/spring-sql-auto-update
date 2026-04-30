---
name: SQL 分割逻辑增强设计
description: 增强 SqlExecutor.splitSqlStatements() 方法，处理 DELIMITER、转义引号、触发器、SQL Server 语法等边界情况
type: project
---

# SQL 分割逻辑增强设计

**文档版本**: 1.0
**创建日期**: 2026-04-17
**作者**: Claude

---

## Context

Flyway Digital 是一个轻量级 SQL 数据库迁移工具。核心模块 `SqlExecutor` 中的 `splitSqlStatements()` 方法负责将 SQL 文件内容分割为独立的执行语句。

当前实现已支持：
- 字符串中的分号（单引号、双引号）
- 注释中的分号（行注释 `--`、块注释 `/* */`）
- Oracle/达梦 PL/SQL 块（`DECLARE...BEGIN...END`）

但仍存在以下边界情况问题，可能导致 SQL 执行失败或产生意外结果：

1. **MySQL 存储过程**：不支持 `DELIMITER` 语法，导致存储过程定义被错误分割
2. **SQL 标准引号转义**：不支持 `''` 转义（两个单引号表示一个单引号字符）
3. **触发器定义**：MySQL 触发器需要 DELIMITER 支持，其他数据库触发器边界情况
4. **SQL Server 语法**：`IF...BEGIN...END` 结构与 PL/SQL 的 BEGIN END 混淆
5. **函数定义**：`CREATE FUNCTION` 内部的分号处理

**Why**: 这些问题可能导致生产环境的迁移脚本执行失败，特别是在使用 MySQL 存储过程、触发器或 SQL Server 语法时。需要增强分割逻辑以支持更多数据库语法。

**How to apply**: 通过在现有状态机基础上增加新的状态和检测逻辑，保持向后兼容的同时支持新语法。

---

## 问题分析

### 问题 1: MySQL DELIMITER 语法（高风险）

**描述**: MySQL 使用 `DELIMITER` 命令改变语句分隔符，用于定义存储过程和触发器。

**示例**:
```sql
DELIMITER ;;
CREATE PROCEDURE my_proc()
BEGIN
  SELECT * FROM users;
  INSERT INTO logs VALUES (1);
END;;
DELIMITER ;
```

**当前行为**: `splitSqlStatements()` 不识别 `DELIMITER` 命令，会在存储过程内部的分号处分割。

**预期行为**: 整个 `CREATE PROCEDURE` 作为单条语句执行。

**影响范围**: MySQL/MariaDB 数据库的存储过程和触发器定义脚本。

---

### 问题 2: SQL 标准引号转义（中风险）

**描述**: SQL 标准使用两个单引号 `''` 表示一个单引号字符，当前代码只处理反斜杠转义 `\'`。

**示例**:
```sql
INSERT INTO t (name) VALUES ('It''s a test');
INSERT INTO t (msg) VALUES ('O''Reilly''s book');
```

**当前行为**: 可能将 `''` 误判为字符串结束和新字符串开始。

**预期行为**: `''` 应被视为转义的单引号，不结束字符串。

**代码位置**: `SqlExecutor.java:473-476`

---

### 问题 3: 触发器定义（中风险）

**描述**: 触发器定义包含 `BEGIN...END` 结构，需要正确处理。

**示例（Oracle/达梦）**:
```sql
CREATE TRIGGER my_trigger
BEFORE INSERT ON t
FOR EACH ROW
BEGIN
  INSERT INTO logs VALUES (NEW.id);
END;
```

**示例（MySQL）**:
```sql
DELIMITER ;;
CREATE TRIGGER my_trigger
BEFORE INSERT ON t
FOR EACH ROW
BEGIN
  INSERT INTO logs VALUES (NEW.id);
END;;
DELIMITER ;
```

**当前行为**: Oracle/达梦触发器可能正确处理（BEGIN END 嵌套跟踪），MySQL 触发器需要 DELIMITER 支持。

---

### 问题 4: SQL Server IF BEGIN END（中风险）

**描述**: SQL Server 使用 `IF...BEGIN...END` 和 `ELSE...BEGIN...END` 结构，与 PL/SQL 的 DECLARE BEGIN END 语义不同。

**示例**:
```sql
IF @x = 1
BEGIN
  SELECT * FROM t1;
END
ELSE
BEGIN
  SELECT * FROM t2;
END
```

**当前行为**: `BEGIN` 会增加 `plsqlDepth`，`END` 会减少，但 SQL Server 的 BEGIN END 不属于 PL/SQL 块概念。

**潜在问题**: 如果 SQL Server 脚本中有 `DECLARE`，可能触发 PL/SQL 块跟踪逻辑，导致分割错误。

---

### 问题 5: CREATE FUNCTION（低风险）

**描述**: 函数定义与存储过程类似，包含内部分号。

**示例（Oracle）**:
```sql
CREATE OR REPLACE FUNCTION my_func(x INT)
RETURN INT
AS
BEGIN
  RETURN x * 2;
END;
```

**示例（MySQL）**:
```sql
DELIMITER ;;
CREATE FUNCTION my_func(x INT)
RETURNS INT
BEGIN
  RETURN x * 2;
END;;
DELIMITER ;
```

**当前行为**: Oracle 函数可能正确处理（BEGIN END 跟踪），MySQL 函数需要 DELIMITER 支持。

---

## 设计方案

### 方案概述

**选择方案**: 增强现有状态机 + DELIMITER 预处理钩子

**理由**:
- 保持向后兼容，改动最小
- 不引入外部依赖
- 通过预处理钩子处理特殊语法

---

### 核心改进

#### 1. DELIMITER 支持

**新增字段**:
```java
private String currentDelimiter = ";"; // 当前分隔符
```

**处理逻辑**:
在分割循环开始处检测 `DELIMITER` 命令：
```java
// 检测 DELIMITER 命令（仅在行首）
if (!inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment) {
    if (isAtLineStart(sqlContent, i)) {
        String delimiterCmd = extractDelimiterCommand(sqlContent, i);
        if (delimiterCmd != null) {
            currentDelimiter = delimiterCmd;
            // 跳过 DELIMITER 行（直到遇到 \n）
            while (i < sqlContent.length() && sqlContent.charAt(i) != '\n') {
                currentStatement.append(sqlContent.charAt(i));
                i++;
            }
            // 分割 DELIMITER 行作为独立语句（将被忽略执行）
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

**辅助方法**:
```java
private boolean isAtLineStart(String sqlContent, int index) {
    // 检查当前位置是否为行首（前面只有空白或换行）
    while (index > 0) {
        char prev = sqlContent.charAt(index - 1);
        if (prev == '\n') return true;
        if (!Character.isWhitespace(prev)) return false;
        index--;
    }
    return true;
}

private String extractDelimiterCommand(String sqlContent, int index) {
    // 提取 DELIMITER 后的分隔符
    String remaining = sqlContent.substring(index).trim();
    if (remaining.toUpperCase().startsWith("DELIMITER")) {
        String delimiter = remaining.substring(9).trim();
        if (delimiter.isEmpty()) {
            delimiter = ";"; // DELIMITER ; 恢复默认
        }
        return delimiter;
    }
    return null;
}
```

**分割逻辑调整**:
```java
// 替换固定的分号检测
if (matchesDelimiter(trimmedStatement, currentDelimiter)
        && !inSingleQuote
        && !inDoubleQuote
        && !inLineComment
        && !inBlockComment
        && plsqlDepth == 0) {
    // 分割语句
}
```

```java
private boolean matchesDelimiter(String statement, String delimiter) {
    if (delimiter.equals(";")) {
        return statement.endsWith(";");
    } else {
        return statement.endsWith(delimiter);
    }
}
```

---

#### 2. SQL 标准引号转义

**修改单引号处理逻辑**:
```java
// 处理单引号字符串
if (!inLineComment && !inBlockComment && !inDoubleQuote) {
    if (c == '\'') {
        // 检查 SQL 标准转义: ''
        if (nextChar == '\'') {
            // '' 表示一个单引号字符，不结束字符串
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

---

#### 3. 触发器和存储过程关键字识别

**扩展关键字检测**（可选增强，用于更精确的块跟踪）:
```java
private String extractKeywordAt(String sqlContent, int index) {
    // ... 现有逻辑 ...

    // 扩展关键字列表
    if ("DECLARE".equalsIgnoreCase(result)
            || "BEGIN".equalsIgnoreCase(result)
            || "END".equalsIgnoreCase(result)
            || "PROCEDURE".equalsIgnoreCase(result)
            || "FUNCTION".equalsIgnoreCase(result)
            || "TRIGGER".equalsIgnoreCase(result)) {
        return result;
    }

    return null;
}
```

---

#### 4. SQL Server 语法支持（可选）

**方案**: 检测 SQL Server 方言，调整 BEGIN END 跟踪策略。

**简化方案**: 不特别处理 SQL Server 的 IF BEGIN END，因为：
- SQL Server 迁移脚本通常不使用 `DECLARE` 关键字
- 现有 `plsqlDepth` 跟踪不会在没有 `DECLARE` 的情况下启动
- SQL Server 用户应确保 `BEGIN...END` 块内的分号正确放置

---

### 辅助改进

#### 1. DELIMITER 语句过滤

在执行阶段过滤 DELIMITER 语句：
```java
private void executeSql(Connection connection, String sqlContent, String scriptName) throws SQLException {
    // ... 现有逻辑 ...

    for (String statement : statements) {
        String trimmedStatement = statement.trim();
        if (trimmedStatement.isEmpty()) {
            continue;
        }

        // 跳过 DELIMITER 语句
        if (trimmedStatement.toUpperCase().startsWith("DELIMITER")) {
            LOGGER.debug("[SqlExecutor] Skipping DELIMITER statement: {}", trimmedStatement);
            continue;
        }

        // ... 执行逻辑 ...
    }
}
```

---

## 测试计划

### 新增测试用例

| 测试 ID | 描述 | SQL 示例 |
|---------|------|----------|
| T1 | MySQL 存储过程定义 | `DELIMITER ;; CREATE PROCEDURE... END;; DELIMITER ;` |
| T2 | MySQL 触发器定义 | `DELIMITER ;; CREATE TRIGGER... END;; DELIMITER ;` |
| T3 | SQL 标准引号转义 | `VALUES ('It''s a test')` |
| T4 | 多重引号转义 | `VALUES ('O''Reilly''s book')` |
| T5 | DELIMITER 恢复默认 | `DELIMITER ;; ... ;; DELIMITER ; SELECT * FROM t;` |
| T6 | 混合场景 | 普通语句 + DELIMITER + 存储过程 + 恢复 |

---

## 验证方式

1. **单元测试**: 在 `SqlExecutorTest.java` 中添加新测试用例
2. **集成测试**: 使用 H2 模拟 MySQL 模式测试存储过程定义
3. **回归测试**: 确保所有现有测试（62 个）仍然通过

---

## 实施步骤

1. 在 `SqlExecutor.java` 中添加 `currentDelimiter` 字段
2. 实现 `extractDelimiterCommand()` 和 `isAtLineStart()` 方法
3. 修改单引号处理逻辑支持 `''` 转义
4. 修改分割逻辑使用 `currentDelimiter`
5. 在 `executeSql()` 中过滤 DELIMITER 语句
6. 添加测试用例
7. 运行回归测试

---

## 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 向后兼容性破坏 | 低 | 保持默认分隔符为 `;`，仅处理显式 DELIMITER |
| DELIMITER 解析错误 | 中 | 严格的行首检测，日志记录 |
| 转义引号边界情况 | 低 | 充分的测试用例覆盖 |

---

## 文件修改清单

| 文件 | 修改内容 |
|------|----------|
| `SqlExecutor.java` | 新增 DELIMITER 支持、引号转义改进 |
| `SqlExecutorTest.java` | 新增测试用例 |

---

## 后续建议

1. **性能测试**: 大型 SQL 文件（>1MB）的分割性能
2. **方言检测**: 自动检测数据库方言，启用相应处理策略
3. **文档更新**: 更新 `SQL_SPLIT_TEST.md` 和用户文档