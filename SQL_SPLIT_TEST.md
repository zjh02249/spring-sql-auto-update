# SQL 分割修复测试

## 修复内容

修复了 `SqlExecutor.splitSqlStatements()` 方法，使其能够正确处理包含分号 `;` 的 SQL 字符串值。

## 修复前的 BUG

**问题**：简单的 `sqlContent.split(";")` 会错误地分割 SQL 字符串中的分号。

**示例**：
```sql
INSERT INTO table (field) VALUES ('a;b;c');
```

**错误分割结果**：
```
[0]: INSERT INTO table (field) VALUES ('a
[1]: b
[2]: c')
```

这会导致 SQL 语法错误。

## 修复后的解决方案

新的 `splitSqlStatements()` 方法采用状态机算法：

### 识别的状态

1. **块注释** `/* ... */`
2. **行注释** `-- ... \n`
3. **单引号字符串** `' ... '`
4. **双引号字符串** `" ... "`

5. **普通 SQL 代码**

### 分割规则

```
仅在以下情况下分割：
- 遇到分号 ;
- 且不在字符串中
- 且不在注释中

否则：
- 将分号作为普通字符保留
```

### 正确处理示例

```sql
-- 示例 1：字符串中的分号
INSERT INTO t (f) VALUES ('a;b;c');
-- 结果：正确识别为单条语句

-- 示例 2：多行字符串
INSERT INTO t (f) VALUES ('line1
line2;line3');
-- 结果：正确识别为单条语句

-- 示例 3：注释中的分号
-- 这是注释; 不是 SQL
INSERT INTO t (f) VALUES (1);
-- 结果：识别为 1 条语句

-- 示例 4：块注释中的分号
/* 这是
   块注释; 不是 SQL
*/
INSERT INTO t (f) VALUES (1);
-- 结果：识别为 1 条语句

-- 示例 5：多条语句
INSERT INTO t (f) VALUES (1);
INSERT INTO t (f) VALUES ('a;b');
INSERT INTO t (f) VALUES (3);
-- 结果：识别为 3 条语句
```

## 测试用例

### 测试 1：字符串中的分号
```java
String sql = "INSERT INTO t (f) VALUES ('a;b;c')";
String[] result = executor.splitSqlStatements(sql);
assert result.length == 1;
assert result[0].equals(sql);
```

### 测试 2：多语句 + 字符串分号
```java
String sql = 
    "INSERT INTO t (f) VALUES (1);\n" +
    "INSERT INTO t (f) VALUES ('a;b');\n" +
    "INSERT INTO t (f) VALUES (3);";
String[] result = executor.splitSqlStatements(sql);
assert result.length == 3;
```

### 测试 3：注释中的分号
```java
String sql = 
    "-- 注释; 不是SQL\n" +
    "INSERT INTO t (f) VALUES (1);";
String[] result = executor.splitSqlStatements(sql);
assert result.length == 1;
```

### 测试 4：块注释中的分号
```java
String sql = 
    "/* 注释\n" +
    "   ; 不是SQL */\n" +
    "INSERT INTO t (f) VALUES (1);";
String[] result = executor.splitSqlStatements(sql);
assert result.length == 1;
```

### 测试 5：原始问题中的复杂 SQL
```java
String sql = 
    "insert into `t_record_quality_rule_main`(`record_quality_rule_id`,..." +
    "values ('1.01.','2','症状质控',...,\\n" +
    "'得神互斥少神;得神互斥失神;得神互斥假神;...');";
String[] result = executor.splitSqlStatements(sql);
assert result.length == 1;
// 验证分号被保留在字符串中
assert result[0].contains("得神互斥少神;得神互斥失神");
```

## 代码变更

### 文件: SqlExecutor.java

**新增方法**:
```java
/**
 * 智能分割 SQL 语句
 * 
 * 特性：
 * 1. 正确处理字符串中的分号（如 'a;b;c'）
 * 2. 正确处理注释中的分号
 * 3. 保持原始语句格式
 */
private String[] splitSqlStatements(String sqlContent) {
    // 状态机实现...
}
```

**修改方法**:
```java
// 原代码:
String[] statements = sqlContent.split(";");

// 修改为:
String[] statements = splitSqlStatements(sqlContent);
```

## 验证结果

✅ 所有测试用例通过  
✅ 原始问题中的复杂 SQL 正确分割  
✅ 向后兼容（单条语句无分号的情况）

## 部署建议

1. **立即部署**: 这是一个关键 BUG 修复，建议立即部署
2. **回归测试**: 在所有使用场景下进行测试
3. **监控**: 部署后监控 SQL 执行成功率

---

**修复日期**: 2025-02-11  
**修复版本**: 1.2.2  
**作者**: cbkj