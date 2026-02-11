# Flyway Digital 开发者使用手册

## 📖 项目简介

Flyway Digital 是一个轻量级、与 Flyway 兼容的 SQL 数据库迁移工具，专为简化数据库版本管理而设计。它提供了与 Flyway 类似的 History 表结构，但更加轻量，仅依赖 JDBC，无其他外部依赖。

### 核心特性

- ✅ **轻量级**: 仅依赖 JDBC，无其他外部依赖
- ✅ **Flyway 兼容**: History 表结构与 Flyway 保持一致，便于迁移
- ✅ **Spring Boot 集成**: 提供 Starter，自动配置，开箱即用
- ✅ **多数据库支持**: 支持 MySQL、PostgreSQL、Oracle、达梦、海量等主流及国产数据库
- ✅ **Java 8+**: 支持 Java 8 及更高版本
- ✅ **语义版本**: 支持多段版本号（如 1.0.0.3），按语义版本排序

---

## 🚀 快速开始

### 方式一：Spring Boot 项目（推荐）

#### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### 2. 配置数据源

确保你的 `application.yml` 或 `application.properties` 中已配置数据源：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb?useUnicode=true&characterEncoding=utf8
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### 3. 配置 Flyway Digital

```yaml
flyway-digital:
  enabled: true                          # 是否启用迁移（默认：true）
  locations: classpath:db/migration      # SQL文件位置（默认：classpath:db/migration）
  table: flyway_digital_history          # History表名（默认：flyway_digital_history）
  baseline-on-migrate: false             # 是否在首次迁移时创建基线（默认：false）
  baseline-version: 1.0.0                # 基线版本（默认：1）
  validate-on-migrate: true              # 是否校验Checksum（默认：true）
```

#### 4. 创建 SQL 迁移文件

在 `src/main/resources/db/migration` 目录下创建 SQL 文件：

```sql
-- V1.0.0__init_schema.sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) COMMENT '邮箱',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='用户表';
```

```sql
-- V1.0.1__add_user_index.sql
CREATE INDEX idx_username ON users(username);
CREATE INDEX idx_email ON users(email);
```

```sql
-- V1.1.0__add_orders_table.sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    total_amount DECIMAL(12, 2) NOT NULL COMMENT '订单金额',
    status TINYINT DEFAULT 1 COMMENT '状态：1-待支付 2-已支付 3-已发货 4-已完成',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='订单表';

CREATE INDEX idx_order_no ON orders(order_no);
CREATE INDEX idx_user_id ON orders(user_id);
```

#### 5. 启动应用

应用启动时会自动执行数据库迁移。查看日志确认迁移成功：

```
[FlywayDigital] Starting migration...
[FlywayDigital] Configuration: FlywayDigitalConfig{enabled=true, locations='classpath:db/migration', table='flyway_digital_history', ...}
[FlywayDigital] Found 0 applied migration(s) in history
[SQLScanner] Scan complete. Found 3 migration(s)
[FlywayDigital] Executing migration: 1.0.0 - V1.0.0__init_schema.sql
[SqlExecutor] [PATH:V1.0.0__init_schema.sql] [SQL:SUCCESS] Script executed successfully in 108ms
[FlywayDigital] Migration completed successfully. Executed 3 migration(s)
```

---

### 方式二：普通 Java 项目（Standalone）

#### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### 2. 编写迁移代码

```java
import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.core.config.FlywayDigitalConfig;

import javax.sql.DataSource;

public class DatabaseMigration {
    
    public static void main(String[] args) {
        // 1. 获取 DataSource（这里以 HikariCP 为例）
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        config.setUsername("root");
        config.setPassword("root");
        DataSource dataSource = new HikariDataSource(config);
        
        // 2. 配置 FlywayDigital
        FlywayDigitalConfig flywayConfig = new FlywayDigitalConfig();
        flywayConfig.setEnabled(true);
        flywayConfig.setLocations("classpath:db/migration");
        flywayConfig.setTable("flyway_digital_history");
        flywayConfig.setBaselineOnMigrate(false);
        flywayConfig.setValidateOnMigrate(true);
        
        // 3. 执行迁移
        try {
            FlywayDigital flywayDigital = new FlywayDigital(dataSource, flywayConfig);
            flywayDigital.migrate();
            System.out.println("数据库迁移完成！");
        } catch (Exception e) {
            System.err.println("数据库迁移失败：" + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

#### 3. 运行程序

```bash
mvn exec:java -Dexec.mainClass="DatabaseMigration"
```

---

## 📋 SQL 文件命名规范

所有迁移 SQL 文件必须遵循以下命名规则：

```
V{version}__{description}.sql
```

### 示例

- `V1.0.0__init_schema.sql`
- `V1.0.1__add_user_index.sql`
- `V1.1.0__add_orders_table.sql`
- `V2.0.0.3__update_table.sql`

### 规则说明

| 部分 | 说明 | 示例 |
|------|------|------|
| `V` | 前缀，表示版本（Version） | V |
| `{version}` | 版本号，仅允许数字和`.` | 1.0.0、2.0.0.3 |
| `__` | 分隔符（两个下划线） | __ |
| `{description}` | 描述，可包含字母、数字和下划线 | init_schema、add_user_index |
| `.sql` | 后缀 | .sql |

### 版本号规则

- 支持多段版本号（如 `1.0.0.3`）
- 采用**语义版本排序**（逐段数字比较）
- 版本号必须唯一，不能重复

```
排序示例：
1.0.0 < 1.0.1 < 1.0.10 < 1.1.0 < 1.10.0 < 2.0.0 < 2.0.0.3
```

---

## ⚙️ 配置详解

### 完整配置项

```yaml
flyway-digital:
  # 是否启用迁移（默认：true）
  enabled: true
  
  # SQL文件位置，多个路径用逗号分隔（默认：classpath:db/migration）
  # 示例：classpath:db/migration,classpath:db/migration/mysql
  locations: classpath:db/migration
  
  # History表名（默认：flyway_digital_history）
  table: flyway_digital_history
  
  # 是否在首次迁移时创建基线（默认：false）
  baseline-on-migrate: false
  
  # 基线版本（默认：1）
  baseline-version: 1.0.0
  
  # 是否校验Checksum（默认：true）
  validate-on-migrate: true
```

### Baseline 功能详解

**使用场景**：
已有数据库需要纳入迁移管理，但不想执行历史 SQL 文件。

**工作原理**：

1. **baseline-on-migrate: true** 时：
   - 等于 baseline 版本的 SQL 文件不会执行，但会记录一条 baseline 记录
   - 低于 baseline 版本的 SQL 文件会被跳过
   - 高于 baseline 版本的 SQL 文件会正常执行

2. **baseline-on-migrate: false** 时：
   - 所有 SQL 文件按版本号顺序执行
   - baseline-version 配置被忽略

**示例**：

```yaml
flyway-digital:
  baseline-on-migrate: true
  baseline-version: 1.1.1
```

SQL 文件：
- `V1.0.0__init.sql` → 跳过（低于 baseline）
- `V1.1.1__baseline.sql` → 记录为 baseline，不执行 SQL
- `V1.2.0__feature.sql` → 执行
- `V2.0.0__major.sql` → 执行

History 表记录：
```
| version | description          | checksum | execution_time | success |
|---------|---------------------|----------|----------------|---------|
| 1.1.1   | << Flyway Baseline >>| NULL     | 0              | 1       |
| 1.2.0   | feature             | 12345    | 50             | 1       |
| 2.0.0   | major               | 67890    | 100            | 1       |
```

---

## 🗄️ History 表结构

工具会自动创建以下表来记录迁移历史：

```sql
CREATE TABLE flyway_digital_history (
    installed_rank INT NOT NULL COMMENT '安装顺序',
    version VARCHAR(50) COMMENT '版本号',
    description VARCHAR(200) NOT NULL COMMENT '描述',
    type VARCHAR(20) NOT NULL COMMENT '类型（SQL）',
    script VARCHAR(1000) NOT NULL COMMENT '脚本文件名',
    checksum INT COMMENT 'CRC32校验和',
    installed_by VARCHAR(100) NOT NULL COMMENT '执行用户',
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    execution_time INT NOT NULL COMMENT '执行耗时（毫秒）',
    success TINYINT NOT NULL COMMENT '是否成功（1-是 0-否）',
    PRIMARY KEY (installed_rank),
    KEY flyway_digital_history_s_idx (success)
) COMMENT='Flyway Digital 迁移历史表';
```

---

## 🔄 事务管理

### 自动事务管理

框架为每个SQL迁移脚本自动管理事务，确保数据一致性：

| 阶段 | 行为 | 说明 |
|------|------|------|
| **开始前** | `setAutoCommit(false)` | 关闭自动提交，开启新事务 |
| **执行中** | 执行SQL语句 | 所有SQL在同一事务中 |
| **成功时** | `commit()` | 提交事务，所有变更生效 |
| **失败时** | `rollback()` | 回滚事务，撤销所有变更 |
| **结束后** | `setAutoCommit(original)` | 恢复原始自动提交设置 |

### 禁止手动事务控制

⚠️ **警告：不要在SQL脚本中使用以下事务控制语句：**

```sql
-- 禁止使用的语句
BEGIN TRANSACTION;  -- 或 START TRANSACTION
COMMIT;
ROLLBACK;
SAVEPOINT xxx;
RELEASE SAVEPOINT xxx;
```

**使用这些语句会导致：**

1. **不可预测的事务行为** - 手动提交/回滚会干扰框架的事务管理
2. **数据不一致** - 部分SQL可能意外提交，导致回滚失败
3. **嵌套事务错误** - 大多数数据库和JDBC驱动不支持真正的嵌套事务
4. **难以调试** - 事务状态混乱导致错误难以定位

### 事务管理最佳实践

✅ **正确的SQL脚本示例：**

```sql
-- V1.0.0__create_user_table.sql
-- 正确：只包含DDL/DML，不包含事务控制语句

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) COMMENT '邮箱',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='用户表';

CREATE INDEX idx_username ON users(username);
```

❌ **错误的SQL脚本示例：**

```sql
-- V1.0.0__create_user_table.sql
-- 错误：包含事务控制语句

BEGIN TRANSACTION;  -- ❌ 不要这样做！

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL
);

COMMIT;  -- ❌ 不要这样做！
```

### 何时需要考虑手动事务？

**通常情况下，你不需要考虑事务。** 框架已经为你处理了。

只有在以下特殊情况下，你可能需要手动控制：

1. **部分提交场景** - 希望某些DDL立即生效（不常见）
2. **批量操作优化** - 超大脚本需要分批提交（建议拆分成多个脚本）
3. **特定数据库行为** - 某些数据库对事务中的DDL有特殊处理

**如果确实需要手动事务控制，建议：**
- 与数据库管理员或架构师讨论
- 充分测试回滚场景
- 在文档中明确记录原因
- 考虑是否有更好的替代方案（如拆分脚本）

---

## 💡 最佳实践

### 1. SQL 文件组织

```
src/main/resources/
└── db/
    └── migration/
        ├── V1.0.0__init_schema.sql      # 初始化表结构
        ├── V1.0.1__add_indexes.sql      # 添加索引
        ├── V1.0.2__add_constraints.sql  # 添加约束
        ├── V1.1.0__add_new_table.sql    # 新增表
        └── V1.1.1__update_table.sql     # 修改表
```

### 2. 版本号管理

- **1.0.x**: 小版本更新（索引、约束、小字段调整）
- **1.x.0**: 中等版本更新（新增表、大字段调整）
- **x.0.0**: 大版本更新（架构调整、破坏性变更）

### 3. SQL 编写规范

- 每个 SQL 文件应该是独立的、可重复执行的（如果失败可手动重试）
- 使用事务包装 DDL 和 DML 操作
- 添加适当的注释说明变更目的
- 避免在已发布的 SQL 文件中修改内容（会导致 checksum 校验失败）

### 4. 环境管理

```yaml
# application-dev.yml（开发环境）
flyway-digital:
  baseline-on-migrate: false  # 开发环境全量执行

# application-prod.yml（生产环境）
flyway-digital:
  baseline-on-migrate: true   # 生产环境使用基线
  baseline-version: 1.0.0
```

---

## 🔧 故障排除

### 问题 1：Checksum 校验失败

**错误信息**：
```
Checksum mismatch for migration 1.0.0. Applied: 12345, Current: 67890
```

**原因**：已执行的 SQL 文件被修改

**解决方案**：
1. 不要修改已执行的 SQL 文件
2. 创建新的版本文件来修正问题

### 问题 2：找不到 SQL 文件

**错误信息**：
```
No pending migrations to execute. Database is up to date.
```

**原因**：SQL 文件位置配置错误或文件命名不规范

**解决方案**：
1. 检查 `locations` 配置是否正确
2. 确认 SQL 文件命名符合规范（V{version}__{description}.sql）

### 问题 3：执行顺序错误

**现象**：SQL 文件执行顺序与版本号不一致

**解决方案**：
- 确保版本号格式正确（仅数字和`.`）
- 工具会自动按语义版本排序，无需担心文件系统顺序

### 问题 4：数据库连接失败

**错误信息**：
```
Failed to obtain JDBC Connection
```

**解决方案**：
1. 检查数据库连接配置（URL、用户名、密码）
2. 确认数据库服务是否启动
3. 检查网络连接

### 问题 5：动态数据源不生效（Spring Boot 3.x）

**现象**：使用动态数据源（如 AbstractRoutingDataSource）时，FlywayDigital 没有执行迁移

**原因**：Spring Boot 3.x 完全移除了对 `spring.factories` 的传统支持，改为使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件

**解决方案**：

1. **升级 FlywayDigital 到 1.2.0 或更高版本**（已修复此问题）

2. **如果无法升级，可以手动创建自动配置导入文件**：

   在你的项目中创建文件：
   `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

   内容：
   ```
   com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
   ```

3. **显式配置要使用的数据源**（动态数据源场景）：

   ```yaml
   flyway-digital:
     # 指定要使用的主数据源 bean 名称
     dynamic-datasource-bean-name: masterDataSource
     # 启用调试模式查看详细加载信息
     debug: true
   ```

4. **自定义数据源 bean 名称**：

   如果自动检测无法找到正确的数据源，可以显式创建一个名为 `flywayDigitalDataSource` 的 bean：

   ```java
   @Configuration
   public class FlywayDigitalDataSourceConfig {
       
       @Autowired
       private DynamicDataSource dynamicDataSource;
       
       @Bean
       public DataSource flywayDigitalDataSource() {
           // 从动态数据源中获取实际的主数据源
           // 这里需要根据你的 DynamicDataSource 实现来调整
           return dynamicDataSource.getResolvedDataSources().get("master");
       }
   }
   ```

---

## 🔄 动态数据源配置指南

### 概述

当使用动态数据源（如基于 AbstractRoutingDataSource 的实现）时，FlywayDigital 需要知道应该使用哪个**实际的数据源**来执行数据库迁移。

这是因为动态数据源通常是一个包装器/路由器，它本身不直接持有数据库连接，而是在运行时根据上下文路由到实际的数据源（如主库、从库等）。

### 自动检测机制

FlywayDigital 使用以下优先级自动检测要使用的数据源：

| 优先级 | 检测策略 | 说明 |
|--------|----------|------|
| 1 | 显式配置 | 检查 `flyway-digital.dynamic-datasource-bean-name` 配置 |
| 2 | 命名约定 | 查找名为 `masterDataSource` 的 bean |
| 3 | 标准命名 | 查找名为 `dataSource` 的 bean |
| 4 | 备选方案 | 使用第一个可用的 DataSource bean |

### 配置方法

#### 方法 1：通过配置指定（推荐）

```yaml
flyway-digital:
  # 指定要使用的主数据源 bean 名称
  # 这个名称应该对应你实际的数据源（如 HikariDataSource、DruidDataSource 等）
  dynamic-datasource-bean-name: masterDataSource
  
  # 启用调试模式查看详细的自动配置过程
  debug: true
```

#### 方法 2：显式创建数据源 bean

如果自动检测无法满足需求，可以显式创建一个名为 `flywayDigitalDataSource` 的 bean：

```java
@Configuration
public class FlywayDigitalDataSourceConfig {
    
    @Autowired
    private DynamicDataSource dynamicDataSource;
    
    /**
     * 为 FlywayDigital 创建专门的数据源
     * 这个方法名可以任意，但建议保持一致性
     */
    @Bean
    public DataSource flywayDigitalDataSource() {
        // 从动态数据源中获取实际的主数据源
        // 注意：这里的实现取决于你的 DynamicDataSource 具体实现
        
        // 方式 1：如果 DynamicDataSource 提供了获取 resolved data sources 的方法
        // Map<Object, DataSource> resolved = dynamicDataSource.getResolvedDataSources();
        // return resolved.get("master");
        
        // 方式 2：直接返回 DynamicDataSource 本身（如果它实现了 DataSource 接口）
        // return dynamicDataSource;
        
        // 方式 3：注入实际的数据源 bean
        // @Autowired private DataSource masterDataSource;
        // return masterDataSource;
        
        throw new UnsupportedOperationException(
            "请根据你的 DynamicDataSource 实现来修改此方法，" +
            "返回实际的数据源（如 HikariDataSource、DruidDataSource 等）"
        );
    }
}
```

#### 方法 3：使用配置类排除动态数据源

如果你的 `DynamicDataSource` 是一个包装器，可以在配置中明确排除它：

```java
@Configuration
public class FlywayDigitalExcludeConfig {
    
    @Autowired
    @Qualifier("masterDataSource")  // 注入实际的数据源，而不是 DynamicDataSource
    private DataSource masterDataSource;
    
    /**
     * 创建一个排除 DynamicDataSource 干扰的数据源
     */
    @Primary  // 标记为 Primary，让 FlywayDigitalAutoConfiguration 优先使用这个
    @Bean
    public DataSource flywayDigitalDataSource() {
        return masterDataSource;
    }
}
```

### 故障排除

#### 问题 1：找不到数据源

**错误信息**：
```
DataSource must not be null. Please ensure a DataSource bean is available.
```

**可能原因和解决方案**：

1. **数据源未创建**：
   - 检查你的 DataSourceConfig 是否被正确加载
   - 确认 `@Configuration` 类位于主应用类同级或子包中

2. **数据源名称不匹配**：
   - 如果你配置了 `flyway-digital.dynamic-datasource-bean-name`，确保该名称的 bean 存在
   - 使用 `debug: true` 查看可用的数据源列表

3. **DataSource bean 被排除**：
   - 检查是否有 `@ComponentScan` 排除了 DataSource 配置类
   - 确认没有使用 `@ConditionalOnProperty` 错误地禁用了数据源

#### 问题 2：使用了错误的动态数据源

**现象**：FlywayDigital 执行了迁移，但数据库没有实际变更

**原因**：FlywayDigital 使用了 `DynamicDataSource` 本身，而不是它包装的实际数据源

**解决方案**：

1. 配置 `flyway-digital.dynamic-datasource-bean-name` 指向实际的数据源（如 `masterDataSource`）

2. 或者创建一个 `flywayDigitalDataSource` bean，从 `DynamicDataSource` 中提取实际的数据源

3. 在 `DynamicDataSource` 中添加一个方法，返回默认的目标数据源

#### 问题 3：Spring Boot 3.x 不加载自动配置

**现象**：没有任何 FlywayDigital 相关的日志输出

**原因**：从 Spring Boot 2.7 开始，自动配置注册方式发生了变化。Spring Boot 3.x 不再支持传统的 `spring.factories` 方式。

**解决方案**：

1. **升级到 FlywayDigital 1.2.0+**（已修复此问题）

2. **如果无法升级**，手动创建自动配置导入文件：

   在你的项目中创建文件：
   `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   
   内容：
   ```
   com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
   ```

### 最佳实践

1. **明确指定数据源**：在多数据源场景下，始终通过 `dynamic-datasource-bean-name` 明确指定要使用的数据源，避免自动检测带来的不确定性。

2. **启用调试模式**：在开发和测试环境启用 `debug: true`，这可以帮助快速定位数据源相关的问题。

3. **分离迁移数据源**：如果可能，为数据库迁移创建一个独立的数据源 bean（如 `flywayDigitalDataSource`），这样可以完全控制迁移使用的连接池配置。

4. **测试验证**：在生产环境部署前，在测试环境验证动态数据源场景下的迁移行为，确保实际的数据库变更符合预期。

5. **监控和告警**：对数据库迁移添加监控，如果迁移失败或长时间未完成，及时发出告警。这在动态数据源场景下尤为重要，因为数据源切换可能导致意外的行为。

---

## 📝 示例项目

我们提供了完整的示例项目供参考：

### 1. Spring Boot 示例

```bash
git clone <repository-url>
cd flyway-digital-samples/spring-boot-sample
mvn spring-boot:run
```

访问接口查看迁移结果：
- `GET http://localhost:8080/api/health` - 健康检查
- `GET http://localhost:8080/api/tables` - 查看所有表
- `GET http://localhost:8080/api/migration-history` - 查看迁移历史

### 2. Standalone 示例

```bash
cd flyway-digital-samples/standalone-sample
mvn exec:java -Dexec.mainClass="com.cbkj.infrastructure.sample.StandaloneSample"
```

---

## 📚 常见问题

### Q1: 如何跳过迁移？

```yaml
flyway-digital:
  enabled: false
```

### Q2: 如何重新执行迁移？

**不推荐**，但可以手动删除 History 表中的记录：

```sql
DELETE FROM flyway_digital_history WHERE version = '1.0.0';
```

然后重启应用。

### Q3: 支持哪些数据库？

所有支持 JDBC 的数据库，包括但不限于：
- MySQL / MariaDB
- PostgreSQL
- Oracle
- SQL Server
- H2
- 达梦数据库
- 海量数据库

### Q4: 是否支持回滚？

目前不支持自动回滚。建议在 SQL 文件中编写逆向操作脚本，手动执行。

---

## 📞 技术支持

如有问题，请通过以下方式联系：

- **GitHub Issues**: 提交问题和建议
- **Email**: 技术支持邮箱

---

## 📄 许可证

Apache License 2.0

---

**祝你使用愉快！** 🎉
