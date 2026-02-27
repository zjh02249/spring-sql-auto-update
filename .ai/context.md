# 项目背景与技术栈

## 📖 项目背景

**项目名称**: Flyway Digital  
**项目定位**: 轻量级、Flyway-Compatible SQL 数据库迁移工�? 
**创建时间**: 2026 年初  
**当前版本**: 1.2.9.5

### 核心问题

企业在数据库版本管理中面临的痛点�?
1. **Flyway 过重**: 官方 Flyway 依赖过多，包体积�?2. **国产数据库兼�?*: 需要支持达梦、海量等国产数据�?3. **简单需求复杂化**: 简单的 SQL 迁移不需要完�?Flyway 功能
4. **部署困难**: 企业内网环境依赖下载困难

### 解决方案

Flyway Digital 提供�?
- �?轻量级实现：仅依�?JDBC
- �?Flyway 兼容：History 表结构与 Flyway 保持一�?- �?数据库无关：不绑定特定数据库
- �?Spring Boot 集成：提�?Starter 自动配置
- �?易于部署：单 JAR 包，无外部依�?
## 🛠 技术栈

### 核心技�?
| 技�?| 版本 | 说明 |
|------|------|------|
| **Java** | 1.8+ | 兼容 Java 8 及更高版�?|
| **Spring Boot** | 2.x / 3.x | 同时兼容两个主流版本 |
| **JDBC** | 标准 JDBC 4.0+ | 唯一运行时依�?|
| **SLF4J** | 1.7.x | 日志门面 |
| **Maven** | 3.x | 构建工具 |

### 测试技�?
| 技�?| 用�?|
|------|------|
| JUnit 4 | 单元测试框架 |
| H2 Database | 集成测试内存数据�?|

### 构建与发�?
| 工具 | 用�?|
|------|------|
| Maven | 构建、测试、打�?|
| Maven Deploy Plugin | 发布到私�?Maven 仓库 |

## 🎯 项目目标

### 短期目标（已完成�?
- [x] 实现核心 SQL 迁移功能
- [x] 支持语义化版本号
- [x] 实现 CRC32 Checksum 校验
- [x] 提供 Spring Boot Starter
- [x] 支持动态数据源（Spring Boot 3.x�?- [x] 修复 SQL 分割逻辑 BUG
- [x] 发布�?Maven 仓库

### 中期目标（进行中�?
- [ ] 完善单元测试覆盖�?- [ ] 支持更多数据库方言（可选）
- [ ] 提供 CLI 工具
- [ ] 完善文档和示�?
### 长期目标

- [ ] 支持 Baseline-on-migrate 增强
- [ ] 提供迁移回滚功能（需谨慎�?- [ ] 支持迁移钩子（Before/After�?- [ ] 提供 Gradle Plugin

## 🏗 核心设计原则

### 1. 简单优�?- 只提供核心迁移功�?- 不追�?Flyway 全部特�?- 代码易读易维�?
### 2. 轻量�?- 最小化依赖
- 只依�?JDBC
- 避免引入重型框架

### 3. 兼容�?- Java 1.8 兼容
- Spring Boot 2.x / 3.x 同时支持
- 数据库无关设�?
### 4. 可靠�?- 事务保证
- Checksum 校验
- 详细的执行日�?
### 5. 可集�?- 提供 Spring Boot Starter
- 支持编程式调�?- 支持动态数据源

## 📦 项目结构

```
flyway-digital/
├── flyway-digital-core/              # 核心模块（发布）
�?  ├── core/                          # 核心迁移引擎
�?  ├── executor/                      # SQL 执行�?�?  ├── scanner/                       # SQL 文件扫描�?�?  ├── history/                       # 迁移历史管理
�?  └── model/                         # 领域模型
�?├── flyway-digital-spring-boot-starter/ # Spring Boot Starter（发布）
�?  ├── autoconfigure/                 # 自动配置
�?  └── META-INF/spring/               # Spring Boot 配置
�?└── flyway-digital-samples/            # 示例（不发布�?    ├── spring-boot-sample/            # Spring Boot 示例
    └── standalone-sample/             # 独立使用示例
```

## 🌐 目标用户

### 主要用户群体

1. **企业内部项目**: 需要轻量级数据库迁移工�?2. **国产数据库用�?*: 使用达梦、海量等国产数据�?3. **内网环境**: 无法访问外网，需要简单依�?4. **微服务项�?*: 每个服务需要独立的数据库迁�?
### 典型场景

- Spring Boot 项目自动执行数据库迁�?- CI/CD 流程中集成数据库版本管理
- 多环境（开发、测试、生产）数据库同�?- 数据库结构演进追�?
## 📊 项目现状

**当前版本**: 1.2.9.5
**发布状�?*: �?已发布到私有 Maven 仓库  
**Maven 坐标**:
```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.2.7</version>
</dependency>
```

**仓库地址**: http://maven.tcmbrain.cn/repository/maven-releases/  
**GitHub 地址**: https://github.com/zjh02249/spring-sql-auto-update

---

**最后更�?*: 2026-02-12  
**维护�?*: cbkj
