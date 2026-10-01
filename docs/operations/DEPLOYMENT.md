# Lightsail 배포

마지막 수정: 2026-10-02

우리집 운영 환경은 AWS Lightsail 인스턴스 한 대에서 Caddy, Next.js, Spring API,
PostgreSQL을 Docker Compose로 실행한다.

## 배포 흐름

```text
main push
→ CI 성공
→ main에서 Release production workflow 수동 실행
→ 웹·API 이미지 병렬 빌드
→ GHCR에 latest와 commit SHA 태그로 게시
→ 게시한 commit SHA로 배포 자동 진행
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

이 세 값은 `Environment secrets`가 아니라 `Environment variables` 영역에 등록한다.
workflow가 `${{ vars.* }}` 문법으로 읽기 때문에 Secret에만 등록하면 빈 값으로 처리되어
AWS 자격 증명 설정 단계가 실패한다. 반대로 SSH 개인 키와 host key는 Variable로 옮기지
않고 기존 Environment Secret으로 유지한다.

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
2. Actions에서 `Release production`을 선택한다.
3. `Run workflow`의 branch를 `main`으로 선택하고 수동 실행한다.
4. web과 api 이미지 게시 job이 모두 성공한 뒤 deploy job이 이어지는지 확인한다.
5. 임시 SSH 개방, 배포, 임시 SSH 제거 단계가 모두 성공했는지 확인한다.
6. 컨테이너 healthcheck와 운영 도메인의 HTTPS, 로그인, 주요 기능을 smoke test한다.

`Publish images`는 배포 없이 이미지만 게시할 때 사용한다. `Deploy production`은 이미
게시된 `latest` 또는 40자리 commit SHA를 재배포하거나 rollback할 때 사용한다. 정상
배포에서는 `Release production`이 현재 `main`의 commit SHA를 자동으로 전달한다.

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

## PostgreSQL 일일 논리 백업

운영 데이터는 장애 시 최대 24시간 손실을 허용한다. PostgreSQL은 매일 03:30
`Asia/Seoul`에 custom format 논리 백업을 만들고 서울 리전의 전용 S3 버킷에 저장한다.
예약 시각에 서버가 중지돼 있었으면 `systemd timer`의 `Persistent=true`가 재시작 후
누락된 실행을 보충한다.

백업 데이터 흐름은 다음과 같다.

```text
PostgreSQL 컨테이너
→ pg_dump custom format
→ 제한된 로컬 임시 파일
→ 비어 있지 않은지 확인
→ pg_restore --list 형식 검사
→ AWS CLI SHA-256 checksum 업로드
→ 비공개 S3 버킷의 postgres/ prefix
→ 업로드 성공 후 로컬 파일 삭제
```

운영 서버의 관련 파일은 다음과 같다. 이 파일들은 실제 버킷명과 자격 증명을 포함할 수
있으므로 저장소나 운영 로그에 복사하지 않는다.

| 경로 | 역할 | 권한 |
| --- | --- | --- |
| `/home/ubuntu/.aws/credentials` | `woorijip-backup` AWS CLI profile의 access key | `600` |
| `/home/ubuntu/.aws/config` | 서울 리전과 CLI profile 설정 | `600` |
| `/home/ubuntu/.config/woorijip-backup` | 버킷명, 리전, profile 이름 | `600` |
| `/home/ubuntu/woorijip/deploy/backup-postgres.sh` | 덤프 검사, 업로드와 로컬 정리 | `700` |
| `/etc/systemd/system/woorijip-backup.service` | `ubuntu` 사용자로 백업 스크립트 실행 | root 관리 |
| `/etc/systemd/system/woorijip-backup.timer` | 매일 03:30 KST 실행, 최대 5분 분산 | root 관리 |

S3 버킷은 퍼블릭 액세스를 모두 차단하고 기본 암호화를 활성화한다. 백업 전용 IAM 사용자는
해당 버킷의 `postgres/` prefix에 대한 `s3:PutObject`와 `s3:AbortMultipartUpload`만
허용한다. 평상시 자격 증명에는 목록 조회, 다운로드와 삭제 권한을 추가하지 않는다.

수동 실행과 상태 확인은 다음 명령을 사용한다.

```bash
sudo systemctl start woorijip-backup.service
systemctl status woorijip-backup.service --no-pager
journalctl -u woorijip-backup.service --since today --no-pager
systemctl list-timers --all woorijip-backup.timer
```

성공 기준은 service가 `status=0/SUCCESS`로 끝나고 S3 콘솔의 `postgres/` prefix에
0바이트가 아닌 새 `.dump` 객체가 생성되는 것이다. 백업 IAM 사용자는 목록 조회 권한이
없으므로 서버에서 `aws s3 ls`가 거부되는 것은 정상이다. 로그에는 성공 여부와 파일명만
남기며 거래 내용과 자격 증명을 출력하지 않는다.

S3 lifecycle은 현재 객체와 versioning을 사용한 경우 noncurrent version을 모두 포함해
14일 뒤 제거하도록 구성한다. lifecycle 적용과 Lightsail 일일 자동 스냅샷 활성화는 아직
운영 확인 전이므로 아래 준비 현황에서 완료로 표시하지 않는다. 백업 실패는 현재
`systemd` journal로만 확인할 수 있으며 자동 실패 알림도 아직 구성하지 않았다.

### 논리 백업 복구 검증

복구는 운영 데이터베이스를 덮어쓰지 않고 별도의 임시 PostgreSQL 17 컨테이너에서 한다.
평상시 업로드 IAM 사용자에 읽기 권한을 추가하지 말고, 복구할 객체 하나에만 접근할 수
있는 별도의 임시 profile로 덤프를 내려받는다.

1. 복구할 S3 객체와 생성 시각, 크기를 관리자 권한으로 확인한다.
2. 임시 읽기 권한으로 덤프를 권한이 제한된 디렉터리에 내려받는다.
3. `pg_restore --list`로 archive 목록을 확인한다.
4. 네트워크 포트를 외부에 공개하지 않은 임시 `postgres:17-alpine` 컨테이너에 복원한다.
5. Flyway schema history와 핵심 테이블의 존재 여부, 대표 row count를 확인한다. 거래 원문은
   로그나 작업 결과에 복사하지 않는다.
6. 검증이 끝나면 임시 컨테이너와 로컬 덤프를 제거하고 임시 읽기 자격 증명을 폐기한다.

복구 검증은 아직 운영에서 실행하지 않았다. 최초 1회 실제 검증 후 분기마다 반복하고,
PostgreSQL major version 또는 백업 형식이 달라지면 추가로 실행한다.

## Rollback

정상 동작했던 commit SHA를 사용해 `Deploy production` workflow를 다시 실행한다.
Flyway migration은 이전 이미지를 실행한다고 자동으로 되돌아가지 않으므로, 배포된
migration이 이전 애플리케이션과 호환되는지 먼저 확인한다.

## 운영 준비 현황

- [x] Lightsail 2GB 인스턴스와 고정 IPv4, 운영 DNS, HTTPS 구성
- [x] GitHub Actions와 commit SHA를 사용한 첫 운영 배포
- [x] GitHub OIDC와 runner `/32`를 사용한 SSH 방화벽 자동 개방·회수 검증
- [x] 운영 도메인의 Google 로그인과 수동 거래 등록 smoke test
- [x] AWS 월 USD 20 예산과 비용 알림 설정
- [ ] AI 거래 초안 생성·저장 smoke test
- [ ] 로그아웃 후 거래 API 접근 차단 검증
- [ ] Lightsail 일일 자동 스냅샷 활성화
- [x] PostgreSQL 일일 논리 백업을 서버 외부 S3에 업로드
- [x] `systemd timer` 등록과 수동 덤프·형식 검사·업로드 검증
- [ ] S3 논리 백업을 14일 후 삭제하는 lifecycle 적용
- [ ] PostgreSQL 논리 백업 실패 알림 구성
- [x] 논리 백업의 격리된 복구 절차 문서화
- [ ] 스냅샷과 논리 백업의 실제 복구 검증

위 체크리스트는 운영 환경에서 직접 확인한 범위만 표시한다. 기능 구현 상태는
`docs/PLAN.md`를 기준으로 확인하며, 코드와 테스트가 완료됐더라도 운영 smoke test를
실행하지 않은 항목은 완료로 간주하지 않는다.
