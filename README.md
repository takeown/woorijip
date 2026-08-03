# 우리집

부부가 함께 쓰는 자동 가계부 프로젝트입니다.

## 구성

- `apps/web`: Next.js 웹 앱
- `apps/api`: Kotlin/Spring Boot API
- `compose.yaml`: 로컬 PostgreSQL

## 요구 사항

- Node.js 24 LTS
- JDK 21+
- Docker

## 시작하기

```bash
cp .env.example .env
docker compose up -d
pnpm dev:api
```

다른 터미널에서 웹 앱을 실행합니다.

```bash
pnpm dev:web
```

- Web: http://localhost:3100
- API health: http://localhost:8080/health

## 확인

```bash
pnpm lint:web
pnpm test:web
pnpm build:web
pnpm test:api
pnpm build:api
```

## 문서

- 현재 작업 범위와 백로그: `docs/PLAN.md`
- 완료한 마일스톤: `docs/MILESTONES_ARCHIVE.md`
- 제품·기술 결정: `docs/DECISIONS.md`
- API 구조와 변경 지점: `docs/ARCHITECTURE.md`
- 데이터 관계, 소유권과 금액 의미: `docs/DATA_MODEL.md`
- 보안 기준: `docs/SECURITY.md`
- 운영과 배포: `docs/DEPLOYMENT.md`
