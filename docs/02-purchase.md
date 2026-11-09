# 2단계: 구매 (발주 → 승인 → 입고)

## 왜 발주, 승인, 입고를 나누나

물건을 사는 일은 세 사람의 서로 다른 행위다.

| 행위 | 누가 | 남는 것 |
|---|---|---|
| 발주 | 구매 담당 | "이 공급처에서 이 품목을 이 값에 사겠다"는 약속 (발주서) |
| 승인 | 구매 팀장 | 그 약속을 회사가 책임지겠다는 확인 |
| 입고 | 창고 담당 | 실제로 물건이 들어왔다는 사실 (입고 문서) |

셋을 한 화면의 "저장" 하나로 처리하면 "누가 사겠다고 했고, 누가 허락했고, 실제로 몇 개가 들어왔는지"가 구분되지 않는다.
발주 100개에 실제로 60개만 들어오는 일은 흔하다. 발주서와 입고 문서를 따로 두어야 약속과 사실의 차이(남은 40개)가 보인다.

## 문서 구조: 머리 + 줄, 그리고 이력

```
purchase_order (머리: 공급처, 입고 창고, 발주일, 상태, version)
 ├─ purchase_order_line   (줄: 품목, 수량, 단가, 받은 수량)       1:N
 └─ purchase_order_event  (상태 이력: 누가, 언제, 무엇을, 사유)   1:N, 지우지 않음

goods_receipt (머리: 발주, 받은 창고, 입고일)
 └─ goods_receipt_line    (줄: 어느 발주 줄을, 몇 개)             1:N
```

- **줄은 머리를 통해서만 바뀐다.** 줄 Repository가 없다. 줄 하나만 바꾸면 "모든 줄을 다 받았나 → 입고 완료" 같은 머리의 상태가 어긋날 수 있다.
  머리(`PurchaseOrder`)가 줄과 이력을 함께 책임지는 집합 루트다.
- **이력은 따로 쌓는다.** 감사 컬럼 `updated_by`는 마지막으로 바꾼 사람 한 명만 남는다. 누가 승인 요청하고 누가 반려했는지는 덮어써지면 안 되는
  기록이라 `purchase_order_event`에 한 줄씩 쌓고, 고치거나 지우지 않는다(그래서 `BaseEntity`를 상속하지 않는다). 반려·취소·마감 사유와 입고 번호도 여기 남는다.
- **입고 줄도 품목을 가진다.** 발주 줄에서 따라오는 값이지만 따로 저장한다. 3단계 재고 원장은 "품목 × 창고"로 쌓이므로 입고 줄만 보고 품목을 알아야 한다.
  받은 창고도 발주에서 복사해 둔다.
- **문서 번호는 DB 시퀀스.** "오늘 최대 번호 + 1"은 동시에 두 건을 만들면 같은 번호가 나온다(1단계 check-then-act와 같은 문제).
  시퀀스는 롤백돼도 되돌아가지 않아 번호에 빈칸이 생길 수 있지만, 발주·입고 번호는 빈칸이 있어도 된다.

## 상태 전이

```
           submit             approve            receive(일부)            receive(나머지)
  DRAFT ─────────▶ SUBMITTED ─────────▶ APPROVED ─────────▶ PARTIALLY_RECEIVED ─────────▶ RECEIVED
  작성 중     ▲     승인 요청    │          승인     │           부분 입고   │              입고 완료
              └──── reject ─────┘                   │                       └── close ──▶ CLOSED (잔량 마감)
                     반려                            │
  DRAFT · SUBMITTED · APPROVED ── cancel ──▶ CANCELED (취소. 입고가 있으면 취소 대신 마감)
```

전이 규칙은 `PurchaseOrderStatus.next()` 한곳에 있다. 화면 버튼, Service의 if문, 테스트가 각자 규칙을 들고 있으면 언젠가 어긋난다.

- 상태는 setter로 바꾸지 않는다. `submit()`, `approve()`, `reject()`, `cancel()`, `close()`, `receive()`가 모두 private `transition()`을 지나가고,
  거기서 전이 가능 여부 검사와 이력 기록을 함께 한다. "상태는 바뀌었는데 이력이 없다"는 일이 생길 수 없다.
- 고칠 수 있는 것은 작성 중(DRAFT)뿐이다. 승인 요청 뒤에 고치려면 반려받아 작성 중으로 돌아간다. 승인자가 본 내용과 실제 발주 내용이 달라지면 안 되기 때문이다.
- 반려·취소·마감은 사유가 필수다(`ReasonRequest`의 `@NotBlank`). 사유 없는 반려는 작성자가 무엇을 고쳐야 할지 모른다.
- 입고가 한 번이라도 있으면 취소할 수 없다. 받은 물건은 없던 일이 되지 않는다. 더 받지 않으려면 잔량 마감한다.
- 잘못된 전이는 409 `INVALID_STATUS_TRANSITION`이고, 메시지에 지금 상태를 적는다(`취소 상태의 발주는 승인 요청(으)로 바꿀 수 없습니다.`).

API도 상태를 PUT으로 바꾸지 않고 행위마다 URL을 둔다(`POST /{id}/approve`). "status를 APPROVED로 바꿔 줘"가 아니라 "승인한다"를 요청해야
서버가 누가, 어떤 규칙으로 승인하는지 판단할 수 있다.

## 직무 분리: 만든 사람은 승인하지 않는다

한 사람이 발주를 만들고 스스로 승인하면 "아는 업체에 비싸게 발주"해도 아무도 모른다. 승인은 두 번째 눈이다.
`approve()`는 작업자가 발주 작성자(`created_by`)이거나 마지막 승인 요청자(이력의 SUBMITTED)이면 403 `SELF_APPROVAL_NOT_ALLOWED`로 막는다.
작성자만 보면 "남이 만든 발주를 내가 승인 요청하고 내가 승인"하는 길이 열리므로 둘 다 본다.

화면은 지금 작업자가 작성자·요청자이면 안내 문구를 보여 주지만 승인 버튼을 숨기지는 않는다. 규칙의 기준은 서버이고, 화면에서 버튼을 숨겨도
API를 직접 부르면 그만이다. 1단계에서 정한 것처럼 작업자는 `X-User` 헤더라서 학습용이다. 실서비스라면 인증 토큰에서 꺼내야 이 규칙이 의미가 있다.

## 낙관적 락: 화면이 본 버전

팀장이 부분 입고된 발주를 열어 두고 "남은 강판은 안 받겠다"며 잔량 마감을 누르려는 사이, 창고에서 강판 일부가 입고될 수 있다.
팀장은 입고 전 화면을 보고 판단한 것이다. 화면을 연 시점과 버튼을 누른 시점 사이에 다른 사람이 문서를 바꿀 수 있으므로,
상태를 바꾸는 요청은 모두 화면이 본 `version`을 함께 보낸다.

| 방어 | 위치 | 막는 것 |
|---|---|---|
| 1차: 버전 비교 | `PurchaseOrder.checkVersion()` | 화면을 연 뒤 다른 사람이 바꾼 경우 → 409 `CONCURRENT_MODIFICATION` (몇 버전을 보고 있었는지 알려 줌) |
| 2차: `@Version` | Hibernate `update ... where id = ? and version = ?` | 1차 비교를 동시에 통과한 요청 → `ObjectOptimisticLockingFailureException` → 같은 409 |

1단계 중복 코드의 "사전 검사 + unique 제약"과 같은 두 겹 구조다. 화면은 409 `CONCURRENT_MODIFICATION`이면 "다시 조회" 버튼을 보여 준다.
버전은 "같은지"만 의미가 있다. 한 번 저장에 1이 오를 수도 2가 오를 수도 있다(트러블슈팅 3).

## 입고: 한 트랜잭션에 셋, 그리고 잠금

입고 한 건은 한 트랜잭션 안에서 세 가지를 함께 바꾼다.

1. 입고 문서 생성 (`goods_receipt`, `goods_receipt_line`)
2. 발주 줄의 받은 수량 누적 (`received_quantity`)
3. 발주 상태 전이(부분 입고 / 입고 완료)와 이력

하나라도 실패하면 셋 다 없던 일이 된다. 두 줄을 입고하는데 둘째 줄이 남은 수량을 넘으면, 첫째 줄도 반영되지 않는다(`GoodsReceiptApiTest`).
"입고 문서는 있는데 발주는 그대로" 같은 어긋남이 ERP가 막아야 하는 일이다. 3단계에서 재고 원장 기록이 네 번째로 더해진다.

입고 요청에는 `version`이 없다. 입고는 "남은 수량 안에서 더하는" 일이라, 화면을 연 뒤 다른 입고가 있었더라도 남은 수량만 넘지 않으면 받아도 된다.
대신 발주 행을 잠그고(`SELECT ... FOR UPDATE`) 남은 수량을 다시 확인한다. 왜 버전이 아니라 잠금인지는 트러블슈팅 4에서 직접 비교했다.

잠금은 `PESSIMISTIC_FORCE_INCREMENT`로 건다. 잠그면서 버전도 올린다. 부분 입고 → 부분 입고처럼 상태가 그대로인 입고도 버전을 바꿔 두어야,
그 발주를 열어 둔 팀장 화면의 잔량 마감이 옛 화면 기준으로 실행되지 않는다(`화면을 연 뒤 입고가 들어왔으면 옛 화면의 마감은 막힌다` 테스트, 화면으로도 확인).

DB에는 마지막 방어선으로 `check (received_quantity >= 0 and received_quantity <= quantity)`를 둔다.

## 기준정보와의 관계

- **쓸 수 있는 기준정보만.** 발주할 때 거래처는 사용 중이고 공급처 역할이 있어야 하고(409 `NOT_A_SUPPLIER`), 창고·품목은 사용 중이어야 한다
  (409 `INACTIVE_MASTER`). 작성 중에 저장해 둔 뒤 품목이 사용 중지될 수 있으므로 승인 요청·승인 때 다시 확인한다.
  승인 뒤에는 확인하지 않는다. 승인된 발주는 회사가 이미 한 약속이라, 그 뒤에 품목을 사용 중지해도 들어오는 물건은 받아야 한다.
- **진행 중인 발주가 있는 거래처는 사용 중지할 수 없다** (409 `PARTNER_HAS_OPEN_ORDERS`). 1단계에서 예고한 규칙이다.
  `PartnerService`가 발주 Repository를 직접 부르면 master → purchase 의존이 생겨 두 모듈이 서로를 참조한다. 그래서 기준정보 쪽은
  `PartnerDeactivationGuard` 인터페이스만 정하고 구매 모듈이 구현한다(`OpenOrderPartnerGuard`). `PartnerService`는 구현체 목록을 주입받아 차례로 묻는다.
  4단계 판매(진행 중인 수주)도 같은 인터페이스로 붙는다.
- **셀 수 있는 단위는 정수만.** 볼트 2.5개는 없지만 철판 2.5kg은 있다. `Unit.allows(quantity)`가 EA·BOX면 소수를 거절한다(400 `INVALID_QUANTITY_FOR_UNIT`).
  발주와 입고 모두 검사한다.
- 같은 품목을 한 발주에 두 줄로 넣지 않는다(400 `DUPLICATE_LINE_ITEM`). 입고·재고에서 어느 줄의 수량인지 헷갈린다.

## 숫자와 날짜

- **금액·수량은 `BigDecimal`, DB는 `numeric`.** `double`은 0.1 + 0.2가 0.30000000000000004다. 수량은 `numeric(15,3)`(KG·L 소수 3자리), 단가는 `numeric(15,2)`.
  금액은 수량 × 단가를 원 단위로 반올림해 계산하고 저장하지 않는다(줄에서 언제든 다시 계산되는 파생 값). 부가세는 다루지 않는다.
- **업무 일자는 `LocalDate`, 사건 시점은 `Instant`.** 발주일·입고일은 "11월 9일"이라는 날짜이지 시점이 아니다. 감사 컬럼(`created_at`)은
  시간대와 무관한 시점(timestamptz)이다. 화면도 날짜는 문자열 그대로 다룬다. `new Date("2026-11-09")`는 UTC 자정으로 해석되어 시간대에 따라
  전날이 될 수 있어서 쓰지 않는다(`format.ts`의 `today()`).
- 납기 요청일은 발주일보다, 입고일은 발주일보다 빠를 수 없다(400 `INVALID_INPUT`, DB `ck_purchase_order_due_date`).

## API

| 메서드 | 경로 | 설명 | 실패 |
|---|---|---|---|
| GET | `/api/purchase-orders?keyword=&status=&status=&supplierId=&fromDate=&toDate=` | 목록. 상태는 여러 개 가능 | |
| GET | `/api/purchase-orders/{id}` | 상세 (줄, 이력, version) | 404 |
| POST | `/api/purchase-orders` | 작성 → 작성 중, 번호 `PO-000001` | 400 `INVALID_INPUT`·`INVALID_QUANTITY_FOR_UNIT`·`DUPLICATE_LINE_ITEM`, 409 `NOT_A_SUPPLIER`·`INACTIVE_MASTER` |
| PUT | `/api/purchase-orders/{id}` | 수정 (작성 중만, version 필요) | 위와 같음 + 409 `INVALID_STATUS_TRANSITION`·`CONCURRENT_MODIFICATION` |
| POST | `/{id}/submit`, `/{id}/approve` | 승인 요청, 승인 (`{"version"}`) | 403 `SELF_APPROVAL_NOT_ALLOWED`, 409 |
| POST | `/{id}/reject`, `/{id}/cancel`, `/{id}/close` | 반려, 취소, 잔량 마감 (`{"version", "reason"}`) | 400 (사유 없음), 409 |
| POST | `/api/purchase-orders/{id}/receipts` | 입고 → 번호 `GR-000001` | 400, 409 `EXCEEDS_REMAINING_QUANTITY`·`INVALID_STATUS_TRANSITION` |
| GET | `/api/goods-receipts?keyword=&purchaseOrderId=&fromDate=&toDate=` | 입고 내역 | |

DELETE는 없다. 잘못 만든 발주는 취소하고, 입고는 고치지 않는다(3단계 이후 반품·조정 같은 반대 거래로 바로잡는다).

목록은 공급처·창고를 fetch join으로 함께 가져오고, 금액 합계에 필요한 줄은 `default_batch_fetch_size: 100`으로 발주 N건의 줄을 IN 쿼리 한 번에 가져온다.
실제로 몇 번 쿼리가 나가는지는 6단계(N+1)에서 측정한다.

## 트러블슈팅

### 1. 기존 테스트가 전부 깨졌다: 참조되는 테이블은 truncate할 수 없다

V3를 추가하자 1단계 테스트 27개가 `@BeforeEach`에서 실패했다.

```
ERROR: cannot truncate a table referenced in a foreign key constraint
  세부 정보: Table "purchase_order" references "partner".
  힌트: Truncate table "purchase_order" at the same time, or use TRUNCATE ... CASCADE.
```

발주가 거래처·품목·창고를 FK로 참조하게 되었기 때문이다. 발주 행이 하나도 없어도 PostgreSQL은 참조 관계만 보고 막는다(행마다 확인하는 DELETE와 다르다).
기존 테스트는 `truncate table partner restart identity cascade`로 바꿨고, 구매 테스트는 관련 테이블을 한 문장으로 함께 비운다(`PurchaseFixtures.reset`).
`cascade`는 참조하는 테이블까지 비우므로 운영 DB에서는 쓰면 안 되는 명령이다.

### 2. 발주 줄을 고쳐 저장하면 `DUPLICATE_KEY`(409)가 난다: flush 순서 (직접 재현)

처음에는 줄 교체를 `lines.clear()` 후 새 줄을 `add`하는 방식으로 짰다. 2줄 → 1줄로 고쳐 저장하자 409 `DUPLICATE_KEY`가 났다.

```
insert into purchase_order_line (item_id, line_no, purchase_order_id, ...) values (?, ?, ?, ...)   ← 새 1번 줄
ERROR: duplicate key value violates unique constraint "uk_purchase_order_line"
  Key (purchase_order_id, line_no)=(1, 1) already exists.
```

SQL 로그에 옛 줄의 DELETE가 없다. Hibernate는 flush할 때 INSERT → UPDATE → DELETE 순서로 실행하므로, `orphanRemoval`로 지워질 옛 1번 줄이
아직 남아 있는 상태에서 새 1번 줄을 INSERT한다. 사용자는 아무것도 중복으로 넣지 않았는데 "같은 데이터가 이미 저장되어 있습니다"를 보게 된다.

`replaceLines()`를 줄 번호 순서대로 맞추는 방식으로 바꿨다. 남는 줄은 그 자리에서 고치고(UPDATE), 늘어난 줄은 INSERT, 줄어든 줄은 DELETE된다.
줄 id도 유지되므로 나중에 줄을 참조하는 데이터가 생겨도 안전하다.

### 3. 줄 수량만 바꾸면 버전이 그대로다 (직접 재현)

"줄만 바꿔도 버전이 올라서 옛 화면의 저장을 막는다" 테스트가 `expected:<1> but was:<0>`로 실패했다. 수량만 바꾸면 UPDATE가 `purchase_order_line`에만
나가고, 머리 행은 바뀌지 않으니 `@Version`도 오르지 않는다. 그러면 버전 0을 보고 있던 다른 화면이 그대로 저장해서 방금 고친 내용을 덮어쓴다.
(줄이 추가·삭제될 때는 Hibernate가 컬렉션 변경으로 보고 머리 버전을 올렸다. 규칙이 반쪽이다.)

수정할 때 `entityManager.lock(po, PESSIMISTIC_FORCE_INCREMENT)`로 머리 버전을 직접 올리도록 했다. 그러자 이번에는 메모처럼 머리 값도 함께 바뀐 경우
`expected:<1> but was:<2>`가 됐다. 직접 올린 1 + flush 때 Hibernate가 올린 1이다. 컬렉션을 자동 증가에서 빼 봐도(`@OptimisticLock(excluded = true)`)
머리 값 변경분이 남아 2였다. 정확히 1씩 올리려고 복잡하게 만들 이유가 없어서, 버전은 "같은지"만 비교하는 값이라고 정하고 테스트도 "올랐는가"를 본다.

### 4. 같은 발주에 입고가 동시에 들어오면: 잠금 vs 버전 (직접 재현)

`GoodsReceiptRaceTest`는 볼트 100개 발주에 20개짜리 입고 10건을 실제 HTTP로 동시에 보낸다. 정답은 5건 성공, 5건 남은 수량 초과다.

| 방식 | 결과 | 받은 수량 / 입고 줄 합계 |
|---|---|---|
| 발주 행 잠금 (`findByIdForReceipt`, 지금 코드) | `{201=5, 409 EXCEEDS_REMAINING_QUANTITY=5}` | 100 / 100 |
| 잠금 없이 `findById` (버전만) | `{201=1, 409 CONCURRENT_MODIFICATION=9}` (두 번 실행, 같은 결과) | 20 / 20 |

잠금 없이도 데이터는 맞았다. 첫 입고가 상태를 바꾸며(승인 → 부분 입고) 버전을 올렸고, 같은 버전을 읽은 나머지 9건은 `@Version`에 막혔다.
문제는 **80개가 남아 있는데 9건이 거절됐다**는 것이다. 창고 담당자는 이유 없이 "다른 사용자가 먼저 변경했습니다"를 보고 다시 시도해야 한다.

- 낙관적 락은 "충돌이 드물다"는 가정이다. 충돌하면 실패시키고 사람이 다시 하게 한다. 같은 발주를 두 사람이 동시에 고치는 일은 드물고,
  고쳤다면 사람이 내용을 다시 봐야 하므로 발주 수정·승인에 맞다.
- 입고는 같은 발주에 여러 사람이 동시에 스캔하는 일이 정상이고, 서로의 내용을 덮어쓰는 게 아니라 더하는 일이다. 줄 세워서 차례로 처리하면
  모두 성공할 수 있다. 그래서 행을 잠근다. 두 번째 요청은 첫 번째가 커밋할 때까지 기다렸다가 줄어든 남은 수량을 보고 판단한다.

잠금도 대가가 있다. 기다리는 동안 DB 커넥션을 붙잡고 있으므로 잠금 구간(트랜잭션)을 짧게 유지해야 한다. 5단계의 "동시 출고로 재고가 음수"도
같은 선택이다. `@Version`까지 없는 경우도 재현하려 했지만, 테스트 준비 단계의 승인 요청이 버전 비교에 걸려 결과를 얻지 못해 여기 적지 않는다.

## 테스트

| 테스트 | DB | 검증 |
|---|---|---|
| `PurchaseOrderStatusTest` | 없음 (단위 테스트) | 전이 규칙표: 승인 없이 입고 불가, 반려 경로, 입고 후 취소 불가·마감 가능, 종료 상태 (7개) |
| `PurchaseOrderApiTest` | Testcontainers PostgreSQL 17 | 번호·금액, 공급처 아님, 정수 단위, 같은 품목 두 줄, 줄 필드 오류 위치, 납기일, 줄 교체(재현 2), 줄만 바꿔도 버전(재현 3), 직무 분리 2가지, 반려 후 재수정, 승인 요청 후 수정 불가, 취소 후 전이 불가, 승인 요청 시 사용 중지 품목, 거래처 사용 중지 가드, 상태 여러 개 조회 (16개) |
| `GoodsReceiptApiTest` | Testcontainers PostgreSQL 17 | 부분 입고 → 입고 완료, 받은 수량 = 입고 줄 합계, 초과 시 전체 롤백, 승인 전 입고 불가, 정수 단위, 다른 발주 줄·같은 줄 두 번, 입고일, 취소 불가·마감 후 입고 불가, 옛 화면의 마감 차단, 입고 검색 (10개) |
| `GoodsReceiptRaceTest` | Testcontainers PostgreSQL 17 + 실제 서버 | 동시 입고 10건 → 5건 성공, 받은 수량 100 = 입고 줄 합계 100 |

1단계 테스트까지 합쳐 76개가 모두 통과한다.

화면은 헤드리스 Chrome(puppeteer-core)으로 실제 클릭·입력하며 확인했다.

- 발주: 빈 채로 등록하면 공급처·품목·수량 오류가 각 칸 아래에 표시, EA 품목에 2.5개를 넣으면 `INVALID_QUANTITY_FOR_UNIT`,
  줄 추가 후 합계 88,500원 표시, 등록하면 발주서 주소로 바뀌고 작성 중 상태.
- 승인: 구매 담당(buyer01)이 승인 요청 후 직접 승인하면 직무 분리 안내와 `SELF_APPROVAL_NOT_ALLOWED`, 작업자를 구매 팀장(manager01)으로 바꾸면 승인.
- 입고: 발주서의 "입고 처리"로 입고 화면에 그 발주가 선택된 채 열림, 1번 줄 60개만 입고하면 GR 번호 안내와 부분 입고, 남은 40개보다 많은 50개는
  `EXCEEDS_REMAINING_QUANTITY`.
- 동시 수정: 팀장이 발주서를 연 사이 다른 곳에서 10개를 더 입고한 뒤 잔량 마감을 누르면 `CONCURRENT_MODIFICATION`(화면 버전 4, 현재 버전 5)과
  "다시 조회" 버튼, 다시 조회 후 마감하면 사유가 이력에 남음. 목록의 "전체" 조회에서 마감 상태 확인.
