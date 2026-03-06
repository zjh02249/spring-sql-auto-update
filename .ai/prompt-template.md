# AI Session 启动模板

**用途**: 新 Session 自动加载项目上下文  
**更新**: 2026-03-06

---

你现在参与一个长期软件工程项目，需要基于已有上下文继续开发。

---

## 项目背景

{{context}}

---

## 架构决策

{{decisions}}

---

## 当前阶段总结

{{summary}}

---

## 当前任务

{{current_task}}

---

## 技术约束

{{constraints}}

---

## 工作指令

1. 先读取并理解上述上下文。
2. 保持架构一致性，不破坏已发布 API。
3. 严格遵守 Java 8、轻量依赖、Flyway 兼容等约束。
4. 优先给出可运行、可验证的代码与测试。
5. 如有状态变化，及时回写 `.ai` 文档。

---

## 关键文件位置

### 核心源码

- `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java`
- `flyway-digital-core/src/main/java/com/cbkj/infrastructure/core/FlywayDigital.java`
- `flyway-digital-core/src/main/java/com/cbkj/infrastructure/config/FlywayDigitalConfig.java`
- `flyway-digital-spring-boot-starter/src/main/java/com/flywaydigital/autoconfigure/FlywayDigitalAutoConfiguration.java`

### 文档

- `BUILD_AND_DEPLOY.md`
- `README.md`
- `README-DEV.md`
- `.ai/`

---

## 常用命令

```bash
# 编译
mvn clean compile

# 测试
mvn test

# 仅验证 core 模块
mvn -pl flyway-digital-core verify
```

---

## 启动提示

如果当前没有新的明确任务，请先基于上下文向用户确认下一步优先事项。

当前基线状态：

- 当前版本：`v1.3.6.1`
- 当前阶段：第二阶段进行中
- 当前覆盖率：`flyway-digital-core` 总行覆盖率约 `84.78%`
- 当前质量状态：`verify` 已通过

---

## 注意事项

- 必须保持 Java 8 兼容。
- 新增注释和说明优先使用中文。
- 文档内容要以“当前事实”为准，历史内容要标明历史语境。
- 如果继续补测试，请补“长期有价值”的边界和回归场景，而不是只为刷覆盖率。

---

我准备好了，请根据 `{{current_task}}` 开始工作。
