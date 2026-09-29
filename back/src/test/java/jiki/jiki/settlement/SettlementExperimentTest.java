package jiki.jiki.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("settlement-experiment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false", "spring.datasource.hikari.maximum-pool-size=10",
        "logging.level.root=WARN", "logging.level.org.hibernate=ERROR",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=", "jwt.expiration=3600000"
})
class SettlementExperimentTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url = Objects.requireNonNull(System.getenv("PERF_DB_URL"));
        if (!url.matches("jdbc:mariadb://(localhost|127\\.0\\.0\\.1):[0-9]+/jiki_perf_[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Dedicated local jiki_perf_* database required");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> Objects.requireNonNull(System.getenv("PERF_DB_USER")));
        r.add("spring.datasource.password", () -> Objects.requireNonNull(System.getenv("PERF_DB_PASSWORD")));
    }
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired SettlementService service;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;
    @Autowired JwtProvider jwt;
    @LocalServerPort int port;

    @Test
    void measureConservationConcurrentUpdatesAndRetries() throws Exception {
        String label = System.getProperty("settlement.label");
        assertTrue(label.matches("[a-zA-Z0-9_-]+"));
        boolean after = !label.equals("before");
        Path out = Path.of(System.getProperty("settlement.output"), label);
        Files.createDirectories(out.getParent()); Files.createDirectory(out);
        TransactionTemplate tx = new TransactionTemplate(manager);
        SettlementTestData data = new SettlementTestData(em);
        tx.executeWithoutResult(s -> data.user("admin"));
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("time", java.time.Instant.now().toString());
        metrics.put("java", System.getProperty("java.version"));
        metrics.put("database", jdbc.queryForObject("select version()", String.class));
        metrics.put("isolation", jdbc.queryForObject("select @@tx_isolation", String.class));

        Long remainder = tx.execute(s -> data.promise("remainder", List.of(data.user("r-late")),
                List.of(data.user("r-host"), data.user("r-two"), data.user("r-three")), 1000).getId());
        long sumBefore = sumBalances();
        service.decideRewards("r-host", new RewardDto(remainder));
        long missing = sumBefore - sumBalances();
        metrics.put("remainder_missing_amount", missing);
        metrics.put("remainder_admin_credit", money("admin") - 10_000);
        if (after) { assertEquals(0, missing); assertEquals(10_001, money("admin")); }

        Long allLate = tx.execute(s -> data.promise("all-late", List.of(data.user("all-late-host")), List.of(), 1000).getId());
        long recordsBefore = jdbc.queryForObject("select count(*) from money_record m join site_user u on u.id=m.user_id where u.username='admin'", Long.class);
        service.decideRewards("all-late-host", new RewardDto(allLate));
        long adminRecords = jdbc.queryForObject("select count(*) from money_record m join site_user u on u.id=m.user_id where u.username='admin'", Long.class) - recordsBefore;
        metrics.put("all_late_admin_records", adminRecords);
        if (after) assertEquals(1, adminRecords);

        int inconsistentRounds = 0;
        List<Map<String, Object>> rounds = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                String prefix = "concurrent-" + round;
                List<Long> ids = tx.execute(s -> {
                    SiteUser late = data.user(prefix + "-late");
                    SiteUser host = data.user(prefix + "-host");
                    return List.of(data.promise(prefix + "-a", List.of(late), List.of(host), 1000).getId(),
                            data.promise(prefix + "-b", List.of(late), List.of(host), 1000).getId());
                });
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                long start = System.nanoTime();
                for (Long id : ids) futures.add(pool.submit(() -> new TransactionTemplate(manager).execute(s -> {
                    // Controlled interleaving: both transactions have the same stale user state.
                    // Models entity state already read before a competing settlement commits.
                    users.findByUsername(prefix + "-late").orElseThrow();
                    users.findByUsername(prefix + "-host").orElseThrow();
                    try { barrier.await(10, TimeUnit.SECONDS); }
                    catch (Exception e) { throw new RuntimeException(e); }
                    return service.decideRewards(prefix + "-host", new RewardDto(id));
                })));
                List<String> errors = new ArrayList<>();
                for (Future<?> future : futures) {
                    try { future.get(30, TimeUnit.SECONDS); }
                    catch (Exception e) { errors.add(e.toString()); }
                }
                int lateMoney = money(prefix + "-late"), hostMoney = money(prefix + "-host");
                long records = jdbc.queryForObject("select count(*) from money_record m join site_user u on u.id=m.user_id where u.username in (?,?)", Long.class, prefix + "-late", prefix + "-host");
                boolean correct = lateMoney == 8000 && hostMoney == 12000 && records == 4 && errors.isEmpty();
                if (!correct) inconsistentRounds++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("round", round); row.put("late_balance", lateMoney); row.put("host_balance", hostMoney);
                row.put("records", records); row.put("errors", errors); row.put("correct", correct);
                row.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0); rounds.add(row);
            }
        } finally { pool.shutdownNow(); }
        metrics.put("concurrent_rounds", 20); metrics.put("inconsistent_rounds", inconsistentRounds);
        metrics.put("rounds", rounds);
        if (after) {
            verifySharedAdminAndOppositeTransfers(tx, data);
            metrics.put("shared_admin_opposite_transfer_rounds", 10);
            long mismatches = jdbc.queryForObject("select count(*) from money_record r where r.balance_after_transaction <> 10000 + (select sum(case when m.is_penalty then -m.amount else m.amount end) from money_record m where m.user_id=r.user_id and m.id<=r.id)", Long.class);
            assertEquals(0, mismatches);
            metrics.put("ledger_prefix_mismatches", mismatches);
        }
        Files.writeString(out.resolve("integrity.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));
        if (after) assertEquals(0, inconsistentRounds);

        for (int repetition = 1; repetition <= 3; repetition++) {
            String prefix = "retry-" + repetition;
            Long id = tx.execute(s -> data.promise(prefix, List.of(data.user(prefix + "-late")), List.of(data.user(prefix + "-host")), 1000).getId());
            ProcessBuilder builder = new ProcessBuilder(Objects.requireNonNull(System.getenv("PERF_K6")), "run", "--quiet", "--no-color", "--no-usage-report",
                    "--out", "json=" + out.resolve(prefix + "-samples.json.gz"), Path.of("docs/settlement-experiment/retry.js").toAbsolutePath().toString());
            builder.environment().put("BASE_URL", "http://127.0.0.1:" + port);
            builder.environment().put("TOKEN", jwt.generateToken(prefix + "-host"));
            builder.environment().put("PROMISE_ID", id.toString());
            builder.environment().put("AFTER", String.valueOf(after));
            builder.environment().put("SUMMARY_FILE", out.resolve(prefix + "-summary.json").toString());
            builder.redirectErrorStream(true); builder.redirectOutput(out.resolve(prefix + "-k6.log").toFile());
            Process child = builder.start();
            try {
                assertTrue(child.waitFor(90, TimeUnit.SECONDS)); assertEquals(0, child.exitValue());
            } finally { if (child.isAlive()) child.destroyForcibly(); }
            assertEquals(9000, money(prefix + "-late")); assertEquals(11000, money(prefix + "-host"));
            long records = jdbc.queryForObject("select count(*) from money_record m join site_user u on u.id=m.user_id where u.username in (?,?)", Long.class, prefix + "-late", prefix + "-host");
            assertEquals(2, records);
        }
        System.out.println("SETTLEMENT " + label + ": missing=" + missing + ", adminRecords=" + adminRecords + ", inconsistent=" + inconsistentRounds + "/20");
    }
    private long sumBalances() { return jdbc.queryForObject("select sum(money) from site_user", Long.class); }
    private void verifySharedAdminAndOppositeTransfers(TransactionTemplate tx, SettlementTestData data) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                String prefix = "opposite-" + round;
                List<Long> ids = tx.execute(s -> {
                    SiteUser a = data.user(prefix + "-a"), b = data.user(prefix + "-b");
                    SiteUser c = data.user(prefix + "-c"), d = data.user(prefix + "-d");
                    return List.of(data.promise(prefix + "-one", List.of(a), List.of(b, c, d), 1000).getId(),
                            data.promise(prefix + "-two", List.of(b), List.of(a, c, d), 1000).getId());
                });
                int adminBefore = money("admin");
                long totalBefore = sumBalances();
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    int index = i;
                    futures.add(pool.submit(() -> new TransactionTemplate(manager).execute(s -> {
                        for (String suffix : List.of("-a", "-b", "-c", "-d")) users.findByUsername(prefix + suffix).orElseThrow();
                        users.findByUsername("admin").orElseThrow();
                        try { barrier.await(10, TimeUnit.SECONDS); }
                        catch (Exception e) { throw new RuntimeException(e); }
                        return service.decideRewards(prefix + (index == 0 ? "-b" : "-a"), new RewardDto(ids.get(index)));
                    })));
                }
                for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
                assertEquals(9333, money(prefix + "-a")); assertEquals(9333, money(prefix + "-b"));
                assertEquals(10666, money(prefix + "-c")); assertEquals(10666, money(prefix + "-d"));
                assertEquals(adminBefore + 2, money("admin")); assertEquals(totalBefore, sumBalances());
                assertEquals(10L, jdbc.queryForObject("select count(*) from money_record where promise_id in (?,?)", Long.class, ids.get(0), ids.get(1)));
            }
        } finally { pool.shutdownNow(); }
    }
    private int money(String username) { return jdbc.queryForObject("select money from site_user where username=?", Integer.class, username); }
}
