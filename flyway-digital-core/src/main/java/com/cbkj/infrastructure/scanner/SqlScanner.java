package com.cbkj.infrastructure.scanner;

import com.cbkj.infrastructure.model.MigrationVersion;
import com.cbkj.infrastructure.model.SqlMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL文件扫描器
 * 负责扫描指定位置的SQL迁移文件
 *
 * 重构后职责：
 * 1. 协调各个专门的扫描器（文件系统、JAR、索引）
 * 2. 管理扫描流程
 * 3. 版本号重复检查
 * 4. 结果排序
 */
public class SqlScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlScanner.class);
    private static final Map<String, CacheEntry> SCAN_CACHE = new ConcurrentHashMap<String, CacheEntry>();
    private static final AtomicLong CACHE_HITS = new AtomicLong(0L);

    private final String locations;
    private final MigrationFileParser fileParser;
    private final FileSystemScanner fileSystemScanner;
    private final JarScanner jarScanner;

    public SqlScanner(String locations) {
        this.locations = locations;
        this.fileParser = new MigrationFileParser();
        this.fileSystemScanner = new FileSystemScanner();
        this.jarScanner = new JarScanner();
    }

    /**
     * 扫描所有配置的 locations，返回找到的 SQL 迁移文件
     *
     * @return 按版本号排序的 SQL 迁移列表
     */
    public List<SqlMigration> scan() {
        List<String> normalizedLocations = normalizeLocations();
        String cacheKey = buildCacheKey(normalizedLocations);
        ScanFingerprint fingerprint = buildFingerprint(normalizedLocations);

        LOGGER.info("[SQLScanner] Starting scan for locations: {}", locations);

        CacheEntry cachedEntry = SCAN_CACHE.get(cacheKey);
        if (cachedEntry != null && cachedEntry.matches(fingerprint)) {
            CACHE_HITS.incrementAndGet();
            List<SqlMigration> cachedMigrations = copyMigrations(cachedEntry.getMigrations());
            LOGGER.info("[SQLScanner] Cache hit for locations: {}", cacheKey);
            logScanComplete(cachedMigrations);
            return cachedMigrations;
        }

        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        for (String location : normalizedLocations) {
            LOGGER.info("[SQLScanner] Scanning location: {}", location);
            List<SqlMigration> locationMigrations = scanLocation(location);
            migrations.addAll(locationMigrations);
        }

        checkForDuplicateVersions(migrations);
        Collections.sort(migrations);
        SCAN_CACHE.put(cacheKey, new CacheEntry(fingerprint, migrations));

        logScanComplete(migrations);
        return migrations;
    }

    /**
     * 仅供测试使用：清空扫描缓存。
     */
    static void clearCacheForTests() {
        SCAN_CACHE.clear();
        CACHE_HITS.set(0L);
    }

    /**
     * 仅供测试使用：读取缓存命中次数。
     */
    static long getCacheHitCountForTests() {
        return CACHE_HITS.get();
    }

    /**
     * 仅供测试使用：读取缓存条目数量。
     */
    static int getCacheSizeForTests() {
        return SCAN_CACHE.size();
    }

    private List<String> normalizeLocations() {
        List<String> normalized = new ArrayList<String>();
        String[] locationArray = locations.split(",");
        for (String location : locationArray) {
            String trimmed = location.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String buildCacheKey(List<String> normalizedLocations) {
        return String.join(",", normalizedLocations);
    }

    private ScanFingerprint buildFingerprint(List<String> normalizedLocations) {
        List<String> locationFingerprints = new ArrayList<String>();
        for (String location : normalizedLocations) {
            locationFingerprints.add(buildLocationFingerprint(location));
        }
        return new ScanFingerprint(locationFingerprints);
    }

    private String buildLocationFingerprint(String location) {
        if (location.startsWith("classpath:")) {
            return buildClasspathFingerprint(location);
        }
        return buildFileSystemFingerprint(location);
    }

    private String buildClasspathFingerprint(String location) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SqlScanner.class.getClassLoader();
        }
        return "classpath:" + location + "@loader:" + System.identityHashCode(classLoader);
    }

    private String buildFileSystemFingerprint(String path) {
        File directory = new File(path);
        String absolutePath = directory.getAbsolutePath();
        if (!directory.exists()) {
            return "fs:" + absolutePath + ":missing";
        }
        if (!directory.isDirectory()) {
            return "fs:" + absolutePath + ":not-directory";
        }

        List<String> fileFingerprints = new ArrayList<String>();
        collectFileFingerprints(directory, directory, fileFingerprints);
        Collections.sort(fileFingerprints);
        return "fs:" + absolutePath + ":" + String.join(";", fileFingerprints);
    }

    private void collectFileFingerprints(File root, File current, List<String> fileFingerprints) {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectFileFingerprints(root, file, fileFingerprints);
            } else if (file.getName().endsWith(".sql")) {
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                fileFingerprints.add(relativePath + "|" + file.length() + "|" + file.lastModified());
            }
        }
    }

    private List<SqlMigration> copyMigrations(List<SqlMigration> migrations) {
        return new ArrayList<SqlMigration>(migrations);
    }

    private void logScanComplete(List<SqlMigration> migrations) {
        LOGGER.info("[SQLScanner] Scan complete. Found {} migration(s)", migrations.size());
        for (SqlMigration migration : migrations) {
            LOGGER.info("[SQLScanner]   - {}: {}", migration.getVersion(), migration.getScript());
        }
    }

    /**
     * 扫描单个 location
     */
    private List<SqlMigration> scanLocation(String location) {
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();

        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            migrations.addAll(loadFromIndex(path));
        } else {
            migrations.addAll(scanFileSystem(location));
        }

        return migrations;
    }

    /**
     * 从 migration.index 文件加载 SQL 文件列表
     * 构建期已生成该文件，运行期只读取索引，不再扫描目录或 jar
     * 如果 index 文件不存在，降级到传统扫描模式（用于开发和测试环境）
     */
    private List<SqlMigration> loadFromIndex(String path) {
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = SqlScanner.class.getClassLoader();
        }

        String indexPath = path + "/migration.index";
        InputStream indexStream = classLoader.getResourceAsStream(indexPath);
        if (indexStream == null) {
            LOGGER.warn("[SQLScanner] Migration index not found: {}, falling back to traditional scanning", indexPath);
            return scanClasspathFallback(path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
            String fileName;
            while ((fileName = reader.readLine()) != null) {
                fileName = fileName.trim();
                if (fileName.isEmpty()) {
                    continue;
                }

                InputStream sqlStream = classLoader.getResourceAsStream(path + "/" + fileName);
                if (sqlStream == null) {
                    throw new IllegalStateException("Missing SQL file: " + fileName);
                }

                String sqlContent = readInputStream(sqlStream);
                SqlMigration migration = parseMigrationFile(fileName, "classpath:" + path, new MigrationFileParser.ContentProvider() {
                    @Override
                    public String getContent() {
                        return sqlContent;
                    }
                });
                if (migration != null) {
                    migrations.add(migration);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load migration index: " + indexPath, e);
        }

        return migrations;
    }

    /**
     * 传统的 classpath 扫描模式（用于开发和测试环境的 fallback）
     */
    private List<SqlMigration> scanClasspathFallback(String path) {
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = SqlScanner.class.getClassLoader();
            }

            Enumeration<java.net.URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                LOGGER.info("[SQLScanner] Found classpath resource: {}", url);

                if ("file".equals(url.getProtocol())) {
                    File file = new File(url.toURI());
                    migrations.addAll(scanDirectory(file, "classpath:" + path));
                } else if ("jar".equals(url.getProtocol())) {
                    String urlPath = url.getPath();
                    handleJarUrl(urlPath, path, migrations);
                } else {
                    handleJarUrl(url.toString(), path, migrations);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error scanning classpath: {}", path, e);
        }

        return migrations;
    }

    /**
     * 扫描文件系统路径（用于开发环境）
     */
    private List<SqlMigration> scanFileSystem(String path) {
        return fileSystemScanner.scan(path);
    }

    /**
     * 递归扫描目录（用于开发环境）
     */
    private List<SqlMigration> scanDirectory(File directory, String basePath) {
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();

        File[] files = directory.listFiles();
        if (files == null) {
            return migrations;
        }

        for (File file : files) {
            if (file.isDirectory()) {
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
        return parseMigrationFile(fileName, basePath, new MigrationFileParser.ContentProvider() {
            @Override
            public String getContent() throws IOException {
                try (FileInputStream fis = new FileInputStream(file)) {
                    return readInputStream(fis);
                }
            }
        });
    }

    /**
     * 解析迁移文件（通用）
     */
    private SqlMigration parseMigrationFile(String fileName, String location, MigrationFileParser.ContentProvider contentProvider) {
        return fileParser.parse(fileName, location, contentProvider);
    }

    /**
     * 检查版本号重复
     */
    private void checkForDuplicateVersions(List<SqlMigration> migrations) {
        Set<MigrationVersion> versions = new HashSet<MigrationVersion>();
        for (SqlMigration migration : migrations) {
            if (!versions.add(migration.getVersion())) {
                throw new IllegalStateException(
                        "[SQLScanner] Found duplicate migration version: " + migration.getVersion());
            }
        }
    }

    /**
     * 处理 JAR URL（支持标准 JAR 和 Spring Boot nested JAR）
     */
    private void handleJarUrl(String urlPath, String entryPath, List<SqlMigration> migrations) {
        try {
            if (urlPath.contains("BOOT-INF/classes!")) {
                handleBootInfNestedJarUrl(urlPath, entryPath, migrations);
            } else if (urlPath.contains("nested:")) {
                handleNestedJarUrl(urlPath, entryPath, migrations);
            } else {
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
     * 处理 Spring Boot 嵌套 JAR URL 格式: jar:file:/path/to/app.jar!/BOOT-INF/classes!/db/migration
     */
    private void handleBootInfNestedJarUrl(String urlPath, String entryPath, List<SqlMigration> migrations) {
        try {
            LOGGER.info("[SQLScanner] Handling BOOT-INF nested JAR URL: {}", urlPath);

            String remaining = urlPath;
            if (remaining.startsWith("jar:")) {
                remaining = remaining.substring(4);
            }

            String jarPath;
            if (remaining.contains("!")) {
                jarPath = remaining.substring(0, remaining.indexOf("!"));
            } else {
                jarPath = remaining;
            }

            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5);
            }

            LOGGER.info("[SQLScanner] Main JAR path: {}", jarPath);
            migrations.addAll(scanNestedJar(jarPath, "BOOT-INF/classes", entryPath));
        } catch (Exception e) {
            LOGGER.error("[SQLScanner] Error handling BOOT-INF nested JAR URL: {}", urlPath, e);
        }
    }

    /**
     * 处理 Spring Boot nested JAR URL
     * 格式: jar:nested:/path/to/app.jar/!BOOT-INF/classes!/db/migration
     */
    private void handleNestedJarUrl(String urlPath, String entryPath, List<SqlMigration> migrations) {
        try {
            String remaining = urlPath;

            if (remaining.startsWith("jar:")) {
                remaining = remaining.substring(4);
            }
            if (remaining.startsWith("nested:")) {
                remaining = remaining.substring(7);
            }

            int firstBang = remaining.indexOf("!");
            if (firstBang < 0) {
                LOGGER.warn("[SQLScanner] Invalid nested URL format: {}", urlPath);
                return;
            }

            String mainJarPath = remaining.substring(0, firstBang);
            String remainingPath = remaining.substring(firstBang + 1);
            if (remainingPath.startsWith("/")) {
                remainingPath = remainingPath.substring(1);
            }

            String nestedJarPath = null;
            int secondBang = remainingPath.indexOf("!");
            if (secondBang >= 0) {
                nestedJarPath = remainingPath.substring(0, secondBang);
                remainingPath = remainingPath.substring(secondBang + 1);
                if (remainingPath.startsWith("/")) {
                    remainingPath = remainingPath.substring(1);
                }
            }

            LOGGER.info("[SQLScanner] Parsed nested JAR - main: {}, nested: {}, path: {}",
                    mainJarPath, nestedJarPath, remainingPath);

            String fullEntryPath = remainingPath;
            if (entryPath != null && !entryPath.isEmpty() && !fullEntryPath.equals(entryPath)) {
                fullEntryPath = entryPath;
            }

            if (nestedJarPath != null) {
                migrations.addAll(scanNestedJar(mainJarPath, nestedJarPath, fullEntryPath));
            } else {
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
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();

        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
            Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith(entryPath) && name.endsWith(".sql")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    SqlMigration migration = parseMigrationFile(fileName, "classpath:" + entryPath, new MigrationFileParser.ContentProvider() {
                        @Override
                        public String getContent() throws IOException {
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                return readInputStream(is);
                            }
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
     * @param nestedPath 嵌套路徑（如 BOOT-INF/classes）
     * @param entryPath SQL 文件所在路径
     */
    private List<SqlMigration> scanNestedJar(String mainJarPath, String nestedPath, String entryPath) {
        List<SqlMigration> migrations = new ArrayList<SqlMigration>();

        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(mainJarPath)) {
            Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();

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

                if (name.startsWith(searchPrefix) && name.endsWith(".sql")) {
                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    LOGGER.info("[SQLScanner] Found SQL file in nested JAR: {}", name);

                    SqlMigration migration = parseMigrationFile(fileName, "classpath:" + entryPath, new MigrationFileParser.ContentProvider() {
                        @Override
                        public String getContent() throws IOException {
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                return readInputStream(is);
                            }
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
     * 从输入流读取内容
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        return fileParser.readInputStream(inputStream);
    }

    private static final class CacheEntry {
        private final ScanFingerprint fingerprint;
        private final List<SqlMigration> migrations;

        private CacheEntry(ScanFingerprint fingerprint, List<SqlMigration> migrations) {
            this.fingerprint = fingerprint;
            this.migrations = new ArrayList<SqlMigration>(migrations);
        }

        private boolean matches(ScanFingerprint currentFingerprint) {
            return fingerprint.equals(currentFingerprint);
        }

        private List<SqlMigration> getMigrations() {
            return migrations;
        }
    }

    private static final class ScanFingerprint {
        private final List<String> locationFingerprints;

        private ScanFingerprint(List<String> locationFingerprints) {
            this.locationFingerprints = new ArrayList<String>(locationFingerprints);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ScanFingerprint that = (ScanFingerprint) o;
            return locationFingerprints.equals(that.locationFingerprints);
        }

        @Override
        public int hashCode() {
            return locationFingerprints.hashCode();
        }
    }
}