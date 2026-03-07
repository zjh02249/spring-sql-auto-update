# Flyway Digital 开发者手册

## 当前基线

- 当前版本：`1.3.6.1`
- 当前阶段：第二阶段进行中
- Java：`8+`
- Spring Boot：`2.x / 3.x`
- 当前 `flyway-digital-core` 测试总数：`94`
- 当前总行覆盖率：约 `84.78%`
- 当前状态：`mvn -pl flyway-digital-core verify` 已通过

## 模块说明

### flyway-digital-core

- 纯 Java 核心模块
- 不依赖 Spring
- 负责扫描、解析、执行 SQL 与历史记录管理

### flyway-digital-spring-boot-starter

- 提供 Spring Boot 自动配置
- 兼容 Spring Boot 2.x / 3.x
- 支持动态数据源场景

### flyway-digital-samples

- 示例模块
- 不发布到 Maven 仓库

## 关键类

- `com.cbkj.infrastructure.core.FlywayDigital`
- `com.cbkj.infrastructure.config.FlywayDigitalConfig`
- `com.cbkj.infrastructure.executor.SqlExecutor`
- `com.cbkj.infrastructure.scanner.SqlScanner`
- `com.cbkj.infrastructure.history.HistoryRepository`

## 本地开发命令

```bash
# 编译
mvn clean compile

# 全量测试
mvn test

# 仅验证 core 模块
mvn -pl flyway-digital-core verify

# 发布核心模块和 starter
mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am
```

## 依赖示例

### Starter

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.3.6.1</version>
</dependency>
```

### Core

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-core</artifactId>
    <version>1.3.6.1</version>
</dependency>
```

## 独立使用示例

```java
import com.cbkj.infrastructure.config.FlywayDigitalConfig;
import com.cbkj.infrastructure.core.FlywayDigital;

import javax.sql.DataSource;

public class DatabaseMigration {

    public static void main(String[] args) {
        DataSource dataSource = null; // 按实际项目创建

        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setEnabled(true);
        config.setLocations("classpath:db/migration");
        config.setTable("flyway_digital_history");
        config.setBaselineOnMigrate(false);
        config.setValidateOnMigrate(true);

        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        flywayDigital.migrate();
    }
}
```

## SQL 约定

- 文件命名：`V{version}__{description}.sql`
- 版本号采用语义化比较，不按字符串排序
- 已发布 SQL 文件不要修改，否则会触发 checksum 校验失败

## 事务约定

- 每个 SQL 文件一个事务
- 不要在 SQL 脚本中手动控制事务
- 对 Oracle、达梦、MySQL 的 DDL 回滚限制要有明确预期

## 动态数据源注意事项

- Spring Boot 场景下优先使用 starter 自动配置
- 如项目存在多个数据源，建议显式指定主数据源 bean
- Spring Boot 3.x 必须保留 `AutoConfiguration.imports`

## 当前建议

第二阶段最优先的工作仍然是质量与可维护性：

1. 持续补边界测试，防止后续回归。
2. 持续性能评估并推进第一轮性能优化落地。
3. 暂不优先启动 CLI、Plugin、回滚等第三阶段功能。

## 相关性能文档

- `PERFORMANCE_TESTING.md`


## 第二阶段性能优化执行建议

- 先执行 `mvn -pl flyway-digital-core -Pperf-benchmark test-compile exec:java -Dexec.mainClass=com.cbkj.infrastructure.performance.PerformanceBenchmarkMain -Dperf.sizes=100,500,1000` 采集新基线。
- 重点对比 `firstRunMs`、`secondRunMs` 与 `scanMs`，判断优化收益是否主要体现在首次链路或重复启动链路。
- 每次优化后至少补 1 个边界/异常测试，避免性能改动引入行为回归。
- 详细记录请同步更新 `PERFORMANCE_TESTING.md`。
