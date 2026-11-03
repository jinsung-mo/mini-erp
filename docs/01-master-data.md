# 1단계: 기준정보 ① 품목

## 마스터 데이터란

| 구분 | 예 | 특징 |
|---|---|---|
| 마스터 데이터 | 품목, 거래처, 창고 | 잘 바뀌지 않고, 모든 거래가 참조한다 |
| 트랜잭션 데이터 | 발주, 입고, 출고 | 매일 쌓인다 |

발주서 한 줄은 "어느 거래처에서, 어느 품목을, 몇 개"이고, 전부 마스터를 가리키는 참조다.
마스터가 틀리면 그 위에 쌓인 모든 거래가 틀린다. 그래서 마스터에는 세 가지 규칙을 둔다.

1. **코드는 유일하고, 등록 후 바꾸지 않는다.** `BOLT-M10`은 엑셀, 바코드, 다른 시스템이 이 품목을 부르는 이름(업무 키)이다.
   테이블끼리의 참조(FK)에는 바뀔 일이 없는 숫자 `id`(대리 키)를 쓴다. 코드는 `UNIQUE` 제약으로, 변경 불가는
   `@Column(updatable = false)`와 "수정 요청에 코드 필드가 없음"으로 지킨다.
2. **삭제하지 않고 사용 중지한다.** 작년 발주서가 이 품목을 가리키고 있다. 지우면 이력이 깨지거나(FK가 없다면) 지울 수 없다(FK가 있다면).
   그래서 DELETE API가 없고, 사용 중지/재개를 업무 행위로 드러낸다(`POST /api/items/{id}/deactivate`).
3. **누가 언제 바꿨는지 남긴다.** 아래 감사 컬럼.

## 테이블은 Flyway로 직접 관리한다

```
erp-api/src/main/resources/db/migration/
└─ V1__create_item.sql      ← 서버가 뜰 때 아직 적용 안 된 파일만 순서대로 실행
```

- 적용 기록은 DB의 `flyway_schema_history` 테이블에 버전·체크섬과 함께 남는다. 같은 파일은 두 번 실행되지 않는다.
- Hibernate는 `ddl-auto: validate`로 엔티티와 테이블이 맞는지만 검사한다. 안 맞으면 서버가 뜨지 않는다(빨리 실패).
- **이미 적용된 파일은 고치지 않는다.** V1에 주석 한 줄만 추가해도 다음 기동 때 서버가 뜨지 않는다(직접 재현함).
  ```
  Migration checksum mismatch for migration version 1
  -> Applied to database : 842834393
  -> Resolved locally    : 2044316923
  ```
  고칠 것이 있으면 `V2__...sql`을 새로 만든다. 모든 환경(로컬·개발·운영)이 같은 순서로 같은 파일을 실행해야 하기 때문이다.
- `ddl-auto: update`를 쓰지 않는 이유: 컬럼 이름을 바꾸면 새 컬럼만 생기고 옛 컬럼은 남는다. 애플리케이션이 운영 DB 구조를
  몰래 바꾸고, 변경 이력도 남지 않는다.
- Liquibase가 아니라 Flyway인 이유: DB가 PostgreSQL 하나라 DB 독립 포맷(XML/YAML)이 필요 없고, SQL을 그대로 보는 편이 낫다.

DB 제약조건은 데이터를 지키는 마지막 방어선이다.

```sql
constraint uk_item_code unique (code),                              -- 동시에 같은 코드가 들어와도 하나만 저장
constraint ck_item_unit check (unit in ('EA', 'BOX', 'KG', 'L'))    -- 코드에 버그가 있어도 이상한 단위는 못 들어옴
```

대가도 있다. 단위를 추가하려면 Java enum과 CHECK 제약을 함께 바꿔야 한다(새 마이그레이션).

## 감사 컬럼: 누가, 언제

모든 업무 테이블은 `BaseEntity`를 상속해서 `created_at/by`, `updated_at/by`를 가진다.

```
요청 (X-User: buyer01)
  → CurrentUser.id()            헤더에서 작업자 id를 꺼냄 (없거나 형식이 틀리면 anonymous)
  → AuditorAware                "누가"
  → DateTimeProvider            "언제" = Instant.now() (서버 시간대와 무관한 UTC 시점, 컬럼은 timestamptz)
  → AuditingEntityListener      INSERT/UPDATE 직전에 BaseEntity 필드를 채움
```

- 개발자가 `setUpdatedBy()`를 부르지 않는다. 그래서 빠뜨릴 수 없다.
- `@EnableJpaAuditing`을 메인 클래스가 아니라 `JpaAuditingConfig`에 붙였다. 메인 클래스에 붙이면 JPA가 없는 `@WebMvcTest`가
  JPA 설정을 찾다가 실패한다. 테스트 슬라이스는 별도 `@Configuration` 클래스를 스캔하지 않는다.
- **작업자 선택은 학습용이다.** 로그인 대신 상단바에서 고른 작업자를 `X-User` 헤더로 보낸다. 헤더는 누구나 바꿀 수 있으므로
  실서비스에서는 인증 토큰(Spring Security)에서 꺼내야 한다. HTTP 헤더에는 ASCII만 안전해서 한글 이름이 아니라 영문 id를 보낸다.
  2단계의 직무 분리 규칙("본인이 만든 발주는 본인이 승인할 수 없다")도 이 작업자 정보로 구현한다.

## 품목 API

| 메서드 | 경로 | 설명 | 실패 |
|---|---|---|---|
| GET | `/api/items?keyword=&active=` | 코드·품목명 검색 (대소문자 무시), 사용 여부 필터 | |
| GET | `/api/items/{id}` | 상세 | 404 `NOT_FOUND` |
| POST | `/api/items` | 등록 → `201 Created` + `Location` | 400 `INVALID_INPUT`, 409 `DUPLICATE_CODE`·`DUPLICATE_KEY` |
| PUT | `/api/items/{id}` | 품목명·규격·단위 수정 (코드 제외) | 400, 404 |
| POST | `/api/items/{id}/deactivate` | 사용 중지 | 404 |
| POST | `/api/items/{id}/activate` | 사용 재개 | 404 |

계층별 역할:

- **Controller**: HTTP ↔ DTO 변환, `@Valid` 입력 검증
- **Service**: 업무 규칙(중복 코드), 트랜잭션 경계. 클래스 기본값은 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`.
  엔티티 → 응답 DTO 변환도 Service 안에서 끝낸다(OSIV를 껐으므로 트랜잭션 밖에서는 지연 로딩을 할 수 없다)
- **Entity**: 자기 상태를 스스로 지킨다. setter 없이 `changeInfo()`, `deactivate()`처럼 의도가 드러나는 메서드로만 바뀐다
- **Repository**: 조회 쿼리 (`search`는 JPQL)

엔티티를 그대로 응답하지 않고 DTO(`ItemDtos.Response`)로 바꾼다. 엔티티를 그대로 내보내면 테이블 구조가 API 계약에 묶이고,
연관관계가 생겼을 때 지연 로딩이 트랜잭션 밖에서 실패하거나 순환 참조로 JSON이 끝없이 커진다.

## 트러블슈팅

### 1. 중복 검사를 했는데도 중복 에러(500)가 난다: check-then-act 경쟁

```java
if (itemRepository.existsByCode(code)) throw DUPLICATE_CODE;   // ① 검사
itemRepository.save(new Item(code, ...));                        // ② 저장
```

두 요청이 동시에 ①을 "없음"으로 통과하면 둘 다 ②로 간다. 검사와 저장 사이가 아주 짧아도 동시 요청은 그 틈에 끼어든다.
`ItemCodeRaceTest`가 같은 코드로 10개 요청을 동시에 보내서 재현한다.

```
[동시 등록 결과] {201=1, 409 DUPLICATE_KEY=9}
```

10개가 전부 ①을 통과했고, 9개는 DB unique 제약에 막혔다. 방어는 두 겹이다.

| 방어 | 위치 | 역할 |
|---|---|---|
| 1차: 사전 검사 | `ItemService.create` | 흔한 경우(이미 있는 코드)에 친절한 메시지 → 409 `DUPLICATE_CODE` |
| 2차: unique 제약 | `uk_item_code` | 동시 요청까지 막는 진짜 보장. 걸리면 `GlobalExceptionHandler`가 SQLState `23505`를 보고 409 `DUPLICATE_KEY`로 응답 |

2차 방어의 예외를 처리하지 않으면 9개가 500으로 나간다. unique 위반이 아닌 제약 위반(NOT NULL, FK, CHECK)은
입력 검증이나 업무 규칙이 놓친 버그이므로 그대로 500으로 남긴다. 5단계의 "동시 출고로 재고가 음수가 되는 문제"도 같은 구조다.

### 2. 수정했는데 응답의 수정자·수정일시가 옛날 값이다: flush 시점

`@LastModifiedBy`와 `@LastModifiedDate`는 `@PreUpdate`에서 채워진다. `@PreUpdate`는 UPDATE가 실행되는 flush 때 불린다.
변경 감지(dirty checking)는 보통 커밋 직전에 flush하므로, 엔티티를 바꾸고 곧바로 DTO를 만들면 응답에는 바뀌기 전 값이 담긴다.
`ItemService.flushAndRespond()`가 DTO를 만들기 전에 `flush()`한다. `ItemApiTest`가 수정 응답의 `updatedBy`를 검사한다.

등록은 이 문제가 없다. `IDENTITY` 전략은 id를 받아야 해서 `save()` 순간 INSERT를 실행하고, `@PrePersist`도 그때 불린다.

### 3. 통합 테스트에 `@Transactional`을 붙이지 않은 이유

붙이면 테스트가 끝날 때 롤백되어 편하지만, 요청 전체가 테스트 트랜잭션 하나에 묶인다. 그러면 커밋·flush 시점에 생기는 문제
(위 2번, 지연 로딩)가 테스트에서는 재현되지 않는다. 대신 테스트마다 `truncate table item restart identity`로 비운다.

### 4. Windows Git Bash에서 curl로 한글 JSON을 보내면 400 `BAD_REQUEST`

`curl -d '{"name":"육각볼트"}'`가 `Failed to read request`(JSON 파싱 실패)로 실패했다. 같은 요청을 ASCII로만 보내면 201이 나왔다.
`--trace-ascii`로 보니 `육` 한 글자가 2바이트로 전송되었다. UTF-8이면 3바이트다. Windows가 명령줄 인자를 시스템 코드페이지(CP949)로
바꿔 curl에 넘긴 것이다. 본문을 UTF-8 파일로 만들어 `--data-binary @body.json`으로 보내면 된다(201 확인).
화면(브라우저)과 Node `fetch`는 항상 UTF-8로 보내므로 영향이 없다.

## 테스트

| 테스트 | DB | 검증 |
|---|---|---|
| `GlobalExceptionHandlerTest` | 없음 (`@WebMvcTest`) | 에러 응답 형식 8가지 (DB 제약 위반 → 409/500 포함) |
| `ItemApiTest` | Testcontainers PostgreSQL 17 | 등록·중복·형식 오류·수정·사용 중지·검색·404 (8개) |
| `ItemCodeRaceTest` | Testcontainers PostgreSQL 17 + 실제 서버 | 동시 등록 10건 → 1건만 저장, 500 없음 |

H2가 아니라 실제 PostgreSQL로 테스트한다. SQL 문법, 제약조건, 락 동작이 운영과 같아야 5단계 동시성 테스트가 의미 있다.
`./gradlew test`를 돌리려면 Docker가 켜져 있어야 한다.

화면은 헤드리스 Chrome으로 실제 클릭·입력하며 확인했다. 확인한 흐름: 목록 조회, 코드 자동 대문자 변환, 품목명이 비었을 때 서버 검증
오류 표시, 등록 후 수정 모드 전환, 중복 코드 안내, 작업자를 바꿔 수정하면 수정자만 바뀌는 것, 사용 중지 후 "사용" 목록에서 빠지고
"전체" 목록에는 남는 것.
