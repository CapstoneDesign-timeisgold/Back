package jiki.jiki.settlement;

import jakarta.persistence.EntityManager;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.settlement.dto.RewardDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All data/schema changes are confined to an explicitly selected disposable local database. */
@Tag("settlement-mariadb")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false", "spring.datasource.hikari.maximum-pool-size=12",
        "settlement.account.initialize=false", "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=", "jwt.expiration=3600000",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class SettlementMariaDbConcurrencyTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = Objects.requireNonNull(System.getenv("SETTLEMENT_TEST_DB_URL"), "SETTLEMENT_TEST_DB_URL required");
        if (!url.matches("jdbc:mariadb://(localhost|127\\.0\\.0\\.1):[0-9]+/jiki_test_settlement_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Only a disposable local jiki_test_settlement_* database is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> Objects.requireNonNull(System.getenv("SETTLEMENT_TEST_DB_USER")));
        registry.add("spring.datasource.password", () -> Objects.requireNonNull(System.getenv("SETTLEMENT_TEST_DB_PASSWORD")));
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired SettlementRetryService retry;
    @SpyBean SettlementService service;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    private TransactionTemplate tx;
    private SettlementTestData data;

    @BeforeEach void resetSyntheticData() {
        for (String table : List.of("money_record", "participant", "promise", "friend", "site_user")) {
            jdbc.update("delete from " + table);
        }
        jdbc.execute("create table if not exists settlement_retry_probe (id int primary key, amount int not null) engine=InnoDB");
        jdbc.update("delete from settlement_retry_probe");
        jdbc.update("insert into settlement_retry_probe values (1, 0), (2, 0)");
        tx = new TransactionTemplate(manager);
        data = new SettlementTestData(em);
    }

    @Test void schemaActuallyUsesInnoDbAndRejectsDuplicateLedgerEntries() {
        assertEquals(3, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=database() "
                + "and table_name in ('promise','site_user','money_record') and engine='InnoDB'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from (select index_name from information_schema.statistics "
                + "where table_schema=database() and table_name='money_record' and non_unique=0 group by index_name "
                + "having group_concat(column_name order by seq_in_index)='promise_id,user_id,is_penalty') i", Integer.class));
        long id = simplePromise();
        retry.decideRewards("host", new RewardDto(id));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "insert into money_record (promise_id,user_id,is_penalty,amount,balance_after_transaction) "
                        + "select promise_id,user_id,is_penalty,amount,balance_after_transaction from money_record where promise_id=? limit 1", id));
        assertBalancesAndRecords(9000, 11000, 2);
    }

    @Test void tenConcurrentHttpRequestsApplyTheSameSettlementOnlyOnce() throws Exception {
        long id = simplePromise();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CyclicBarrier start = new CyclicBarrier(10);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < 10; i++) requests.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                postSettlement(id).andExpect(status().isNoContent());
                return null;
            }));
            for (Future<?> request : requests) request.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertBalancesAndRecords(9000, 11000, 2);
        var first = service.getPromiseResultDetails(id, "host");
        jdbc.update("update promise set penalty=777 where id=?", id);
        assertEquals(first, retry.decideRewards("host", new RewardDto(id)));
        assertBalancesAndRecords(9000, 11000, 2);
    }

    @Test void differentPromisesSharingAccountsPreserveEveryTransferAndLedgerBalance() throws Exception {
        List<Long> ids = tx.execute(s -> {
            var late = data.user("late"); var host = data.user("host");
            return List.of(data.promise("one", List.of(late), List.of(host), 1000).getId(),
                    data.promise("two", List.of(late), List.of(host), 1000).getId());
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (Long id : ids) requests.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return retry.decideRewards("host", new RewardDto(id));
            }));
            for (Future<?> request : requests) request.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertBalancesAndRecords(8000, 12000, 4);
    }

    @Test void actualPromiseLockTimeoutRetriesWithAFreshPersistenceContextThenSucceeds() throws Exception {
        long id = simplePromise();
        CountDownLatch timedOut = new CountDownLatch(1);
        List<Object> contexts = new CopyOnWriteArrayList<>();
        doAnswer(call -> {
            contexts.add(em.getDelegate());
            try { return call.callRealMethod(); }
            catch (RuntimeException failure) { timedOut.countDown(); throw failure; }
        }).when(service).decideRewards(eq("host"), any());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection blocker = lockRow("promise", id)) {
            Future<?> request = pool.submit(() -> retry.decideRewards("host", new RewardDto(id)));
            try {
                assertTrue(timedOut.await(8, TimeUnit.SECONDS), "Promise locking query must time out, rather than wait indefinitely");
            } finally { blocker.commit(); }
            request.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(2, contexts.size());
        assertNotSame(contexts.get(0), contexts.get(1));
        assertBalancesAndRecords(9000, 11000, 2);
    }

    @Test void actualAccountLockTimeoutExhaustionReturns503AndLeavesEverythingUnchanged() throws Exception {
        long id = simplePromise();
        long hostId = jdbc.queryForObject("select id from site_user where username='host'", Long.class);
        try (Connection blocker = lockRow("site_user", hostId)) {
            postSettlement(id).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("error").value("Settlement is busy; retry the same promise ID"));
            verify(service, times(3)).decideRewards(eq("host"), any());
            assertBalancesAndRecords(10000, 10000, 0);
            assertFalse(jdbc.queryForObject("select is_settled from promise where id=?", Boolean.class, id));
            assertNull(jdbc.queryForObject("select settlement_result from promise where id=?", String.class, id));
            blocker.commit();
        }
        postSettlement(id).andExpect(status().isNoContent());
        assertBalancesAndRecords(9000, 11000, 2);
    }

    @Test void actualDeadlockRollsBackEarlierWritesAndRetriesTheEntireSettlement() throws Exception {
        List<Long> ids = tx.execute(s -> {
            var late = data.user("late"); var host = data.user("host");
            return List.of(data.promise("one", List.of(late), List.of(host), 1000).getId(),
                    data.promise("two", List.of(late), List.of(host), 1000).getId());
        });
        CyclicBarrier firstLocks = new CyclicBarrier(2);
        Map<Long, AtomicInteger> attempts = new ConcurrentHashMap<>();
        // Inject only a test contention probe inside the real transactional service.
        // Opposite lock ordering deliberately creates a real InnoDB deadlock; production keeps sorted locking.
        doAnswer(call -> {
            long id = ((RewardDto) call.getArgument(1)).getPromiseId();
            int attempt = attempts.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
            int first = id == ids.get(0) ? 1 : 2, second = 3 - first;
            jdbc.update("update settlement_retry_probe set amount=amount+1 where id=?", first);
            if (attempt == 1) firstLocks.await(10, TimeUnit.SECONDS);
            jdbc.update("update settlement_retry_probe set amount=amount+1 where id=?", second);
            return call.callRealMethod();
        }).when(service).decideRewards(eq("host"), any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (Long id : ids) requests.add(pool.submit(() -> retry.decideRewards("host", new RewardDto(id))));
            for (Future<?> request : requests) request.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(3, attempts.values().stream().mapToInt(AtomicInteger::get).sum(), "One deadlock victim retries once");
        assertEquals(List.of(2, 2), jdbc.queryForList("select amount from settlement_retry_probe order by id", Integer.class),
                "The victim's first write must roll back before retry");
        assertBalancesAndRecords(8000, 12000, 4);
    }

    private long simplePromise() {
        return tx.execute(s -> data.promise("simple", List.of(data.user("late")), List.of(data.user("host")), 1000).getId());
    }

    private Connection lockRow(String table, long id) throws Exception {
        Connection connection = source.getConnection();
        try {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from " + table + " where id=? for update")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
            }
            return connection;
        } catch (Exception failure) { connection.close(); throw failure; }
    }

    private org.springframework.test.web.servlet.ResultActions postSettlement(long id) throws Exception {
        return mvc.perform(post("/settlement/reward").header("Authorization", "Bearer " + jwt.generateToken("host"))
                .contentType("application/json").content("{\"promiseId\":" + id + "}"));
    }

    private void assertBalancesAndRecords(int late, int host, long records) {
        assertEquals(late, jdbc.queryForObject("select money from site_user where username='late'", Integer.class));
        assertEquals(host, jdbc.queryForObject("select money from site_user where username='host'", Integer.class));
        assertEquals(20000L, jdbc.queryForObject("select sum(money) from site_user", Long.class));
        assertEquals(records, jdbc.queryForObject("select count(*) from money_record", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from money_record r where r.balance_after_transaction <> 10000 + "
                + "(select sum(case when m.is_penalty then -m.amount else m.amount end) from money_record m "
                + "where m.user_id=r.user_id and m.id<=r.id)", Long.class));
    }
}
