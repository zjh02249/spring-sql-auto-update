# .ai 使用指南

## 目标
为长期开发提供稳定上下文，支持多人协作与会话切换。

## 标准流程
1. 开发前阅读：`context.md`、`constraints.md`、`summary.md`。
2. 开发中持续更新：`current-task.md`。
3. 发生设计/行为变更：追加到 `decisions.md`。
4. 发布时执行：版本同步 -> 测试 -> 发布 -> `.ai` 同步 -> Git 提交。

## 发布检查清单
1. 更新所有 `pom.xml` 版本号。
2. 执行 `mvn test`。
3. 发布核心模块：
   `mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am`
4. 更新 `.ai` 全部文档。
5. 统一提交代码与文档。

## 当前基线
- 已发布版本：`1.3.6.1`
- 发布时间：`2026-03-06`