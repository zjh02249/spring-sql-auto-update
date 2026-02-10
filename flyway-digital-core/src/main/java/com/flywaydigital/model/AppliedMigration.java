package com.cbkj.infrastructure.model;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * 已应用的迁移记录
 * 对应flyway_digital_history表中的一条记录
 */
public class AppliedMigration {

    private int installedRank;
    private String version;
    private String description;
    private String type;
    private String script;
    private Integer checksum;
    private String installedBy;
    private Timestamp installedOn;
    private int executionTime;
    private boolean success;

    public AppliedMigration() {
    }

    public AppliedMigration(int installedRank, String version, String description,
                            String type, String script, Integer checksum, String installedBy,
                            Timestamp installedOn, int executionTime, boolean success) {
        this.installedRank = installedRank;
        this.version = version;
        this.description = description;
        this.type = type;
        this.script = script;
        this.checksum = checksum;
        this.installedBy = installedBy;
        this.installedOn = installedOn;
        this.executionTime = executionTime;
        this.success = success;
    }

    public int getInstalledRank() {
        return installedRank;
    }

    public void setInstalledRank(int installedRank) {
        this.installedRank = installedRank;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Integer getChecksum() {
        return checksum;
    }

    public void setChecksum(Integer checksum) {
        this.checksum = checksum;
    }

    public String getInstalledBy() {
        return installedBy;
    }

    public void setInstalledBy(String installedBy) {
        this.installedBy = installedBy;
    }

    public Timestamp getInstalledOn() {
        return installedOn;
    }

    public void setInstalledOn(Timestamp installedOn) {
        this.installedOn = installedOn;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(int executionTime) {
        this.executionTime = executionTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppliedMigration that = (AppliedMigration) o;
        return installedRank == that.installedRank &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(installedRank, version);
    }

    @Override
    public String toString() {
        return "AppliedMigration{" +
                "installedRank=" + installedRank +
                ", version='" + version + '\'' +
                ", description='" + description + '\'' +
                ", script='" + script + '\'' +
                ", checksum=" + checksum +
                ", installedBy='" + installedBy + '\'' +
                ", installedOn=" + installedOn +
                ", executionTime=" + executionTime +
                ", success=" + success +
                '}';
    }
}
