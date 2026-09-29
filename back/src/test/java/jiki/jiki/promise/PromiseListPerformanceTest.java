package jiki.jiki.promise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jiki.jiki.security.JwtProvider;
import jiki.jiki.user.SiteUser;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;


import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only. Refuses to initialize Hibernate against any non-benchmark schema. */
@Tag("performance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=false",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.cache.use_query_cache=false",
        "spring.datasource.hikari.maximum-pool-size=10",
        "logging.level.org.hibernate=ERROR",
        "logging.level.root=WARN",
        "jwt.secret=cGVyZm9ybWFuY2UtdGVzdC1vbmx5LWtleS0zMmJ5dGVzISE=",
        "jwt.expiration=3600000"
})
class PromiseListPerformanceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = Objects.requireNonNull(System.getenv("PERF_DB_URL"), "PERF_DB_URL required");
        if (!url.matches("jdbc:mariadb://(localhost|127\\.0\\.0\\.1):[0-9]+/jiki_perf_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Only a local dedicated jiki_perf_* database is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> Objects.requireNonNull(System.getenv("PERF_DB_USER")));
        registry.add("spring.datasource.password", () -> Objects.requireNonNull(System.getenv("PERF_DB_PASSWORD")));
    }

    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PromiseService service;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    final Map<Integer, List<PromiseListDto>> expected = new HashMap<>();
    final LocalDateTime future = LocalDateTime.of(2099, 1, 1, 12, 0);

    @Test
    void benchmarkSqlAndAuthenticatedHttp() throws Exception {
        String label = System.getProperty("perf.label", "manual");
        assertTrue(label.matches("[a-zA-Z0-9_-]+"));
        Path output = Path.of(System.getProperty("perf.output"), label);
        // Keep previous evidence intact: choose a new label for every run.
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> seed());
        Files.writeString(output.resolve("environment.txt"),
                "timestamp=" + java.time.Instant.now() + "\njava=" + System.getProperty("java.version")
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\nmaxHeapBytes=" + Runtime.getRuntime().maxMemory()
                + "\ndatabase=" + tx.execute(s -> em.createNativeQuery("select version()").getSingleResult())
                + "\nloadGenerator=k6 external process; constant VUs; no think time"
                + "\nhikariPool=10\nserver=random port, embedded Tomcat; k6/server/MariaDB on same machine"
                + "\ndataset=10/50/100 accepted; each has 20 pending + 20 declined + 20 cancelled; distinct creator per promise"
                + "\nhttpDataset=100 accepted; same authenticated account for all virtual users"
                + "\nrepetitions=3; warmup=5s per concurrency; measurement=" + System.getProperty("perf.seconds") + "s per repetition\n");

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        Files.writeString(output.resolve("sql.csv"), "scope,accepted,repetition,prepared_statements,returned\n");
        stats.setStatisticsEnabled(true);
        try {
            for (int count : List.of(10, 50, 100)) {
                for (int repetition = 1; repetition <= 3; repetition++) {
                    stats.clear();
                    List<PromiseListDto> actual = service.getPromiseList("perf-" + count);
                    long statements = stats.getPrepareStatementCount();
                    verify(count, actual);
                    append(output.resolve("sql.csv"), "service," + count + "," + repetition + "," + statements + "," + actual.size() + "\n");
                    if (label.startsWith("after")) assertTrue(statements <= 2, "List query must not grow with N");
                }
            }
            HttpClient client = client();
            stats.clear();
            var response = client.send(request(), HttpResponse.BodyHandlers.ofString());
            long statements = stats.getPrepareStatementCount();
            assertEquals(200, response.statusCode());
            verify(100, mapper.readValue(response.body(), new TypeReference<List<PromiseListDto>>() {}));
            append(output.resolve("sql.csv"), "http,100,1," + statements + ",100\n");
        } finally {
            stats.setStatisticsEnabled(false);
        }
        Files.writeString(output.resolve("expected.json"), mapper.writeValueAsString(expected.get(100)));
        for (int vus : List.of(1, 10, 30)) {
            runK6(output, vus, 0, 5);
            for (int repetition = 1; repetition <= 3; repetition++) {
                runK6(output, vus, repetition, Integer.parseInt(System.getProperty("perf.seconds", "10")));
            }
        }
    }

    private void runK6(Path output, int vus, int repetition, int seconds) throws Exception {
        String k6 = Objects.requireNonNull(System.getenv("PERF_K6"), "PERF_K6 executable path required");
        String name = "vus-" + vus + "-run-" + repetition;
        ProcessBuilder process = new ProcessBuilder(k6, "run", "--quiet", "--no-color", "--no-usage-report",
                "--out", "json=" + output.resolve(name + "-samples.json.gz"),
                Path.of("docs/performance/promise-list.js").toAbsolutePath().toString());
        process.environment().put("BASE_URL", "http://127.0.0.1:" + port);
        process.environment().put("TOKEN", jwt.generateToken("perf-100"));
        process.environment().put("VUS", String.valueOf(vus));
        process.environment().put("DURATION", seconds + "s");
        process.environment().put("EXPECTED_FILE", output.resolve("expected.json").toAbsolutePath().toString());
        process.environment().put("SUMMARY_FILE", output.resolve(name + "-summary.json").toAbsolutePath().toString());
        process.redirectErrorStream(true);
        process.redirectOutput(output.resolve(name + "-k6.log").toFile());
        Process child = process.start();
        try {
            assertTrue(child.waitFor(seconds + 60L, java.util.concurrent.TimeUnit.SECONDS), "k6 timed out");
            assertEquals(0, child.exitValue(), "k6 failed; inspect " + name + "-k6.log");
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
        System.out.println("k6 complete: " + name + (repetition == 0 ? " (warmup, excluded)" : ""));
    }
    private void seed() {
        for (int count : List.of(10, 50, 100)) {
            SiteUser guest = user("perf-" + count);
            List<PromiseListDto> rows = new ArrayList<>();
            for (int i = 0; i < count + 60; i++) {
                SiteUser host = user("host-" + count + "-" + i);
                Promise p = new Promise();
                p.setTitle("perf-promise-" + count + "-" + i);
                p.setDate("2099-01-01"); p.setTime("12:00"); p.setCreator(host);
                // Includes legacy null-deadline fallback and settled promises.
                p.setParticipationDeadline(i % 2 == 0 ? future.minusHours(1) : null);
                p.setSettled(i % 7 == 0);
                em.persist(p);
                Participant participant = new Participant();
                participant.setPromise(p); participant.setGuest(guest); participant.setHost(host);
                participant.setStatus(i < count ? ParticipantStatus.ACCEPTED :
                        i < count + 20 ? ParticipantStatus.PENDING : i < count + 40 ? ParticipantStatus.DECLINED : ParticipantStatus.CANCELLED);
                em.persist(participant);
                if (i < count) rows.add(PromiseListDto.builder().promiseId(p.getId()).title(p.getTitle())
                        .date(p.getDate()).time(p.getTime()).creatorUsername(host.getUsername())
                        .participationDeadline(p.getParticipationDeadline() == null ? future : p.getParticipationDeadline())
                        .participationOpen(!p.isSettled()).build());
            }
            expected.put(count, rows);
        }
        em.flush(); em.clear();
    }

    private SiteUser user(String name) {
        SiteUser user = new SiteUser();
        user.setUsername(name); user.setNickname(name); user.setEmail(name + "@example.test");
        user.setPassword("benchmark-not-for-login"); em.persist(user); return user;
    }

    private void verify(int count, List<PromiseListDto> actual) {
        assertEquals(count, actual.size());
        // API has no explicit ordering contract; compare all fields by promise ID.
        assertEquals(expected.get(count).stream().collect(Collectors.toMap(PromiseListDto::getPromiseId, x -> x)),
                actual.stream().collect(Collectors.toMap(PromiseListDto::getPromiseId, x -> x)));
    }

    private HttpClient client() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    }

    private HttpRequest request() {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/promise"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + jwt.generateToken("perf-100"))
                .GET().build();
    }

    private void append(Path path, String value) throws Exception {
        Files.writeString(path, value, StandardOpenOption.APPEND);
    }


}
