package com.trafficlab;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionPoolTest {

    private static final int CONCURRENT = 100;
    private static final int[] POOL_SIZES = {5, 20, 50, 100};

    @LocalServerPort int port;

    @Test
    void compareConnectionPoolSizes() throws InterruptedException {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        PoolMeasure[] results = new PoolMeasure[POOL_SIZES.length];
        for (int i = 0; i < POOL_SIZES.length; i++) {
            results[i] = measure(client, POOL_SIZES[i]);
            printResult(results[i]);
        }
        printComparison(results);
    }

    private PoolMeasure measure(WebTestClient client, int poolSize) throws InterruptedException {
        client.post()
                .uri("/api/demo/pool/resize?size=" + poolSize)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(PoolStatsResponse.class);
        warmUpPool(client, poolSize);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT);
        CountDownLatch ready = new CountDownLatch(CONCURRENT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT);
        long[] times = new long[CONCURRENT];

        AtomicBoolean sampling = new AtomicBoolean(true);
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger maxWaiting = new AtomicInteger();
        Thread sampler = new Thread(() -> sampleStats(client, sampling, maxActive, maxWaiting));
        sampler.setName("pool-stats-sampler");
        sampler.start();

        for (int i = 0; i < CONCURRENT; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    long startedAt = System.currentTimeMillis();
                    client.get()
                            .uri("/api/demo/heavy-query")
                            .exchange()
                            .expectStatus().is2xxSuccessful()
                            .expectBody(String.class);
                    times[idx] = System.currentTimeMillis() - startedAt;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        sampling.set(false);
        sampler.join(TimeUnit.SECONDS.toMillis(2));
        pool.shutdown();

        Arrays.sort(times);
        return new PoolMeasure(poolSize, times, maxActive.get(), maxWaiting.get());
    }

    private void warmUpPool(WebTestClient client, int poolSize) throws InterruptedException {
        ExecutorService warmUpExecutor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch done = new CountDownLatch(poolSize);
        for (int i = 0; i < poolSize; i++) {
            warmUpExecutor.submit(() -> {
                try {
                    client.get()
                            .uri("/api/demo/heavy-query")
                            .exchange()
                            .expectStatus().is2xxSuccessful()
                            .expectBody(String.class);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        warmUpExecutor.shutdown();

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline) {
            PoolStatsResponse stats = client.get()
                    .uri("/api/demo/pool/stats")
                    .exchange()
                    .expectStatus().is2xxSuccessful()
                    .expectBody(PoolStatsResponse.class)
                    .returnResult()
                    .getResponseBody();

            if (stats != null && stats.totalConnections() >= poolSize) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private void sampleStats(
            WebTestClient client,
            AtomicBoolean sampling,
            AtomicInteger maxActive,
            AtomicInteger maxWaiting
    ) {
        while (sampling.get()) {
            try {
                PoolStatsResponse stats = client.get()
                        .uri("/api/demo/pool/stats")
                        .exchange()
                        .expectStatus().is2xxSuccessful()
                        .expectBody(PoolStatsResponse.class)
                        .returnResult()
                        .getResponseBody();

                if (stats != null) {
                    maxActive.accumulateAndGet(stats.activeConnections(), Math::max);
                    maxWaiting.accumulateAndGet(stats.threadsAwaitingConnection(), Math::max);
                }
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                // Stats sampling must not fail the load measurement.
            }
        }
    }

    private void printResult(PoolMeasure result) {
        long[] sorted = result.sortedTimes();
        System.out.printf("""
            ┌─────────────────────────────────────────────┐
            │ Pool Size %-33d │
            ├──────────────┬──────────────────────────────┤
            │ 최소         │ %27dms │
            │ 평균         │ %27dms │
            │ p50          │ %27dms │
            │ p95          │ %27dms │
            │ p99          │ %27dms │
            │ 최대         │ %27dms │
            │ 최대 Active  │ %29d │
            │ 최대 Waiting │ %29d │
            └──────────────┴──────────────────────────────┘
            %n""",
                result.poolSize(),
                sorted[0],
                avg(sorted),
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted[sorted.length - 1],
                result.maxActiveConnections(),
                result.maxThreadsAwaitingConnection()
        );
    }

    private void printComparison(PoolMeasure[] results) {
        System.out.println("""
            ┌─────────────────────────────────────────────────────────────┐
            │ Pool Size별 비교                                             │
            ├───────────┬──────────┬──────────┬──────────┬───────────────┤
            │ Pool Size │     평균 │      p95 │ 최대대기 │ 기준대비 개선 │
            ├───────────┼──────────┼──────────┼──────────┼───────────────┤""");

        long baselineP95 = percentile(results[0].sortedTimes(), 95);
        for (PoolMeasure result : results) {
            long avg = avg(result.sortedTimes());
            long p95 = percentile(result.sortedTimes(), 95);
            System.out.printf(
                    "            │ %9d │ %7dms │ %7dms │ %8d │ %11.1fx │%n",
                    result.poolSize(),
                    avg,
                    p95,
                    result.maxThreadsAwaitingConnection(),
                    ratio(baselineP95, p95)
            );
        }

        System.out.println("""
            └───────────┴──────────┴──────────┴──────────┴───────────────┘
            """);
    }

    private long avg(long[] sorted) {
        long sum = 0;
        for (long v : sorted) sum += v;
        return sum / sorted.length;
    }

    private long percentile(long[] sorted, int p) {
        int idx = (int) Math.ceil(sorted.length * p / 100.0) - 1;
        return sorted[Math.max(0, idx)];
    }

    private double ratio(long before, long after) {
        return after == 0 ? 0 : (double) before / after;
    }

    private record PoolMeasure(
            int poolSize,
            long[] sortedTimes,
            int maxActiveConnections,
            int maxThreadsAwaitingConnection
    ) {
    }

    private record PoolStatsResponse(
            int activeConnections,
            int idleConnections,
            int threadsAwaitingConnection,
            int totalConnections,
            int maximumPoolSize
    ) {
    }
}
