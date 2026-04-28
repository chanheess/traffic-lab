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
> "공식인 (코어 수 * 2 + 디스크 수)는 출발점일 뿐이고, 실제로는 측정으로 결정했습니다. Pool size를 5, 20, 50, 100으로 변경하며 부하 테스트한 결과 50에서 최적이었고, 100으로 늘리니 DB CPU가 포화되어 오히려 응답 시간이 늘었습니다."

**Q: "Connection이 부족하면 어떤 일이 생기나요?"**
> "요청은 Connection을 받기 위해 대기하고, 대기 시간이 응답 시간에 누적됩니다. HikariCP의 `connectionTimeout`을 넘으면 예외가 발생해 요청 자체가 실패합니다. 그래서 활성 커넥션 수와 대기 커넥션 수를 함께 모니터링하는 게 중요합니다."
