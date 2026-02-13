# Flyway Digital - AGENTS.md

**Generated**: 2025-02-13
**Version**: 1.2.8
**Project Type**: Java Maven Multi-Module
**Language**: Java 8+
**Framework**: Spring Boot 2.x/3.x

---

## OVERVIEW

轻量级、Flyway-Compatible SQL 数据库迁移工具。仅依赖 JDBC，支持 MySQL、PostgreSQL、Oracle、达梦等数据库，提供 Spring Boot Starter 自动配置。

**核心特性**:
- 语义版本管理（支持多段版本号如 1.0.0.3）
- CRC32 Checksum 校验
- Baseline-on-migrate 功能
- 动态数据源支持（Spring Boot 3.x 兼容）

---

## STRUCTURE

flyway-digital/
 ├── flyway-digital-core/                              # 核心迁移引擎 [发布到 Maven]
 │   └── src/main/java/com/cbkj/infrastructure/
 │       ├── core/FlywayDigital.java                  # 主入口类，协调迁移流程
 │       ├── config/FlywayDigitalConfig.java           # 核心配置类
 │       ├── executor/SqlExecutor.java                # SQL 执行器，事务管理
 │       ├── scanner/                                  # SQL 文件扫描模块
 │       │   ├── SqlScanner.java                     # SQL 扫描协调器（Facade）
 │       │   ├── MigrationFileParser.java            # 迁移文件解析器
 │       │   ├── FileSystemScanner.java              # 文件系统扫描器
 │       │   └── JarScanner.java                     # JAR 文件扫描器
 │       ├── history/                                # 迁移历史管理
 │       │   ├── HistoryRepository.java              # 历史记录 CRUD
 │       │   └── HistoryTableManager.java            # History 表管理
 │       ├── model/                                  # 领域模型
 │       │   ├── MigrationVersion.java               # 版本号解析与比较
 │       │   ├── SqlMigration.java                   # SQL 迁移模型
 │       │   └── AppliedMigration.java             # 已应用迁移记录
 │       └── util/                                   # 工具类
 │           ├── ChecksumCalculator.java             # CRC32 校验和计算
 │           └── VersionComparator.java              # 版本号比较器
 ├── flyway-digital-spring-boot-starter/               # Spring: Boot Starter [发布到 Maven]
 │   └── src/main/java/com/flywaydigital/autoconfigure/
 │       ├── FlywayDigitalAutoConfiguration.java      # 自动配置类
 │       └── FlywayDigitalProperties.java             # 配置属性
 ├── flyway-digital-samples/                            # 示例模块 [不发布]
 │   ├── spring-boot-sample/                           # Spring: Boot 示例
 │   └── standalone-sample/                            # 独立使用示例

---

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| **核心迁移逻辑** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/core/FlywayDigital.java | 主入口类，协调迁移流程 |
| **自动配置** | flyway-digital-spring-boot-starter/src/main/java/com/flywaydigital/autoconfigure/FlywayDigitalAutoConfiguration.java | Spring Boot 自动配置 |
| **动态数据源支持** | FlywayDigitalAutoConfiguration.java#determineDataSource() | 智能数据源查找逻辑 |
| **SQL 执行** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/executor/SqlExecutor.java | 事务管理，分号分割 SQL |
| **SQL 扫描协调** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/scanner/SqlScanner.java | Facade 模式协调各扫描器 |
| **文件解析** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/scanner/MigrationFileParser.java | 解析 SQL 文件名、版本、描述、校验和 |
| **文件系统扫描** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/scanner/FileSystemScanner.java | 本地文件系统目录扫描 |
| **JAR 扫描** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/scanner/JarScanner.java | JAR 及嵌套 JAR 扫描 |
| **版本解析** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/model/MigrationVersion.java | 语义版本解析和比较 |
| **历史记录** | flyway-digital-core/src/main/java/com/cbkj/infrastructure/history/ | History 表 CRUD 操作 |
| **集成测试** | flyway-digital-core/src/test/java/com/cbkj/infrastructure/integration/ | H2 内存数据库测试 |
| **发布规范** | BUILD_AND_DEPLOY.md | 构建和发布流程文档 |
| **动态数据源指南** | DYNAMIC_DATASOURCE_GUIDE.md | Spring Boot 3.x + 动态数据源配置 |
| **开发者文档** | README-DEV.md | 详细使用文档 |

---

## CODE MAP

| Symbol | Type | Location | Role |
|--------|------|----------|------|
| **Core** ||||
| FlywayDigital | Class | core/FlywayDigital.java | 主入口，协调迁移流程 |
| FlywayDigitalConfig | Class | config/FlywayDigitalConfig.java | 核心配置类 |
| **Scanner** ||||
| SqlScanner | Class | scanner/SqlScanner.java | 扫描协调器（Facade模式） |
| MigrationFileParser | Class | scanner/MigrationFileParser.java | 迁移文件解析（版本、描述、校验和） |
| FileSystemScanner | Class | scanner/FileSystemScanner.java | 文件系统目录扫描 |
| JarScanner | Class | scanner/JarScanner.java | JAR文件扫描（支持嵌套JAR） |
| **Executor** ||||
| SqlExecutor | Class | executor/SqlExecutor.java | SQL执行和事务管理 |
| **History** ||||
| HistoryRepository | Class | history/HistoryRepository.java | 迁移历史CRUD操作 |
| HistoryTableManager | Class | history/HistoryTableManager.java | History表结构管理 |
| **Model** ||||
| MigrationVersion | Class | model/MigrationVersion.java | 语义版本解析和比较 |
| SqlMigration | Class | model/SqlMigration.java | SQL迁移领域模型 |
| AppliedMigration | Class | model/AppliedMigration.java | 已应用迁移记录 |
| **Util** ||||
| ChecksumCalculator | Class | util/ChecksumCalculator.java | CRC32校验和计算 |
| VersionComparator | Class | util/VersionComparator.java | 版本号比较器 |
| **Spring Boot Starter** ||||
| FlywayDigitalAutoConfiguration | Class | autoconfigure/FlywayDigitalAutoConfiguration.java | Spring Boot自动配置 |
| FlywayDigitalProperties | Class | autoconfigure/FlywayDigitalProperties.java | 配置属性绑定 |
| determineDataSource() | Method | FlywayDigitalAutoConfiguration.java | 动态数据源查找 |

---

## CONVENTIONS

### 代码规范
- **包名**: com.cbkj.infrastructure (核心模块)
- **类命名**: PascalCase，描述性名称
- **日志格式**: [ClassName] 消息，如 [FlywayDigital] Starting migration...
- **注释**: 中文注释为主，关键配置类有详细 Javadoc

### 模块组织
- **core**: 纯 Java，无 Spring 依赖，可独立使用
- **starter**: Spring Boot 自动配置，依赖 core
- **samples**: 示例代码，**不发布到 Maven 仓库**

### SQL 文件命名
V{version}__{description}.sql

示例:
- V1.0.0__init_schema.sql
- V1.0.1__add_user_index.sql
- V2.0.0.3__update_table.sql

---

## ANTI-PATTERNS (THIS PROJECT)

### 部署相关
- ❌ **不要发布示例模块**：spring-boot-sample 和 standalone-sample 已配置 maven.deploy.skip=true
- ❌ **不要覆盖已发布版本**：Maven 仓库不允许覆盖
- ✅ **只发布核心模块**：使用 mvn deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am

### 版本管理
- ✅ **最新版本**：1.2.8（已发布到 Maven 仓库）
- ✅ **版本同步更新**：所有 pom.xml 文件版本号必须保持一致

### 动态数据源
- ❌ **不要在 SQL 脚本中使用事务控制语句**：BEGIN TRANSACTION、COMMIT、ROLLBACK
- ✅ **框架自动管理事务**：每个 SQL 文件在一个事务中执行

### Spring Boot 3.x 兼容
- ❌ **不能只使用 spring.factories**：Spring Boot 3.x 需要 AutoConfiguration.imports
- ✅ **双轨制配置**：同时提供 spring.factories 和 AutoConfiguration.imports

### 版本号管理
- ❌ **不要手动修改已发布 SQL 文件**：会导致 Checksum 校验失败
- ✅ **创建新版本文件**：如需修改，创建新的版本文件

---

## UNIQUE STYLES

### 事务管理
- 框架自动为每个 SQL 脚本开启事务
- 成功时自动提交，失败时自动回滚
- SQL 脚本中**禁止**手动事务控制

### 动态数据源支持
- 通过 flyway-digital.dynamic-datasource-bean-name 指定数据源
- 自动检测 masterDataSource、dataSource 等常见名称
- 支持从 AbstractRoutingDataSource 中解析实际数据源

### 调试模式
- 配置 flyway-digital.debug: true 启用详细日志
- 输出 DataSource bean 发现情况
- 输出数据源选择过程

---

## COMMANDS

### 开发
mvn clean compile
mvn clean test
mvn clean package -DskipTests

### 发布（重要！）
mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am
mvn clean deploy -DskipTests

### 版本更新
sed -i 's/<version>1.2.5<\/version>/<version>1.2.6<\/version>/g' pom.xml flyway-digital-core/pom.xml flyway-digital-spring-boot-starter/pom.xml flyway-digital-samples/pom.xml flyway-digital-samples/spring-boot-sample/pom.xml flyway-digital-samples/standalone-sample/pom.xml

---

## NOTES

### 动态数据源场景配置
flyway-digital:
  enabled: true
  locations: classpath:db/migration
  dynamic-datasource-bean-name: masterDataSource  # 关键配置
  debug: true                                      # 推荐启用

### 模块发布规则
- **发布**：flyway-digital-core、flyway-digital-spring-boot-starter
- **不发布**：spring-boot-sample、standalone-sample（示例模块）

### 代码统计
- **Java 源文件**：14个（核心模块）
- **测试文件**：7个（core 模块）
- **代码覆盖率**：配置 Jacoco（最低 70% 线率）

### 关键文档位置
- 构建规范：BUILD_AND_DEPLOY.md
- 动态数据源指南：DYNAMIC_DATASOURCE_GUIDE.md
- 开发者文档：README-DEV.md
- 发布说明：RELEASE_NOTES_1.2.0.md

### 已知限制
- SQL 分割使用简单分号分割，不支持存储过程等复杂场景
- 不支持自动回滚（需手动编写逆向 SQL）
- 仅支持标准 JDBC 数据库（通过 JDBC 驱动）

---

**Last Updated**: 2026-02-13
**Maintainer**: cbkj
**License**: Apache License 2.0
