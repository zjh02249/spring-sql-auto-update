#!/bin/bash
# Flyway Digital 测试数据库停止脚本

set -e

echo "=== 停止测试数据库容器 ==="

# MySQL 5.7
if docker ps --format '{{.Names}}' | grep -q '^flyway_mysql57$'; then
    echo "停止 MySQL 5.7..."
    docker stop flyway_mysql57
fi

# PostgreSQL
if docker ps --format '{{.Names}}' | grep -q '^flyway_postgres$'; then
    echo "停止 PostgreSQL..."
    docker stop flyway_postgres
fi

# 达梦 DM8
if docker ps --format '{{.Names}}' | grep -q '^flyway_dm8$'; then
    echo "停止达梦 DM8..."
    docker stop flyway_dm8
fi

echo ""
echo "=== 所有测试数据库已停止 ==="
echo "重新启动: ./scripts/init-databases.sh"