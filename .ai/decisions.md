# 架构决策记录 (ADR)

本文档记录项目中的关键架构决策，帮助理解为什么采用某种设计。

---

## ADR-001: 选择 JDBC 作为唯一数据库依赖

**日期**: 2025-01 初  
**状态**: ✅ 已采纳

### 背景

需要支持多种数据库（MySQL、PostgreSQL、Oracle、达梦、海量等），同时保持轻量级。

### 决策

只依赖标准 JDBC，不使用任何 ORM 框架或数据库抽象层。

### 理由

1. **轻量级**: JDBC 是 JDK 自带，无额外依赖
2. **通用性**: 所有数据库都支持 JDBC
3. **简单性**: 直接执行 SQL，不需要学习 ORM
4. **可控性**: 完全控制 SQL 执行细节

### 影响

- ✅ 优点: 最小化依赖，最大化兼容性
- ⚠️ 缺点: 需要手动管理连接和事务

---

## ADR-002: History 表结构与 Flyway 保持一致

**日期**: 2025-01 初  
**状态**: ✅ 已采纳

### 背景

用户可能需要从 Flyway 迁移到本工具，或者两者混用。

### 决策

History 表字段与 Flyway 完全一致：
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

### 理由

1. **兼容性**: 可以与 Flyway 共用历史表
2. **迁移性**: 从 Flyway 迁移零成本
3. **标准化**: 遵循行业标准

### 影响

- ✅ 优点: 迁移成本低，用户熟悉
- ⚠️ 缺点: 受 Flyway 设计限制

---

## ADR-003: 使用 CRC32 计算 Checksum

**日期**: 2025-01 初  
**状态**: ✅ 已采纳

### 背景

需要检测 SQL 文件是否被修改。

### 决策

使用 Java 内置的 `CRC32` 类计算 Checksum。

### 理由

1. **简单**: JDK 自带，无需引入依赖
2. **快速**: 计算速度快
3. **兼容**: 与 Flyway 一致

### 影响

- ✅ 优点: 简单高效，与 Flyway 兼容
- ⚠️ 缺点: 碰撞概率比 SHA256 高（但对此场景足够）

---

## ADR-004: SQL 文件命名规范采用语义化版本

**日期**: 2025-01 初  
**状态**: ✅ 已采纳

### 背景

需要明确的版本排序规则。

### 决策

文件命名格式：`V{version}__{description}.sql`

版本号规则：
- 支持多段版本号（如 `1.0.0.3`）
- 使用语义化排序（逐段数字比较）
- 不使用字符串排序

示例：
```
V1.0.0__init_schema.sql
V1.0.1__add_user_index.sql
V2.0.0.3__update_table.sql
```

### 理由

1. **清晰**: 语义化版本易理解
2. **兼容**: 与 Flyway 规范一致
3. **扩展性**: 支持多段版本号

### 影响

- ✅ 优点: 版本管理清晰
- ⚠️ 缺点: 用户需要遵循命名规范

---

## ADR-005: 事务级别为每个 SQL 文件一个事务

**日期**: 2025-01 初  
**状态**: ✅ 已采纳

### 背景

需要保证迁移的原子性。

### 决策

每个 SQL 文件在一个独立事务中执行：
- 成功：自动提交
- 失败：自动回滚

### 理由

1. **原子性**: 单个文件要么全部成功，要么全部失败
2. **简单性**: 不需要用户手动管理事务
3. **安全性**: 失败自动回滚，不影响数据库状态

### 影响

- ✅ 优点: 自动事务管理，安全可靠
- ⚠️ 缺点: 不支持跨文件事务

---

## ADR-006: SQL 语句分割使用状态机算法

**日期**: 2025-02-11  
**状态**: ✅ 已采纳

### 背景

发现 SQL 字符串中包含分号时被错误分割的严重 BUG：
```sql
INSERT INTO config VALUES ('url', 'jdbc:mysql://localhost:3306;user=root');
```
上述 SQL 会被错误地在字符串中的分号处分割。

### 决策

实现状态机算法，正确处理：
- 单引号字符串 `'...'`
- 双引号字符串 `"..."`
- 行注释 `-- ... \n`
- 块注释 `/* ... */`

只有在普通 SQL 代码中的分号才分割语句。

### 实现

```java
private String[] splitSqlStatements(String sqlContent) {
    // 状态跟踪
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    
    // 逐字符扫描，维护状态机
    // 只有在普通 SQL 代码中的分号才分割
}
```

### 理由

1. **正确性**: 解决字符串中分号被错误分割的 BUG
2. **完备性**: 覆盖所有 SQL 语法场景
3. **可靠性**: 已有 10+ 测试用例验证

### 影响

- ✅ 优点: 正确处理复杂 SQL
- ⚠️ 缺点: 不支持存储过程等高级特性（需要数据库特定语法）

### 测试覆盖

已有测试用例：
- ✅ 单引号字符串包含分号
- ✅ 双引号字符串包含分号
- ✅ 行注释包含分号
- ✅ 块注释包含分号
- ✅ 混合场景

---

## ADR-007: Spring Boot 2.x 和 3.x 双轨制配置

**日期**: 2025-01  
**状态**: ✅ 已采纳

### 背景

Spring Boot 3.x 改变了自动配置的注册方式。

### 决策

同时提供两种配置文件：
1. `META-INF/spring.factories` - Spring Boot 2.x
2. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` - Spring Boot 3.x

### 理由

1. **兼容性**: 同时支持 Spring Boot 2.x 和 3.x
2. **向后兼容**: 不影响现有用户
3. **向前兼容**: 支持新版本 Spring Boot

### 影响

- ✅ 优点: 最大化兼容性
- ⚠️ 缺点: 需要维护两份配置

---

## ADR-008: 动态数据源支持智能查找

**日期**: 2025-02  
**状态**: ✅ 已采纳

### 背景

企业项目常用动态数据源（如 MyBatis-Plus 的 DynamicDataSource）。

### 决策

自动查找策略：
1. 优先使用配置的 `dynamic-datasource-bean-name`
2. 其次查找 `masterDataSource`
3. 再查找 `dataSource`
4. 支持从 `AbstractRoutingDataSource` 中解析实际数据源

### 理由

1. **灵活性**: 适配不同数据源配置方式
2. **智能性**: 自动查找，减少配置
3. **兼容性**: 支持主流动态数据源框架

### 影响

- ✅ 优点: 开箱即用，无需复杂配置
- ⚠️ 缺点: 可能误判，需要 debug 模式辅助

---

## ADR-009: 示例模块不发布到 Maven 仓库

**日期**: 2025-02  
**状态**: ✅ 已采纳

### 背景

示例模块仅用于演示，不应该发布到 Maven 仓库。

### 决策

在示例模块的 `pom.xml` 中配置：
```xml
<properties>
    <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

部署命令只指定核心模块：
```bash
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

### 理由

1. **清晰性**: 明确哪些模块需要发布
2. **安全性**: 避免误发布示例代码
3. **效率**: 减少发布时间

### 影响

- ✅ 优点: 清晰明确，不会误发布
- ⚠️ 缺点: 需要记住正确的部署命令

---

## 决策原则

所有架构决策遵循以下原则：

1. **简单优先**: 选择最简单的可行方案
2. **兼容优先**: 优先考虑兼容性
3. **可维护性**: 选择易于维护的方案
4. **可测试性**: 确保方案可测试
5. **文档化**: 所有决策必须记录

---

**最后更新**: 2025-02-12  
**维护者**: cbkj
