# 轻量级、Flyway-Compatible SQL 迁移工具

Flyway-Digital 是一个轻量级、与 Flyway 兼容�?SQL 数据库迁移工具，专为简化数据库版本管理而设计�?
## 特点

- **轻量级**: 仅依赖JDBC，无其他外部依赖
- **Flyway 兼容**: History 表结构与 Flyway 保持一致，便于迁移
- **Spring Boot 集成**: 提供 Starter，自动配置，开箱即用
- **多数据库支持**: 不绑定特定数据库，支持 MySQL、PostgreSQL、Oracle、达梦、海量等
- **Java 8+**: 支持 Java 8 及更高版本

**注意**: Oracle 和达梦、MySQL 一样：DDL（表结构相关、索引相关、视图相关、约束相关、数据库对象相关、库级操作、权限相关（很多人不知道也是 DDL）） 不能回滚，PostgreSQL 部分可以。
- **轻量�?*: 仅依�?JDBC，无其他外部依赖
- **Flyway 兼容**: History 表结构与 Flyway 保持一致，便于迁移
- **Spring Boot 集成**: 提供 Starter，自动配置，开箱即�?- **多数据库支持**: 不绑定特定数据库，支�?MySQL、PostgreSQL、Oracle、达梦、海量等
- **Java 8+**: 支持 Java 8 及更高版�?
## 快速开�?
### Spring Boot 项目

1. 添加依赖
```xml

<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>

```

2. 配置 `application.yml`

```yaml
flyway-digital:
  enabled: true
  locations: classpath:db/migration
  table: flyway_digital_history
  baseline-on-migrate: false
  validate-on-migrate: true
```

3. 创建 SQL 迁移文件

�?`src/main/resources/db/migration` 目录下创�?SQL 文件�?
```sql
-- V1.0.0__init_schema.sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- V1.0.1__add_user_index.sql
CREATE INDEX idx_username ON users(username);
```

4. 启动应用

应用启动时会自动执行数据库迁移�?
### 独立使用

```java
import com.flywaydigital.core.FlywayDigital;
import com.flywaydigital.core.config.FlywayDigitalConfig;

import javax.sql.DataSource;

public class Main {
    public static void main(String[] args) throws Exception {
        // 获取 DataSource（这里假设已经有了）
        DataSource dataSource = ...;
        
        // 配置
        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setLocations("classpath:db/migration");
        config.setTable("flyway_digital_history");
        
        // 执行迁移
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();
    }
}
```

## 配置�?
| 配置�?| 默认�?| 说明 |
|--------|--------|------|
| `flyway-digital.enabled` | `true` | 是否启用迁移 |
| `flyway-digital.locations` | `classpath:db/migration` | SQL文件位置，多个路径用逗号分隔 |
| `flyway-digital.table` | `flyway_digital_history` | History表名 |
| `flyway-digital.baseline-on-migrate` | `false` | 是否在首次迁移时创建基线 |
| `flyway-digital.baseline-version` | `1` | 基线版本 |
| `flyway-digital.validate-on-migrate` | `true` | 是否校验Checksum |
| `flyway-digital.out-of-order` | `false` | 是否允许无序迁移 |

## SQL 文件命名规范

所有迁�?SQL 文件必须遵循以下命名规则�?
```
V{version}__{description}.sql
```

**示例�?*
- `V1.0.0__init_schema.sql`
- `V1.0.1__add_user_index.sql`
- `V2.0.0.3__update_table.sql`

**version 规则�?*
- 仅允许数字与 `.` 组成
- 支持多段版本号（�?`2.0.0.3`�?- 采用语义版本排序（逐段数字比较，非字符串排序）

**description 规则�?*
- 位于 `__` �?`.sql` 之间
- `_` 在写�?history 表时转为空格

## History 表结�?
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
    PRIMARY KEY (installed_rank),
    KEY flyway_digital_history_s_idx (success)
);
```

## 日志格式

迁移工具会输出以下格式的日志�?
```
[SqlExecutor] [PATH:{script_name}] [TIME:{timestamp}] [SQL:START] Executing script: {script_name}
[SqlExecutor] [PATH:{script_name}] [TIME:{timestamp}] [SQL:SUCCESS] Script executed successfully in {time}ms
[SqlExecutor] [PATH:{script_name}] [TIME:{timestamp}] [SQL:FAILED] Script execution failed after {time}ms: {error_message}
```

## 版本兼容�?
- Java 8+
- Spring Boot 2.x / 3.x
- JDBC 4.0+

## 支持的数据库

理论上支持所有标准JDBC数据库，包括但不限于�?- MySQL
- PostgreSQL
- Oracle
- SQL Server
- H2
- 达梦数据库
- 海量数据库
- 其他国产数据库

**特别提醒**: Oracle 和达梦数据库与 MySQL 类似：DDL语句（包括表结构相关、索引相关、视图相关、约束相关、数据库对象相关、库级操作、权限相关等命令 - 所有这些很多开发人员不知道也属于 DDL 语句）执行后无法回滚；而 PostgreSQL 对部分 DDL 操作提供了事务性支持，可在事务中执行和回滚。

## 许可证
Apache License 2.0

## 贡献

欢迎提交 Issue �?Pull Request�?
## 联系方式

如有问题，请通过 GitHub Issues 联系我们�?
