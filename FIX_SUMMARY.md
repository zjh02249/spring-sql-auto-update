# 修复总结：达梦数据库 PL/SQL 块支持

## 问题描述
对于达梦(DM)数据库的 PL/SQL 匿名块（DECLARE...BEGIN...END），原代码会错误地在块内部的分号处分割 SQL 语句，导致执行失败。

例如以下 SQL 应该作为一个整体执行：
```sql
DECLARE V_CNT INT;
BEGIN
  SELECT COUNT(*) INTO V_CNT FROM ALL_TABLES WHERE TABLE_NAME = 'MY_TABLE';
  IF V_CNT = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE MY_TABLE (ID INT)';
  END IF;
END;
```

但原代码会在每个分号处分割，破坏了 PL/SQL 块的完整性。

## 修复内容

### 1. 修改 `splitSqlStatements` 方法

添加了 PL/SQL 块跟踪机制：
- `plsqlDepth` - 跟踪 BEGIN/END 的嵌套深度
- `inDeclareSection` - 标记是否在 DECLARE 声明区

逻辑说明：
- `DECLARE` 出现时，设置 `plsqlDepth=1` 和 `inDeclareSection=true`
- `BEGIN` 出现时，如果是 DECLARE 后的 BEGIN，清除 `inDeclareSection` 标志但不增加深度；否则增加深度
- `END` 出现时，如果后面跟着分号（不是 END IF/LOOP），则减少深度
- 当 `plsqlDepth > 0` 时，分号不分割语句

### 2. 新增辅助方法

- `extractKeywordAt` - 从指定位置提取 SQL 关键字（大小写不敏感），确保在单词边界处匹配，避免将 `DECLARE_VAR` 误识别为 `DECLARE`
- `isEndOfBlock` - 检查 END 关键字后面是否紧跟着分号（表示块结束符），而不是 IF/LOOP 等控制结构

### 3. 新增测试用例

在 `SqlExecutorTest.java` 中添加了 6 个测试用例：
1. `testDeclareBeginEndBlock` - 测试基本的 DECLARE...BEGIN...END 块
2. `testDmDeclareBlockFullScenario` - 测试完整的达梦数据库场景
3. `testMixedNormalSqlAndDeclareBlock` - 测试混合普通 SQL 和 DECLARE 块
4. `testBeginEndBlockWithoutDeclare` - 测试不带 DECLARE 的独立 BEGIN...END 块
5. `testNestedBeginEndBlock` - 测试嵌套的 BEGIN...END 块
6. `testDeclareColumnNameNotTreatedAsKeyword` - 测试确保包含 DECLARE_/BEGIN_/END_ 的列名不会被误识别为关键字

## 测试验证

所有测试通过：
- 原有 56 个测试仍然通过（向后兼容）
- 新增 6 个测试全部通过
- 总计 62 个测试通过

## 兼容性

- 保持对原有 SQL 分割逻辑的完全向后兼容
- 仅影响包含 DECLARE/BEGIN/END 关键字的 PL/SQL 块
- 对普通 SQL 语句无影响

## 版本更新

当前版本：1.2.9.21  
建议发布版本：1.2.9.22  
（补丁版本号递增，因为这是一个BUG修复）
