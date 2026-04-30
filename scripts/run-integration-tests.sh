#!/bin/bash
# Flyway Digital 集成测试运行脚本
# 在真实数据库上执行 SQL 升级脚本测试

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_RESOURCES="$PROJECT_ROOT/flyway-digital-core/src/test/resources/integration"

echo "=== Flyway Digital 真实数据库集成测试 ==="

# 检查数据库容器是否运行
check_containers() {
    echo "检查数据库容器状态..."

    MYSQL_RUNNING=$(docker ps --format '{{.Names}}' | grep -c '^flyway_mysql57$' || echo 0)
    PG_RUNNING=$(docker ps --format '{{.Names}}' | grep -c '^flyway_postgres$' || echo 0)
    DM8_RUNNING=$(docker ps --format '{{.Names}}' | grep -c '^flyway_dm8$' || echo 0)

    if [ "$MYSQL_RUNNING" -eq 0 ]; then
        echo "警告: MySQL 5.7 容器未运行，跳过 MySQL 测试"
    fi

    if [ "$PG_RUNNING" -eq 0 ]; then
        echo "警告: PostgreSQL 容器未运行，跳过 PostgreSQL 测试"
    fi

    if [ "$DM8_RUNNING" -eq 0 ]; then
        echo "警告: 达梦 DM8 容器未运行，跳过达梦测试"
    fi
}

# 运行 Maven 集成测试
run_integration_tests() {
    echo ""
    echo ">>> 执行集成测试..."

    cd "$PROJECT_ROOT"

    # 使用 Maven profile 运行集成测试
    # 需要在 pom.xml 中配置 integration-test profile

    if [ "$MYSQL_RUNNING" -gt 0 ]; then
        echo ""
        echo "=== MySQL 5.7 测试 ==="
        mvn -pl flyway-digital-core test -Dtest=IntegrationTestMySQL \
            -Ddb.url=jdbc:mysql://localhost:3307/flyway_test \
            -Ddb.user=flyway \
            -Ddb.password=flyway123
    fi

    if [ "$PG_RUNNING" -gt 0 ]; then
        echo ""
        echo "=== PostgreSQL 测试 ==="
        mvn -pl flyway-digital-core test -Dtest=IntegrationTestPostgreSQL \
            -Ddb.url=jdbc:postgresql://localhost:5433/flyway_test \
            -Ddb.user=flyway \
            -Ddb.password=flyway123
    fi

    if [ "$DM8_RUNNING" -gt 0 ]; then
        echo ""
        echo "=== 达梦 DM8 测试 ==="
        mvn -pl flyway-digital-core test -Dtest=IntegrationTestDM8 \
            -Ddb.url=jdbc:dm://localhost:5237 \
            -Ddb.user=SYSDBA \
            -Ddb.password=SYSDBA
    fi
}

# 重置数据库以备下次测试
reset_databases() {
    echo ""
    echo ">>> 重置数据库..."
    "$SCRIPT_DIR/reset-databases.sh"
}

# 主流程
check_containers
run_integration_tests
reset_databases

echo ""
echo "=== 集成测试完成 ==="