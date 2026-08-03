# API 구조 안내

Kotlin과 Spring을 처음 보는 상태에서 이 저장소의 API를 읽고 고칠 수 있도록 정리한
문서다. 일반적인 Spring 설명이 아니라 이 프로젝트에 실제로 있는 파일만 다룬다.

제품 방향은 `docs/PLAN.md`, 결정 배경은 `docs/DECISIONS.md`, 데이터 관계와 금액 의미는
`docs/DATA_MODEL.md`, 보안 기준은 `docs/SECURITY.md`, 작업 규칙은 `AGENTS.md`를 본다.

## 1. 전체 그림

```
브라우저
   │
   ▼
Caddy (운영에만 존재)            deploy/Caddyfile
   ├── /api/*  ──▶ Spring API   apps/api
   └── 그 외    ──▶ Next.js 웹   apps/web
                        │
Spring API ──▶ PostgreSQL
```

운영에서는 Caddy가 한 도메인을 웹과 API로 나눈다. Caddy는 `/api` 접두사를 떼지 않고
그대로 넘기고, API 컨테이너가 `SERVER_SERVLET_CONTEXT_PATH=/api`로 떠 있어서
`/api/transactions` 요청이 코드의 `/transactions` 매핑에 도달한다.

로컬 개발에서는 Caddy가 없다. 웹은 `localhost:3100`, API는 `localhost:8080`으로 따로
뜨고 context path도 없다. 서로 다른 출처이므로 이때만 CORS 설정이 실제로 동작한다.
운영에서 CORS 문제가 안 보이는데 로컬에서만 막힌다면 이 차이 때문이다.

## 2. 요청 하나가 지나가는 길

거래를 등록하는 `POST /transactions`를 따라가면 전체 구조가 한 번에 보인다.

| 순서 | 파일 | 하는 일 |
| --- | --- | --- |
| 1 | `config/SecurityConfig.kt` | 로그인 여부 확인. 안 했으면 여기서 401로 끝난다 |
| 2 | `auth/AllowedGoogleAccountFilter.kt` | 허용된 Google 계정인지 매 요청 다시 확인 |
| 3 | `auth/CurrentUserArgumentResolver.kt` | 로그인 정보를 `CurrentUser`(내부 사용자 + household)로 변환 |
| 4 | `transaction/TransactionController.kt` | JSON을 객체로 받고 입력값 검증 |
| 5 | `transaction/TransactionService.kt` | 실제 규칙 판단 (결제자가 우리 household인가 등) |
| 6 | `transaction/TransactionRepository.kt` | SQL 실행 |
| 7 | PostgreSQL | 저장 |

중간에 문제가 생기면 `error/ApiExceptionHandler.kt`가 받아서 오류 JSON을 만든다.

읽는 순서를 하나만 고르라면 **Controller → Service → Repository**다. 이 세 개만
따라가면 대부분의 기능을 이해할 수 있다.

수동 입력의 가맹점 분류 추천은 다음 경로를 추가로 지난다.

| 순서 | 파일 | 하는 일 |
| --- | --- | --- |
| 1 | `transaction/MerchantClassificationRuleController.kt` | 가맹점 문자열을 추천 조회 요청으로 받는다 |
| 2 | `transaction/MerchantClassificationRuleService.kt` | 가맹점을 정규화하고 현재 household 규칙만 찾는다 |
| 3 | `transaction/MerchantClassificationRuleRepository.kt` | 규칙과 태그를 조회하거나 거래 저장과 함께 갱신한다 |

거래 생성 요청에 추천 규칙 ID가 있으면 `TransactionService`가 household, 정규화
가맹점, 카테고리와 태그가 현재 규칙과 모두 같은지 다시 검사한다. 일치할 때만 분류
출처를 `MERCHANT_RULE`로 기록하므로 다른 household 규칙 ID를 보내도 적용되지 않는다.

AI 자연어 거래 입력은 다음 경로를 지난다.

| 순서 | 파일 | 하는 일 |
| --- | --- | --- |
| 1 | `ai/AiTransactionDraftController.kt` | 최대 세 개의 사용자 메시지 형식과 길이를 검증한다 |
| 2 | `ai/AiSensitiveInputGuard.kt` | 외부 전송 금지 데이터가 있으면 요청을 400으로 거부한다 |
| 3 | `ai/AiTransactionDraftService.kt` | 메시지를 한 거래 문맥으로 합치고 현재 시각과 내부 사용자 ID를 생성 문맥에 넣는다 |
| 4 | `ai/OpenAiTransactionDraftGenerator.kt` | 프롬프트와 응답 스키마를 만들고 Responses API를 호출한다 |
| 5 | `ai/AiTransactionDraftService.kt` | 모델 출력을 서버 규칙으로 검증하고 household 구성원 결제자로 변환한다 |

기간별 지출 통계와 반복 지출 설명은 다음 경로를 지난다.

| 순서 | 파일 | 하는 일 |
| --- | --- | --- |
| 1 | `statistics/SpendingStatisticsController.kt` | 기간, 기준 날짜와 결제자 필터를 받는다 |
| 2 | `statistics/SpendingStatisticsService.kt` | 서울 시간 범위와 이전 비교 기간을 정하고 집계를 조합한다 |
| 3 | `statistics/SpendingStatisticsRepository.kt` | household와 결제자 범위 안에서 합계, 카테고리와 태그를 집계한다 |
| 4 | `statistics/RecurringSpendingChangeExplainer.kt` | 확정 태그의 신규·증가·감소·종료를 결정적인 문장으로 만든다 |

반복 지출 설명은 외부 AI를 호출하지 않는다. 사용자가 확정한 `구독`, `공과금`,
`정기결제` 태그만 비교하고, 반복성이 보장되지 않는 `생활` 카테고리나 가맹점 패턴은
자동으로 반복 지출이라 판단하지 않는다. 태그는 서로 겹칠 수 있으므로 설명별 금액을
더해 반복 지출 총액으로 사용하지 않는다.

AI 요청에서 `AiSensitiveInputGuard`는 금지 데이터를 외부 전송 직전에 검사하고,
`OpenAiSafetyIdentifier`는 내부 사용자 ID를 HMAC 가명 식별자로 바꾼다. 허용 전송
필드, 저장 금지 데이터와 검증 기준은 `docs/SECURITY.md`를 따른다.

AI 거래 초안은 카드·현금·QR 결제 경로와 온누리상품권·임산부 바우처 잔액 유형을
서로 다른 필드로 반환한다. 외부 AI에는 household의 실제 잔액 계정 ID를 전달하지 않는다.
웹은 AI가 반환한 잔액 유형과 결제자를 현재 household에서 조회한 사용자별 계정 ID와
연결하고, 사용자가 초안을 확인한 뒤 기존 `POST /transactions` 요청으로 저장한다.

사용자가 새 가맹점 규칙을 저장하면 `MerchantClassificationRuleService`가 같은
transaction 안에서 현재 household의 재확정 대상 `MIGRATION` 거래와 이전 규칙으로
자동 보완된 미확정 거래를 조회한다. 정규화 가맹점이 정확히 같고 후보 조회 이후
`updated_at`이 바뀌지 않은 거래만 `MERCHANT_RULE/HIGH`로 보완한 뒤 규칙 태그로
교체한다. 표준 카테고리로 정상 변환된 과거 사용자 입력, 사용자가 확정한 거래,
다른 자동 분류가 적용된 거래와 동시에 수정된 거래는 변경하지 않는다.

## 3. 각 계층이 맡는 일

### Controller — HTTP 담당

`transaction/TransactionController.kt`

바깥세상(HTTP, JSON)과 안쪽(Kotlin 객체) 사이를 번역한다. 비즈니스 판단은 하지
않는다. 하는 일은 세 가지다.

- 어떤 주소가 어떤 함수인지 연결 (`@PostMapping("/transactions")`)
- 요청 JSON을 객체로 받기 (`@RequestBody`)
- 값이 말이 되는지 검사 (`@Valid`와 `@field:NotNull` 같은 표시)

응답으로 내보내는 모양은 `TransactionResponse` 같은 별도 클래스로 정의한다. 데이터베이스
모델(`Transaction`)을 그대로 내보내지 않는다. 그래야 내부 구조를 바꿔도 API 약속이
안 깨진다.

거래 목록은 배열을 바로 반환하지 않고 `TransactionPageResponse`의 `items`와
`nextCursor`를 반환한다. 첫 요청은 최근 20건을 조회하고, 다음 요청은 `nextCursor`를
그대로 전달해 이전 기록을 이어서 조회한다. 커서는 정렬 기준인 발생 시각과 ID를
Base64 URL 형식의 불투명한 문자열로 표현하지만, 무결성을 보장하는 서명 값은 아니다.

### Service — 규칙 담당

`transaction/TransactionService.kt`

"이 거래를 저장해도 되는가"를 판단한다. 예를 들어 결제자가 우리 household 구성원인지,
카드 결제인데 카드사가 비어 있지는 않은지 확인한다. 별도 잔액을 사용하는 거래는
`StoredValueAccountService`와 함께 계정을 잠그고 잔액 부족 여부를 확인한 뒤 사용 변동을
저장한다. 카드와 QR은 결제 경로이며 온누리상품권과 임산부 바우처는 잔액 계정이다.

`@Transactional`이 붙은 함수는 **중간에 실패하면 그때까지 한 일이 전부 취소된다.**
거래를 저장하고 태그를 저장하는 두 단계 중 뒤가 실패하면 앞도 없던 일이 된다.

### Repository — 데이터베이스 담당

`transaction/TransactionRepository.kt`

이 프로젝트는 두 가지 방식을 섞어 쓴다.

**함수 이름으로 쿼리 만들기** — 몸통이 없는데 동작한다. 단순한 조회는 Spring이
이름을 읽어서 SQL을 자동으로 만든다.

```kotlin
fun findByIdAndHouseholdId(id: Long, householdId: Long): Transaction?
// → id와 household_id가 모두 일치하는 거래 한 건 조회
```

**SQL 직접 쓰기** — 조건이 복잡하면 `@Query`에 SQL을 적는다. `:householdId`처럼 콜론이
붙은 자리에 함수 인자가 들어간다.

거래 목록은 `(occurred_at, id)` 내림차순 커서 다음의 행만 `LIMIT`으로 조회한다.
household와 결제자 조건은 SQL에 함께 들어가며, Service는 실제 페이지에 포함된 거래
ID의 태그만 추가로 조회한다. 새 거래가 앞에 추가돼도 offset 페이지처럼 다음 조회의
행이 밀리지 않는다.

집계처럼 더 복잡한 건 `statistics/SpendingStatisticsRepository.kt`처럼 `JdbcTemplate`으로
직접 쓴다. `storedvalue/StoredValueAccountRepository.kt`도 계정별 변동 합계로 현재 잔액을
계산하고 동시 사용 전에 계정 행을 잠그기 위해 명시적인 SQL을 사용한다.

카드 명세서는 `CardStatementParser` 인터페이스 뒤에서 카드사별로 해석한다.
`KbCardStatementParser`는 XLSX를, `HyundaiCardStatementParser`는 HTML 기반 XLS를 읽어 같은
`StatementCandidate`로 변환한다. 현대카드 온누리 청구할인 행은 거래 후보로 만들지 않고
바로 앞 구매 후보에 `ONNURI_GIFT_CERTIFICATE` 잔액 힌트를 기록한다.

### 별도 잔액 — 상품권과 바우처

`storedvalue/StoredValueAccountController.kt`는 household 구성원별 온누리상품권·임산부
바우처 잔액 조회와 충전·지급 입력을 받는다. `stored_value_accounts`는 잔액의 종류와
`owner_user_id` 소유자를,
`stored_value_movements`는 충전·지급과 거래 사용을 보존한다. 잔액은 별도 숫자를 갱신하지
않고 변동의 `balance_delta` 합계로 계산한다.

거래가 잔액 계정을 사용하면 `transactions.stored_value_account_id`와 `SPEND` 변동이
연결된다. 거래 수정에서는 기존 사용 변동을 제거한 뒤 새 금액과 계정으로 다시 검증하고,
거래 삭제에서는 외래 키의 `ON DELETE CASCADE`가 사용 변동을 함께 제거해 잔액을 복원한다.

### Flyway — 데이터베이스 구조 변경

`src/main/resources/db/migration/`

`V1__...sql`부터 번호 순서대로 실행된다. 애플리케이션이 뜰 때 아직 실행 안 된 파일만
자동으로 돈다.

**이미 배포된 파일은 절대 수정하지 않는다.** 운영 데이터베이스에는 이미 실행 기록이
남아 있어서, 내용을 바꾸면 다음 배포가 실패한다. 바꾸고 싶으면 새 번호 파일을 추가한다.

## 4. Spring이 대신 해주는 것

Kotlin 문법은 아는데 "이건 왜 동작하지?" 싶은 지점들이다.

**생성자에 적은 것이 자동으로 채워진다**

```kotlin
class TransactionController(
    private val transactionService: TransactionService,
)
```

`TransactionService`를 직접 만들어 넣는 코드가 어디에도 없다. `@Service`, `@Repository`,
`@Component`, `@RestController`가 붙은 클래스는 Spring이 시작할 때 하나씩 만들어두고,
필요한 곳에 알아서 넣어준다. 그래서 생성자에 타입만 적으면 된다.

**함수 인자가 자동으로 채워진다**

```kotlin
fun create(currentUser: CurrentUser, @Valid @RequestBody request: CreateTransactionRequest)
```

`currentUser`에 아무 표시가 없는데도 값이 들어온다. `auth/CurrentUserArgumentResolver.kt`가
"`CurrentUser` 타입 인자를 보면 이렇게 채워라"고 등록돼 있기 때문이다. 등록은
`config/WebConfig.kt`의 `addArgumentResolvers`에서 한다.

**오류가 한곳으로 모인다**

`error/ApiExceptionHandler.kt`에 `@RestControllerAdvice`가 붙어 있다. 어느 Controller에서
예외가 나든 여기로 온다. 그래서 Controller마다 try/catch를 쓰지 않는다.

`ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "거래를 찾을 수 없습니다.")` 하나를
던지면 404 상태와 `code` 필드가 붙은 JSON이 알아서 나간다.

## 5. 무엇을 바꾸려면 어디를 고치나

| 하고 싶은 것 | 고칠 곳 |
| --- | --- |
| 거래에 필드 추가 | 새 Flyway 파일 → `transaction/Transaction.kt` → `TransactionController.kt`의 요청·응답 클래스 |
| 카테고리 목록 변경 | `transaction/TransactionClassification.kt` + 새 Flyway 파일(CHECK 제약도 함께) |
| 상품권·바우처 잔액 변경 | `storedvalue/StoredValueAccountController.kt` → `StoredValueAccountService.kt` → `StoredValueAccountRepository.kt` |
| 카드사 명세서 형식 추가 | `statement/CardStatementParser.kt` 구현 → parser 테스트 → 공통 대조·반영 테스트 |
| 가맹점 분류 추천 변경 | `transaction/MerchantClassificationRuleController.kt` → `MerchantClassificationRuleService.kt` → `MerchantClassificationRuleRepository.kt` |
| 새 API 주소 추가 | 해당 도메인 폴더에 Controller 함수 추가 |
| 검증 규칙 변경 | 단순 형식이면 요청 클래스의 `@field:` 표시, 판단이 필요하면 Service |
| 오류 메시지·코드 변경 | `error/ErrorCode.kt`와 예외를 던지는 곳 |
| 로그인 허용 계정 변경 | 코드가 아니라 환경변수 `GOOGLE_ALLOWED_EMAILS` |
| CORS 허용 주소·메서드 | `config/WebConfig.kt` |
| AI 프롬프트·모델 | `ai/OpenAiTransactionDraftGenerator.kt`, 모델은 환경변수 `OPENAI_MODEL` |
| AI 외부 전송 금지 데이터 | `ai/AiSensitiveInputGuard.kt` |
| AI 가명 식별자 | `ai/OpenAiSafetyIdentifier.kt`, 비밀값은 환경변수 `OPENAI_SAFETY_IDENTIFIER_SECRET` |

## 6. 고장났을 때

**로그 보기 (운영)**

```bash
docker compose -f compose.prod.yaml logs -f api
docker compose -f compose.prod.yaml ps        # 컨테이너 상태
```

**증상별 확인 지점**

| 증상 | 먼저 볼 곳 |
| --- | --- |
| 애플리케이션이 아예 안 뜬다 | Flyway 오류일 가능성이 높다. 로그 맨 위쪽의 migration 실패 메시지 |
| 로그인이 안 된다 | `GOOGLE_ALLOWED_EMAILS`에 이메일이 있는지, Google 콘솔의 redirect URI |
| 로그인은 되는데 401이 계속 뜬다 | `AllowedGoogleAccountFilter`가 매 요청 allowlist를 검사한다 |
| 로컬에서만 특정 요청이 막힌다 | CORS. `config/WebConfig.kt`의 `allowedMethods` |
| 저장은 되는데 화면에 안 보인다 | household 경계. 조회 쿼리에 `household_id` 조건이 있다 |
| AI 입력이 502를 낸다 | OpenAI 키·모델 설정, 또는 타임아웃(연결 5초·읽기 30초) |

**되돌리기**

배포는 `Release production` workflow를 수동 실행하는 구조다. 문제가 생기면 이전에
성공한 commit SHA로 `Deploy production`을 다시 실행하면 애플리케이션은 돌아간다.
다만 **Flyway로 이미 바뀐 데이터베이스 구조는 되돌아가지 않는다.** 스키마를 바꾼
배포를 되돌릴 때는 이 점을 먼저 확인한다.

## 7. 테스트를 명세로 읽기

문서는 낡지만 테스트는 낡으면 실패해서 티가 난다. 어떤 동작이 보장되는지 확실히
알고 싶으면 테스트를 본다.

```bash
pnpm test:api      # 저장소 루트에서
```

테스트 이름이 곧 보장 내용이다.

```
`updates a confirmed mismatched transaction and preserves user details`
`rejects a correction when the reviewed transaction changed after preview`
```

이 테스트들은 진짜 PostgreSQL을 임시로 띄워서(Testcontainers) 실제 Flyway migration을
적용한 뒤 돈다. 그래서 통과했다면 SQL과 스키마까지 함께 검증된 것이다.

기능을 바꿀 때는 관련 테스트부터 찾아 읽으면 무엇을 깨뜨리면 안 되는지 먼저 알 수 있다.
