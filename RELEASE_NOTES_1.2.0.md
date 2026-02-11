# Flyway Digital 1.2.0 版本发布说明

## 🎉 版本信息

- **版本号**: 1.2.0
- **发布日期**: 2025-02-11
- **支持 Spring Boot**: 2.x / 3.x
- **支持 Java**: 8+

## 🚀 新特性

### 1. Spring Boot 3.x 完整兼容 ✅

**问题背景**: Spring Boot 3.x 完全移除了对 `spring.factories` 的传统支持，改为使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

**解决方案**: 
- ✅ 新增 `AutoConfiguration.imports` 文件，兼容 Spring Boot 3.x
- ✅ 保留 `spring.factories` 文件，向后兼容 Spring Boot 2.x

### 2. 动态数据源完整支持 ✅

**问题背景**: 在使用动态数据源（如 `AbstractRoutingDataSource`）的场景中，FlywayDigital 无法正确获取实际的数据源，导致迁移失败。

**解决方案**:

#### 新增配置项

```yaml
flyway-digital:
  # 指定实际的数据源 bean 名称（如 masterDataSource）
  dynamic-datasource-bean-name: masterDataSource
  
  # 启用调试模式，查看详细的自动配置过程
  debug: true
```

#### 增强自动配置逻辑

- **智能数据源查找**: 自动从动态数据源中解析出实际的数据源
- **命名约定支持**: 优先查找 `masterDataSource`、`dataSource` 等常见名称
- **显式配置支持**: 通过 `dynamic-datasource-bean-name` 明确指定数据源

#### 调试日志输出

启用 `debug: true` 后，可以看到详细的自动配置过程：

```
[FlywayDigitalAutoConfiguration] === DEBUG MODE ENABLED ===
[FlywayDigitalAutoConfiguration] Found 3 DataSource bean(s):
[FlywayDigitalAutoConfiguration]   - masterDataSource: com.alibaba.druid.pool.DruidDataSource
[FlywayDigitalAutoConfiguration]   - slaveDataSource: com.alibaba.druid.pool.DruidDataSource
[FlywayDigitalAutoConfiguration]   - dynamicDataSource: com.example.DynamicDataSource
[FlywayDigitalAutoConfiguration] Using DataSource bean named 'masterDataSource' (class: com.alibaba.druid.pool.DruidDataSource)
```

## 📚 文档更新

### 新增文档

1. **DYNAMIC_DATASOURCE_GUIDE.md** (800+ 行)
   - 动态数据源问题完整分析
   - 三种解决方案详细说明
   - 配置示例和故障排除指南
   - 最佳实践建议

2. **README-DEV.md 更新**
   - 新增"动态数据源配置指南"章节
   - 常见问题 Q5：动态数据源不生效
   - 三种解决方案的详细步骤

## 🔧 技术改进

### 代码优化

1. **FlywayDigitalProperties.java**
   - 新增 `dynamicDatasourceBeanName` 属性
   - 新增 `debug` 属性
   - 完善 Javadoc 注释

2. **FlywayDigitalAutoConfiguration.java**
   - 重构数据源查找逻辑
   - 添加调试日志输出
   - 支持多种数据源选择策略
   - 改进错误提示信息

3. **自动配置兼容性**
   - 保留 `spring.factories`（Spring Boot 2.x 兼容）
   - 新增 `AutoConfiguration.imports`（Spring Boot 3.x 兼容）

## 🧪 测试验证

### 测试场景

1. **Spring Boot 2.x 兼容性测试**
   - ✅ Spring Boot 2.7.18 + Java 8
   - ✅ 单数据源场景
   - ✅ 动态数据源场景

2. **Spring Boot 3.x 兼容性测试**
   - ✅ Spring Boot 3.4.1 + Java 21
   - ✅ 单数据源场景
   - ✅ 动态数据源场景

3. **多数据库支持测试**
   - ✅ MySQL 8.0
   - ✅ PostgreSQL 15
   - ✅ H2（嵌入式）

### 性能测试

- ✅ 1000+ 个 SQL 文件加载和排序性能良好
- ✅ 大数据量迁移（10万+ 记录）性能稳定
- ✅ 内存占用合理，无内存泄漏

## 📦 版本升级指南

### 从 1.1.0 升级到 1.2.0

#### Maven 依赖更新

```xml
<!-- 旧版本 -->
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- 新版本 -->
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

#### 配置文件更新（可选）

**对于动态数据源场景，建议添加以下配置：**

```yaml
flyway-digital:
  # 指定实际的数据源 bean 名称（推荐）
  dynamic-datasource-bean-name: masterDataSource
  
  # 启用调试模式（开发和测试环境推荐）
  debug: true
```

#### 兼容性说明

- ✅ **向后兼容**：1.2.0 完全兼容 1.1.0 的所有功能
- ✅ **配置兼容**：现有的配置文件无需修改即可运行
- ✅ **API 兼容**：所有公共 API 保持不变

### 从 1.0.x 升级到 1.2.0

如果你还在使用 1.0.x 版本，升级到 1.2.0 需要注意：

1. **包名变更**：1.1.0 开始包名从 `com.flywaydigital` 改为 `com.cbkj.infrastructure`
2. **GroupId 变更**：Maven groupId 从 `com.flywaydigital` 改为 `com.cbkj.infrastructure`

请参考 1.1.0 的升级指南进行包名和依赖的更新。

## 🐛 已知问题

### 1. Spring Boot 3.x + DevTools 热部署问题

**问题描述**：在使用 Spring Boot DevTools 时，偶尔会出现 FlywayDigital 重复执行的情况。

**解决方案**：
```yaml
spring:
  devtools:
    restart:
      exclude: db/migration/**
```

### 2. 多数据源 + 事务管理器冲突

**问题描述**：在使用多个 `@Primary` 数据源时，可能会出现事务管理器冲突。

**解决方案**：
显式指定事务管理器：
```java
@Transactional(transactionManager = "masterTransactionManager")
public void myMethod() {
    // ...
}
```

## 📞 技术支持

如果你在使用过程中遇到任何问题，可以通过以下方式寻求帮助：

1. **GitHub Issues**: 提交问题和建议
   - 地址：`https://github.com/zjh02249/spring-sql-auto-update/issues`

2. **文档查阅**:
   - `README.md` - 快速开始指南
   - `README-DEV.md` - 开发者详细文档
   - `DYNAMIC_DATASOURCE_GUIDE.md` - 动态数据源配置指南

3. **调试日志**: 
   启用 `flyway-digital.debug: true` 查看详细的自动配置过程

## 🙏 致谢

感谢所有为 Flyway Digital 做出贡献的开发者！

特别感谢：
- 所有提交 Issue 和 PR 的社区成员
- 提供反馈和建议的用户
- 参与测试和验证的开发者

## 📄 许可证

Apache License 2.0

---

**祝你使用愉快！** 🎉

如有问题，请通过 GitHub Issues 联系我们。
