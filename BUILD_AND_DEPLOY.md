# Flyway Digital 构建与发布规范

> **文档目的**：规范项目的构建和发布流程，确保核心模块正确发布到 Maven 仓库，同时避免不必要的示例模块发布。
>
> **适用对象**：项目维护者、CI/CD 流程、自动化部署脚本

---

## 📋 模块分类

本项目采用多模块结构，模块分为两类：

### 1. 核心模块（需要发布到 Maven 仓库）

| 模块名称 | 说明 | Artifact ID |
|---------|------|-------------|
| `flyway-digital-core` | 核心迁移引擎 | `flyway-digital-core` |
| `flyway-digital-spring-boot-starter` | Spring Boot 自动配置 Starter | `flyway-digital-spring-boot-starter` |

**为什么需要发布**：
- 这些是供其他项目依赖使用的库
- 用户通过 Maven 依赖引入这些模块
- 需要版本管理和 artifact 分发

### 2. 示例模块（**不需要**发布到 Maven 仓库）

| 模块名称 | 说明 | Artifact ID |
|---------|------|-------------|
| `spring-boot-sample` | Spring Boot 集成示例 | `spring-boot-sample` |
| `standalone-sample` | 独立使用示例 | `standalone-sample` |

**为什么不需要发布**：
- 这些是演示用途的示例项目
- 不会被其他项目作为依赖引用
- 发布会浪费存储空间和构建时间
- 已配置 `maven.deploy.skip=true` 跳过部署

---

## 🚀 发布流程

### 手动发布（本地开发环境）

#### 1. 只发布核心模块（推荐）

```bash
# 命令说明：
# -pl：指定要构建的模块列表（只构建核心模块）
# -am：also-make，自动构建指定模块依赖的其他模块
# -DskipTests：跳过测试（发布前应在本地验证测试通过）

mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

#### 2. 发布整个项目（包含示例模块）- **不推荐**

```bash
# 警告：这会尝试发布所有模块，包括示例模块
# 示例模块已配置跳过部署，但仍会被构建

mvn clean deploy -DskipTests
```

#### 3. 跳过示例模块发布

```bash
# 使用 -pl 和 ! 符号排除特定模块
# 这种方法更清晰，显式声明哪些模块不发布

mvn clean deploy -DskipTests \
    -pl '!spring-boot-sample,!standalone-sample'
```

---

### CI/CD 自动化发布（GitHub Actions/GitLab CI）

#### 推荐配置

```yaml
# .github/workflows/deploy.yml 示例
name: Deploy to Maven Repository

on:
  push:
    tags:
      - 'v*'  # 当推送 v 开头的标签时触发，如 v1.2.1

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 8
        uses: actions/setup-java@v3
        with:
          java-version: '8'
          distribution: 'temurin'
          
      - name: Configure Maven settings
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << 'EOF'
          <settings>
            <servers>
              <server>
                <id>maven-releases</id>
                <username>${{ secrets.MAVEN_USERNAME }}</username>
                <password>${{ secrets.MAVEN_PASSWORD }}</password>
              </server>
            </servers>
          </settings>
          EOF
          
      - name: Run tests
        run: mvn clean test
        
      - name: Deploy to Maven repository
        # 关键：只部署核心模块，跳过示例模块
        run: |
          mvn clean deploy -DskipTests \
            -pl flyway-digital-core,flyway-digital-spring-boot-starter \
            -am
```

---

## ⚙️ 项目配置说明

### 示例模块如何跳过部署

在 `spring-boot-sample/pom.xml` 和 `standalone-sample/pom.xml` 中已配置：

```xml
<properties>
    <!-- 跳过部署，示例模块不需要发布到 Maven 仓库 -->
    <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

**效果**：
- 执行 `mvn deploy` 时，这些模块会被跳过
- 不会生成和上传 artifact 到远程仓库
- 本地 `mvn install` 仍然有效（用于本地测试）

---

## 📝 版本号管理

### 版本号格式

采用语义化版本（Semantic Versioning）：

```
主版本号.次版本号.修订号
  X     .   Y    .   Z
```

- **X（主版本号）**：不兼容的 API 修改
- **Y（次版本号）**：向下兼容的功能新增
- **Z（修订号）**：向下兼容的问题修正

### 版本号更新场景

| 场景 | 版本变化 | 示例 |
|-----|---------|------|
| Bug 修复 | 修订号 +1 | 1.2.1 → 1.2.2 |
| 新功能（兼容） | 次版本号 +1 | 1.2.1 → 1.3.0 |
| 破坏性变更 | 主版本号 +1 | 1.2.1 → 2.0.0 |

---

## 🐛 常见问题

### Q1: 为什么示例模块部署失败？

**错误信息**：
```
Failed to deploy artifacts: Could not transfer artifact ... 400 cannot be updated
```

**原因**：
- 示例模块的版本号（如 1.1.0）已存在于 Maven 仓库
- Maven 仓库不允许覆盖已发布的版本

**解决方案**：
1. 更新示例模块的版本号（通常跟随核心模块版本）
2. 或者直接跳过示例模块的部署（推荐，因为示例模块不需要发布）

### Q2: 如何只部署核心模块？

**命令**：
```bash
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

### Q3: 发布前需要执行测试吗？

**推荐流程**：
```bash
# 1. 先运行测试（确保代码质量）
mvn clean test

# 2. 测试通过后，再部署（跳过重复测试）
mvn clean deploy -DskipTests \
    -pl flyway-digital-core,flyway-digital-spring-boot-starter \
    -am
```

---

## 📚 相关文档

- [README.md](README.md) - 项目简介和快速开始
- [README-DEV.md](README-DEV.md) - 开发者详细文档
- [DYNAMIC_DATASOURCE_GUIDE.md](DYNAMIC_DATASOURCE_GUIDE.md) - 动态数据源配置指南
- [RELEASE_NOTES_1.2.0.md](RELEASE_NOTES_1.2.0.md) - 版本发布说明

---

**最后更新**：2025-02-11

**维护者**：cbkj

**许可证**：Apache License 2.0
