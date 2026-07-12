# API 애플리케이션 작업 지침

이 파일은 저장소 루트 `AGENTS.md`의 공통 규칙에 API 전용 규칙을 추가한다.

## 기술 구성

- Kotlin
- Spring Boot
- Spring Web MVC
- Spring Data JDBC
- Flyway
- PostgreSQL
- Gradle Wrapper

## Kotlin 규칙

- null 가능성을 타입으로 명확하게 표현하고 불필요한 `!!`를 사용하지 않는다.
- 데이터 전달에는 간결한 `data class`를 사용한다.
- 변경할 이유가 없는 값과 참조는 `val`로 선언한다.
- 예외를 정상적인 분기 처리 수단으로 남용하지 않는다.
- Java 스타일을 그대로 옮기기보다 읽기 쉬운 Kotlin 표준 표현을 우선한다.
- 프레임워크가 요구하지 않는 한 클래스와 함수를 불필요하게 `open`으로 만들지 않는다.

## 계층과 API 규칙

- Controller는 HTTP 매핑, 요청 변환, 입력 검증에 집중한다.
- 실제 비즈니스 규칙이 있을 때 Service를 만든다. 단순히 Repository 호출만 전달하는
  Service는 만들지 않는다.
- 데이터베이스 접근은 Spring Data Repository 또는 명시적인 JDBC 쿼리에서 한다.
- HTTP 경계에서는 요청·응답 DTO를 사용한다. 영속 모델을 그대로 노출하지 않는다.
- API 경로는 `/transactions`처럼 복수 명사를 사용한다.
- 중앙 오류 처리를 도입한 이후 JSON 오류 응답에는 안정적인 기계 판독용 코드와
  이해하기 쉬운 메시지를 포함한다.
- 입력은 API 경계에서 검증하고 성공·실패 요청을 모두 테스트한다.
- `/health`는 가벼운 애플리케이션 상태 확인 엔드포인트로 유지한다.

## 데이터 규칙

- 시간은 timezone을 보존하는 타입으로 저장하고 ISO 8601 형식으로 직렬화한다.
- 금액은 정수 KRW로 저장하며 `Float` 또는 `Double`을 사용하지 않는다.
- household 기능 추가 이후 모든 가구 소유 데이터는 `household_id`로 제한한다.
- 스키마 변경은 `src/main/resources/db/migration`의 Flyway migration으로 작성한다.
- 배포 환경에서 Hibernate 또는 기타 도구의 자동 스키마 생성에 의존하지 않는다.
- 이미 배포된 migration을 수정하지 않고 새 migration을 추가한다.

## 테스트와 검증

- 입력 검증과 HTTP 계약은 Controller 수준에서 테스트한다.
- 쿼리와 매핑은 실제 PostgreSQL과 최대한 가까운 방식으로 검증한다.
- 비즈니스 규칙은 Spring context 없이 단위 테스트할 수 있게 작성한다.
- 테스트 이름은 동작과 기대 결과가 드러나게 작성한다.

저장소 루트에서 실행한다.

```bash
pnpm test:api
pnpm build:api
```

전역 Gradle이나 Kotlin 대신 `apps/api/gradlew`를 사용한다.
