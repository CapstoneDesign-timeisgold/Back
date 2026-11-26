package jiki.jiki.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

public final class HttpLoad {
    private HttpLoad() {}

    public static HttpClient client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static Result forDuration(int users, Duration duration, HttpRequest request, HttpClient client,
                                     Predicate<HttpResponse<String>> check) throws Exception {
        return run(users, duration.plusSeconds(40), request, client, check, elapsed -> elapsed < duration.toNanos());
    }

    public static Result forIterations(int users, int iterations, Duration timeout, HttpRequest request,
                                       HttpClient client, Predicate<HttpResponse<String>> check) throws Exception {
        AtomicInteger issued = new AtomicInteger();
        return run(users, timeout, request, client, check, elapsed -> issued.getAndIncrement() < iterations);
    }

    private static Result run(int users, Duration timeout, HttpRequest request, HttpClient client,
                              Predicate<HttpResponse<String>> check, LongPredicate nextRequest) throws Exception {
        AtomicLong start = new AtomicLong();
        CyclicBarrier ready = new CyclicBarrier(users, () -> start.set(System.nanoTime()));
        Callable<List<Sample>> worker = () -> {
            List<Sample> samples = new ArrayList<>();
            ready.await();
            while (nextRequest.test(System.nanoTime() - start.get())) {
                long requestStart = System.nanoTime();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long latency = System.nanoTime() - requestStart;
                samples.add(new Sample(requestStart - start.get(), latency, response.statusCode(), check.test(response)));
            }
            return samples;
        };

        ExecutorService pool = Executors.newFixedThreadPool(users);
        try {
            List<Callable<List<Sample>>> workers = new ArrayList<>();
            for (int user = 0; user < users; user++) {
                workers.add(worker);
            }
            List<Future<List<Sample>>> futures = pool.invokeAll(workers, timeout.toNanos(), TimeUnit.NANOSECONDS);
            long elapsed = System.nanoTime() - start.get();
            List<Sample> samples = new ArrayList<>();
            for (Future<List<Sample>> future : futures) {
                if (future.isCancelled()) {
                    throw new IllegalStateException("HTTP load exceeded " + timeout);
                }
                samples.addAll(future.get());
            }
            if (samples.isEmpty()) {
                throw new IllegalStateException("HTTP load produced no samples");
            }
            return new Result(List.copyOf(samples), summarize(users, elapsed, samples));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Summary summarize(int users, long elapsed, List<Sample> samples) {
        long[] latencies = samples.stream().mapToLong(Sample::latencyNanos).sorted().toArray();
        Map<Integer, Integer> statuses = new TreeMap<>();
        int failedChecks = 0;
        for (Sample sample : samples) {
            statuses.merge(sample.status(), 1, Integer::sum);
            if (!sample.passed()) {
                failedChecks++;
            }
        }
        double seconds = elapsed / 1_000_000_000.0;
        return new Summary(users, samples.size(), seconds, samples.size() / seconds,
                milliseconds(Arrays.stream(latencies).average().orElseThrow()),
                percentile(latencies, 0.50), percentile(latencies, 0.95), percentile(latencies, 0.99),
                milliseconds(latencies[0]), milliseconds(latencies[latencies.length - 1]), failedChecks, statuses);
    }

    private static double percentile(long[] sorted, double fraction) {
        double rank = fraction * (sorted.length - 1);
        long low = sorted[(int) Math.floor(rank)];
        long high = sorted[(int) Math.ceil(rank)];
        return milliseconds(low + (high - low) * (rank - Math.floor(rank)));
    }

    private static double milliseconds(double nanos) {
        return nanos / 1_000_000.0;
    }

    private record Sample(long startNanos, long latencyNanos, int status, boolean passed) {}

    public record Summary(int users, int requests, double elapsedSeconds, double requestsPerSecond,
                          double meanMs, double medianMs, double p95Ms, double p99Ms,
                          double minMs, double maxMs, int failedChecks, Map<Integer, Integer> statusCounts) {}

    public static final class Result {
        private final List<Sample> samples;
        private final Summary summary;

        private Result(List<Sample> samples, Summary summary) {
            this.samples = samples;
            this.summary = summary;
        }

        public Summary summary() {
            return summary;
        }

        public void write(ObjectMapper mapper, Path output, String name) throws IOException {
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve(name + "-summary.json").toFile(), summary);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                    Files.newOutputStream(output.resolve(name + "-samples.csv.gz"))), StandardCharsets.UTF_8))) {
                writer.write("start_ns,latency_ns,status,check_passed\n");
                for (Sample sample : samples) {
                    writer.write(sample.startNanos() + "," + sample.latencyNanos() + "," + sample.status() + "," + sample.passed());
                    writer.newLine();
                }
            }
        }
    }
}
