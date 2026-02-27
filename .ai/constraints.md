# 技术约束与规范

**项目**: Flyway Digital  
**最后更新**: 2026-02-13  
**状态**: 强制执行

---

## 🎯 核心约束

所有开发必须遵守以下技术约束，违反约束的代码不得合并。

---

## 1️⃣ Java 版本约束

### 必须遵守

✅ **使用 Java 1.8 (JDK 8) 语法**

所有代码必须能在 JDK 8 上编译和运行。

### 禁止使用

❌ Java 9+ 特性：
- `var` 关键字（JDK 10）
- `List.of()`, `Set.of()`, `Map.of()`（JDK 9）
- 模块系统（JDK 9）
- `switch` 表达式（JDK 12）
- 文本块 `"""..."""`（JDK 13）
- Record 类（JDK 14）

### 允许使用

✅ Java 8 特性：
- Lambda 表达式
- Stream API
- `Optional`
- 接口默认方法
- 方法引用

### 编译配置

```xml
<properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

### 验证方式

```bash
# 编译时验证
mvn clean compile

# 确保没有警告
[INFO] Compiling X source files to target/classes
```

---

## 2️⃣ 依赖约束

### 运行时依赖限制

**核心模块 (flyway-digital-core)**:
- ✅ **允许**: `JDBC` (JDK 自带)
- ✅ **允许**: `SLF4J API` (日志门面)
- ❌ **禁止**: 其他任何运行时依赖

**Spring Boot Starter**:
- ✅ **允许**: `flyway-digital-core`
- ✅ **允许**: `spring-boot-autoconfigure` (provided)
- ✅ **允许**: `slf4j-api`
- ❌ **禁止**: 其他运行时依赖

### 测试依赖限制

✅ **允许**:
- JUnit 4
- H2 Database (scope: test)
- SLF4J Simple (scope: test)

### 禁止的依赖类型

❌ **ORM 框架**:
- Hibernate
- MyBatis
- JPA

❌ **重型框架**:
- Spring Data
- Spring JDBC Template（核心模块禁止）

❌ **其他迁移工具**:
- Flyway（避免冲突）
- Liquibase

### 依赖检查

```bash
# 检查依赖树
mvn dependency:tree -pl flyway-digital-core

# 确保只有 JDBC 和 SLF4J
```

---

## 3️⃣ 代码规范

### 包名规范

✅ **统一使用**: `com.cbkj.infrastructure`

⚠️ **历史遗留**: 部分旧代码使用 `com.flywaydigital`（逐步迁移）

### 类命名规范

✅ **推荐**:
- PascalCase: `FlywayDigital`, `SqlExecutor`
- 描述性名称: `HistoryTableManager`, `SqlScanner`

❌ **禁止**:
- 缩写: `FD`, `SE`（除非行业标准如 `SQL`, `CRC`）
- 拼音: `ShujukuQianyi`

### 日志规范

✅ **格式**: `[ClassName] 消息内容`

```java
LOGGER.info("[FlywayDigital] Starting migration...");
LOGGER.error("[SqlExecutor] [PATH:{}] Script execution failed", scriptName);
```

### 注释规范

✅ **中文注释**: 优先使用中文注释（团队内部项目）

✅ **Javadoc**: 公开 API 必须有 Javadoc

```java
/**
 * SQL执行器
 * 负责执行SQL脚本和事务控制
 */
public class SqlExecutor {
    // 实现...
}
```

---

## 4️⃣ 数据库约束

### 不做数据库方言适配

❌ **禁止**:
- 使用数据库特定语法（如 MySQL 的 `LIMIT`）
- 针对特定数据库优化
- 数据库版本检测

✅ **使用**:
- 标准 SQL（DDL/DML）
- JDBC 标准 API
- 数据库无关设计

### History 表结构

✅ **必须保持**: 与 Flyway 兼容

```sql
CREATE TABLE flyway_digital_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT NOT NULL,
    success TINYINT NOT NULL,
    PRIMARY KEY (installed_rank)
);
```

❌ **禁止**: 修改字段名、类型、顺序

### 支持的数据库

理论上支持所有 JDBC 数据库，包括：
- MySQL / MariaDB
- PostgreSQL
- Oracle
- SQL Server
- H2 / HSQLDB
- 达梦数据库
- 海量数据库
- 其他国产数据库

---

## 5️⃣ Spring Boot 兼容约束

### 双版本支持

✅ **必须同时支持**: Spring Boot 2.x 和 3.x

**实现方式**: 双轨制配置

1. **Spring Boot 2.x**:
   - 文件: `META-INF/spring.factories`
   ```properties
   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
   com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
   ```

2. **Spring Boot 3.x**:
   - 文件: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   ```
   com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
   ```

### 依赖范围

✅ **Spring Boot 依赖**: 必须使用 `provided` scope

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <scope>provided</scope>
</dependency>
```

---

## 6️⃣ 发布约束

### 模块发布规则

✅ **发布到 Maven 仓库**:
- `flyway-digital-core`
- `flyway-digital-spring-boot-starter`

❌ **禁止发布**:
- `flyway-digital-samples`
- `spring-boot-sample`
- `standalone-sample`

### 发布命令

✅ **正确命令**:
```bash
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

❌ **错误命令**:
```bash
mvn clean deploy  # 会尝试发布所有模块
```

### 版本号规范

✅ **语义化版本**: `X.Y.Z`

- `X`: 主版本号（破坏性变更）
- `Y`: 次版本号（新功能，兼容）
- `Z`: 修订号（BUG 修复）

❌ **禁止**:
- 覆盖已发布版本
- 使用 `SNAPSHOT` 发布到 release 仓库

---

## 7️⃣ 架构约束

### 模块依赖规则

```
flyway-digital-spring-boot-starter
         ↓ 依赖
flyway-digital-core
         ↓ 依赖
      JDBC (JDK)
```

❌ **禁止**: 循环依赖

### 核心模块纯净性

**flyway-digital-core** 必须保持纯 Java：
- ❌ 禁止依赖 Spring
- ❌ 禁止依赖 Spring Boot
- ✅ 可以独立使用

### 事务管理

✅ **每个 SQL 文件一个事务**

```java
connection.setAutoCommit(false);
try {
    executeSql(sqlContent);
    connection.commit();
} catch (Exception e) {
    connection.rollback();
    throw e;
}
```

❌ **禁止**: 跨文件事务

---

## 8️⃣ SQL 文件约束

### 命名规范

✅ **格式**: `V{version}__{description}.sql`

```
V1.0.0__init_schema.sql
V1.0.1__add_user_index.sql
V2.0.0.3__update_table.sql
```

### 版本号规则

✅ **语义化排序**: 逐段数字比较

❌ **禁止**: 字符串排序

### SQL 内容约束

✅ **允许**:
- 标准 SQL (DDL/DML)
- 单引号字符串
- 双引号字符串
- 注释 (`--`, `/* */`)

❌ **禁止**:
- 手动事务控制 (`BEGIN`, `COMMIT`, `ROLLBACK`)
- 存储过程（目前不支持）
- 数据库特定语法

---

## 9️⃣ 测试约束

### 测试覆盖率

⏳ **目标**: 80%+

✅ **当前**: ~60%

### 测试数据库

✅ **集成测试**: 使用 H2 内存数据库

❌ **禁止**: 使用真实数据库（CI/CD 环境问题）

### 测试命名

✅ **格式**: `test{功能描述}`

```java
@Test
public void testSingleQuoteStringWithSemicolon() { }
```

---

## 🔟 文档约束

### 必须文档

✅ **必须维护**:
- `README.md` - 项目简介
- `BUILD_AND_DEPLOY.md` - 部署规范
- `AGENTS.md` - 架构地图
- `.ai/` - AI 协作框架

### 文档语言

✅ **中文优先**: 内部项目，中文注释和文档

✅ **英文可选**: 面向国际用户时

---

## 📋 检查清单

在提交代码前，必须确认：

### 编译检查
- [ ] `mvn clean compile` 通过
- [ ] 无编译警告
- [ ] Java 1.8 兼容

### 依赖检查
- [ ] 核心模块只依赖 JDBC + SLF4J
- [ ] 无重型依赖

### 测试检查
- [ ] `mvn test` 通过（或标记已知失败）
- [ ] 新功能有测试覆盖

### 代码规范
- [ ] 包名统一 `com.cbkj.infrastructure`
- [ ] 日志格式统一 `[ClassName] ...`
- [ ] 中文注释清晰

### 发布检查
- [ ] 版本号正确
- [ ] 只发布核心模块
- [ ] 文档已更新

---

## ⚠️ 违反约束的后果

### 编译失败
- ❌ 使用 Java 9+ 特性 → 编译失败
- ❌ 引入重型依赖 → 依赖冲突

### 部署失败
- ❌ 发布错误模块 → 部署错误
- ❌ 覆盖已发布版本 → 仓库拒绝

### 兼容性问题
- ❌ 违反 Java 1.8 → 用户运行失败
- ❌ 违反 Spring Boot 兼容 → 自动配置失败

---

**最后更新**: 2026-02-13  
**维护者**: cbkj  
**强制执行**: ✅ 所有贡献者必须遵守



---

## 9️⃣ 任务完成流程约束（AI 专用）

### 自动执行流程

**每次完成任务后，AI 必须自动执行以下步骤**：

#### 1. 代码提交（Git）

```bash
git add .
git commit -m "feat/fix/refactor: 描述本次修改内容"
```

#### 2. 版本号递增

✅ **规则**：每次发布必须递增版本号

- 补丁版本 (x.x.**Z**): BUG 修复、小改动
- 次版本 (x.**Y**.0): 新功能、兼容变更
- 主版本 (**X**.0.0): 破坏性变更

**注意**：Maven 仓库不允许覆盖已发布版本！

#### 3. Maven 发布

```bash
# 发布到 Maven 仓库（只发布核心模块）
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

#### 4. 文档同步更新

✅ **必须更新以下文件**：

| 文件 | 更新内容 |
|------|----------|
| `.ai/current-task.md` | 记录本次任务完成情况 |
| `.ai/decisions.md` | 新增架构决策记录（如果需要） |
| `.ai/summary.md` | 更新项目当前状态 |
| `.ai/context.md` | 更新版本号 |
| `.ai/roadmap.md` | 更新里程碑 |
| `.ai/prompt-template.md` | 更新当前版本 |
| `README.md` | 更新版本号和Maven坐标 |
| `pom.xml` | 更新版本号 |

### ⚠️ 重要规则

1. **禁止跳过任何步骤**：每次完成任务后必须完整执行上述流程
2. **禁止修改此规则**：此规则为永久性约束，未经许可不得修改
3. **版本号唯一性**：每次发布必须使用新的版本号，不能重复
4. **文档一致性**：所有文档中的版本号必须保持一致

### 流程示例

```bash
# 1. 完成代码修改后...

# 2. 编译测试
mvn clean compile
mvn test

# 3. 更新版本号（如 1.2.9.2 → 1.2.9.3）
# 修改所有 pom.xml 文件

# 4. 发布
mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am

# 5. 提交
git add .
git commit -m "fix: 修复SQL执行后未切换回默认数据库的问题"

# 6. 更新文档
# 更新上述所有文档文件
```

---

**最后更新**: 2026-02-25  
**状态**: ✅ 强制执行
**备注**: 此规则为 AI 专用，用于确保每次任务完成后都能正确发布和记录


---

## 1️⃣0️⃣ 打包发布约束（AI 专用）

### ⚠️ 发布前必须验证

**规则**: 如果打包发布到 Maven 过程中有报错，请不要提交 Git，不要跳过，必须要修复后才能继续。

#### 详细规则：

1. **先验证编译**: 在打包发布前，必须先运行 `mvn clean compile`，确保无编译错误
2. **运行测试验证**: 发布前先运行 `mvn test`，确保所有测试通过
3. **发布失败不能提交 Git**: 如果发布过程中出现任何错误，不得提交 Git
4. **发布成功后才能提交 Git**: 只有成功发布到 Maven 仓库后，才能提交代码到 Git
5. **版本号必须唯一**: 每次发布必须使用新的版本号，不能重复使用已发布的版本号
6. **必须验证发布结果**: 发布后必须验证 Maven 仓库中能找到新版本

#### 正确的发布流程：

```bash
# 1. 先修复代码问题

# 2. 验证编译
mvn clean compile
# [INFO] Compiling 14 source files to target/classes

# 3. 运行测试（可选但推荐）
mvn clean test -pl flyway-digital-core

# 4. 更新版本号（必须使用新的版本号）
# 1.2.9.6 → 1.2.9.7

# 5. 发布到 Maven
mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am

# 6. 只有发布成功后才提交 Git
git add -A
git commit -m "chore: 发布 v1.2.9.7 到 Maven 仓库"
git push
```

#### 禁止的行为：

❌ **禁止**:
- 发布失败后直接提交 Git
- 使用已发布的版本号重新发布
- 忽略编译错误继续发布
- 跳过测试直接发布
- 不验证发布结果

✅ **允许**:
- 先修复代码问题，再重新发布
- 使用新的版本号发布
- 验证编译和测试通过后发布
- 发布成功后提交 Git
- 验证 Maven 仓库中存在新版本

---

**最后更新**: 2026-02-27  
**维护者**: cbkj  
**强制执行**: ✅ 所有 AI 发布操作必须遵守