# 技术约束

项目：Flyway Digital  
最后更新：2026-03-06  
状态：强制执行

## 1. 语言与运行时
- 必须兼容 Java 8+。
- 禁止使用仅 Java 9+ 可用的语法特性。

## 2. 依赖约束
- `flyway-digital-core` 保持轻量，核心依赖 JDBC + SLF4J 思路。
- core 模块不引入重型 ORM / Spring 依赖。
- Spring 相关能力仅放在 starter 模块。

## 3. SQL 执行约束
- 每个 SQL 脚本一个事务，由框架管理。
- SQL 脚本中不使用手工事务控制语句。
- 保持 Flyway 兼容历史表行为。

## 4. 发布约束
- 允许发布：`flyway-digital-core`、`flyway-digital-spring-boot-starter`
- 禁止发布：samples 模块
- 所有模块版本号必须同步

## 5. AI 发布流程（强制）
1. 先跑 `mvn test`
2. 按指定模块执行 deploy
3. deploy 失败不得标记发布完成
4. 发布成功后更新 `.ai` 文档
5. 代码与文档同次提交