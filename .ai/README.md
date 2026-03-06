# .ai 目录说明

项目：Flyway Digital  
当前版本：1.3.6.1  
最后更新：2026-03-06

`.ai` 用于持久化 AI 协作上下文，避免多轮会话中信息丢失。

## 文件用途
- `context.md`：项目背景与当前基线
- `constraints.md`：强约束与发布规则
- `current-task.md`：当前任务状态
- `decisions.md`：架构决策记录（ADR）
- `roadmap.md`：阶段规划
- `summary.md`：阶段总结与交接信息
- `prompt-template.md`：会话启动模板
- `USAGE.md`：维护和使用说明

## 维护原则
1. 所有文档版本号与 `pom.xml` 保持一致。
2. 每次发布后更新 `summary.md` 和 `current-task.md`。
3. 行为或架构变化要写入 `decisions.md`。
4. 不保留过期目标版本信息。