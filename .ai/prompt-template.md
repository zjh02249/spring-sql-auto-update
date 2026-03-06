# AI Session 启动模板

**用途**: 新 Session 自动加载项目上下文  
**使用**: `./scripts/ai-new.sh` 自动生成  
**更新**: 2026-03-02

---

你现在参与一个长期软件工程项目，需要基于已有上下文继续开发。

---

## 📖 项目背景

{{context}}

---

## 🏗 架构决策

{{decisions}}

---

## 📊 当前阶段总结

{{summary}}

---

## 🎯 当前任务

{{current_task}}

---

## 🚫 技术约束

{{constraints}}

---

## 📋 工作指令

### 你需要做什么

1. **理解上下文**
   - 仔细阅读上述所有信息
   - 理解项目当前状态
   - 理解当前任务目标

2. **保持架构一致性**
   - 遵循已有架构决策
   - 不破坏现有设计
   - 有疑问先询问

3. **遵守技术约束**
   - Java 1.8 兼容（必须）
   - 仅依赖 JDBC（核心模块）
   - 不做数据库方言适配
   - Spring Boot 2.x/3.x 兼容

4. **优先给出可运行代码**
   - 不要只给理论分析
   - 提供完整可编译代码
   - 包含必要的测试

5. **更新相关文档**
   - 架构变化 → 更新 `decisions.md`
   - 任务进展 → 更新 `current-task.md`
   - Token 超限前 → 运行 `./scripts/ai-summarize.sh`

---

## 🔍 关键文件位置

### 核心源码
- `flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java` - SQL 执行器（关键）
- `flyway-digital-core/src/main/java/com/cbkj/infrastructure/core/FlywayDigital.java` - 主入口
- `flyway-digital-spring-boot-starter/src/main/java/com/cbkj/infrastructure/autoconfigure/FlywayDigitalAutoConfiguration.java` - 自动配置

### 文档
- `BUILD_AND_DEPLOY.md` - 部署规范（重要）
- `AGENTS.md` - 项目架构地图
- `.ai/` - AI 协作框架

---

## ⚙️ 常用命令

### 编译与测试
```bash
# 编译
mvn clean compile

# 测试
mvn test

# 只测试核心模块
mvn test -pl flyway-digital-core
```

### 发布
```bash
# 部署到 Maven 仓库（只发布核心模块）
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

### Git
```bash
# 提交
git add .
git commit -m "feat: xxx"
git push
```

### AI 工具
```bash
# 生成压缩总结（Token 即将超限时）
./scripts/ai-summarize.sh

# 启动新 Session
./scripts/ai-new.sh
```

---

## 🎯 开始工作

现在你已经了解了项目全貌。

**当前任务是什么？**

请查看 `{{current_task}}` 部分，那是你的工作目标。

**如果当前无任务**，请询问用户：

```
我已经加载了项目上下文。

当前状态：v1.3.5 已发布，项目稳定。
请问你希望我做什么？
1. 修复已知问题（测试失败）
2. 新增功能
3. 完善文档
4. 代码重构
5. 其他任务
```

**如果有明确任务**，立即开始工作。

---

## ⚠️ 注意事项

### 必须遵守
- ✅ Java 1.8 语法（不能使用 var, List.of() 等 Java 9+ 特性）
- ✅ 轻量级（核心模块只依赖 JDBC）
- ✅ Flyway 兼容（History 表结构不能改）
- ✅ 架构一致性（遵循现有设计）

### 禁止行为
- ❌ 引入新的重型依赖
- ❌ 破坏现有 API
- ❌ 使用数据库特定语法
- ❌ 覆盖已发布的 Maven 版本

### 遇到问题
- 🤔 架构疑问 → 查看 `decisions.md`
- 🤔 技术限制 → 查看 `constraints.md`
- 🤔 不确定 → 询问用户

---

## 📚 扩展阅读

如果需要更深入了解：

1. **SQL 分割算法**: 查看 `SQL_SPLIT_TEST.md`
2. **动态数据源**: 查看 `DYNAMIC_DATASOURCE_GUIDE.md`
3. **部署流程**: 查看 `BUILD_AND_DEPLOY.md`
4. **开发文档**: 查看 `README-DEV.md`

---

## 🚀 开始吧！

我准备好了，请告诉我你的需求。
