package com.cbkj.infrastructure.core.config;

/**
 * FlywayDigital 配置类
 * 包含所有迁移相关的配置项
 */
public class FlywayDigitalConfig {

    /**
     * 是否启用迁移
     */
    private boolean enabled = true;

    /**
     * SQL文件位置，支持多个路径，用逗号分隔
     * 例如: classpath:db/migration,classpath:db/migration/mysql
     */
    private String locations = "classpath:db/migration";

    /**
     * History表名
     */
    private String table = "flyway_digital_history";

    /**
     * 是否在首次迁移时创建基线
     */
    private boolean baselineOnMigrate = false;

    /**
     * 基线版本，默认为1
     */
    private String baselineVersion = "1";

    /**
     * 是否校验Checksum
     */
    private boolean validateOnMigrate = true;

    /**
     * 是否允许无序迁移（用于开发环境）
     */
    private boolean outOfOrder = false;

    public FlywayDigitalConfig() {
    }

    public FlywayDigitalConfig(boolean enabled, String locations, String table, 
                               boolean baselineOnMigrate) {
        this.enabled = enabled;
        this.locations = locations;
        this.table = table;
        this.baselineOnMigrate = baselineOnMigrate;
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLocations() {
        return locations;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public boolean isValidateOnMigrate() {
        return validateOnMigrate;
    }

    public void setValidateOnMigrate(boolean validateOnMigrate) {
        this.validateOnMigrate = validateOnMigrate;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public void setOutOfOrder(boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
    }

    @Override
    public String toString() {
        return "FlywayDigitalConfig{" +
                "enabled=" + enabled +
                ", locations='" + locations + '\'' +
                ", table='" + table + '\'' +
                ", baselineOnMigrate=" + baselineOnMigrate +
                ", baselineVersion='" + baselineVersion + '\'' +
                ", validateOnMigrate=" + validateOnMigrate +
                ", outOfOrder=" + outOfOrder +
                '}';
    }
}
