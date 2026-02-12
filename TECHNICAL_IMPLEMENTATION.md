# Flyway Digital 技术实现详解

**文档版本**: 1.0  
**最后更新**: 2025-02-11  
**作者**: cbkj

---

## 一、Spring Boot 2.x 与 3.x 双版本兼容实现

### 1.1 核心差异分析

Spring Boot 3.x 相对于 2.x 有重大变化：

| 特性 | Spring Boot 2.x | Spring Boot 3.x | 影响 |
|------|----------------|-----------------|------|
| 自动配置注册 | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **破坏性变更** |
| 基线 JDK | Java 8+ | Java 17+ | 编译目标需保持兼容 |

### 1.2 双轨制配置方案

采用**双轨制配置**：同时提供两种自动配置注册方式

```
META-INF/
├── spring.factories                          ← Spring Boot 2.x
└── spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← Spring Boot 3.x
```

**文件 1: spring.factories (Spring Boot 2.x)**
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
```

**文件 2: AutoConfiguration.imports (Spring Boot 3.x)**
```
com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
```

**为什么这样可行？**
- Spring Boot 2.x 不认识 `AutoConfiguration.imports` → 忽略它
- Spring Boot 2.x 认识 `spring.factories` → 正常加载
- Spring Boot 3.x 认识 `AutoConfiguration.imports` → 优先使用
- Spring Boot 3.x 仍然支持 `spring.factories`（向后兼容）

### 1.3 自动配置类设计

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({FlywayDigital.class, DataSource.class})
@ConditionalOnProperty(prefix = "flyway-digital", name = "enabled", 
                       havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(FlywayDigitalProperties.class)
public class FlywayDigitalAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public FlywayDigital flywayDigital(FlywayDigitalConfig config,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<Map<String, DataSource>> allDataSourcesProvider) {
        
        DataSource dataSource = determineDataSource(dataSourceProvider, 
                                                   allDataSourcesProvider);
        return new FlywayDigital(dataSource, config);
    }
}
```

**关键注解说明**：
- `@ConditionalOnClass`: 类存在时才生效
- `@ConditionalOnProperty`: 配置属性匹配时才生效
- `@AutoConfigureAfter`: 在 DataSourceAutoConfiguration 之后执行
- `@ConditionalOnMissingBean`: 不存在该 Bean 时才创建

---

## 二、动态数据源支持实现

### 2.1 问题分析

动态数据源典型实现：
```java
public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceKey();
    }
}
```

**问题**：`DynamicDataSource` 是路由器，不是实际数据源
**需求**：FlywayDigital 需要实际数据源（如 HikariDataSource）

### 2.2 智能数据源查找算法

**优先级策略**：
```
1. 显式配置 > 2. 常见命名 > 3. 第一个可用 > 4. 任意可用
```

**实现代码**：
```java
private DataSource determineDataSource(
        ObjectProvider<DataSource> dataSourceProvider,
        ObjectProvider<Map<String, DataSource>> allDataSourcesProvider) {
    
    Map<String, DataSource> allDataSources = allDataSourcesProvider.getIfAvailable();
    
    // 1. 显式配置优先
    String preferredBeanName = properties.getDynamicDatasourceBeanName();
    if (preferredBeanName != null && allDataSources.containsKey(preferredBeanName)) {
        return allDataSources.get(preferredBeanName);
    }
    
    // 2. 尝试常见命名
    String[] defaultNames = {"masterDataSource", "dataSource", "primaryDataSource"};
    for (String name : defaultNames) {
        DataSource ds = allDataSources.get(name);
        if (ds != null) return ds;
    }
    
    // 3. 返回第一个可用
    if (!allDataSources.isEmpty()) {
        return allDataSources.values().iterator().next();
    }
    
    // 4. 单数据源场景
    return dataSourceProvider.getIfAvailable();
}
```

### 2.3 为什么使用 ObjectProvider？

1. **延迟加载**：不会强制要求依赖必须存在
2. **多实例支持**：可以获取所有同类型的 Bean
3. **兼容性**：Spring Boot 2.x 和 3.x 都支持

### 2.4 配置方式

**方式 1：显式配置（推荐）**
```yaml
flyway-digital:
  dynamic-datasource-bean-name: masterDataSource
```

**方式 2：自动检测**
- 自动查找 `masterDataSource`、`dataSource` 等

---

## 三、Java 8+ 全版本兼容实现

### 3.1 编译目标设置

```xml
<properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

### 3.2 API 兼容性策略

**只使用 Java 8 标准 API**：

| 功能 | Java 8 实现 | 高版本替代 |
|------|------------|-----------|
| 日期时间 | `java.util.Date` | `java.time.*` (Java 8+) |
| 流式操作 | `java.util.stream.*` | - |
| JDBC | `java.sql.*` | - |

**避免使用**：
- `var` 关键字（Java 10+）
- `Optional.isEmpty()`（Java 11+）
- `Record` 类（Java 14+）

### 3.3 Spring Boot 版本适配

```xml
<properties>
    <spring-boot.version>2.7.18</spring-boot.version>
</properties>
```

**为什么使用 2.7.18？**
- 最后一个支持 Java 8 的 Spring Boot 2.x 版本
- 与 Spring Boot 3.x API 兼容
- 提供 BOM 管理依赖版本

**用户可覆盖版本**：
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>  <!-- 使用 Spring Boot 3.x -->
</parent>
```

### 3.4 运行时兼容性

- ✅ Java 8：完全兼容
- ✅ Java 11：完全兼容
- ✅ Java 17：完全兼容
- ✅ Java 21：完全兼容

**原因**：只使用标准 Java API，不依赖特定 JVM 特性

---

## 四、多数据库支持实现

### 4.1 核心原则

**零数据库依赖，纯 JDBC 实现**

```
FlywayDigital (核心)
    ↓ JDBC 标准接口
DataSource (连接池)
    ↓ 数据库驱动
MySQL/PostgreSQL/Oracle/达梦/...
```

### 4.2 SQL 执行器

```java
public class SqlExecutor {
    public long executeInTransaction(String sqlContent, String scriptName) 
            throws Exception {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        
        try {
            executeSql(conn, sqlContent);
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.close();
        }
    }
}
```

### 4.3 支持的数据库

| 数据库 | 驱动 | 测试状态 |
|--------|------|---------|
| MySQL | mysql-connector-java | ✅ 已测试 |
| PostgreSQL | postgresql | ✅ 已测试 |
| Oracle | ojdbc | ✅ 兼容 |
| SQL Server | mssql-jdbc | ✅ 兼容 |
| H2 | h2 | ✅ 已测试 |
| 达梦 | DmJdbcDriver | ✅ 兼容 |
| 海量 | vastbase | ✅ 兼容 |

### 4.4 方言处理策略

**不处理方言**，完全依赖标准 JDBC

```sql
-- 标准 SQL（所有数据库通用）
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL
);
```

**优点**：
- 无需维护复杂的数据库方言映射
- 用户完全控制 SQL 语句
- 零数据库特定代码

---

## 五、整体架构设计

### 5.1 架构图

```
用户应用层
    ↓
FlywayDigitalAutoConfiguration (自动配置)
    ↓
FlywayDigital (核心)
    ├── FlywayDigitalConfig (配置)
    ├── SqlScanner (扫描SQL)
    ├── SqlExecutor (执行SQL)
    └── HistoryRepository (记录历史)
    ↓
DataSource (连接池)
    ↓
数据库驱动
```

### 5.2 模块依赖

```
flyway-digital (parent)
    ├── flyway-digital-core
    │       └── 依赖: SLF4J, JDBC
    ├── flyway-digital-spring-boot-starter
    │       └── 依赖: core, Spring Boot
    └── flyway-digital-samples (不发布)
            ├── spring-boot-sample
            └── standalone-sample
```

### 5.3 核心组件

| 组件 | 职责 |
|------|------|
| FlywayDigital | 主入口，协调迁移流程 |
| FlywayDigitalConfig | 配置管理 |
| SqlScanner | 扫描和解析 SQL 文件 |
| SqlExecutor | 执行 SQL，事务管理 |
| HistoryRepository | History 表 CRUD |
| MigrationVersion | 版本号解析和比较 |

---

## 六、关键技术点总结

### 6.1 Spring Boot 兼容性

```
配置双轨制
├── spring.factories (Spring Boot 2.x)
└── AutoConfiguration.imports (Spring Boot 3.x)

自动配置类
├── @Configuration
├── @ConditionalOnClass
├── @AutoConfigureAfter
└── @EnableConfigurationProperties
```

### 6.2 动态数据源

```
数据源查找算法
├── ObjectProvider<DataSource> (延迟加载)
├── Map<String, DataSource> (多数据源获取)
├── 优先级查找策略
└── 调试日志输出
```

### 6.3 Java 兼容性

```
编译时
├── Maven Compiler Plugin (source=1.8, target=1.8)
└── 仅使用 Java 8 标准 API

运行时
├── Spring Boot BOM 管理依赖版本
├── 用户可覆盖 Spring Boot 版本
└── 零 JVM 特性依赖
```

---

## 七、最佳实践

### 7.1 发布流程

```bash
# 只发布核心模块
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

### 7.2 动态数据源配置

```yaml
flyway-digital:
  dynamic-datasource-bean-name: masterDataSource
  debug: true
```

### 7.3 版本管理

- 主版本号：不兼容变更
- 次版本号：功能新增
- 修订号：Bug 修复

---

## 八、总结

Flyway Digital 通过以下策略实现广泛兼容性：

1. **Spring Boot 兼容**：双轨制配置（spring.factories + AutoConfiguration.imports）
2. **动态数据源**：智能数据源查找算法
3. **Java 兼容**：编译目标 Java 8，仅使用标准 API
4. **多数据库**：纯 JDBC 实现，零数据库特定代码

**成果**：
- ✅ 同时支持 Spring Boot 2.x 和 3.x
- ✅ 支持 Java 8 到 Java 21 全版本
- ✅ 适配各种动态数据源场景
- ✅ 支持几乎所有 JDBC 数据库
