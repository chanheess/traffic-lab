# 시나리오 4 - Connection Pool 튜닝

> **카테고리:** 인프라
> **학습 목표:** 동시성 처리의 한계, HikariCP 설정의 의미와 영향

---

## 상황 설명

동시 접속자 수가 증가하면서 응답 시간이 급격히 늘어나는 현상
- 단일 API에 동시 요청 100건
- HikariCP 설정에 따른 처리량 변화 측정

---

## 학습 포인트

1. Connection Pool이 부족할 때 어떤 일이 일어나는가
2. Pool size를 무작정 키우면 안 되는 이유
3. CPU 코어 수와 Pool size의 관계
4. Connection 대기 시간 측정의 중요성

---

## 의사결정 가이드

```
응답 시간이 동시성에 따라 급격히 늘어난다
├─ HikariCP 활성 커넥션이 max에 도달 → pool size 증가 검토
│   ├─ DB CPU 여유 있음 → pool size 늘려도 OK
│   └─ DB CPU 포화 → pool size 늘리면 오히려 악화
├─ 트랜잭션이 길게 유지됨 → 트랜잭션 범위 줄이기
└─ N+1 쿼리로 커넥션 점유 시간 길어짐 → 쿼리 최적화 우선
```

**Pool size 공식 (참고):**
```
pool_size = (코어 수 * 2) + 디스크 수
```
이는 출발점이며 실제로는 **측정으로 결정**해야 함

---

## 구현 계획

### 1. 측정 대상 API

```java
@GetMapping("/api/demo/heavy-query")
public List<Result> heavyQuery() {
    // 의도적으로 100ms 정도 걸리는 쿼리
    return repository.findWithSlowOperation();
}
```

### 2. HikariCP 동적 설정 변경

```java
@RestController
public class PoolController {

    @Autowired
    private HikariDataSource dataSource;

    @PostMapping("/api/demo/pool/resize")
    public void resize(@RequestParam int size) {
        dataSource.setMaximumPoolSize(size);
    }

    @GetMapping("/api/demo/pool/stats")
    public PoolStats getStats() {
        HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
        return PoolStats.builder()
            .activeConnections(bean.getActiveConnections())
            .idleConnections(bean.getIdleConnections())
            .threadsAwaitingConnection(bean.getThreadsAwaitingConnection())
            .totalConnections(bean.getTotalConnections())
            .build();
    }
}
```

### 3. 측정 흐름

| 단계 | Pool Size | 동시 요청 | 측정 항목 |
|---|---|---|---|
| 1 | 5 | 100 | 응답 시간, 대기 커넥션 수 |
| 2 | 20 | 100 | 응답 시간, 대기 커넥션 수 |
| 3 | 50 | 100 | 응답 시간, DB CPU 사용률 |
| 4 | 100 | 100 | 응답 시간 변화 (DB 부하 증가 확인) |

---

## 예상 결과

| Pool Size | p95 응답시간 | 대기 커넥션 | 처리량 |
|---|---|---|---|
| 5 | 2,000ms | 95 | 50 RPS |
| 20 | 200ms | 30 | 400 RPS |
| 50 | 150ms | 0 | 600 RPS |
| 100 | 250ms | 0 | 500 RPS (DB 포화로 오히려 저하) |

**핵심 인사이트:** Pool size를 무작정 늘리면 DB가 포화되어 오히려 느려진다.

---

## Thymeleaf 화면 구성

```html
<div class="container">
    <h2>시나리오 4: Connection Pool 튜닝</h2>

    <div class="situation-card">
        <p>동시 요청 100건 처리 시 HikariCP 설정에 따른 변화를 측정합니다.</p>
    </div>

    <div class="controls">
        <label>Pool Size:
            <input type="number" id="poolSize" value="10">
            <button onclick="updatePool()">적용</button>
        </label>
        <button onclick="runTest()">테스트 실행</button>
    </div>

    <div class="real-time-stats">
        <h4>실시간 Pool 상태</h4>
        <div>활성 커넥션: <span id="active">-</span></div>
        <div>유휴 커넥션: <span id="idle">-</span></div>
        <div>대기 중인 요청: <span id="waiting">-</span></div>
    </div>

    <canvas id="resultChart"></canvas>

    <div class="learning">
        <h3>핵심 인사이트</h3>
        <p>Pool size는 무조건 크다고 좋지 않다. DB 처리 능력의 한계를 넘어서면 오히려 성능이 저하된다.</p>
        <p>최적값은 반드시 <strong>측정</strong>으로 결정해야 한다.</p>
    </div>
</div>
```

---

## 면접 답변 포인트

**Q: "HikariCP pool size는 어떻게 정하셨어요?"**
> "공식인 (코어 수 * 2 + 디스크 수)는 출발점일 뿐이고, 실제로는 측정으로 결정했습니다. Pool size를 5, 20, 50, 100으로 변경하며 부하 테스트했고, 커넥션 대기 수와 p95 응답 시간을 같이 봤습니다. 이번 실험처럼 DB CPU를 거의 쓰지 않는 `SLEEP` 쿼리에서는 pool size를 늘릴수록 대기 시간이 줄었지만, 실제 운영에서는 DB CPU와 쿼리 부하까지 함께 봐야 합니다."

**Q: "Connection이 부족하면 어떤 일이 생기나요?"**
> "요청은 Connection을 받기 위해 대기하고, 대기 시간이 응답 시간에 누적됩니다. HikariCP의 `connectionTimeout`을 넘으면 예외가 발생해 요청 자체가 실패합니다. 그래서 활성 커넥션 수와 대기 커넥션 수를 함께 모니터링하는 게 중요합니다."

---

## 테스트 환경

| 항목 | 사양 |
|---|---|
| OS | macOS |
| Docker | MySQL 8.4 |
| MySQL 최대 커넥션 | 200 (`max-connections=200`) |
| 측정 API | `GET /api/demo/heavy-query` |
| 측정 쿼리 | `SELECT SLEEP(0.1)` |
| 동시 요청 | 100개 |
| 측정 방식 | 전원 준비 후 동시 출발 (`CountDownLatch`) |

---

## 측정 결과

> "Connection Pool은 요청 처리량을 직접 늘리는 마법이 아니라, DB 커넥션 대기 시간을 조절하는 장치다. Pool size는 DB가 감당 가능한 범위 안에서 측정으로 정해야 한다."

┌─────────────────────────────────────────────┐
│ Pool Size 5                                 │
├──────────────┬──────────────────────────────┤
│ 최소         │                         105ms │
│ 평균         │                        1081ms │
│ p50          │                        1026ms │
│ p95          │                        1958ms │
│ p99          │                        2064ms │
│ 최대         │                        2064ms │
│ 최대 Active  │                             5 │
│ 최대 Waiting │                            95 │
└──────────────┴──────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Pool Size 20                                │
├──────────────┬──────────────────────────────┤
│ 최소         │                         105ms │
│ 평균         │                         327ms │
│ p50          │                         313ms │
│ p95          │                         517ms │
│ p99          │                         616ms │
│ 최대         │                         616ms │
│ 최대 Active  │                            20 │
│ 최대 Waiting │                            83 │
└──────────────┴──────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Pool Size 50                                │
├──────────────┬──────────────────────────────┤
│ 최소         │                         106ms │
│ 평균         │                         211ms │
│ p50          │                         211ms │
│ p95          │                         316ms │
│ p99          │                         317ms │
│ 최대         │                         317ms │
│ 최대 Active  │                            35 │
│ 최대 Waiting │                            69 │
└──────────────┴──────────────────────────────┘

┌─────────────────────────────────────────────┐
│ Pool Size 100                               │
├──────────────┬──────────────────────────────┤
│ 최소         │                         105ms │
│ 평균         │                         162ms │
│ p50          │                         167ms │
│ p95          │                         214ms │
│ p99          │                         217ms │
│ 최대         │                         219ms │
│ 최대 Active  │                            51 │
│ 최대 Waiting │                            52 │
└──────────────┴──────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pool Size별 비교                                             │
├───────────┬──────────┬──────────┬──────────┬───────────────┤
│ Pool Size │     평균 │      p95 │ 최대대기 │ 기준대비 개선 │
├───────────┼──────────┼──────────┼──────────┼───────────────┤
│         5 │    1081ms │    1958ms │       95 │         1.0x │
│        20 │     327ms │     517ms │       83 │         3.8x │
│        50 │     211ms │     316ms │       69 │         6.2x │
│       100 │     162ms │     214ms │       52 │         9.1x │
└───────────┴──────────┴──────────┴──────────┴───────────────┘

## 측정을 통해 알게 된 것

### 1. Pool size가 작으면 요청이 커넥션 앞에서 줄을 선다

동시 요청 100개에 pool size 5를 적용하면 한 번에 DB 커넥션을 잡을 수 있는 요청은 5개뿐이다.
나머지 요청은 커넥션을 기다리므로 100ms 쿼리라도 p95가 1958ms까지 늘어난다.

```
동시 요청 100개
→ 커넥션 5개만 즉시 사용
→ 나머지 요청은 HikariCP에서 대기
→ 대기 시간이 응답 시간에 누적
```

### 2. Pool size를 늘리면 커넥션 대기 시간이 줄어든다

Pool size를 5에서 100으로 늘리자 p95가 1958ms에서 214ms로 줄었다.
이번 실험의 병목은 DB CPU가 아니라 커넥션 대기였기 때문에 pool size 증가 효과가 크게 나타났다.

### 3. Pool size만 보고 결론 내리면 안 된다

이번 쿼리는 `SELECT SLEEP(0.1)`이라 DB CPU를 거의 쓰지 않는다.
그래서 pool size 100에서도 성능이 나빠지지 않았다.
실제 서비스 쿼리가 CPU, 디스크 I/O, 락을 많이 사용한다면 pool size를 늘렸을 때 DB가 먼저 포화될 수 있다.

### 4. 봐야 할 지표

- HikariCP `activeConnections`
- HikariCP `threadsAwaitingConnection`
- API p95/p99 응답 시간
- DB CPU 사용률
- DB 락 대기, slow query

결론적으로 pool size는 단독 정답이 아니라 **애플리케이션 대기 시간과 DB 처리 능력을 함께 보면서 정하는 값**이다.
