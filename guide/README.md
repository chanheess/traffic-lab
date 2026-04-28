# Traffic Lab

> 트래픽 규모와 상황에 따라 어떤 백엔드 기술이 필요한지 측정 기반으로 학습하기 위한 인터랙티브 플랫폼

## 프로젝트 배경

부트캠프에서 다양한 백엔드 기술(Redis, Kafka, MSA 등)을 학습했지만, **"어떤 트래픽 규모와 상황에서 그 기술이 필요한가"** 에 대한 판단 기준이 부족하다고 느꼈습니다.

이 프로젝트는 직접 시나리오를 설계하고 측정하면서 **기술 도입의 적정 시점을 학습**하기 위한 도구입니다.

## 학습 목표

- 측정 기반으로 기술 도입 시점을 판단하는 사고방식 체득
- 각 기술의 트레이드오프 이해
- "왜 그 기술을 썼는가"에 대한 답변 능력 확보

## 기술 스택

- **Backend:** Spring Boot 3.x, JPA, Spring Cache
- **View:** Thymeleaf, Bootstrap 5, Chart.js
- **Database:** MySQL, Redis
- **Monitoring:** Spring Boot Actuator, Micrometer

## 시나리오 목록

| # | 카테고리 | 시나리오 | 학습 포인트 |
|---|---|---|---|
| 1 | 조회 성능 | 인덱스 효과 측정 | 언제 인덱스가 필요한가 |
| 2 | 캐싱 | Redis 캐시 효과 | 캐시 도입 적정 시점 |
| 3 | 비동기 | 동기 vs 비동기 | 비동기화 판단 기준 |
| 4 | 인프라 | Connection Pool 튜닝 | 동시성 처리 한계 |

## 실행 방법

```bash
# 1. Docker Compose로 인프라 실행
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 브라우저에서 접속
http://localhost:8080
```

## 페이지 구성

- `/` : 시나리오 목록
- `/scenarios/{id}` : 시나리오 상세 + 인터랙티브 데모
- `/guide` : 트래픽 규모별 의사결정 가이드

## 측정 결과 예시

| 시나리오 | Before | After | 개선율 |
|---|---|---|---|
| 인덱스 효과 | p95 800ms | p95 50ms | 94% |
| 캐시 도입 | p95 250ms | p95 5ms | 98% |
| 비동기화 | p95 600ms | p95 50ms | 92% |

## 학습 정리

각 시나리오별 상세 내용은 `scenarios/` 폴더의 마크다운 문서를 참고하세요.

- [00. 의사결정 가이드](scenarios/00-decision-guide.md)
- [01. 인덱스 효과](scenarios/01-index-effect.md)
- [02. 캐시 효과](scenarios/02-cache-effect.md)
- [03. 동기 vs 비동기](scenarios/03-sync-vs-async.md)
- [04. Connection Pool](scenarios/04-connection-pool.md)
