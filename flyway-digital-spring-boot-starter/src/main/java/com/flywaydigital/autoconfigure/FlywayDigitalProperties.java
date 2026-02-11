package com.cbkj.infrastructure.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FlywayDigital 配置属性类
 * 用于从application.yml或application.properties中读取配置
 */
@ConfigurationProperties(prefix = "flyway-digital")
public class FlywayDigitalProperties {

    /**
     * 是否启用迁移，默认true
     */
    private boolean enabled = true;

    /**
     * SQL文件位置，多个路径用逗号分隔
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
     * 是否允许无序迁移（out of order）
     */
    private boolean outOfOrder = false;

    /**
     * 动态数据源场景下，指定要使用的实际数据源 bean 名称
     * 如果不指定，将自动尝试查找 "masterDataSource"、"dataSource" 或第一个可用的数据源
     */
    private String dynamicDatasourceBeanName;

    /**
     * 是否启用调试模式
     * 启用后会输出详细的自动配置加载诊断信息
     */
    private boolean debug = false;

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

    public String getDynamicDatasourceBeanName() {
        return dynamicDatasourceBeanName;
    }

    public void setDynamicDatasourceBeanName(String dynamicDatasourceBeanName) {
        this.dynamicDatasourceBeanName = dynamicDatasourceBeanName;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "FlywayDigitalProperties{" +
                "enabled=" + enabled +
                ", locations='" + locations + '\'' +
                ", table='" + table + '\'' +
                ", baselineOnMigrate=" + baselineOnMigrate +
                ", baselineVersion='" + baselineVersion + '\'' +
                ", validateOnMigrate=" + validateOnMigrate +
                ", outOfOrder=" + outOfOrder +
                ", dynamicDatasourceBeanName='" + dynamicDatasourceBeanName + '\'' +
                ", debug=" + debug +
                '}';
    }
}
