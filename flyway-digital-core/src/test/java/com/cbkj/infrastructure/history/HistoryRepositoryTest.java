package com.cbkj.infrastructure.history;

import com.cbkj.infrastructure.model.AppliedMigration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * HistoryRepository 仓储测试。
 * 用于验证历史记录的保存、查询、排序和状态判断逻辑。
 */
public class HistoryRepositoryTest {

    private static final String TABLE_NAME = "flyway_digital_history_repo_test";

    private DataSource dataSource;
    private HistoryRepository repository;

    /**
     * 初始化 H2 数据源和历史表，确保每个测试用例都在干净环境中执行。
     */
    @Before
    public void setUp() throws Exception {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:history_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");
        dataSource = h2DataSource;

        HistoryTableManager tableManager = new HistoryTableManager(dataSource, TABLE_NAME);
        tableManager.dropTable();
        tableManager.createTableIfNotExists();

        repository = new HistoryRepository(dataSource, TABLE_NAME);
    }

    /**
     * 验证保存后可以按成功状态、版本号和完整列表进行查询。
     */
    @Test
    public void testSaveAndQueryMethods() throws Exception {
        AppliedMigration success = createMigration(1, "1", true, 100);
        AppliedMigration failed = createMigration(2, "2", false, null);
        repository.save(success);
        repository.save(failed);

        List<AppliedMigration> all = repository.findAll();
        assertEquals(2, all.size());

        List<AppliedMigration> successful = repository.findAllSuccessful();
        assertEquals(1, successful.size());
        assertEquals("1", successful.get(0).getVersion());
        assertEquals(Integer.valueOf(100), successful.get(0).getChecksum());

        AppliedMigration found = repository.findByVersion("2");
        assertNotNull(found);
        assertEquals("failed", found.getDescription());
        assertNull(found.getChecksum());
        assertTrue(repository.existsByVersion("1"));
        assertTrue(repository.existsByVersionAndSuccess("2", false));
        assertFalse(repository.existsByVersionAndSuccess("2", true));
    }

    /**
     * 验证查询不存在的版本时返回 null，且存在性检查为 false。
     */
    @Test
    public void testFindByVersionReturnsNullWhenMissing() throws Exception {
        assertNull(repository.findByVersion("missing"));
        assertFalse(repository.existsByVersion("missing"));
    }

    /**
     * 验证 installed_rank 递增、失败记录识别以及当前用户读取逻辑。
     */
    @Test
    public void testNextInstalledRankFailedMigrationsAndCurrentUser() throws Exception {
        assertEquals(1, repository.getNextInstalledRank());
        assertFalse(repository.hasFailedMigrations());

        repository.save(createMigration(1, "1", true, 100));
        assertEquals(2, repository.getNextInstalledRank());

        repository.save(createMigration(2, "2", false, 200));
        assertTrue(repository.hasFailedMigrations());
        assertNotNull(repository.getCurrentUser());
    }

    /**
     * 构造用于仓储测试的迁移记录。
     */
    private AppliedMigration createMigration(int rank, String version, boolean success, Integer checksum) {
        AppliedMigration migration = new AppliedMigration();
        migration.setInstalledRank(rank);
        migration.setVersion(version);
        migration.setDescription(success ? "success" : "failed");
        migration.setType("SQL");
        migration.setScript("V" + version + "__test.sql");
        migration.setChecksum(checksum);
        migration.setInstalledBy("sa");
        migration.setInstalledOn(new Timestamp(System.currentTimeMillis()));
        migration.setExecutionTime(rank * 10);
        migration.setSuccess(success);
        return migration;
    }
}
