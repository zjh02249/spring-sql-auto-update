# 项目背景与技术栈

## 项目背景

**项目名称**: Flyway Digital  
**项目定位**: 轻量级、Flyway-Compatible SQL 数据库迁移工具  
**当前版本**: 1.3.6.1  
**当前阶段**: 第二阶段进行中

### 最近关键状态

- **2026-03-06 / v1.3.6.1**
  - core 与 starter 已发布到 Maven 仓库
  - `SqlExecutor` namespace 恢复与安全控制增强
  - `flyway-digital-core verify` 已通过
  - 当前 Jacoco 总行覆盖率约 `84.78%`

## 核心问题

企业数据库版本管理通常面临以下问题：

1. 官方 Flyway 对部分内部项目来说过重。
2. 国产数据库兼容与部署环境约束较多。
3. 简单 SQL 迁移场景不需要完整平台能力。
4. 内网环境往往要求依赖少、集成直接。

## 解决方案

Flyway Digital 提供：

- ✅ 仅依赖 JDBC 的轻量级实现
- ✅ 与 Flyway 兼容的 History 表结构
- ✅ Spring Boot Starter 自动配置
- ✅ 多数据库支持
- ✅ 可独立使用，也可嵌入 Spring Boot 项目

## 技术栈

### 核心技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 1.8+ | 必须兼容 Java 8 |
| Spring Boot | 2.x / 3.x | Starter 同时兼容 |
| JDBC | 标准 JDBC | 核心运行时依赖 |
| SLF4J | 1.7.x | 日志门面 |
| Maven | 3.x | 构建与发布工具 |

### 测试技术

| 技术 | 用途 |
|------|------|
| JUnit 4 | 单元测试 |
| H2 | 集成测试数据库 |
| Jacoco | 覆盖率校验 |

## 项目目标

### 已完成

- [x] 核心 SQL 迁移功能
- [x] 语义化版本号支持
- [x] CRC32 Checksum 校验
- [x] Spring Boot Starter
- [x] 动态数据源支持
- [x] 达梦 PL/SQL 块支持
- [x] 发布到 Maven 仓库
- [x] 当前覆盖率超过 70% 质量门槛

### 进行中

- [ ] 持续完善测试边界覆盖
- [ ] 继续完善文档与示例
- [ ] 开始第二阶段性能优化

### 后续规划

- [ ] CLI 工具
- [ ] Plugin 工具链
- [ ] 回滚与钩子等高级功能

## 项目结构

```text
flyway-digital/
├── flyway-digital-core/
├── flyway-digital-spring-boot-starter/
└── flyway-digital-samples/
```

## 当前项目现状

- **版本**: `1.3.6.1`
- **阶段**: 第二阶段进行中
- **发布状态**: 已发布到 Maven 仓库
- **测试状态**: `flyway-digital-core verify` 已通过
- **测试总数**: `94`
- **总行覆盖率**: 约 `84.78%`

### Maven 坐标

```xml
<dependency>
    <groupId>com.cbkj.infrastructure</groupId>
    <artifactId>flyway-digital-spring-boot-starter</artifactId>
    <version>1.3.6.1</version>
</dependency>
```

---

**最后更新**: 2026-03-06  
**维护者**: cbkj
