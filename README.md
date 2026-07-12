# 우리집

부부가 함께 쓰는 자동 가계부 프로젝트입니다.

## 구성

- `apps/web`: Next.js 웹 앱
- `apps/api`: Kotlin/Spring Boot API
- `compose.yaml`: 로컬 PostgreSQL

## 요구 사항

- Node.js 24 LTS
- JDK 17+
- Docker

## 시작하기

```bash
cp .env.example .env
docker compose up -d
npm run dev:api
```

다른 터미널에서 웹 앱을 실행합니다.

```bash
npm run dev:web
```

- Web: http://localhost:3000
- API health: http://localhost:8080/health

## 확인

```bash
npm run lint:web
npm run build:web
npm run test:api
npm run build:api
```
