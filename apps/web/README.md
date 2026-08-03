# 우리집 웹

Next.js App Router와 TypeScript로 만든 우리집 웹 애플리케이션이다.

설치, 실행과 전체 검증 명령은 저장소 루트 `README.md`와 `AGENTS.md`를 따른다.

저장소 루트에서 Node 24를 적용한 뒤 실행한다.

```bash
nvm use
pnpm dev:web
```

검증:

```bash
nvm use
pnpm lint:web
pnpm test:web
pnpm build:web
```

로컬 웹 주소는 `http://localhost:3100`이다.
