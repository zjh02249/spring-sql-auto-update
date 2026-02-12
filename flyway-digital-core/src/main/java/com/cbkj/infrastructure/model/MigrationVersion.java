package com.cbkj.infrastructure.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 迁移版本号对象
 * 支持多段版本号，如 1.0.0.3
 * 采用语义版本排序（逐段数字比较）
 */
public class MigrationVersion implements Comparable<MigrationVersion> {

    private final String rawVersion;
    private final int[] segments;

    /**
     * 解析版本字符串
     *
     * @param version 版本字符串，如 "1.0.0" 或 "2.0.0.3"
     * @return MigrationVersion对象
     * @throws IllegalArgumentException 如果版本格式无效
     */
    public static MigrationVersion parse(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        String trimmed = version.trim();

        // 验证版本格式：仅允许数字和.
        if (!trimmed.matches("^[0-9]+(\\.[0-9]+)*$")) {
            throw new IllegalArgumentException(
                "Invalid version format: '" + version + "'. Only digits and dots are allowed.");
        }

        return new MigrationVersion(trimmed);
    }

    private MigrationVersion(String rawVersion) {
        this.rawVersion = rawVersion;
        this.segments = parseSegments(rawVersion);
    }

    private int[] parseSegments(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    @Override
    public int compareTo(MigrationVersion other) {
        if (other == null) {
            return 1;
        }

        int minLength = Math.min(this.segments.length, other.segments.length);

        for (int i = 0; i < minLength; i++) {
            int cmp = Integer.compare(this.segments[i], other.segments[i]);
            if (cmp != 0) {
                return cmp;
            }
        }

        // 如果前面都相同，段数多的版本更大
        return Integer.compare(this.segments.length, other.segments.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MigrationVersion that = (MigrationVersion) o;
        return rawVersion.equals(that.rawVersion);
    }

    @Override
    public int hashCode() {
        return rawVersion.hashCode();
    }

    @Override
    public String toString() {
        return rawVersion;
    }

    /**
     * 获取原始版本字符串
     */
    public String getRawVersion() {
        return rawVersion;
    }

    /**
     * 获取版本段数组
     */
    public int[] getSegments() {
        return segments.clone();
    }
}
