package com.trafficlab;

import com.trafficlab.cache.CacheMetrics;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CacheEffectTest {

    private static final int CONCURRENT = 50;

    @LocalServerPort int port;
    WebTestClient client;

    @Autowired
    CacheMetrics cacheMetrics;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build();
        evictAndReset();
    }

    @Test
    void compareCacheOffVsOn() throws InterruptedException {
        long[] off = measureNoCache();

        evictAndReset();
        long[] coldStart = measureCached(); // 캐시 비어있음 → 전부 miss, DB 직접 조회
        long[] on = measureCached();        // 캐시 채워진 이후 → 전부 hit, Redis 반환

        printResult("캐시 OFF (DB 직접 조회)", off);
        printResult("캐시 ON  1라운드 (Cold Start, 전부 miss)", coldStart);
        printResult("캐시 ON  2라운드 (캐시 채워진 이후, 전부 hit)", on);
        printComparison(off, on);
        printCacheMetrics();
    }

    private long[] measureNoCache() throws InterruptedException {
        return measure("/api/demo/posts/popular");
    }

    private long[] measureCached() throws InterruptedException {
        return measure("/api/demo/posts/popular-cached");
    }

    private long[] measure(String uri) throws InterruptedException {
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

                    long t = System.currentTimeMillis();
                    client.get().uri(uri)
                            .exchange()
                            .expectStatus().is2xxSuccessful();
                    times[idx] = System.currentTimeMillis() - t;
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

    private void evictAndReset() {
        client.delete().uri("/api/demo/cache/popular").exchange();
    }

    private void printCacheMetrics() {
        System.out.printf("""
            ┌──────────────────────────────────┐
            │ Cache Hit Ratio                  │
            ├──────────────┬───────────────────┤
            │ Hits         │ %17d │
            │ Misses       │ %17d │
            │ Hit Ratio    │ %16.1f%% │
            └──────────────┴───────────────────┘
            %n""",
                cacheMetrics.getHits(),
                cacheMetrics.getMisses(),
                cacheMetrics.hitRatio()
        );
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
            │ 지표     │   캐시 OFF   │    캐시 ON   │      개선율│
            ├──────────┼──────────────┼──────────────┼────────────┤
            │ 평균     │ %10dms │ %10dms │ %8.1fx │
            │ p50      │ %10dms │ %10dms │ %8.1fx │
            │ p95      │ %10dms │ %10dms │ %8.1fx │
            │ p99      │ %10dms │ %10dms │ %8.1fx │
            │ 최대     │ %10dms │ %10dms │ %8.1fx │
            └──────────┴──────────────┴──────────────┴────────────┘
            %n""",
                avg(off),             avg(on),             ratio(avg(off), avg(on)),
                percentile(off, 50),  percentile(on, 50),  ratio(percentile(off, 50), percentile(on, 50)),
                percentile(off, 95),  percentile(on, 95),  ratio(percentile(off, 95), percentile(on, 95)),
                percentile(off, 99),  percentile(on, 99),  ratio(percentile(off, 99), percentile(on, 99)),
                off[off.length - 1],  on[on.length - 1],   ratio(off[off.length - 1], on[on.length - 1])
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
