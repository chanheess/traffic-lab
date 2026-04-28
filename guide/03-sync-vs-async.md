# 시나리오 3 - 동기 vs 비동기 처리

> **카테고리:** 비동기
> **학습 목표:** 비동기화의 적정 시점, 부수 작업과 핵심 작업의 분리 판단

---

## 상황 설명

주문 생성 API
- 주문 저장 (핵심 작업)
- 알림 발송 (부수 작업, 외부 API 호출 - 200~500ms 소요)
- 적립금 계산 (부수 작업)

동기 처리 시 사용자 응답이 느려지는 문제 측정

---

## 학습 포인트

1. 동기 처리의 한계 (외부 의존성 지연이 응답 시간에 직접 영향)
2. 비동기 처리의 적정 시점
3. 비동기화의 트레이드오프 (실패 처리, 순서 보장)
4. `@Async` vs 메시지 큐(Kafka)의 차이

---

## 의사결정 가이드

```
이 작업을 비동기로 처리해야 하나?
├─ 사용자가 즉시 결과를 알 필요 없음 → 비동기 적합
│   ├─ 단순 부수 작업 (알림, 로그) → @Async 충분
│   └─ 신뢰성 중요 (결제, 정산) → 메시지 큐 (Kafka, RabbitMQ)
├─ 외부 API 호출이 응답 시간에 큰 영향 → 비동기화 검토
└─ 트랜잭션 정합성 필요 → 동기 유지 또는 Outbox Pattern
```

---

## 구현 계획

### 1. 동기 버전 (Before)

```java
@PostMapping("/api/demo/orders/sync")
public OrderResponse createOrderSync(@RequestBody OrderRequest req) {
    Order order = orderService.create(req);
    notificationService.send(order);     // 200~500ms 외부 API
    rewardService.calculate(order);      // 100ms DB 작업
    return OrderResponse.from(order);
}
```

### 2. 비동기 버전 (After)

```java
@PostMapping("/api/demo/orders/async")
public OrderResponse createOrderAsync(@RequestBody OrderRequest req) {
    Order order = orderService.create(req);
    eventPublisher.publishEvent(new OrderCreatedEvent(order));
    return OrderResponse.from(order);
}

@Async
@EventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    notificationService.send(event.getOrder());
    rewardService.calculate(event.getOrder());
}
```

### 3. 외부 API 시뮬레이션

```java
@Service
public class NotificationService {
    public void send(Order order) {
        try {
            // 외부 API 호출 시뮬레이션
            Thread.sleep(300 + new Random().nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4. ThreadPool 설정

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

---

## 예상 결과

| 모드 | p95 응답시간 | 처리량 (RPS) | 사용자 경험 |
|---|---|---|---|
| 동기 | 600ms | 80 | 느림 |
| 비동기 | 50ms | 800 | 빠름 |

---

## Thymeleaf 화면 구성

```html
<div class="container">
    <h2>시나리오 3: 동기 vs 비동기 처리</h2>

    <div class="situation-card">
        <p>주문 API: 주문 저장 + 알림 발송 + 적립금 계산</p>
        <p>알림 발송은 외부 API 호출로 200~500ms 소요됩니다.</p>
    </div>

    <div class="controls">
        <button onclick="runTest('sync')">동기 처리 테스트</button>
        <button onclick="runTest('async')">비동기 처리 테스트</button>
    </div>

    <div class="results">
        <canvas id="responseTimeChart"></canvas>
        <table class="comparison">
            <tr><th></th><th>동기</th><th>비동기</th></tr>
            <tr><td>p95 응답시간</td><td id="syncP95">-</td><td id="asyncP95">-</td></tr>
            <tr><td>RPS</td><td id="syncRps">-</td><td id="asyncRps">-</td></tr>
        </table>
    </div>

    <div class="learning">
        <h3>주의: 비동기화의 트레이드오프</h3>
        <ul>
            <li>실패 시 재시도 전략 필요</li>
            <li>순서 보장이 안 됨</li>
            <li>디버깅이 복잡해짐</li>
            <li>트랜잭션 경계가 흐려짐</li>
        </ul>
    </div>
</div>
```

---

## 면접 답변 포인트

**Q: "왜 비동기로 처리했어요?"**
> "주문 저장은 사용자가 즉시 결과를 알아야 하지만, 알림 발송과 적립금 계산은 부수 작업이라 사용자 응답에 포함시킬 필요가 없었습니다. 동기 처리 시 외부 API 호출 지연이 사용자 응답에 그대로 영향을 줘 600ms까지 늘어났는데, 이벤트 기반 비동기 처리로 50ms로 줄였습니다."

**Q: "@Async와 Kafka의 차이는?"**
> "@Async는 같은 JVM 안에서 실행돼 가볍지만 서버 재시작 시 작업이 유실됩니다. Kafka는 별도 인프라가 필요하지만 메시지 영속성과 재처리가 보장됩니다. 단순 알림은 @Async, 결제/정산처럼 신뢰성이 중요한 작업은 Kafka가 적합합니다."
