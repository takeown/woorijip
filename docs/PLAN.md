# 우리집 개발 계획

마지막 수정: 2026-07-12

## 목표

결제 메일을 자동으로 수집하고 “이번 달 돈 어디 갔어?” 같은 질문에 답할 수 있는
부부 전용 비공개 가계부를 만든다.

이 프로젝트는 Kotlin과 Spring Boot를 배우는 목적도 있다. 프레임워크의 복잡한
기능을 추가하기 전에, 백엔드의 선택을 이해하고 테스트할 수 있는 작은 end-to-end
기능을 차례대로 완성한다.

## 제품 범위

- 초기에는 허용된 Google 계정 두 개만 접근한다.
- 두 계정은 하나의 household에 속하며 거래내역을 공유한다.
- 결제 메일 수집에는 필요한 최소한의 Gmail 권한만 요청한다.
- 파싱한 거래 필드는 저장할 수 있지만 원본 메일 본문은 저장하지 않는다.
- 결정적인 규칙 기반 수집·저장·조회가 완성된 이후에 AI를 추가한다.
- 첫 번째 클라이언트는 웹이다. 모바일 앱은 나중에 `apps/mobile`로 추가할 수 있다.

## 마일스톤

### M0 — 기반 구성 — 완료

- [x] pnpm 모노레포 구성
- [x] Next.js 웹 애플리케이션 추가
- [x] Kotlin/Spring Boot API 추가
- [x] Docker Compose로 로컬 PostgreSQL 구성
- [x] Flyway와 Spring Data JDBC 의존성 추가
- [x] `/health`와 웹–API 연결 상태 확인 추가
- [x] 웹과 API 빌드 검증

### M1 — 거래내역 vertical slice — 진행 중

결과: 실제 데이터베이스와 UI를 통해 거래내역을 직접 등록하고 조회할 수 있다.

- [ ] 첫 `transactions` 스키마를 Flyway migration으로 정의
- [ ] 거래 영속 모델과 Repository 정의
- [ ] `POST /transactions` 구현
- [ ] `GET /transactions` 구현
- [ ] 양의 정수 KRW 금액과 필수 필드 검증
- [ ] Repository와 HTTP 테스트 추가
- [ ] 웹에서 거래 목록 표시
- [ ] 최소한의 수동 거래 등록 폼 추가
- [ ] 로컬 PostgreSQL을 사용한 전체 흐름 검증

초기 거래 필드:

- `id`
- `merchant`
- 정수 KRW인 `amount`
- `category`
- timezone을 포함한 `occurred_at`
- `created_at`

M1에서는 Controller → 영속 계층 → 데이터베이스 → UI 흐름을 먼저 학습할 수 있도록
household 소유권과 결제자 정보는 M2로 미룬다.

### M2 — Household와 인증 — 예정

- [ ] users, households, household membership 스키마 추가
- [ ] 모든 거래를 household에 연결
- [ ] 웹 애플리케이션에 Google 로그인 추가
- [ ] Google 계정 두 개만 허용
- [ ] API 경계에서 사용자 신원 검증
- [ ] 구조와 테스트를 통해 다른 household 데이터 접근 방지
- [ ] 각 거래의 실제 결제자 기록

### M3 — Gmail 수집 — 예정

- [ ] 각 구성원의 Gmail을 독립적으로 연결
- [ ] 실용적으로 가능한 최소 read-only scope 요청
- [ ] 결정적인 검색 조건으로 결제 관련 메일 탐색
- [ ] AI 없이 하나의 결제 제공자 형식 파싱
- [ ] 동기화를 멱등하게 구현
- [ ] 원문 대신 출처 식별자와 파싱된 필드만 저장
- [ ] 수동 동기화와 동기화 상태 제공

### M4 — 분류와 수정 — 예정

- [ ] 알려진 가맹점 기반 카테고리 규칙 추가
- [ ] 거래내역 수정 UI 추가
- [ ] 이후 자동 분류보다 사용자의 수정을 우선
- [ ] 결정적 규칙으로 분류할 수 없는 거래에만 AI fallback 추가
- [ ] 분류 출처와 신뢰도 기록

### M5 — 가계 분석 — 예정

- [ ] 월별 합계와 카테고리 비교 쿼리 추가
- [ ] 구조화된 거래 데이터 범위 안에서 질문에 답변
- [ ] “이번 달 돈 어디 갔어?” 요약 추가
- [ ] 각 답변의 근거가 된 거래내역 표시
- [ ] API 사용량과 비용 제한 추가

### M6 — 배포 — 예정

- [ ] 웹을 Vercel에 배포
- [ ] API 컨테이너와 PostgreSQL을 Railway에 배포
- [ ] 운영 비밀값과 허용 origin 설정
- [ ] 통제된 배포 단계에서 Flyway migration 실행
- [ ] 데이터베이스 백업과 비용 알림 추가
- [ ] 복구와 rollback 절차 문서화

## 현재 마일스톤 결정 기록

### 2026-07-12 — 거래내역 vertical slice부터 시작

인증을 첫 번째 기능으로 구현하지 않는다. 데이터베이스 기반 거래 흐름 하나를 먼저
완성하면 OAuth와 보안 설정에 가려지지 않은 상태에서 Spring 핵심 개념을 익히고
즉시 결과를 확인할 수 있다.

### 2026-07-12 — JPA보다 Spring Data JDBC를 먼저 사용

테이블 관계와 SQL 중심 데이터 모델링이 학습 과정에서 드러나도록 Spring Data JDBC를
사용한다. identity map, lazy loading 또는 복잡한 aggregate mapping이 실제로 필요해질
때만 JPA 도입을 다시 검토한다.

### 2026-07-12 — 프로젝트 Codex 설정 대신 저장소 지침 사용

지속적인 개발 규칙은 `AGENTS.md`에 둔다. 현재 저장소에는 별도의 모델, sandbox,
MCP 또는 hook 설정이 필요하지 않으므로 `.codex/config.toml`은 추가하지 않는다.

### 2026-07-12 — 공통 규칙과 앱별 규칙 분리

저장소 전체에 적용되는 작업 방식과 보안 규칙은 루트 `AGENTS.md`에 둔다.
Next.js 전용 규칙은 `apps/web/AGENTS.md`, Kotlin/Spring 전용 규칙은
`apps/api/AGENTS.md`에 두어 중복 없이 작업 경로에 맞는 지침을 적용한다.

## 백로그

- 영수증 OCR
- 공동 예산과 저축 목표
- 반복 지출 감지
- 알림과 일간 요약
- Expo 모바일 클라이언트
- OpenAPI 기반 TypeScript 클라이언트 생성
- 실제 캐싱 또는 작업 조율 필요성이 확인된 경우에만 Redis 추가
