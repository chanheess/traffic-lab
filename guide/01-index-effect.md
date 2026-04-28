# 시나리오 1 - 인덱스 효과 측정

> **카테고리:** 조회 성능
> **학습 목표:** 언제 인덱스가 필요한지, 어느 규모에서 효과가 드러나는지 측정으로 체득

---

## 상황 설명

게시판 서비스에서 키워드로 게시글을 검색하는 기능
- 게시글 테이블 100,000건
- 검색 쿼리: `SELECT * FROM posts WHERE title LIKE 'keyword%'`
- 동시 사용자 증가에 따라 응답 시간 측정

---

## 학습 포인트

1. 인덱스 없을 때 Full Table Scan의 실제 비용
2. 인덱스 추가 후 응답 시간 변화
3. LIKE 조건에서 인덱스가 효과적인 케이스 (`'keyword%'` vs `'%keyword%'`)
4. 데이터 규모에 따른 영향 차이 (1만 vs 10만 vs 100만)

---

## 의사결정 가이드

```
조회 API가 느리다
├─ 데이터 규모가 1만 미만 → 인덱스 효과 미미, 굳이 필요 없음
├─ 1만 ~ 10만 → 인덱스로 대부분 해결 가능
├─ 10만 ~ 100만 → 인덱스 + 쿼리 튜닝 필요
└─ 100만 이상 → 인덱스 + 캐시 + 파티셔닝 검토
```

---

## 구현 계획

### 1. 시드 데이터 생성

```java
@PostConstruct
public void seedData() {
    if (postRepository.count() > 0) return;
    List<Post> posts = IntStream.range(0, 100_000)
        .mapToObj(i -> Post.builder()
            .title("게시글 제목 " + UUID.randomUUID())
            .content("내용 " + i)
            .build())
        .toList();
    postRepository.saveAll(posts);
}
```

### 2. 측정 대상 API

```java
@GetMapping("/api/demo/posts/search")
public List<Post> search(@RequestParam String keyword) {
    return postRepository.findByTitleStartingWith(keyword);
}
```

### 3. 인덱스 토글 API

```java
@PostMapping("/api/demo/index/toggle")
public void toggleIndex(@RequestParam boolean enabled) {
    if (enabled) {
        jdbcTemplate.execute("CREATE INDEX idx_post_title ON posts(title)");
    } else {
        jdbcTemplate.execute("DROP INDEX idx_post_title ON posts");
    }
}
```

### 4. 측정 흐름

1. 인덱스 OFF 상태로 50명 동시 요청 → 측정
2. 인덱스 ON 후 동일 요청 → 측정
3. Before/After 차트로 비교

---

## 예상 결과 (참고용)

| 상태 | p95 응답시간 | RPS |
|---|---|---|
| 인덱스 OFF | 800~1200ms | ~80 |
| 인덱스 ON | 30~80ms | ~600 |

---

## Thymeleaf 화면 구성

```html
<!-- scenarios/index-effect.html -->
<div class="container">
    <h2>시나리오 1: 인덱스 효과 측정</h2>

    <!-- 상황 설명 -->
    <div class="card">
        <p>게시글 100,000건에서 키워드 검색 시 인덱스의 효과를 측정합니다.</p>
    </div>

    <!-- 컨트롤 패널 -->
    <div class="controls">
        <button onclick="runTest(false)">인덱스 OFF로 테스트</button>
        <button onclick="runTest(true)">인덱스 ON으로 테스트</button>
    </div>

    <!-- 결과 차트 -->
    <canvas id="resultChart"></canvas>

    <!-- 비교 테이블 -->
    <table id="comparisonTable" class="table"></table>

    <!-- 의사결정 가이드 -->
    <div class="guide">...</div>
</div>
```

---

## 면접 답변 포인트

**Q: "왜 처음부터 인덱스를 안 걸었어요?"**
> "초기 개발 시점에는 데이터가 적어 인덱스 효과가 미미하고, 오히려 INSERT 성능이 떨어져 굳이 걸지 않았습니다. 데이터 규모가 커지면서 측정 결과를 보고 인덱스를 추가하는 게 합리적이라고 판단했습니다."

**Q: "인덱스의 단점은?"**
> "쓰기 성능 저하와 저장 공간 추가 사용입니다. 그래서 조회가 많고 쓰기가 적은 컬럼에 적용하는 게 원칙이고, 카디널리티가 낮은 컬럼(성별, 상태 등)에는 효과가 없어 단독으로 걸지 않습니다."
