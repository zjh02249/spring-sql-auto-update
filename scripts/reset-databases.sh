#!/bin/bash
# Flyway Digital 测试数据库重置脚本
# 清空所有表数据，恢复到初始状态，便于重复执行测试

set -e

echo "=== 重置测试数据库 ==="

# ============ MySQL 5.7 ============
echo ""
echo ">>> 重置 MySQL 5.7..."

if docker ps --format '{{.Names}}' | grep -q '^flyway_mysql57$'; then
    echo "清空 flyway_test 数据库..."
    docker exec flyway_mysql57 mysql -uflyway -pflyway123 flyway_test -e "
        SET FOREIGN_KEY_CHECKS = 0;
        -- 删除所有表
        SELECT CONCAT('DROP TABLE IF EXISTS \`', table_name, '\`;')
        FROM information_schema.tables
        WHERE table_schema = 'flyway_test';
        SET FOREIGN_KEY_CHECKS = 1;
    " &> /dev/null || true

    # 执行动态生成的 DROP 语句
    docker exec flyway_mysql57 mysql -uflyway -pflyway123 flyway_test -N -e "
        SELECT CONCAT('DROP TABLE IF EXISTS \`', table_name, '\`;')
        FROM information_schema.tables
        WHERE table_schema = 'flyway_test'
    " | docker exec -i flyway_mysql57 mysql -uflyway -pflyway123 flyway_test

    # 清空 flyway_history 表（如果存在）
    docker exec flyway_mysql57 mysql -uflyway -pflyway123 flyway_test -e "
        DROP TABLE IF EXISTS flyway_history;
    " 2>/dev/null || true

    echo "MySQL 5.7 已重置"
else
    echo "MySQL 5.7 容器未运行，跳过"
fi

# ============ PostgreSQL ============
echo ""
echo ">>> 重置 PostgreSQL..."

if docker ps --format '{{.Names}}' | grep -q '^flyway_postgres$'; then
    echo "清空 flyway_test 数据库..."

    # 删除所有表（包括 flyway_history）
    docker exec flyway_postgres psql -U flyway -d flyway_test -c "
        DO \$\$ DECLARE
            r RECORD;
        BEGIN
            FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
            END LOOP;
        END \$\$;
    "

    echo "PostgreSQL 已重置"
else
    echo "PostgreSQL 容器未运行，跳过"
fi

# ============ 达梦 DM8 ============
echo ""
echo ">>> 重置达梦 DM8..."

if docker ps --format '{{.Names}}' | grep -q '^flyway_dm8$'; then
    echo "清空测试数据库..."
    # 达梦 SQL 语法与 Oracle 类似
    docker exec flyway_dm8 disql SYSDBA/SYSDBA -e "
        -- 删除所有表（需要根据达梦实际语法调整）
        DECLARE
            sql_text VARCHAR(200);
        BEGIN
            FOR rec IN (SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = 'FLYWAY_TEST') DO
                sql_text := 'DROP TABLE ' || rec.TABLE_NAME || ' CASCADE';
                EXECUTE IMMEDIATE sql_text;
            END LOOP;
        END;
    " 2>/dev/null || true
    echo "达梦 DM8 已重置"
else
    echo "达梦 DM8 容器未运行，跳过"
fi

echo ""
echo "=== 重置完成 ==="
echo "可以重新执行测试脚本"