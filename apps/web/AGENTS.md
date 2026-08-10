# 웹 애플리케이션 작업 지침

이 파일은 저장소 루트 `AGENTS.md`의 공통 규칙에 웹 전용 규칙을 추가한다.

## 기술 구성

- Next.js App Router
- React와 TypeScript
- Tailwind CSS
- pnpm workspace

## 구현 규칙

- 기본적으로 Server Component를 사용한다.
- 브라우저 상태, event handler 또는 브라우저 API가 필요할 때만 Client Component를
  사용하고 `"use client"` 경계를 가능한 작게 유지한다.
- 데이터 조회는 실제 데이터를 사용하는 컴포넌트 가까이에서 수행한다.
- 서버 전용 환경변수에는 `NEXT_PUBLIC_` 접두사를 붙이지 않는다.
- 공개 환경변수는 브라우저에 노출돼도 안전한 값에만 사용한다.
- 로딩, 빈 상태, 오류 상태를 실제 화면 흐름의 일부로 구현한다.
- 폼은 label, keyboard 조작, 오류 메시지를 포함해 접근 가능하게 만든다.
- 공통화는 두 곳 이상에서 실제로 반복되고 의미가 같을 때 한다.
- 현재 디자인이 단순한 동안 별도 UI 패키지나 상태 관리 라이브러리를 추가하지 않는다.

## API 연동

- API 응답 타입을 명시하고 성공하지 않은 HTTP 상태를 처리한다.
- 인증이 추가되기 전까지도 API 주소와 비밀값을 소스 코드에 직접 넣지 않는다.
- 날짜와 금액 표시는 한국 사용자 기준으로 하되 저장·전송 형식과 분리한다.
- API 계약을 임의로 추측하지 않고 백엔드 DTO 및 테스트와 함께 변경한다.

## 관련 문서

변경 경로에 따라 필요한 문서만 추가로 읽는다.

| 변경 범위 | 기준 문서 |
| --- | --- |
| 화면 흐름 또는 제품 범위 | `docs/PLAN.md`와 `docs/DECISIONS.md` |
| 인증, API 전송 데이터 또는 외부 연동 | `docs/SECURITY.md` |
| 운영 환경변수 또는 웹 배포 | `docs/operations/DEPLOYMENT.md` |

## 검증

저장소 루트에서 실행한다.

```bash
pnpm lint:web
pnpm test:web
pnpm build:web
```

<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->
