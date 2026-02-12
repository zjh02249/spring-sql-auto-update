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
                    // 处理标准 JAR 和 Spring Boot nested JAR
                    String urlPath = url.getPath();
                    handleJarUrl(urlPath, path, migrations);
                } else {
                    // 尝试直接作为 JAR URL 处理
                    handleJarUrl(url.toString(), path, migrations);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error scanning classpath: {}", path, e);
        }
        
        return migrations;
    }

    /**
     * 处理 JAR URL（支持标准 JAR 和 Spring Boot nested JAR）
     */
    private void handleJarUrl(String urlPath, String entryPath, List<SqlMigration> migrations) {
        try {
            // Spring Boot 2.5+ nested JAR 格式: jar:nested:/path/to/app.jar/!BOOT-INF/classes!/db/migration
            // 或: nested:/path/to/app.jar/!BOOT-INF/classes!/db/migration
            if (urlPath.contains("nested:")) {
                handleNestedJarUrl(urlPath, entryPath, migrations);
            } else {
                // 标准 JAR 格式: jar:file:/path/to/app.jar!/db/migration
                String jarPath = urlPath;
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring(5);
                }
                if (jarPath.contains("!")) {
                    jarPath = jarPath.substring(0, jarPath.indexOf("!"));
                }
                migrations.addAll(scanJar(jarPath, entryPath));
            }
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error handling JAR URL: {}", urlPath, e);
        }
    }

    /**
     * 处理 Spring Boot nested JAR URL
     * 格式: jar:nested:/path/to/app.jar/!BOOT-INF/classes!/db/migration
     */
    private void handleNestedJarUrl(String urlPath, String entryPath, List<SqlMigration> migrations) {
        try {
            // 解析 nested URL
            // 格式: jar:nested:/path/app.jar/!BOOT-INF/classes!/db/migration
            // 或: nested:/path/app.jar/!BOOT-INF/classes!/db/migration
            
            String remaining = urlPath;
            
            // 移除 jar: 前缀
            if (remaining.startsWith("jar:")) {
                remaining = remaining.substring(4);
            }
            
            // 移除 nested: 前缀
            if (remaining.startsWith("nested:")) {
                remaining = remaining.substring(7);
            }
            
            // 现在 remaining 是: /path/app.jar/!BOOT-INF/classes!/db/migration
            // 找到第一个 ! 之前的是主 JAR 路径
            int firstBang = remaining.indexOf("!");
            if (firstBang < 0) {
                LOGGER.warn("[SQLScanner] Invalid nested URL format: {}", urlPath);
                return;
            }
            
            // 提取主 JAR 路径
            String mainJarPath = remaining.substring(0, firstBang);
            
            // 提取内部路径（BOOT-INF/classes 之后的部分）
            String remainingPath = remaining.substring(firstBang + 1);
            
            // 移除开头的 /
            if (remainingPath.startsWith("/")) {
                remainingPath = remainingPath.substring(1);
            }
            
            // 如果还有 !，说明有嵌套路径（如 BOOT-INF/classes!/db/migration）
            String nestedJarPath = null;
            int secondBang = remainingPath.indexOf("!");
            if (secondBang >= 0) {
                // 格式: BOOT-INF/classes!/db/migration
                nestedJarPath = remainingPath.substring(0, secondBang);
                remainingPath = remainingPath.substring(secondBang + 1);
                if (remainingPath.startsWith("/")) {
                    remainingPath = remainingPath.substring(1);
                }
            }
            
            LOGGER.info("[SQLScanner] Parsed nested JAR - main: {}, nested: {}, path: {}", 
                    mainJarPath, nestedJarPath, remainingPath);
            
            // 构建完整的 entry path
            String fullEntryPath = remainingPath;
            if (entryPath != null && !entryPath.isEmpty() && !fullEntryPath.equals(entryPath)) {
                // 确保使用正确的路径
                fullEntryPath = entryPath;
            }
            
            // 扫描主 JAR
            if (nestedJarPath != null) {
                // 需要扫描嵌套 JAR 内的内容
                migrations.addAll(scanNestedJar(mainJarPath, nestedJarPath, fullEntryPath));
            } else {
                // 直接扫描主 JAR 内的内容
                migrations.addAll(scanJar(mainJarPath, fullEntryPath));
            }
            
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error handling nested JAR URL: {}", urlPath, e);
        }
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
     * 扫描 Spring Boot 嵌套 JAR
     * 
     * @param mainJarPath 主 JAR 文件路径
     * @param nestedPath 嵌套路径（如 BOOT-INF/classes）
     * @param entryPath SQL 文件所在路径
     */
    private List<SqlMigration> scanNestedJar(String mainJarPath, String nestedPath, String entryPath) {
        List<SqlMigration> migrations = new ArrayList<>();
        
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(mainJarPath)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            
            // 构建完整的搜索路径前缀
            String searchPrefix = nestedPath;
            if (!searchPrefix.endsWith("/")) {
                searchPrefix = searchPrefix + "/";
            }
            if (entryPath != null && !entryPath.isEmpty()) {
                searchPrefix = searchPrefix + entryPath;
            }
            if (!searchPrefix.endsWith("/")) {
                searchPrefix = searchPrefix + "/";
            }
            
            LOGGER.info("[SQLScanner] Scanning nested JAR with prefix: {}", searchPrefix);
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否匹配搜索路径且是 SQL 文件
                if (name.startsWith(searchPrefix) && name.endsWith(".sql")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    LOGGER.info("[SQLScanner] Found SQL file in nested JAR: {}", name);
                    
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
            
            LOGGER.info("[SQLScanner] Scanned nested JAR {}:{}, found {} migration(s)", 
                    mainJarPath, nestedPath, migrations.size());
                    
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error scanning nested JAR: {}:{}", mainJarPath, nestedPath, e);
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
