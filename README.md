# Flyway Digital

轻量级、Flyway-Compatible 的 SQL 数据库迁移工具。

## 当前状态

- 当前版本：`1.3.6.1`
- 当前阶段：第二阶段进行中
- Java：`8+`
- Spring Boot：`2.x / 3.x`
- 核心模块当前已通过 `mvn -pl flyway-digital-core verify`

## 特点

- 仅依赖 JDBC，核心模块轻量
- History 表结构与 Flyway 兼容
- 支持语义化版本号和多段版本号
- 提供 Spring Boot Starter 自动配置
- 支持 MySQL、PostgreSQL、Oracle、H2、达梦等数据库

## Maven 依赖

### Spring Boot Starter

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.3.6.1</version>
</dependency>
```

### Standalone Core

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-core</artifactId>
    <version>1.3.6.1</version>
</dependency>
```

## 快速开始

### Spring Boot 配置

```yaml
flyway-digital:
  enabled: true
  locations: classpath:db/migration
  table: flyway_digital_history
  baseline-on-migrate: false
  validate-on-migrate: true
```

### SQL 文件命名规范

```text
V{version}__{description}.sql
```

示例：

- `V1.0.0__init_schema.sql`
- `V1.0.1__add_user_index.sql`
- `V2.0.0.3__update_table.sql`

### 独立使用示例

```java
import com.cbkj.infrastructure.config.FlywayDigitalConfig;
import com.cbkj.infrastructure.core.FlywayDigital;

import javax.sql.DataSource;

public class Main {
    public static void main(String[] args) {
        DataSource dataSource = null; // 按实际项目创建

        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setLocations("classpath:db/migration");
        config.setTable("flyway_digital_history");

        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();
    }
}
```

## 事务说明

- 框架默认按“每个 SQL 文件一个事务”执行。
- 不要在 SQL 脚本中手动写 `BEGIN`、`COMMIT`、`ROLLBACK` 这类事务控制语句。
- Oracle、达梦、MySQL 等数据库的很多 DDL 语句不能回滚，使用时需提前评估。

## 支持的数据库

- MySQL / MariaDB
- PostgreSQL
- Oracle
- SQL Server
- H2
- 达梦
- 其他支持标准 JDBC 的数据库

## 相关文档

- [README-DEV.md](README-DEV.md)
- [BUILD_AND_DEPLOY.md](BUILD_AND_DEPLOY.md)
- [DYNAMIC_DATASOURCE_GUIDE.md](DYNAMIC_DATASOURCE_GUIDE.md)

## 许可证

Apache License 2.0
