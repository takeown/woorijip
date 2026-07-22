# Lightsail 배포

우리집 운영 환경은 AWS Lightsail 인스턴스 한 대에서 Caddy, Next.js, Spring API,
PostgreSQL을 Docker Compose로 실행한다.

## 배포 흐름

```text
main push
→ CI 성공
→ 웹·API 이미지 빌드
→ GHCR에 latest와 commit SHA 태그로 게시
→ Deploy production workflow 수동 실행
→ production Environment 승인
→ Lightsail에서 이미지 교체
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

PostgreSQL의 5432 포트와 웹·API 컨테이너 포트는 외부에 공개하지 않는다.

## GitHub 설정

저장소의 `Settings → Environments`에서 `production` Environment를 만들고 다음 Secret을
등록한다.

- `LIGHTSAIL_HOST`: Lightsail 고정 IPv4 또는 배포용 호스트명
- `LIGHTSAIL_USER`: SSH 사용자
- `LIGHTSAIL_SSH_KEY`: SSH 개인 키 원문
- `LIGHTSAIL_KNOWN_HOSTS`: 검증한 서버의 SSH host key

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

1. `main` CI와 `Publish images` workflow가 성공했는지 확인한다.
2. Actions에서 `Deploy production`을 선택한다.
3. `Run workflow`의 branch를 `main`으로 선택한다.
4. 최신 버전은 `latest`, 특정 버전은 40자리 commit SHA를 입력한다.
5. `production` 배포를 승인하고 컨테이너 상태를 확인한다.

Flyway migration은 API 시작 과정에서 실행된다. migration 실패 시 API healthcheck가
실패하고 배포 workflow도 실패한다.

## Rollback

정상 동작했던 commit SHA를 사용해 `Deploy production` workflow를 다시 실행한다.
Flyway migration은 이전 이미지를 실행한다고 자동으로 되돌아가지 않으므로, 배포된
migration이 이전 애플리케이션과 호환되는지 먼저 확인한다.

## 배포 전 남은 운영 작업

- Lightsail 일일 자동 스냅샷 활성화
- PostgreSQL 논리 백업을 서버 외부에 보관
- 스냅샷과 논리 백업의 복구 절차 검증
- AWS 예산과 비용 알림 설정
- 운영 도메인의 로그인, 거래 등록, AI 초안 생성 smoke test
