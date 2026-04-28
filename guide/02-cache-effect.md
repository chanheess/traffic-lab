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
