package com.cbkj.infrastructure.model;

import java.util.Objects;

/**
 * SQL迁移对象
 * 表示一个待执行的SQL迁移文件
 */
public class SqlMigration implements Comparable<SqlMigration> {

    /**
     * 版本号
     */
    private final MigrationVersion version;

    /**
     * 描述
     */
    private final String description;

    /**
     * 脚本文件名
     */
    private final String script;

    /**
     * 脚本内容
     */
    private final String sqlContent;

    /**
     * Checksum (CRC32)
     */
    private final int checksum;

    /**
     * 资源路径
     */
    private final String location;

    public SqlMigration(MigrationVersion version, String description, String script,
                        String sqlContent, int checksum, String location) {
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.script = Objects.requireNonNull(script, "Script cannot be null");
        this.sqlContent = Objects.requireNonNull(sqlContent, "SqlContent cannot be null");
        this.checksum = checksum;
        this.location = Objects.requireNonNull(location, "Location cannot be null");
    }

    public MigrationVersion getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getScript() {
        return script;
    }

    public String getSqlContent() {
        return sqlContent;
    }

    public int getChecksum() {
        return checksum;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public int compareTo(SqlMigration other) {
        return this.version.compareTo(other.version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqlMigration that = (SqlMigration) o;
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public String toString() {
        return "SqlMigration{" +
                "version=" + version +
                ", description='" + description + '\'' +
                ", script='" + script + '\'' +
                ", checksum=" + checksum +
                ", location='" + location + '\'' +
                '}';
    }
}
