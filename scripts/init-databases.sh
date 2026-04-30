#!/bin/bash
# Flyway Digital 真实数据库测试环境初始化脚本
# 支持 MySQL 5.7、PostgreSQL、达梦 DM8

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=== Flyway Digital 测试数据库环境初始化 ==="

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装，请先安装 Docker"
    exit 1
fi

echo "Docker 版本: $(docker --version)"

# ============ MySQL 5.7 ============
echo ""
echo ">>> 配置 MySQL 5.7..."

# 检查是否已存在
if docker ps -a --format '{{.Names}}' | grep -q '^flyway_mysql57$'; then
    echo "MySQL 5.7 容器已存在，跳过创建"
else
    echo "创建 MySQL 5.7 容器..."
    docker run -d \
        --name flyway_mysql57 \
        -e MYSQL_ROOT_PASSWORD=root123 \
        -e MYSQL_DATABASE=flyway_test \
        -p 3307:3306 \
        mysql:5.7 \
        --character-set-server=utf8mb4 \
        --collation-server=utf8mb4_unicode_ci
fi

# 等待 MySQL 启动
echo "等待 MySQL 5.7 启动..."
sleep 10
until docker exec flyway_mysql57 mysql -uroot -proot123 -e "SELECT 1" &> /dev/null; do
    echo "MySQL 未就绪，等待..."
    sleep 3
done
echo "MySQL 5.7 就绪"

# 创建测试数据库和用户
docker exec flyway_mysql57 mysql -uroot -proot123 -e "
    CREATE DATABASE IF NOT EXISTS flyway_test;
    CREATE USER IF NOT EXISTS 'flyway'@'%' IDENTIFIED BY 'flyway123';
    GRANT ALL PRIVILEGES ON flyway_test.* TO 'flyway'@'%';
    FLUSH PRIVILEGES;
"
echo "MySQL 5.7 配置完成: localhost:3307, 用户 flyway/flyway123, 数据库 flyway_test"

# ============ PostgreSQL ============
echo ""
echo ">>> 配置 PostgreSQL..."

if docker ps -a --format '{{.Names}}' | grep -q '^flyway_postgres$'; then
    echo "PostgreSQL 容器已存在，跳过创建"
else
    echo "创建 PostgreSQL 容器..."
    docker run -d \
        --name flyway_postgres \
        -e POSTGRES_USER=flyway \
        -e POSTGRES_PASSWORD=flyway123 \
        -e POSTGRES_DB=flyway_test \
        -p 5433:5432 \
        postgres:14
fi

# 等待 PostgreSQL 启动
echo "等待 PostgreSQL 启动..."
sleep 5
until docker exec flyway_postgres pg_isready -U flyway &> /dev/null; do
    echo "PostgreSQL 未就绪，等待..."
    sleep 2
done
echo "PostgreSQL 就绪"
echo "PostgreSQL 配置完成: localhost:5433, 用户 flyway/flyway123, 数据库 flyway_test"

# ============ 达梦 DM8 ============
echo ""
echo ">>> 配置达梦 DM8..."

# 达梦 DM8 需要特定镜像，请手动下载或使用已有镜像
# 官方镜像需从达梦官网获取: https://www.dameng.com/
# 社区镜像: dameng/dm8 (如果有)

DM8_IMAGE="dameng/dm8:latest"

if docker images --format '{{.Repository}}:{{.Tag}}' | grep -q "^dameng/dm8"; then
    echo "达梦 DM8 镜像已存在"

    if docker ps -a --format '{{.Names}}' | grep -q '^flyway_dm8$'; then
        echo "达梦 DM8 容器已存在，跳过创建"
    else
        echo "创建达梦 DM8 容器..."
        docker run -d \
            --name flyway_dm8 \
            -p 5237:5236 \
            --privileged \
            dameng/dm8:latest
    fi

    # 等待达梦启动
    echo "等待达梦 DM8 启动（可能需要较长时间）..."
    sleep 30
    echo "达梦 DM8 配置完成: localhost:5237"
else
    echo ""
    echo "警告: 达梦 DM8 镜像不存在"
    echo "请从达梦官网下载 DM8 Docker 镜像: https://www.dameng.com/"
    echo "或联系达梦技术支持获取镜像"
    echo ""
    echo "临时解决方案: 使用 Oracle 兼容模式（H2 数据库模拟）"
    echo "后续测试将跳过达梦真实数据库"
fi

# ============ 输出连接信息 ============
echo ""
echo "=== 测试数据库连接信息 ==="
echo ""
echo "MySQL 5.7:"
echo "  Host: localhost"
echo "  Port: 3307"
echo "  User: flyway"
echo "  Password: flyway123"
echo "  Database: flyway_test"
echo "  JDBC: jdbc:mysql://localhost:3307/flyway_test?useSSL=false&serverTimezone=UTC"
echo ""
echo "PostgreSQL:"
echo "  Host: localhost"
echo "  Port: 5433"
echo "  User: flyway"
echo "  Password: flyway123"
echo "  Database: flyway_test"
echo "  JDBC: jdbc:postgresql://localhost:5433/flyway_test"
echo ""
echo "达梦 DM8 (如果可用):"
echo "  Host: localhost"
echo "  Port: 5237"
echo "  User: SYSDBA (默认)"
echo "  Password: SYSDBA (默认)"
echo "  JDBC: jdbc:dm://localhost:5237"
echo ""

# ============ 创建测试脚本目录 ============
echo ""
echo ">>> 创建测试脚本目录..."
mkdir -p "$PROJECT_ROOT/flyway-digital-core/src/test/resources/integration/mysql"
mkdir -p "$PROJECT_ROOT/flyway-digital-core/src/test/resources/integration/postgresql"
mkdir -p "$PROJECT_ROOT/flyway-digital-core/src/test/resources/integration/dm8"

echo "测试脚本目录已创建"

echo ""
echo "=== 初始化完成 ==="
echo "运行测试: ./scripts/run-integration-tests.sh"
echo "重置数据库: ./scripts/reset-databases.sh"
echo "停止环境: ./scripts/stop-databases.sh"