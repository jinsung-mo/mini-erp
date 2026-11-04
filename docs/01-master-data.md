# 1단계: 기준정보 (품목 · 거래처 · 창고)

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

코드 형식(영문 대문자·숫자·하이픈 2~20자)은 품목·거래처·창고가 같은 규칙을 쓴다(`MasterCode`). 코드에는 **바뀌지 않는 의미만** 담는다.
볼트의 규격 M10은 바뀌지 않으므로 `BOLT-M10`은 괜찮지만, 거래처 코드에 역할을 넣으면(`SUP-001`) 그 공급처가 나중에 고객도 될 때
코드가 거짓말을 하게 된다. 코드는 바꿀 수 없으므로 거래처는 `P0001`처럼 의미 없는 번호로 짓는다.

## 테이블은 Flyway로 직접 관리한다

```
erp-api/src/main/resources/db/migration/
├─ V1__create_item.sql                     ← 서버가 뜰 때 아직 적용 안 된 파일만 순서대로 실행
└─ V2__create_partner_and_warehouse.sql
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
  거래처·창고 테이블도 V1을 고치지 않고 V2로 추가했다.
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

## ① 품목

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

## ② 거래처: 한 회사는 레코드 하나, 공급처·고객은 역할

물건을 사 오는 곳이 공급처(매입), 파는 곳이 고객(매출)이다. 한 회사가 둘 다인 경우가 흔하다(원자재를 사 오는 회사에 제품을 판다).
이 회사를 공급처용·고객용으로 **두 번 등록하면**:

- 시스템은 두 레코드가 같은 회사인지 모른다. 그 회사와의 거래 실적, 받을 돈(채권)과 줄 돈(채무)이 둘로 쪼개진다.
- 회사 정보가 바뀌면 두 곳을 다 고쳐야 하고, 한쪽만 고치면 데이터가 어긋난다.

그래서 거래처는 회사당 하나이고, 공급처·고객은 그 거래처에 붙는 **역할**이다. SAP도 예전에는 고객 마스터와 공급업체 마스터가
따로였고, S/4HANA에서 Business Partner 하나에 역할을 붙이는 방식으로 합쳤다.

| | 모양 | 이유 |
|---|---|---|
| DB | `supplier`, `customer` boolean 컬럼 + `check (supplier or customer)` | 역할이 2개로 고정이라 단순하다. 역할 없는 거래처는 DB가 막는다 |
| API | `"roles": ["SUPPLIER", "CUSTOMER"]`, 검증 `@NotEmpty` | 화면은 역할 목록으로 다루는 편이 자연스럽고, 역할이 늘어도 API 모양이 그대로다 |
| 조회 | `GET /api/partners?role=SUPPLIER&active=true` | 2단계 발주는 공급처만, 4단계 수주는 고객만 고른다. 공급처 겸 고객은 양쪽에 다 나온다 |

DB와 API의 모양이 다르고, 그 사이를 DTO(`PartnerDtos`)와 엔티티(`Partner.getRoles()`, `hasRole()`)가 번역한다.

### 사업자등록번호: "같은 회사인가"를 판별하는 열쇠

코드는 사람이 정하므로 같은 회사를 다른 코드로 또 등록할 수 있다. 국세청이 사업자마다 매기는 사업자등록번호에
unique 제약(`uk_partner_business_no`)을 걸어 막는다.

1. **입력은 관대하게, 저장은 한 형식으로.** `123-45-67891`과 `1234567891`이 섞여 저장되면 unique 제약은 둘을 다른 값으로 보고
   중복을 못 막는다. 입력은 하이픈·공백이 있어도 받고(`BusinessNumber.normalize`), 저장은 숫자 10자리만 한다.
   DB도 `check (business_no ~ '^[0-9]{10}$')`로 한 번 더 막는다. 하이픈은 화면이 붙여서 보여 준다(`formatBusinessNo`).
2. **오타는 입구에서 거른다.** 마지막 자리는 앞 9자리로 계산한 검증번호다(가중치 1,3,7,1,3,7,1,3,5).
   이 검사는 Bean Validation 커스텀 제약 `@ValidBusinessNumber`로 만들었다. 그래서 `@NotBlank`처럼 400 응답의 `errors`에
   `businessNo` 필드 오류로 나가고, 화면은 별도 처리 없이 그 입력 아래에 보여 준다.
   형식 검증은 DTO에서, DB를 봐야 하는 검증(중복)은 Service에서 한다.
3. **NULL과 unique.** 개인 고객·해외 거래처는 번호가 없다. PostgreSQL의 unique 제약은 NULL끼리를 같은 값으로 보지 않으므로
   번호 없는 거래처는 여러 개 등록할 수 있다. CHECK 제약도 결과가 NULL이면 통과한다.

중복이면 409 `DUPLICATE_BUSINESS_NO`로 **어느 거래처와 겹치는지** 알려 준다
(`이미 등록된 사업자등록번호입니다: 101-81-00087 (P0007 차카물산)`). 대개 "새로 등록하지 말고 그 거래처에 역할을 추가하라"는 뜻이다.

## ② 창고: 재고가 놓이는 곳

코드·이름·위치뿐인 가장 단순한 마스터지만 3단계의 축이다. **재고는 "품목 × 창고" 단위로 쌓인다.** 같은 볼트라도 본사 창고의
30개와 외부 창고의 70개는 다른 재고이고, 창고 간 이동은 "A 창고 출고 + B 창고 입고" 두 줄로 기록한다.
"재고가 남은 창고는 사용 중지할 수 없다"는 규칙은 재고가 생기는 3단계에서 `deactivate()`에 더한다.

## 거래처·창고 API

| 메서드 | 경로 | 설명 | 실패 |
|---|---|---|---|
| GET | `/api/partners?keyword=&role=&active=` | 코드·거래처명 검색, 숫자로 된 검색어는 사업자등록번호에서도 찾음. `role`은 `SUPPLIER`/`CUSTOMER` | 400 `BAD_REQUEST` (role 값 오류) |
| POST | `/api/partners` | 등록 | 400 `INVALID_INPUT` (`businessNo`, `roles` 등), 409 `DUPLICATE_CODE`·`DUPLICATE_BUSINESS_NO`·`DUPLICATE_KEY` |
| PUT | `/api/partners/{id}` | 거래처명·사업자등록번호·구분 수정 (코드 제외) | 400, 404, 409 `DUPLICATE_BUSINESS_NO` |
| GET·POST | `/api/partners/{id}`, `/{id}/deactivate`, `/{id}/activate` | 품목과 같음 | 404 |
| * | `/api/warehouses` ... | 품목과 같은 모양 (창고명·위치) | 품목과 같음 |

## 세 번째 반복: 공통으로 뺀 것과 빼지 않은 것

품목·거래처·창고는 모양이 거의 같지만 `BaseMasterService<T>` 같은 공통 CRUD는 만들지 않았다.

- **업무 규칙은 도메인마다 둔다.** 곧 갈라진다. 거래처는 이미 사업자등록번호 규칙이 더해졌고, 창고는 3단계에서
  "재고가 남으면 사용 중지 불가", 거래처는 "진행 중인 발주가 있으면 사용 중지 불가"가 들어온다. 공통 클래스였다면
  분기(`if (this instanceof ...)`)나 훅 메서드가 늘어난다.
- **배관은 뺀다.**
  - 백엔드: 코드 형식 규칙(`MasterCode`), 검색어 → LIKE 패턴(`LikePatterns`). 원래 있던 `BaseEntity`, `ErrorCode`, `GlobalExceptionHandler`.
  - 화면: 조회 → 선택 → 저장 → 다시 조회 흐름(`useMasterPage`), 저장 중 표시와 서버 오류를 필드별로 나누기(`useMasterForm`),
    그리드·입력 패널 틀·사용 중지 버튼(`MasterParts`). 화면 파일에는 그 화면에만 있는 것(조회 조건, 컬럼, 입력 항목)만 남는다.
    품목 화면도 이 부품으로 바꿨고, 기존 화면 동작이 그대로인지 다시 확인했다.

`useMasterPage(itemApi.search, ...)`처럼 조회 함수는 **컴포넌트 밖에서 만든 함수**를 넘긴다. 렌더링마다 새로 만드는 화살표 함수
(`(s) => itemApi.search(s)`)를 넘기면 effect의 의존성이 매번 바뀌어 조회가 끝없이 반복된다.

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
입력 검증이나 업무 규칙이 놓친 버그이므로 그대로 500으로 남긴다. 사업자등록번호 중복도 같은 두 겹 구조다
(`DUPLICATE_BUSINESS_NO` → `uk_partner_business_no`). 5단계의 "동시 출고로 재고가 음수가 되는 문제"도 같은 구조다.

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

### 5. 마이그레이션이 중간에 실패하면 DB는 어떤 상태로 남나 (직접 재현)

V2에는 `create table partner`와 `create table warehouse`가 차례로 들어 있다. 두 번째 테이블에 일부러 타입 오타(`varchr`)를 넣고 서버를 띄웠다.

```
Migrating schema "public" to version "2 - create partner and warehouse"
Migration of schema "public" to version "2 - create partner and warehouse" failed! Changes successfully rolled back.
SQL State  : 42704
Message    : ERROR: type "varchr" does not exist
```

DB를 확인하니 먼저 성공했던 `partner` 테이블도 없었고, `flyway_schema_history`에는 V1만 있었다(실패 기록도 없음).
PostgreSQL은 DDL도 트랜잭션 안에서 실행하고, Flyway는 마이그레이션 파일 하나를 트랜잭션 하나로 실행한다. 그래서 파일 전체가
없던 일이 되었고, 오타만 고쳐서 다시 띄우면 됐다(`Successfully applied 1 migration ... now at version v2`).

MySQL이라면 다르다. DDL은 실행 즉시 커밋되므로 `partner` 테이블은 남고, 이력에는 실패한 V2가 기록된다. 남은 테이블을 손으로
지우고 `flyway repair`로 실패 기록을 지운 뒤 다시 실행해야 한다. 운영 배포 중에 이런 일이 생기면 DB가 반쯤 바뀐 상태로 서비스가 멈춘다.
PostgreSQL에서도 `create index concurrently`처럼 트랜잭션 안에서 실행할 수 없는 문장은 이 보장을 받지 못한다.

### 6. 중복 검사를 했는데 친절한 메시지 대신 `DUPLICATE_KEY`가 난다: 자동 flush (직접 재현)

거래처 수정에서 "엔티티를 바꾸고 → 중복 검사"로 순서만 바꿔서 테스트를 돌렸다.

```java
partner.changeInfo(name, businessNo, roles);       // ① 엔티티 변경 (아직 DB에는 안 감)
checkBusinessNoNotTaken(businessNo, partner.getId()); // ② 중복 검사 쿼리
```

`expected:<DUPLICATE_BUSINESS_NO> but was:<DUPLICATE_KEY>`로 실패했다. SQL 로그를 보면 ② 쿼리보다 UPDATE가 먼저 나갔다.

```
select ... from partner where id=?                          ← 수정할 거래처 조회
update partner set business_no='1234567891', ... where id=? ← ②를 실행하기 직전에 자동 flush
ERROR: duplicate key value violates unique constraint "uk_partner_business_no"
```

Hibernate는 JPQL 쿼리를 실행하기 전에, 그 쿼리 결과에 영향을 줄 수 있는 변경(같은 테이블의 바뀐 엔티티)을 먼저 DB에 반영한다
(flush 모드 AUTO). 쿼리가 최신 상태를 보게 하려는 장치인데, 변경이 검사보다 먼저면 검사 쿼리가 실행되기도 전에 DB 제약이 터진다.
그래서 `PartnerService.update`는 **검사를 먼저, 변경은 나중에** 한다. 응답 코드는 409로 같아서 놓치기 쉽지만, 사용자는 어느 거래처와
겹치는지 알 수 없게 된다. `PartnerApiTest`의 "수정해서 다른 거래처의 사업자번호와 겹치면" 테스트가 이 순서를 지킨다.

### 7. 수정할 때 "자기 자신과 중복"이 나는 버그 (테스트로 막음)

품목은 코드를 바꿀 수 없어서 수정할 때 중복 검사가 없었다. 사업자등록번호는 오타를 고칠 수 있어야 하므로 수정할 때도 검사하는데,
`existsByBusinessNo(no)`처럼 단순히 찾으면 이름만 고쳐도 자기 번호와 겹쳐 409가 난다. 찾은 거래처가 자기 자신이면 통과시킨다
(`findByBusinessNo(no).filter(other -> !other.getId().equals(selfId))`). "수정할 때 자기 사업자번호는 중복이 아니다" 테스트가 지킨다.

### 8. 검색어에서 숫자만 뽑아 쓰면 모든 거래처가 걸린다 (테스트로 막음)

사업자등록번호 검색을 "검색어에서 숫자만 뽑아 `%숫자%`"로 만들면, `볼트`처럼 숫자가 없는 검색어는 빈 문자열 → `%%`가 되어
모든 거래처와 일치한다. 그래서 검색어가 숫자·하이픈·공백으로만 되어 있을 때만 사업자등록번호 조건을 만든다(`PartnerService.businessNoPattern`).
같은 이유로 `P0001` 같은 코드 검색이 사업자등록번호까지 뒤지지 않는다.

## 테스트

| 테스트 | DB | 검증 |
|---|---|---|
| `GlobalExceptionHandlerTest` | 없음 (`@WebMvcTest`) | 에러 응답 형식 8가지 (DB 제약 위반 → 409/500 포함) |
| `ItemApiTest` | Testcontainers PostgreSQL 17 | 등록·중복·형식 오류·수정·사용 중지·검색·404 (8개) |
| `ItemCodeRaceTest` | Testcontainers PostgreSQL 17 + 실제 서버 | 동시 등록 10건 → 1건만 저장, 500 없음 |
| `BusinessNumberTest` | 없음 (순수 단위 테스트) | 정규화, 검증번호 통과·실패(자리 바뀜 오타 포함), 표시 형식 (10개) |
| `PartnerApiTest` | Testcontainers PostgreSQL 17 | 숫자 10자리 저장, 역할 둘 다, 검증번호·구분 오류, 하이픈만 다른 중복, NULL 여러 개, 자기 자신 제외, 역할 조회, 사업자번호 검색, role 값 오류 (11개) |
| `WarehouseApiTest` | Testcontainers PostgreSQL 17 | 등록·중복·수정(빈 위치 → null)·사용 중지 (4개) |

H2가 아니라 실제 PostgreSQL로 테스트한다. SQL 문법, 제약조건, 락 동작이 운영과 같아야 5단계 동시성 테스트가 의미 있다.
`./gradlew test`를 돌리려면 Docker가 켜져 있어야 한다.

화면은 헤드리스 Chrome으로 실제 클릭·입력하며 확인했다.

- 품목: 목록 조회, 코드 자동 대문자 변환, 품목명이 비었을 때 서버 검증 오류 표시, 등록 후 수정 모드 전환, 중복 코드 안내,
  작업자를 바꿔 수정하면 수정자만 바뀌는 것, 사용 중지 후 "사용" 목록에서 빠지고 "전체" 목록에는 남는 것.
  공통 부품으로 바꾼 뒤 목록·선택·전체 조회를 다시 확인했다.
- 거래처: 사업자등록번호 하이픈 표시, 검증번호 오류와 구분 미선택 오류가 각 입력 아래에 동시에 표시, 하이픈 없이 입력해도
  하이픈 형식으로 표시, 같은 번호로 다시 등록하면 기존 거래처(P0007)를 알려 주는 것, 기존 거래처에 역할을 추가하는 흐름,
  구분별 조회, 사업자등록번호 일부로 검색.
- 창고: 등록, 사용 중지 후 "사용" 목록에서 빠지고 "전체" 목록에는 "중지"로 남는 것.
