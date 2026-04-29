package com.trafficlab;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IndexEffectTest {

    private static final int CONCURRENT = 50;
    private static final String KEYWORD  = "a";

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbcTemplate;
    WebTestClient restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
        try {
            jdbcTemplate.execute("DROP INDEX idx_post_title ON posts");
        } catch (Exception ignored) {}
    }

    @Test
    void compareIndexOffVsOn() throws InterruptedException {
        long[] off = measure(false);
        long[] on  = measure(true);

        printResult("인덱스 OFF", off);
        printResult("인덱스 ON", on);
        printComparison(off, on);
    }

    private long[] measure(boolean indexEnabled) throws InterruptedException {
        restTemplate.post()
            .uri("/api/demo/index/toggle?enabled=" + indexEnabled)
            .exchange()
            .expectStatus().is2xxSuccessful();

        ExecutorService pool  = Executors.newFixedThreadPool(CONCURRENT);
        CountDownLatch  ready = new CountDownLatch(CONCURRENT);
        CountDownLatch  start = new CountDownLatch(1);
        CountDownLatch  done  = new CountDownLatch(CONCURRENT);
        long[] times = new long[CONCURRENT];

        for (int i = 0; i < CONCURRENT; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    long requestStartedAt = System.currentTimeMillis();
                    boolean ok = restTemplate.get()
                        .uri("/api/demo/posts/search?keyword=" + KEYWORD)
                        .exchange()
                        .returnResult(String.class)
                        .getStatus()
                        .is2xxSuccessful();
                    times[idx] = System.currentTimeMillis() - requestStartedAt;

                    if (!ok) {
                        System.err.println("요청 실패");
                    }
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
        pool.shutdown();
        Arrays.sort(times);
        return times;
    }

    private void printResult(String label, long[] sorted) {
        System.out.printf("""
            ┌─────────────────────────────────────┐
            │ %-35s │
            ├──────────┬──────────────────────────┤
            │ 최소     │ %23dms │
            │ 평균     │ %23dms │
            │ p50      │ %23dms │
            │ p95      │ %23dms │
            │ p99      │ %23dms │
            │ 최대     │ %23dms │
            └──────────┴──────────────────────────┘
            %n""",
            label,
            sorted[0],
            avg(sorted),
            percentile(sorted, 50),
            percentile(sorted, 95),
            percentile(sorted, 99),
            sorted[sorted.length - 1]
        );
    }

    private void printComparison(long[] off, long[] on) {
        System.out.printf("""
            ┌──────────────────────────────────────────────────────┐
            │ Before / After 비교                                  │
            ├──────────┬──────────────┬──────────────┬────────────┤
            │ 지표     │   인덱스 OFF │    인덱스 ON │      개선율│
            ├──────────┼──────────────┼──────────────┼────────────┤
            │ 평균     │ %10dms │ %10dms │ %8.1fx │
            │ p50      │ %10dms │ %10dms │ %8.1fx │
            │ p95      │ %10dms │ %10dms │ %8.1fx │
            │ p99      │ %10dms │ %10dms │ %8.1fx │
            │ 최대     │ %10dms │ %10dms │ %8.1fx │
            └──────────┴──────────────┴──────────────┴────────────┘
            %n""",
            avg(off),          avg(on),          ratio(avg(off), avg(on)),
            percentile(off,50),percentile(on,50), ratio(percentile(off,50),percentile(on,50)),
            percentile(off,95),percentile(on,95), ratio(percentile(off,95),percentile(on,95)),
            percentile(off,99),percentile(on,99), ratio(percentile(off,99),percentile(on,99)),
            off[off.length-1], on[on.length-1],  ratio(off[off.length-1], on[on.length-1])
        );
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
}
