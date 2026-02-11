# FlywayDigital 动态数据源配置指南

## 问题背景

在 Spring Boot 3.x（特别是 3.4.1+）环境中使用动态数据源时，FlywayDigital 可能无法自动触发数据库迁移。这通常表现为：

1. **没有任何 FlywayDigital 日志输出** - 自动配置完全未加载
2. **找不到数据源** - 自动配置无法从动态数据源中解析出实际的数据源
3. **迁移未执行** - 虽然配置看起来正常，但数据库没有执行任何迁移脚本

## 根本原因

### 1. Spring Boot 3.x 自动配置机制变更

从 Spring Boot 2.7 开始，官方引入了新的自动配置注册机制：

- **旧方式**（Spring Boot 2.6 及以下）：使用 `META-INF/spring.factories`
- **新方式**（Spring Boot 2.7+，Spring Boot 3.x 强制）：使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Spring Boot 3.x 已经完全移除了对旧方式的支持**，这就是为什么使用旧版本的 FlywayDigital 在 Spring Boot 3.4.1 中没有任何反应。

### 2. 动态数据源的特殊性

动态数据源（如 `AbstractRoutingDataSource` 的实现）本质上是一个包装器/路由器：

```java
// 典型的动态数据源结构
public class DynamicDataSource extends AbstractRoutingDataSource {
    private Map<Object, DataSource> resolvedDataSources; // 实际的数据源映射
    
    @Override
    protected Object determineCurrentLookupKey() {
        // 根据上下文返回数据源 key
        return DataSourceContextHolder.getDataSourceKey();
    }
}
```

**问题**：FlywayDigital 需要的是一个**实际的、可以直接获取数据库连接的 DataSource**（如 HikariDataSource、DruidDataSource），而不是一个路由包装器。

如果直接将 `DynamicDataSource` 传给 FlywayDigital，可能导致：
- 无法正确获取数据库连接
- 连接池配置无法生效
- 迁移操作执行在非预期的数据库上

## 解决方案

### 方案 1：升级到 FlywayDigital 1.2.0+（推荐）

新版本已经修复了 Spring Boot 3.x 兼容性问题，并添加了对动态数据源的完整支持。

**Maven 依赖**：
```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

**配置示例**（动态数据源场景）：
```yaml
flyway-digital:
  enabled: true
  locations: classpath:db/migration
  
  # 关键配置：指定要使用的实际数据源 bean 名称
  # 这应该是你实际的 DataSource（如 HikariDataSource），而不是 DynamicDataSource
  dynamic-datasource-bean-name: masterDataSource
  
  # 启用调试模式，查看详细的自动配置过程
  debug: true
  
  # 其他配置...
  baseline-on-migrate: false
  validate-on-migrate: true
```

**调试输出示例**：
```
[FlywayDigitalAutoConfiguration] === DEBUG MODE ENABLED ===
[FlywayDigitalAutoConfiguration] Found 3 DataSource bean(s):
[FlywayDigitalAutoConfiguration]   - masterDataSource: com.zaxxer.hikari.HikariDataSource
[FlywayDigitalAutoConfiguration]   - slaveDataSource: com.zaxxer.hikari.HikariDataSource
[FlywayDigitalAutoConfiguration]   - dynamicDataSource: com.jiuzhekan.cbkj.pre_api_devices.config.datasource.DynamicDataSource
[FlywayDigitalAutoConfiguration] Using DataSource bean named 'masterDataSource' (class: com.zaxxer.hikari.HikariDataSource)
```

### 方案 2：手动创建自动配置导入文件（如果无法升级）

如果你的项目无法升级到 FlywayDigital 1.2.0+，可以手动创建 Spring Boot 3.x 兼容的自动配置导入文件。

**步骤 1**：在你的项目中创建文件：

路径：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

内容：
```
com.cbkj.infrastructure.autoconfigure.FlywayDigitalAutoConfiguration
```

**步骤 2**：创建动态数据源配置类（解决动态数据源问题）：

```java
@Configuration
public class FlywayDigitalDynamicDataSourceConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayDigitalDynamicDataSourceConfig.class);
    
    @Autowired
    private FlywayDigitalProperties flywayDigitalProperties;
    
    /**
     * 创建 FlywayDigital 专用的数据源
     * 
     * 这个 bean 的名称必须是 "flywayDigitalDataSource"，
     * 这样 FlywayDigitalAutoConfiguration 会优先使用它
     */
    @Bean(name = "flywayDigitalDataSource")
    @ConditionalOnMissingBean(name = "flywayDigitalDataSource")
    public DataSource flywayDigitalDataSource(
            @Autowired Map<String, DataSource> allDataSources) {
        
        LOGGER.info("[FlywayDigitalDynamicDataSourceConfig] Creating flywayDigitalDataSource");
        LOGGER.info("[FlywayDigitalDynamicDataSourceConfig] Available DataSource beans: {}", allDataSources.keySet());
        
        // 1. 如果配置了显式的 bean 名称，使用该名称
        String preferredBeanName = flywayDigitalProperties.getDynamicDatasourceBeanName();
        if (preferredBeanName != null && !preferredBeanName.isEmpty()) {
            DataSource ds = allDataSources.get(preferredBeanName);
            if (ds != null) {
                LOGGER.info("[FlywayDigitalDynamicDataSourceConfig] Using preferred DataSource: {}", preferredBeanName);
                return ds;
            }
            LOGGER.warn("[FlywayDigitalDynamicDataSourceConfig] Preferred DataSource '{}' not found", preferredBeanName);
        }
        
        // 2. 尝试常见的数据源名称
        String[] commonNames = {"masterDataSource", "dataSource", "primaryDataSource"};
        for (String name : commonNames) {
            DataSource ds = allDataSources.get(name);
            if (ds != null && !isDynamicDataSourceWrapper(ds)) {
                LOGGER.info("[FlywayDigitalDynamicDataSourceConfig] Using DataSource: {}", name);
                return ds;
            }
        }
        
        // 3. 返回第一个非动态数据源的 DataSource
        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            if (!isDynamicDataSourceWrapper(entry.getValue())) {
                LOGGER.info("[FlywayDigitalDynamicDataSourceConfig] Using first available DataSource: {}", entry.getKey());
                return entry.getValue();
            }
        }
        
        // 4. 如果都是动态数据源，返回第一个
        Map.Entry<String, DataSource> first = allDataSources.entrySet().iterator().next();
        LOGGER.warn("[FlywayDigitalDynamicDataSourceConfig] All DataSources appear to be dynamic wrappers. " +
                "Using first available: {}", first.getKey());
        return first.getValue();
    }
    
    /**
     * 检查数据源是否是动态包装器
     */
    private boolean isDynamicDataSourceWrapper(DataSource dataSource) {
        String className = dataSource.getClass().getName();
        return className.contains("Routing") || 
               className.contains("Dynamic") ||
               className.contains("AbstractRoutingDataSource");
    }
}
```

### 方案 3：完全手动配置（绕过自动配置）

如果你需要完全控制 FlywayDigital 的初始化和数据源选择，可以完全手动配置：

```java
@Configuration
@ConditionalOnProperty(prefix = "flyway-digital", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ManualFlywayDigitalConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ManualFlywayDigitalConfig.class);
    
    @Autowired
    private FlywayDigitalProperties properties;
    
    /**
     * 手动创建 FlywayDigital bean，完全控制数据源选择
     */
    @Bean(initMethod = "migrate")
    @ConditionalOnMissingBean(FlywayDigital.class)
    public FlywayDigital flywayDigital(
            @Qualifier("masterDataSource") DataSource masterDataSource) throws Exception {
        
        LOGGER.info("[ManualFlywayDigitalConfig] Creating FlywayDigital with manual configuration");
        LOGGER.info("[ManualFlywayDigitalConfig] Using DataSource: {}", masterDataSource.getClass().getName());
        
        // 创建配置
        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setEnabled(properties.isEnabled());
        config.setLocations(properties.getLocations());
        config.setTable(properties.getTable());
        config.setBaselineOnMigrate(properties.isBaselineOnMigrate());
        config.setBaselineVersion(properties.getBaselineVersion());
        config.setValidateOnMigrate(properties.isValidateOnMigrate());
        config.setOutOfOrder(properties.isOutOfOrder());
        
        // 创建并返回 FlywayDigital
        return new FlywayDigital(masterDataSource, config);
    }
}
```

## 总结

在 Spring Boot 3.x + 动态数据源环境中使用 FlywayDigital 时，主要面临两个问题：

1. **Spring Boot 3.x 自动配置机制变更** - 需要使用新的 `AutoConfiguration.imports` 文件
2. **动态数据源的特殊性** - 需要从包装器中提取实际的数据源

**推荐解决方案**：
- 升级 FlywayDigital 到 1.2.0+（已修复所有兼容性问题）
- 配置 `flyway-digital.dynamic-datasource-bean-name` 明确指定数据源
- 启用 `debug: true` 查看详细的自动配置过程

**备选方案**：
- 手动创建自动配置导入文件（如果不能升级）
- 完全手动配置 FlywayDigital（如果需要完全控制）

通过以上方案，你可以在 Spring Boot 3.x + 动态数据源环境中成功使用 FlywayDigital 进行数据库迁移。
