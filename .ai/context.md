# 项目背景

## 项目概述
- 名称：Flyway Digital
- 定位：轻量级、Flyway-Compatible SQL 迁移工具
- 当前已发布版本：`1.3.6.1`
- 最后发布时间：`2026-03-06`

## 核心能力
- 语义版本迁移排序
- CRC32 校验
- 脚本事务执行
- baseline-on-migrate
- Spring Boot Starter 自动配置（2.x/3.x）
- 动态数据源支持

## 模块结构
- `flyway-digital-core`：迁移执行引擎
- `flyway-digital-spring-boot-starter`：Spring 集成
- `flyway-digital-samples`：示例（不发布）

## 1.3.6.1 变更摘要
- 替换 `SqlExecutor` 实现，增强 namespace 处理与恢复。
- 使用 JDBC Savepoint 处理切换失败回滚。
- 新增 namespace 标识符风险验证测试。
- 全量测试通过后完成 Maven 发布。