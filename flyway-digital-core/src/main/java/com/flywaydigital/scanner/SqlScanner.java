package com.cbkj.infrastructure.scanner;

import com.cbkj.infrastructure.model.MigrationVersion;
import com.cbkj.infrastructure.model.SqlMigration;
import com.cbkj.infrastructure.util.ChecksumCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL文件扫描器
 * 负责扫描指定位置的SQL迁移文件
 */
public class SqlScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlScanner.class);

    /**
     * SQL文件名匹配模式: V{version}__{description}.sql
     */
    private static final Pattern MIGRATION_PATTERN = Pattern.compile(
            "^V(\\d+(?:\\.\\d+)*)__(.+)\\.sql$",
            Pattern.CASE_INSENSITIVE
    );

    private final String locations;

    public SqlScanner(String locations) {
        this.locations = locations;
    }

    /**
     * 扫描所有配置的locations，返回找到的SQL迁移文件
     *
     * @return 按版本号排序的SQL迁移列表
     */
    public List<SqlMigration> scan() {
        LOGGER.info("[SQLScanner] Starting scan for locations: {}", locations);
        
        List<SqlMigration> migrations = new ArrayList<>();
        String[] locationArray = locations.split(",");

        for (String location : locationArray) {
            location = location.trim();
            if (location.isEmpty()) {
                continue;
            }
            
            LOGGER.info("[SQLScanner] Scanning location: {}", location);
            List<SqlMigration> locationMigrations = scanLocation(location);
            migrations.addAll(locationMigrations);
        }

        // 检查版本号重复
        checkForDuplicateVersions(migrations);

        // 按版本号排序
        Collections.sort(migrations);

        LOGGER.info("[SQLScanner] Scan complete. Found {} migration(s)", migrations.size());
        for (SqlMigration migration : migrations) {
            LOGGER.info("[SQLScanner]   - {}: {}", migration.getVersion(), migration.getScript());
        }

        return migrations;
    }

    /**
     * 扫描单个location
     */
    private List<SqlMigration> scanLocation(String location) {
        List<SqlMigration> migrations = new ArrayList<>();

        // 处理classpath:前缀
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            migrations.addAll(scanClasspath(path));
        } else {
            // 文件系统路径
            migrations.addAll(scanFileSystem(location));
        }

        return migrations;
    }

    /**
     * 扫描类路径
     */
    private List<SqlMigration> scanClasspath(String path) {
        List<SqlMigration> migrations = new ArrayList<>();
        
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = SqlScanner.class.getClassLoader();
            }

            java.util.Enumeration<java.net.URL> resources = classLoader.getResources(path);
            
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                LOGGER.info("[SQLScanner] Found classpath resource: {}", url);
                
                if ("file".equals(url.getProtocol())) {
                    java.io.File file = new java.io.File(url.toURI());
                    migrations.addAll(scanDirectory(file, "classpath:" + path));
                } else if ("jar".equals(url.getProtocol())) {
                    String jarPath = url.getPath();
                    if (jarPath.startsWith("file:")) {
                        jarPath = jarPath.substring(5);
                    }
                    if (jarPath.contains("!")) {
                        jarPath = jarPath.substring(0, jarPath.indexOf("!"));
                    }
                    migrations.addAll(scanJar(jarPath, path));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error scanning classpath: {}", path, e);
        }
        
        return migrations;
    }

    /**
     * 扫描JAR文件
     */
    private List<SqlMigration> scanJar(String jarPath, String entryPath) {
        List<SqlMigration> migrations = new ArrayList<>();
        
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith(entryPath) && name.endsWith(".sql")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    SqlMigration migration = parseMigrationFile(fileName, "classpath:" + entryPath, () -> {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            return readInputStream(is);
                        }
                    });
                    if (migration != null) {
                        migrations.add(migration);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error scanning JAR: {}", jarPath, e);
        }
        
        return migrations;
    }

    /**
     * 扫描文件系统路径
     */
    private List<SqlMigration> scanFileSystem(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            LOGGER.warn("[SQLScanner] Directory does not exist: {}", path);
            return new ArrayList<>();
        }
        if (!directory.isDirectory()) {
            LOGGER.warn("[SQLScanner] Path is not a directory: {}", path);
            return new ArrayList<>();
        }
        return scanDirectory(directory, path);
    }

    /**
     * 递归扫描目录
     */
    private List<SqlMigration> scanDirectory(File directory, String basePath) {
        List<SqlMigration> migrations = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files == null) {
            return migrations;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                migrations.addAll(scanDirectory(file, basePath));
            } else if (file.getName().endsWith(".sql")) {
                SqlMigration migration = parseMigrationFile(file, basePath);
                if (migration != null) {
                    migrations.add(migration);
                }
            }
        }

        return migrations;
    }

    /**
     * 解析迁移文件
     */
    private SqlMigration parseMigrationFile(File file, String basePath) {
        String fileName = file.getName();
        
        // 使用ContentProvider读取文件内容
        return parseMigrationFile(fileName, basePath, () -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                return readInputStream(fis);
            }
        });
    }

    /**
     * 解析迁移文件（通用）
     */
    private SqlMigration parseMigrationFile(String fileName, String location, ContentProvider contentProvider) {
        Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            LOGGER.debug("[SQLScanner] File does not match migration pattern, skipping: {}", fileName);
            return null;
        }

        String versionStr = matcher.group(1);
        String description = matcher.group(2).replace("_", " ");

        try {
            MigrationVersion version = MigrationVersion.parse(versionStr);
            String sqlContent = contentProvider.getContent();
            int checksum = ChecksumCalculator.calculate(sqlContent);

            LOGGER.info("[SQLScanner] Found migration: version={}, script={}", version, fileName);
            
            return new SqlMigration(version, description, fileName, sqlContent, checksum, location);
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error parsing migration file: {}", fileName, e);
            return null;
        }
    }

    /**
     * 读取输入流内容为字符串
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 检查版本号重复
     */
    private void checkForDuplicateVersions(List<SqlMigration> migrations) {
        java.util.Set<MigrationVersion> versions = new java.util.HashSet<>();
        for (SqlMigration migration : migrations) {
            if (!versions.add(migration.getVersion())) {
                throw new IllegalStateException(
                        "[SQLScanner] Found duplicate migration version: " + migration.getVersion());
            }
        }
    }

    /**
     * 内容提供者接口
     */
    @FunctionalInterface
    private interface ContentProvider {
        String getContent() throws IOException;
    }
}
