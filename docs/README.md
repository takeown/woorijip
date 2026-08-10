# 문서 안내

우리집의 제품, 구현과 운영 기준 문서를 역할별로 찾기 위한 진입점이다.
문서 내용을 다른 파일에 복제하거나 합쳐서 생성하지 않고, 각 기준 문서를 직접 연결한다.

## 제품과 결정

- [개발 계획](./PLAN.md): 현재 마일스톤, 다음 순서와 백로그
- [완료 마일스톤](./MILESTONES_ARCHIVE.md): 완료된 범위와 검증 기록
- [결정 기록](./DECISIONS.md): 제품·기술 결정과 배경

## 공통 기준

- [보안 기준](./SECURITY.md): 인증, household 경계, 외부 전송, 파일과 비밀값

## API와 데이터

- [API 구조](./api/ARCHITECTURE.md): 요청 흐름, 계층별 역할, 변경 지점과 장애 확인 순서
- [데이터 모델](./api/DATA_MODEL.md): 데이터 관계, 소유권, 금액 의미와 삭제 정책

## 웹

- [웹 작업 지침](../apps/web/AGENTS.md): Next.js 구성, API 연동, 접근성과 검증 기준

웹 구조가 작업 지침만으로 설명하기 어려워질 때 `docs/web/ARCHITECTURE.md`를 추가한다.
현재는 실제 내용이 없는 문서를 미리 만들지 않는다.

## 운영과 가이드

- [운영과 배포](./operations/DEPLOYMENT.md): Lightsail, GitHub Actions, 상태 확인과
  rollback
- [초보 기획 가이드](./guides/JUNIOR_PLANNER_CODEX_GUIDE.md): Codex와 함께 기획 작업을
  진행하는 방법

## 문서 소유권

| 변경 내용 | 함께 확인할 문서 |
| --- | --- |
| 제품 범위, 마일스톤 또는 백로그 | `docs/PLAN.md` |
| 제품·기술 결정과 배경 | `docs/DECISIONS.md` |
| API 요청 흐름과 계층 구조 | `docs/api/ARCHITECTURE.md` |
| 테이블 관계, 소유권, 금액 의미 또는 삭제 정책 | `docs/api/DATA_MODEL.md` |
| 인증, 권한, 외부 연동, 파일, 로그 또는 비밀값 | `docs/SECURITY.md` |
| 배포, 운영, 복구 또는 rollback | `docs/operations/DEPLOYMENT.md` |
