# Lightsail 배포

우리집 운영 환경은 AWS Lightsail 인스턴스 한 대에서 Caddy, Next.js, Spring API,
PostgreSQL을 Docker Compose로 실행한다.

## 배포 흐름

```text
main push
→ CI 성공
→ main에서 Publish images workflow 수동 실행
→ 웹·API 이미지 병렬 빌드
→ GHCR에 latest와 commit SHA 태그로 게시
→ 40자리 commit SHA로 Deploy production workflow 수동 실행
→ GitHub OIDC로 AWS 단기 자격 증명 발급
→ GitHub runner IPv4만 22/TCP에 임시 허용
→ production Environment Secret으로 SSH 연결
→ Lightsail에서 이미지 교체
→ 성공·실패와 관계없이 임시 SSH 규칙 제거
```

애플리케이션 비밀값은 Docker 이미지나 GitHub Actions에 전달하지 않는다. Lightsail의
`~/woorijip/deploy/.env`에만 저장한다.

## 사전 준비

- 공인 IPv4가 포함된 Lightsail Linux 인스턴스
- 인스턴스에 연결한 고정 IPv4
- 고정 IPv4를 가리키는 도메인
- 인바운드 80/TCP, 443/TCP, 443/UDP 허용
- 관리할 주소로 제한한 22/TCP
- Lightsail에 설치한 Docker Engine과 Docker Compose plugin
- GHCR private image를 읽을 수 있는 `read:packages` 토큰
- GitHub OIDC를 신뢰하고 Lightsail SSH 방화벽만 변경할 수 있는 IAM 역할

PostgreSQL의 5432 포트와 웹·API 컨테이너 포트는 외부에 공개하지 않는다.

배포 workflow는 고정 IP가 없는 GitHub-hosted runner의 공인 IPv4를 확인한 뒤 해당
주소의 `/32`만 22/TCP에 임시로 추가한다. 마지막 cleanup 단계는 성공·실패와 관계없이
추가했던 정확한 `/32` 규칙만 제거한다. 관리자의 기존 IPv4 규칙은 유지되며 22/TCP를
모든 IPv4에 개방하지 않는다.

## GitHub 설정

저장소의 `Settings → Environments`에서 `production` Environment를 만들고 다음 Secret을
등록한다.

- `LIGHTSAIL_HOST`: Lightsail 고정 IPv4 또는 배포용 호스트명
- `LIGHTSAIL_USER`: SSH 사용자
- `LIGHTSAIL_SSH_KEY`: SSH 개인 키 원문
- `LIGHTSAIL_KNOWN_HOSTS`: 검증한 서버의 SSH host key

같은 Environment에 다음 Variable을 등록한다.

- `AWS_ROLE_ARN`: GitHub Actions가 OIDC로 맡을 IAM 역할 ARN
- `AWS_REGION`: Lightsail 리전. 현재 운영 환경은 `ap-northeast-2`
- `LIGHTSAIL_INSTANCE_NAME`: 배포할 Lightsail 인스턴스 이름

IAM 역할의 OIDC 신뢰 정책은 저장소 전체가 아니라 production Environment로 제한한다.

```text
repo:takeown/woorijip:environment:production
```

역할 권한은 운영 인스턴스 ARN에 대한 다음 두 작업만 허용한다.

```text
lightsail:OpenInstancePublicPorts
lightsail:CloseInstancePublicPorts
```

가능한 경우 `production` Environment에 승인자를 지정한다. Repository의 기본 workflow
권한은 읽기로 유지하고, 이미지 게시 workflow에만 `packages: write`를 부여한다.

## 서버 초기 설정

저장소의 `deploy/.env.example`을 참고해 서버에 운영 환경변수를 만든다.

```bash
mkdir -p ~/woorijip/deploy
chmod 700 ~/woorijip ~/woorijip/deploy
vi ~/woorijip/deploy/.env
chmod 600 ~/woorijip/deploy/.env
```

서버에서 GHCR에 한 번 로그인한다. 비밀번호 입력에는 GitHub 비밀번호가 아니라
`read:packages` 권한을 가진 토큰을 사용한다.

```bash
docker login ghcr.io -u takeown
```

GitHub Actions가 첫 배포에서 `compose.prod.yaml`과 `deploy/Caddyfile`을 서버로 복사한다.

## Google OAuth 설정

Google Cloud의 운영 OAuth 클라이언트에 실제 도메인을 사용한 값을 등록한다.

```text
승인된 JavaScript 원본: https://운영-도메인
승인된 리디렉션 URI: https://운영-도메인/api/login/oauth2/code/google
```

운영 환경에서는 Spring context path를 `/api`로 설정하므로 OAuth callback도 `/api`
아래에 위치한다.

## 배포

1. 배포할 `main` commit의 CI가 성공했는지 확인한다.
2. Actions에서 `Publish images`를 선택하고 `main` 브랜치에서 수동 실행한다.
3. web과 api 이미지 게시 job이 모두 성공했는지 확인한다.
4. 배포할 `main`의 40자리 commit SHA를 복사한다. `latest`는 사용하지
   않는 것을 기본으로 한다.
5. Actions에서 `Deploy production`을 선택한다.
6. `Run workflow`의 branch를 `main`으로 선택하고 commit SHA를 입력한다.
7. 임시 SSH 개방, 배포, 임시 SSH 제거 단계가 모두 성공했는지 확인한다.
8. 컨테이너 healthcheck와 운영 도메인의 HTTPS, 로그인, 주요 기능을 smoke test한다.

workflow가 강제 취소되거나 runner 장애로 cleanup 단계까지 실행되지 못했다면 Lightsail의
IPv4 방화벽을 확인한다. 관리자 주소가 아닌 배포 시각에 추가된 22/TCP `/32` 규칙만
수동으로 제거한다.

Flyway migration은 API 시작 과정에서 실행된다. migration 실패 시 API healthcheck가
실패하고 배포 workflow도 실패한다.

## 상태와 로그 확인

서버에서 운영 컨테이너 상태를 확인한다.

```bash
cd ~/woorijip
docker compose --env-file deploy/.env -f compose.prod.yaml ps
```

문제가 있으면 최근 로그를 확인한다. 로그에 비밀값이나 거래 원문이 포함되지
않았는지 확인하고 공유한다.

```bash
cd ~/woorijip
docker compose --env-file deploy/.env -f compose.prod.yaml logs --tail=200 api web caddy postgres
```

## Rollback

정상 동작했던 commit SHA를 사용해 `Deploy production` workflow를 다시 실행한다.
Flyway migration은 이전 이미지를 실행한다고 자동으로 되돌아가지 않으므로, 배포된
migration이 이전 애플리케이션과 호환되는지 먼저 확인한다.

## 운영 준비 현황

- [x] Lightsail 2GB 인스턴스와 고정 IPv4, 운영 DNS, HTTPS 구성
- [x] GitHub Actions와 commit SHA를 사용한 첫 운영 배포
- [x] 운영 도메인의 Google 로그인과 수동 거래 등록 smoke test
- [x] AWS 월 USD 20 예산과 비용 알림 설정
- [ ] AI 거래 초안 생성·저장 smoke test
- [ ] 로그아웃 후 거래 API 접근 차단 검증
- [ ] Lightsail 일일 자동 스냅샷 활성화
- [ ] PostgreSQL 논리 백업을 서버 외부에 보관
- [ ] 스냅샷과 논리 백업의 복구 절차 검증

현재 거래 기능의 운영 smoke test 범위는 M1에서 구현한 등록·조회다.
거래 수정·삭제 UI와 API는 아직 구현하지 않았으며 M5 범위로 남겨 둔다.
