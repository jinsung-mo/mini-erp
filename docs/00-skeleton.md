# 0단계: 프로젝트 골격

## 목표

업무 기능은 하나도 없지만 **화면 → API → DB가 끝까지 연결된 상태**를 먼저 만든다 (walking skeleton).
1단계부터는 "연결이 되나?"를 걱정하지 않고 업무 규칙에만 집중하기 위해서다.

`http://localhost:5173`의 시스템 상태 화면이 세 계층의 연결 상태를 보여준다.

```
브라우저 ──GET /api/system/status──▶ Vite(:5173) ──proxy──▶ Spring Boot(:8082) ──SQL──▶ PostgreSQL(:55433)
```

## 설계 결정

### 1. Spring Boot 4.1

처음에는 자료가 많은 3.5를 고려했지만, 3.5는 무료(OSS) 지원이 끝나 Spring Initializr에서 빠졌다
(2026-11-03 기준 4.0.9 / 4.1.2 / 4.2.0-RC1만 제공). 새 프로젝트를 지원이 끝난 버전으로 시작할 이유가 없어서
현재 GA 기본값인 4.1.2를 골랐다. 4.x에서 달라진 점: 스타터가 모듈별로 쪼개졌다(`spring-boot-starter-webmvc`,
`...-webmvc-test`). Jackson 3를 쓰고, null 안전성 표기가 JSpecify(`@Nullable`)로 바뀌었다.

### 2. 패키지는 계층이 아니라 도메인으로 나눈다

```
com.minierp.controller / service / repository        ← 계층별 (택하지 않음)
com.minierp.purchase / inventory / sales / common     ← 도메인별 (택함)
```

ERP는 모듈 단위로 커지고, 모듈 단위로 담당자가 나뉜다. 발주 기능을 고칠 때 `purchase/` 안만 보면 되게 한다.
나중에 모듈을 서비스로 떼어낼 때(MSA)도 경계가 이미 그어져 있다. 각 도메인 안에서는
Controller → Service → Repository 계층을 지킨다.

### 3. 에러 응답: HTTP 상태코드 + RFC 9457 ProblemDetail + 업무 `code`

```json
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{ "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "품목 ITEM-001을 찾을 수 없습니다.", "instance": "/api/...", "code": "NOT_FOUND" }
```

- **HTTP 상태코드를 그대로 쓴다.** 실패를 실패로 응답해야 브라우저 개발자 도구, 프록시, 모니터링이 실패로 집계한다.
  mini-mes는 기존 C# 시스템과의 계약(HTTP 200 + `resultCode`)을 지켜야 해서 그렇게 하지 않았다.
  mini-erp는 새로 만드는 시스템이라 표준을 따른다.
- **`code`는 화면이 분기하는 기준이다.** 409 하나에도 "재고 부족"과 "이미 승인된 발주"처럼 의미가 여러 가지다.
  화면은 `detail` 문구가 아니라 `code`로 분기한다. 문구는 바뀌어도 `code`는 API 계약이다.
- **Spring이 던지는 예외에도 `code`를 붙인다.** `ResponseEntityExceptionHandler`를 상속하면 404(없는 URL), 405,
  JSON 파싱 실패(400)도 ProblemDetail로 나간다. `createResponseEntity`에서 HTTP 상태 이름(`METHOD_NOT_ALLOWED`)을
  `code`로 넣어서, 화면은 어떤 실패든 같은 형식으로 받는다.
- **500 응답에는 내부 정보를 넣지 않는다.** 예외 메시지에는 DB 주소나 SQL이 섞일 수 있다.
  원인은 서버 로그(ERROR + 스택트레이스)에만 남기고, 응답은 "서버 내부 오류"로 통일한다.

### 4. JPA 기본 설정

| 설정 | 값 | 이유 |
|---|---|---|
| `spring.jpa.open-in-view` | `false` | 켜 두면 HTTP 응답이 끝날 때까지 DB 커넥션을 붙잡는다. 요청이 몰리면 커넥션 풀이 바닥나서 상관없는 API까지 멈춘다. 끄면 트랜잭션(Service)이 끝날 때 커넥션을 반납한다 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 테이블을 Hibernate가 자동으로 만들거나 바꾸지 않는다. ERP 테이블에는 `CHECK (수량 >= 0)` 같은 제약조건이 데이터를 지키는 마지막 방어선이라 DDL을 직접 관리한다 (1단계에서 마이그레이션 도구 도입) |
| `hikari.connection-timeout` | `5000` | 커넥션을 못 얻을 때 기본 30초를 기다린다. 로컬에서는 DB가 꺼졌을 때 5초 만에 실패하게 했다 |

### 5. 프론트에서 API를 부르는 방식: Vite 프록시

브라우저는 `localhost:5173/api/...`로 요청하고, Vite 개발 서버가 `127.0.0.1:8082`로 넘긴다.
브라우저 입장에서는 출처(origin)가 하나라서 **CORS 설정이 필요 없다.** 운영에서는 nginx 같은 리버스 프록시가
같은 역할을 한다 (mini-mes의 nginx `/java` → WAS 프록시와 같은 구조).

모든 호출은 `erp-web/src/api/client.ts`의 `api()` 함수 하나를 지나간다. 실패는 항상 `ApiError`(`status`, `code`,
`fieldErrors`, `fromServer`)로 바뀌어서, 화면마다 에러 처리 방식이 달라지지 않는다.

### 6. 포트와 바인딩

| 계층 | 주소 | 비고 |
|---|---|---|
| WEB | `localhost:5173` | Vite 기본 포트, `strictPort`로 고정 |
| WAS | `127.0.0.1:8082` | 8080(팀원 컨테이너), 8081(mini-mes)을 피함. 같은 네트워크의 다른 PC에서는 접근할 수 없다 |
| DB | `127.0.0.1:55433` | 55432(mini-mes)를 피함 |

## 확인한 장애 시나리오

시스템 상태 화면에서 세 경우를 모두 재현했다.

| 상황 | API 응답 | 화면 |
|---|---|---|
| 정상 | `200` + DB 버전/시각 | WEB·WAS·DB 모두 정상 |
| DB 중지 (`docker compose stop db`) | 약 5초 뒤 `500` ProblemDetail `INTERNAL_SERVER_ERROR` | WAS 정상, DB 응답 없음 |
| API 중지 | Vite 프록시가 `502` `text/plain` 반환 | WAS 응답 없음, DB 알 수 없음 |

DB를 다시 켜면 재시작 없이 바로 복구된다 (커넥션 풀이 새 커넥션을 만든다).
DB가 꺼졌을 때 실제 원인(`CannotGetJdbcConnectionException`)은 API 로그에만 남고 응답에는 나가지 않는다.

**화면은 "API가 답했는가"를 어떻게 아는가:** 상태코드만으로는 알 수 없다. 500은 Spring도, 프록시도 돌려줄 수 있다.
그래서 응답 본문이 ProblemDetail 모양(`status`, `code`)이면 Spring이 직접 답한 것으로 본다.
API가 꺼져 있으면 Vite가 대신 `502 text/plain`을 돌려주므로 ProblemDetail로 읽히지 않는다.

## 트러블슈팅 메모

- **`localhost` vs `127.0.0.1`:** API는 IPv4 loopback(`127.0.0.1`)에서만 받는다. Node 17+는 `localhost`를 IPv6(`::1`)로
  먼저 풀 수 있어서, 프록시 대상을 `localhost:8082`로 쓰면 연결이 거부될 수 있다. 프록시 대상은 `127.0.0.1`로 적었다.
- **React effect 안의 setState (oxlint `react(set-state-in-effect)` 경고):** 처음에는 `useEffect`에서 "확인 중"으로
  바꾸는 함수를 바로 호출했다. 이렇게 하면 렌더링 직후 렌더링이 한 번 더 일어난다. 조회 함수(`fetchStatus`)는 결과만
  돌려주고, effect는 결과를 반영만 하도록 나눴다. `ignore` 플래그를 둬서 화면을 떠난 뒤 늦게 도착한 응답은 버린다.
- **Spring Initializr 기본 테스트(`contextLoads`) 제거:** 전체 컨텍스트를 띄우는 테스트라 DB가 없으면 실패한다.
  대신 DB 없이 웹 계층만 띄우는 `@WebMvcTest`로 에러 응답 형식 6가지를 검증한다. DB가 필요한 테스트는
  실제 PostgreSQL로 돌릴 방법을 정해서 이후 단계에서 추가한다.
