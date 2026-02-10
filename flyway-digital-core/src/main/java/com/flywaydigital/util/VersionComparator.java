package com.cbkj.infrastructure.util;

import com.cbkj.infrastructure.model.MigrationVersion;

import java.util.Comparator;

/**
 * 版本比较器
 * 用于对MigrationVersion进行排序
 */
public class VersionComparator implements Comparator<MigrationVersion> {

    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {
        // 单例模式
    }

    @Override
    public int compare(MigrationVersion v1, MigrationVersion v2) {
        if (v1 == null && v2 == null) {
            return 0;
        }
        if (v1 == null) {
            return -1;
        }
        if (v2 == null) {
            return 1;
        }
        return v1.compareTo(v2);
    }
}
