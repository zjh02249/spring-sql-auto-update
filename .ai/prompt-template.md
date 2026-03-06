# 会话启动模板

请继续 Flyway Digital 项目工作，并先阅读：
1. `.ai/context.md`
2. `.ai/constraints.md`
3. `.ai/summary.md`
4. `.ai/current-task.md`
5. `.ai/decisions.md`

执行要求：
- 保持 Java 8+ 兼容
- 不引入不必要依赖
- 遵守模块发布边界
- 若发生架构/行为变化，更新 `decisions.md`
- 若涉及发布，严格执行：版本同步 -> 测试 -> 发布 -> `.ai` 更新 -> 提交

当前基线：
- 已发布版本：`1.3.6.1`
- 重点方向：SqlExecutor namespace 处理、风险验证、发布一致性