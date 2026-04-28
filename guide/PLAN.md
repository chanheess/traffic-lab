# Traffic Lab - 이틀치 진행 계획

> 목적: 트래픽 규모와 상황에 따라 어떤 백엔드 기술이 필요한지 측정 기반으로 학습하는 인터랙티브 플랫폼 구축
> 스택: Spring Boot 3.x + Thymeleaf + MySQL + Redis + Chart.js + Bootstrap 5

---

## 프로젝트 컨셉

부트캠프에서 배운 기술들(Redis, 인덱스, 비동기 등)이 **언제, 왜 필요한지**를 직접 측정하며 학습하는 도구

### 페이지 구성
- `/` : 시나리오 목록
- `/scenarios/{id}` : 시나리오 상세 + 인터랙티브 데모
- `/guide` : 트래픽 규모별 의사결정 가이드

---

## Day 1 - 환경 구축 + 핵심 시나리오 2개

### 오전 (4시간)

**1. 프로젝트 초기 세팅 (1시간)**
- Spring Boot 3.x 프로젝트 생성 (Spring Initializr)
- 의존성: Web, Thymeleaf, JPA, MySQL, Redis, Lombok, Actuator
- docker-compose로 MySQL, Redis 띄우기
- 기본 레이아웃 (Bootstrap 5 CDN)

**2. 시나리오 모델 및 공통 페이지 (1시간)**
- `Scenario` 엔티티 (id, title, category, description, decisionGuide)
- 시나리오 목록 페이지 (`/`)
- 시나리오 상세 페이지 골격 (`/scenarios/{id}`)
- BenchmarkResult 엔티티 (시나리오별 측정 결과 저장)

**3. 부하 측정 공통 모듈 (2시간)**
- `BenchmarkService` - 내부 호출로 N번 동시 요청 후 응답시간 측정
- `BenchmarkResult` - p50, p95, p99, RPS 저장
- Chart.js로 결과 시각화 컴포넌트

```java
// BenchmarkService 예시 인터페이스
public BenchmarkResult run(String endpoint, int concurrency, int totalRequests);
```

### 오후 (4시간)

**4. 시나리오 1: 인덱스 효과 측정 (2시간)**
- 게시글 100,000건 생성 (시드 데이터)
- 인덱스 없는 조회 API: `/api/demo/posts/search?keyword=`
- 인덱스 적용 토글 가능하게 (런타임에 인덱스 추가/삭제)
- Before/After 측정 비교 화면

**5. 시나리오 2: 캐시 효과 측정 (2시간)**
- 인기 게시글 조회 API: `/api/demo/posts/popular`
- 캐시 OFF: 매번 DB 조회
- 캐시 ON: `@Cacheable` Redis 적용
- Cache Hit Ratio 시각화

---

## Day 2 - 시나리오 2개 추가 + 의사결정 가이드 + 마무리

### 오전 (4시간)

**6. 시나리오 3: 동기 vs 비동기 (2시간)**
- 알림 발송이 포함된 API: `/api/demo/orders`
- 동기 모드: 알림 발송 완료까지 대기 (응답 느림)
- 비동기 모드: `@Async` 또는 이벤트 발행
- 응답 시간 비교 시각화

**7. 시나리오 4: Connection Pool 튜닝 (2시간)**
- 동시 요청 100건에서 HikariCP 설정 변경
- pool size 5 vs 20 vs 50 비교
- Connection 대기 시간 시각화

### 오후 (4시간)

**8. 의사결정 가이드 페이지 (`/guide`) (2시간)**
- 트래픽 규모별 트리 형태 가이드
- 정적 콘텐츠 (Thymeleaf 템플릿)

**9. README + 블로그 글 작성 (1시간)**

**10. 이력서 반영 + GitHub 푸시 (1시간)**

---

## 시나리오 목록 (총 4개)

| # | 카테고리 | 시나리오 | 학습 포인트 |
|---|---|---|---|
| 1 | 조회 성능 | 인덱스 효과 측정 | 언제 인덱스가 필요한가 |
| 2 | 캐싱 | Redis 캐시 효과 | 캐시 도입 적정 시점 |
| 3 | 비동기 | 동기 vs 비동기 처리 | 비동기화 판단 기준 |
| 4 | 인프라 | Connection Pool 튜닝 | 동시성 처리 한계 |

---

## 기술적 의의

### 단순 부하 테스트와의 차이
- k6 같은 도구는 **테스트 실행자**
- 이 프로젝트는 **학습자가 직접 코드 + 측정 + 결과 + 의사결정 가이드를 한 곳에서 경험**

### 면접 어필 포인트
- "기술을 써봤다"가 아니라 **"왜 그 기술이 필요한지 판단할 수 있다"**
- 측정 기반 사고방식 체득

---

## 우선순위 (시간 부족 시 자르기 순서)

**필수 (이력서에 쓰려면 최소):**
- 시나리오 1 (인덱스) + 시나리오 2 (캐시) 완성
- README 작성
- GitHub 푸시

**여유 있으면:**
- 시나리오 3, 4
- 의사결정 가이드 페이지

**버려도 됨:**
- 디자인 디테일 (Bootstrap 기본 스타일로 충분)
- 인증 / 회원가입 (불필요)
