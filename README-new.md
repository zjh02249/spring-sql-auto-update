# Flyway Digital - 轻量级 SQL 迁移工具

[![Maven Central](https://img.shields.io/badge/Maven-1.1.0-blue)](http://maven.tcmbrain.cn/repository/maven-releases/com/cbkj/infrastructure/)
[![Java 8+](https://img.shields.io/badge/Java-8+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x%2F3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

> 一个轻量级、与 Flyway 兼容的 SQL 数据库迁移工具

## ✨ 核心特性

- **🪶 轻量级**: 仅依赖 JDBC，无其他外部依赖
- **🔄 Flyway 兼容**: History 表结构与 Flyway 保持一致，便于迁移
- **🚀 Spring Boot 集成**: 提供 Starter，自动配置，开箱即用
- **🗄️ 多数据库支持**: 支持 MySQL、PostgreSQL、Oracle、达梦、海量等
- **🔢 语义版本**: 支持多段版本号（如 1.0.0.3），按语义版本排序
- **☕ Java 8+**: 支持 Java 8 及更高版本

---

## 🚀 快速开始

### Spring Boot 项目（推荐）

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### 2. 配置

```yaml
# application.yml
flyway-digital:
  enabled: true
  locations: classpath:db/migration
  table: flyway_digital_history
  baseline-on-migrate: false
  validate-on-migrate: true
```

#### 3. 创建 SQL 迁移文件

在 `src/main/resources/db/migration` 目录下创建 SQL 文件：

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

#### 4. 启动应用

```bash
mvn spring-boot:run
```

应用启动时会自动执行数据库迁移。查看日志确认：

```
[FlywayDigital] Starting migration...
[FlywayDigital] Found 3 migration(s) to execute
[SqlExecutor] [SQL:SUCCESS] Script executed successfully
[FlywayDigital] Migration completed successfully. Executed 3 migration(s)
```

---

### 普通 Java 项目

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

```java
import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.core.config.FlywayDigitalConfig;

import javax.sql.DataSource;

public class DatabaseMigration {
    
    public static void main(String[] args) throws Exception {
        // 1. 获取 DataSource
        DataSource dataSource = ...;
        
        // 2. 配置
        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setLocations("classpath:db/migration");
        config.setTable("flyway_digital_history");
        
        // 3. 执行迁移
        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();
    }
}
```

---

## 📚 详细文档

- **[开发者使用手册](README-DEV.md)** - 完整的使用指南、配置详解、最佳实践和故障排除
- [示例项目](flyway-digital-samples/)
  - [Spring Boot 示例](flyway-digital-samples/spring-boot-sample/)
  - [Standalone 示例](flyway-digital-samples/standalone-sample/)

---

## 🗄️ SQL 文件命名规范

```
V{version}__{description}.sql
```

**示例**:
- `V1.0.0__init_schema.sql`
- `V1.0.1__add_user_index.sql`
- `V2.0.0.3__update_table.sql`

**version 规则**:
- 仅允许数字与 `.` 组成
- 支持多段版本号（如 `2.0.0.3`）
- 采用语义版本排序（逐段数字比较）

---

## ⚙️ 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `flyway-digital.enabled` | `true` | 是否启用迁移 |
| `flyway-digital.locations` | `classpath:db/migration` | SQL文件位置 |
| `flyway-digital.table` | `flyway_digital_history` | History表名 |
| `flyway-digital.baseline-on-migrate` | `false` | 是否创建基线 |
| `flyway-digital.baseline-version` | `1` | 基线版本 |
| `flyway-digital.validate-on-migrate` | `true` | 是否校验Checksum |

---

## 🗄️ History 表结构

```sql
CREATE TABLE flyway_digital_history (
    installed_rank INT NOT NULL COMMENT '安装顺序',
    version VARCHAR(50) COMMENT '版本号',
    description VARCHAR(200) NOT NULL COMMENT '描述',
    type VARCHAR(20) NOT NULL COMMENT '类型',
    script VARCHAR(1000) NOT NULL COMMENT '脚本文件名',
    checksum INT COMMENT 'CRC32校验和',
    installed_by VARCHAR(100) NOT NULL COMMENT '执行用户',
    installed_on TIMESTAMP NOT NULL COMMENT '执行时间',
    execution_time INT NOT NULL COMMENT '执行耗时(ms)',
    success TINYINT NOT NULL COMMENT '是否成功',
    PRIMARY KEY (installed_rank)
) COMMENT='Flyway Digital 迁移历史表';
```

---

## 📦 Maven 仓库

已发布到私有 Maven 仓库：

```xml
<repository>
    <id>maven-releases</id>
    <name>TCM Brain Maven Releases</name>
    <url>http://maven.tcmbrain.cn/repository/maven-releases/</url>
</repository>
```

---

## 🏗️ 项目结构

```
flyway-digital/
├── flyway-digital-core/                 # 核心迁移引擎
├── flyway-digital-spring-boot-starter/  # Spring Boot Starter
├── flyway-digital-samples/              # 示例项目
│   ├── spring-boot-sample/
│   └── standalone-sample/
├── README.md                            # 本文件
└── README-DEV.md                        # 开发者使用手册
```

---

## 📝 日志格式

```
[SqlExecutor] [PATH:V1.0.0__init_schema.sql] [TIME:xxx] [SQL:START] Executing script: V1.0.0__init_schema.sql
[SqlExecutor] [PATH:V1.0.0__init_schema.sql] [TIME:xxx] [SQL:SUCCESS] Script executed successfully in 108ms
```

---

## 🔢 版本兼容性

- Java 8+
- Spring Boot 2.x / 3.x
- JDBC 4.0+

---

## 🗄️ 支持的数据库

- MySQL / MariaDB
- PostgreSQL
- Oracle
- SQL Server
- H2
- 达梦数据库
- 海量数据库
- 其他 JDBC 兼容数据库

---

## 📄 许可证

Apache License 2.0

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

**开始使用 Flyway Digital，让数据库迁移变得简单！** 🎉
