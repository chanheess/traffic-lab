# 시나리오 2 - Redis 캐시 효과 측정

> **카테고리:** 캐싱
> **학습 목표:** 캐시 도입의 적정 시점과 효과 측정, 캐시가 부적합한 경우 학습

---

## 상황 설명

인기 게시글 TOP 10 조회 API
- 조회수 기준 정렬, JOIN 포함 복잡 쿼리
- 변경 빈도 낮음, 조회 빈도 매우 높음
- 동시 사용자 증가 시 DB 부하 측정

---

## 학습 포인트

1. 캐시가 효과적인 데이터의 특성 (조회 빈도 높음, 변경 빈도 낮음)
2. Cache Hit Ratio의 의미와 측정
3. TTL 설정의 트레이드오프 (정합성 vs 성능)
4. 캐시가 오히려 독이 되는 경우 (변경 빈도가 높은 데이터)

---

## 의사결정 가이드

```
캐시를 도입해야 하나?
├─ 변경 빈도 < 조회 빈도 → YES, 적합
│   ├─ 강한 정합성 필요 → 짧은 TTL + 이벤트 기반 무효화
│   └─ 약간의 stale 허용 → 긴 TTL
├─ 변경 빈도 ≥ 조회 빈도 → NO, 캐시 무효화 비용이 더 큼
└─ 사용자별 개인화 데이터 → 신중히 검토 (메모리 부담)
```

---

## 구현 계획

### 1. 측정 대상 API (캐시 OFF)

```java
@GetMapping("/api/demo/posts/popular")
public List<PostDto> getPopularPosts() {
    return postRepository.findTop10ByOrderByViewCountDesc();
}
```

### 2. 캐시 적용 버전

```java
@Cacheable(value = "popularPosts", key = "'top10'")
@GetMapping("/api/demo/posts/popular-cached")
public List<PostDto> getPopularPostsCached() {
    return postRepository.findTop10ByOrderByViewCountDesc();
}
```

### 3. Redis 설정

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.builder(factory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)))
            .build();
    }
}
```

### 4. Cache Hit Ratio 측정

```java
@Component
public class CacheMetrics {
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public double hitRatio() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0 : (double) hits.get() / total * 100;
    }
}
```

### 5. 측정 흐름

1. 캐시 OFF 상태로 200명 동시 요청 → 측정
2. 캐시 ON 후 동일 요청 → 측정 (1회는 miss, 이후 hit)
3. Hit Ratio 함께 표시

---

## 예상 결과

| 상태 | p95 응답시간 | RPS | Cache Hit Ratio |
|---|---|---|---|
| 캐시 OFF | 250ms | 300 | - |
| 캐시 ON | 5ms | 3000+ | 99.5% |

---

## Thymeleaf 화면 구성

```html
<div class="container">
    <h2>시나리오 2: Redis 캐시 효과 측정</h2>

    <div class="situation-card">
        <p>인기 게시글 TOP 10 조회 (변경 빈도 낮음, 조회 빈도 높음)</p>
        <p>이 데이터는 캐시에 적합한 패턴입니다.</p>
    </div>

    <div class="controls">
        <button onclick="runTest('without-cache')">캐시 OFF 테스트</button>
        <button onclick="runTest('with-cache')">캐시 ON 테스트</button>
        <button onclick="clearCache()">캐시 초기화</button>
    </div>

    <div class="results">
        <canvas id="responseTimeChart"></canvas>
        <div class="metrics">
            <div>Cache Hit Ratio: <span id="hitRatio">-</span></div>
            <div>총 요청 수: <span id="totalRequests">-</span></div>
        </div>
    </div>

    <!-- 학습 내용 -->
    <div class="learning">
        <h3>이런 데이터는 캐시에 적합합니다</h3>
        <ul>
            <li>변경 빈도가 낮음</li>
            <li>조회 빈도가 매우 높음</li>
            <li>모든 사용자가 같은 데이터를 봄</li>
        </ul>

        <h3>이런 데이터는 캐시 부적합</h3>
        <ul>
            <li>실시간 가격, 재고</li>
            <li>사용자별 개인화 데이터</li>
            <li>변경 빈도가 조회 빈도보다 높음</li>
        </ul>
    </div>
</div>
```

---

## 트레이드오프 정리

| 항목 | 캐시 도입 효과 | 비용 |
|---|---|---|
| DB 부하 | 99% 감소 | 추가 인프라 (Redis) |
| 응답 시간 | 50배 개선 | 정합성 관리 복잡도 |
| 메모리 | - | Redis 메모리 사용 |
| 운영 | - | 무효화 전략 설계 필요 |

---

## 면접 답변 포인트

**Q: "왜 Redis를 도입했어요?"**
> "단순히 빠르다는 이유가 아니라, 측정을 통해 도입을 결정했습니다. 인기 게시글 조회 API는 변경 빈도가 낮고 조회 빈도가 높아 캐시에 적합한 패턴이었고, 부하 테스트에서 DB 응답이 250ms를 넘는 것을 확인한 후 도입했습니다. 도입 후 응답 시간이 5ms로 개선됐습니다."

**Q: "캐시 무효화는 어떻게 했어요?"**
> "TTL과 이벤트 기반 무효화를 함께 사용했습니다. 게시글 변경 시 `@CacheEvict`로 즉시 무효화하고, 안전장치로 5분 TTL을 설정해 캐시-DB 불일치 시간을 최대 5분으로 제한했습니다."

---

## 측정 결과

> 동시 사용자 50명, 조회수 기준 TOP 10 API 기준

| 지표 | 캐시 OFF | 캐시 ON (Cold Start) | 캐시 ON (Hit) |
|---|---|---|---|
| 평균 | 628ms | 546ms | **19ms** |
| p50 | 626ms | 550ms | **19ms** |
| p95 | 944ms | 868ms | **21ms** |
| p99 | 947ms | 875ms | **21ms** |
| 최대 | 947ms | 875ms | **21ms** |
| 개선율 (평균) | 기준 | - | **33x** |
| 개선율 (p95) | 기준 | - | **45x** |

---

## 측정을 통해 알게 된 것

### 1. 캐시 ON이라도 처음엔 효과 없음 (Cold Start)

캐시가 비어있는 상태에서 50명이 동시에 요청하면 전부 DB로 쏟아진다.
캐시 OFF(628ms)와 Cold Start(546ms)가 비슷한 이유가 이것이다.
이것을 **Cache Stampede** 라고 한다.

```
Redis 비어있음 → 50명 동시 요청 → 전부 DB 조회 → 캐시 의미 없음
```

### 2. 캐시가 채워진 이후 효과가 극적으로 나타남 (2라운드)

한 번 캐시가 채워지면 이후 요청은 전부 Redis 메모리에서 반환된다.
p95 기준 944ms → 21ms, **45배 개선**.

```
DB 조회 (디스크 I/O + 쿼리) → 수백ms
Redis 조회 (메모리)          → 수ms
```

### 3. 캐시 적합 조건

- 변경 빈도 < 조회 빈도
- 약간의 stale 데이터를 허용할 수 있는 경우
- 모든 사용자가 동일한 데이터를 보는 경우 (개인화 데이터는 부적합)

### 4. 단점 및 주의사항

**정합성 문제**: TTL 5분 동안 DB가 바뀌어도 Redis는 옛날 값을 반환한다.

**Cache Stampede**: TTL 만료 순간 동시 요청이 몰리면 전부 DB로 폭증한다.
해결 방법:
- 뮤텍스: 첫 요청만 DB 조회, 나머지는 대기
- stale-while-revalidate: 만료된 값을 일단 반환하고 백그라운드에서 갱신

**운영 복잡도**: 장애 시 DB 문제인지, Redis 문제인지, stale 데이터인지 구분해야 한다.

결과값이 큰 경우 캐싱 자체보다 페이지네이션으로 쪼개거나, 자주 쓰는 첫 페이지만 캐싱하는 방식을 쓰면 좋아보인다.


┌─────────────────────────────────────┐
│ 캐시 OFF (DB 직접 조회)                   │
├──────────┬──────────────────────────┤
│ 최소     │                     320ms │
│ 평균     │                     628ms │
│ p50      │                     626ms │
│ p95      │                     944ms │
│ p99      │                     947ms │
│ 최대     │                     947ms │
└──────────┴──────────────────────────┘

┌─────────────────────────────────────┐
│ 캐시 ON  1라운드 (Cold Start, 전부 miss)   │
├──────────┬──────────────────────────┤
│ 최소     │                     212ms │
│ 평균     │                     546ms │
│ p50      │                     550ms │
│ p95      │                     868ms │
│ p99      │                     875ms │
│ 최대     │                     875ms │
└──────────┴──────────────────────────┘

┌─────────────────────────────────────┐
│ 캐시 ON  2라운드 (캐시 채워진 이후, 전부 hit)     │
├──────────┬──────────────────────────┤
│ 최소     │                      16ms │
│ 평균     │                      19ms │
│ p50      │                      19ms │
│ p95      │                      21ms │
│ p99      │                      21ms │
│ 최대     │                      21ms │
└──────────┴──────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Before / After 비교                                  │
├──────────┬──────────────┬──────────────┬────────────┤
│ 지표     │   캐시 OFF   │    캐시 ON   │      개선율│
├──────────┼──────────────┼──────────────┼────────────┤
│ 평균     │        628ms │         19ms │     33.1x │
│ p50      │        626ms │         19ms │     32.9x │
│ p95      │        944ms │         21ms │     45.0x │
│ p99      │        947ms │         21ms │     45.1x │
│ 최대     │        947ms │         21ms │     45.1x │
└──────────┴──────────────┴──────────────┴────────────┘