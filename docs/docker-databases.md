# Flyway Digital Docker 测试数据库配置

本文档描述如何在本地 Docker 环境中配置真实数据库测试环境，用于验证 SQL 分割逻辑的正确性。

## 概述

Flyway Digital 的 SQL 分割逻辑需要在不同数据库方言上进行真实测试：
- **MySQL 5.7**: DELIMITER 语法、存储过程、触发器
- **PostgreSQL 14**: 函数定义、PL/pgSQL
- **达梦 DM8**: PL/SQL 块、存储过程、触发器

单元测试使用 H2 内存数据库，无法完全模拟真实数据库的语法差异。

## 快速开始

```bash
# 1. 初始化测试数据库环境
./scripts/init-databases.sh

# 2. 运行集成测试
./scripts/run-integration-tests.sh

# 3. 重置数据库（清空数据）
./scripts/reset-databases.sh

# 4. 停止测试环境
./scripts/stop-databases.sh
```

## 数据库连接信息

### MySQL 5.7

| 参数 | 值 |
|------|-----|
| Host | localhost |
| Port | 3307 |
| User | flyway |
| Password | flyway123 |
| Database | flyway_test |
| JDBC URL | `jdbc:mysql://localhost:3307/flyway_test?useSSL=false&serverTimezone=UTC` |

**特点**:
- 支持 DELIMITER 语法
- 支持存储过程和触发器定义
- 字符集: utf8mb4

### PostgreSQL 14

| 参数 | 值 |
|------|-----|
| Host | localhost |
| Port | 5433 |
| User | flyway |
| Password | flyway123 |
| Database | flyway_test |
| JDBC URL | `jdbc:postgresql://localhost:5433/flyway_test` |

**特点**:
- 支持 PL/pgSQL 函数和存储过程
- 支持 $$ 分隔符语法
- 支持 DO 匿名代码块

### 达梦 DM8

| 参数 | 值 |
|------|-----|
| Host | localhost |
| Port | 5237 |
| User | SYSDBA |
| Password | SYSDBA |
| JDBC URL | `jdbc:dm://localhost:5237` |

**注意**: 达梦 DM8 Docker 镜像需从达梦官网获取。

## 测试脚本目录结构

```
flyway-digital-core/src/test/resources/integration/
├── mysql/
│   ├── V1__init.sql           -- 初始化表结构
│   ├── V2__delimiter_proc.sql -- DELIMITER 存储过程测试
│   ├── V3__delimiter_trigger.sql -- DELIMITER 触发器测试
│   ├── V4__quote_escape.sql   -- 引号转义测试
│   └── V5__mixed_complex.sql  -- 混合复杂场景
├── postgresql/
│   ├── V1__init.sql           -- 初始化表结构
│   ├── V2__function.sql       -- 函数定义测试
│   ├── V3__do_block.sql       -- DO 匿名块测试
│   └── V4__mixed_complex.sql  -- 混合复杂场景
└── dm8/
    ├── V1__init.sql           -- 初始化表结构
    ├── V2__plsql_block.sql    -- PL/SQL 块测试
    ├── V3__trigger.sql        -- 触发器定义测试
    └── V4__mixed_complex.sql  -- 混合复杂场景
```

## SQL 测试覆盖场景

### 1. MySQL DELIMITER 语法

```sql
-- 存储过程
DELIMITER ;;
CREATE PROCEDURE my_proc()
BEGIN
  SELECT * FROM users;
  INSERT INTO logs VALUES (1);
END;;
DELIMITER ;

-- 触发器
DELIMITER ;;
CREATE TRIGGER my_trigger
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
  INSERT INTO logs VALUES (NEW.id);
END;;
DELIMITER ;
```

### 2. SQL 标准引号转义

```sql
INSERT INTO users (name) VALUES ('It''s a test');
INSERT INTO users (msg) VALUES ('O''Reilly''s book');
```

### 3. PostgreSQL PL/pgSQL

```sql
-- 函数定义
CREATE OR REPLACE FUNCTION my_func(x INT)
RETURNS INT AS $$
BEGIN
  RETURN x * 2;
END;
$$ LANGUAGE plpgsql;

-- DO 匿名块
DO $$ BEGIN
  INSERT INTO logs VALUES (1);
END $$;
```

### 4. Oracle/达梦 PL/SQL 块

```sql
DECLARE
  v_count INT;
BEGIN
  SELECT COUNT(*) INTO v_count FROM users;
  INSERT INTO logs VALUES (v_count);
END;
```

### 5. 混合复杂场景

- 注释中的分号
- 多层嵌套 BEGIN END
- 字符串中的特殊字符
- 多语句混合

## 手动测试步骤

### 连接到数据库

```bash
# MySQL
docker exec -it flyway_mysql57 mysql -uflyway -pflyway123 flyway_test

# PostgreSQL
docker exec -it flyway_postgres psql -U flyway -d flyway_test

# 达梦 (使用 disql 命令行工具)
docker exec -it flyway_dm8 disql SYSDBA/SYSDBA
```

### 手动执行 SQL 文件

```bash
# MySQL
docker exec -i flyway_mysql57 mysql -uflyway -pflyway123 flyway_test < test.sql

# PostgreSQL
docker exec -i flyway_postgres psql -U flyway -d flyway_test < test.sql
```

## 常见问题

### Q: 达梦 DM8 镜像如何获取？

达梦 DM8 Docker 镜像需从达梦官网下载：https://www.dameng.com/

部分第三方镜像可能存在，但建议使用官方镜像以确保稳定性。

### Q: 容器启动失败？

检查端口是否被占用：
```bash
# Windows
netstat -an | findstr "3307"
netstat -an | findstr "5433"

# Linux/Mac
lsof -i :3307
lsof -i :5433
```

### Q: 如何持久化数据？

测试环境默认不持久化数据（容器删除后数据丢失）。
如需持久化，添加 `-v` 参数：
```bash
docker run -d --name flyway_mysql57 \
    -v mysql_data:/var/lib/mysql \
    ...
```

## 相关文档

- [SQL 分割测试说明](../SQL_SPLIT_TEST.md)
- [项目记忆 - 真实数据库测试流程](~/.claude/projects/*/memory/real-database-testing.md)