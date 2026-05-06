package com.trafficlab;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncVsAsyncTest {

    private static final int CONCURRENT = 50;
    private static final String REQUEST_BODY = """
            {"productName": "테스트 상품", "amount": 10000}
            """;

    @LocalServerPort int port;

    @Test
    void compareSyncVsAsync() throws InterruptedException {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(java.time.Duration.ofSeconds(60))
                .build();

        long[] sync = measure(client, "/api/demo/orders/sync");
        long[] async = measure(client, "/api/demo/orders/async");

        printResult("동기 처리 (알림+적립금 응답 포함)", sync);
        printResult("비동기 처리 (주문 저장 후 즉시 반환)", async);
        printComparison(sync, async);
    }

    private long[] measure(WebTestClient client, String uri) throws InterruptedException {
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
                    client.post().uri(uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(REQUEST_BODY)
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

    private void printResult(String label, long[] sorted) {
        System.out.printf("""
            ┌─────────────────────────────────────────────┐
            │ %-43s │
            ├──────────┬──────────────────────────────────┤
            │ 최소     │ %31dms │
            │ 평균     │ %31dms │
            │ p50      │ %31dms │
            │ p95      │ %31dms │
            │ p99      │ %31dms │
            │ 최대     │ %31dms │
            └──────────┴──────────────────────────────────┘
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

    private void printComparison(long[] sync, long[] async) {
        System.out.printf("""
            ┌──────────────────────────────────────────────────────┐
            │ Before / After 비교                                  │
            ├──────────┬──────────────┬──────────────┬────────────┤
            │ 지표     │     동기     │    비동기    │      개선율│
            ├──────────┼──────────────┼──────────────┼────────────┤
            │ 평균     │ %10dms │ %10dms │ %8.1fx │
            │ p50      │ %10dms │ %10dms │ %8.1fx │
            │ p95      │ %10dms │ %10dms │ %8.1fx │
            │ p99      │ %10dms │ %10dms │ %8.1fx │
            │ 최대     │ %10dms │ %10dms │ %8.1fx │
            └──────────┴──────────────┴──────────────┴────────────┘
            %n""",
                avg(sync),              avg(async),             ratio(avg(sync), avg(async)),
                percentile(sync, 50),   percentile(async, 50),  ratio(percentile(sync, 50), percentile(async, 50)),
                percentile(sync, 95),   percentile(async, 95),  ratio(percentile(sync, 95), percentile(async, 95)),
                percentile(sync, 99),   percentile(async, 99),  ratio(percentile(sync, 99), percentile(async, 99)),
                sync[sync.length - 1],  async[async.length - 1], ratio(sync[sync.length - 1], async[async.length - 1])
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
