# 架构决策记录

## ADR-009：SqlExecutor namespace 处理升级
日期：2026-03-06  
状态：已采纳

### 背景
需要修复脚本执行后默认库/schema 恢复问题，并扩大 namespace 提取覆盖范围。

### 决策
- 使用 `extractNamespace` 作为新语义命名。
- 支持 UPDATE/DELETE/INSERT/REPLACE/CREATE/ALTER/DROP/TRUNCATE 的 namespace 提取。
- 使用 JDBC `Savepoint` API 处理切换失败回滚。
- 在语句级 finally 中确保尝试恢复默认 namespace。

### 影响
- namespace 切换失败时事务安全性更高。
- 标识符校验更严格，非约定命名会快速失败。
- 已补充测试验证严格模式与正常路径。

## ADR-010：1.3.6.1 发布流程基线
日期：2026-03-06  
状态：已采纳

### 决策
发布固定流程：
1. 版本同步
2. 全量测试
3. 定向发布（core + starter）
4. `.ai` 文档同步
5. Git 提交

### 结果
降低发布遗漏和版本漂移风险，提升可追溯性。