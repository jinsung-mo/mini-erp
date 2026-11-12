# 4단계: 판매 (수주 → 확정·할당 → 출고)

## 현재고만 보고 주문을 받으면 생기는 일

창고에 볼트가 100개 있다. 오전에 A 고객 80개를 받고 출고는 내일 하기로 했다. 오후에 B 고객이 50개를 주문한다.

- 현재고만 보면 100 ≥ 50이라 받는다. 내일 A에 80개를 내보내면 남는 건 20개, B에게 약속한 50개는 없다.
- 이 상태를 약속 초과(over-promise)라고 한다. 영업은 납기를 약속했고, 창고는 물건이 없고, 고객은 연락을 받는다.

그래서 ERP는 "창고에 있는 수량"과 "새로 약속할 수 있는 수량"을 나눠서 본다.

| 용어 | 뜻 | 예 |
|---|---|---|
| 현재고 (`quantity`) | 창고에 실제로 있는 수량. 원장 합계 | 100 |
| 할당 (`allocated_quantity`) | 확정한 수주 중 아직 내보내지 않은 수량 | 80 |
| 가용 (`quantity - allocated`) | 새 수주에 약속할 수 있는 수량 | 20 |

## 할당을 언제 잡나

| 방식 | 장점 | 단점 |
|---|---|---|
| A. 출고할 때만 재고 검사 | 단순하다 | 수주 확정이 아무것도 보장하지 않는다. 약속 초과가 그대로 생긴다 |
| **B. 수주 확정 때 할당 (선택)** | 확정한 수주는 물건이 있다는 보장 | 할당을 늘리고 줄이는 시점(확정·출고·취소·마감)을 모두 맞춰야 한다 |

B를 골랐다. 대가로 생기는 "할당을 놓치는 경로"를 막는 것이 이번 단계의 설계 중심이다.

## 상태와 할당이 함께 움직이는 지점

```
DRAFT ─confirm─▶ CONFIRMED ─ship(일부)─▶ PARTIALLY_SHIPPED ─ship(나머지)─▶ SHIPPED
        할당 +              현재고 -, 할당 -              └─close─▶ CLOSED (남은 할당 -)
DRAFT · CONFIRMED ─cancel─▶ CANCELED (확정 상태였다면 할당 전부 -)
```

| 행위 | 현재고 | 할당 | 원장 |
|---|---|---|---|
| 확정 | 그대로 | + 줄 수량 | 없음 (약속은 실물 이동이 아니다) |
| 출고 | - 출고 수량 | - 출고 수량 | SHIPMENT 한 줄 (음수) |
| 취소 (확정 상태) | 그대로 | - 남은 수량 전부 | 없음 |
| 잔량 마감 | 그대로 | - 남은 수량 | 없음 |

출고하면 현재고와 할당이 같은 만큼 줄어서 **가용은 그대로**다. 약속해 둔 몫이 나갔을 뿐이다.

발주와 달리 승인 단계가 없다. 수주는 고객이 보낸 주문이라 내부 결재보다 "약속할 물건이 있는가"가 핵심이고, 그 확인이 확정이다.
직무 분리는 2단계 발주에서 이미 다뤘다.

## 할당 규칙은 상태 규칙 옆에 둔다

엔티티(`SalesOrder`)는 재고를 직접 바꾸지 않는다. 대신 전이 메서드가 "이 전이로 할당이 얼마나 바뀌어야 하는지"를 돌려준다.

```java
public List<StockAllocation> confirm(String actor)                // 줄마다 +남은 수량
public List<StockAllocation> cancel(String reason, String actor)  // 할당을 잡고 있던 상태였으면 -남은 수량, 아니면 빈 목록
public List<StockAllocation> close(String reason, String actor)
```

Service는 그 목록을 같은 트랜잭션에서 `StockLedger.allocate()`에 넘긴다.

```java
SalesOrder so = lockForStockChange(id, request.version());
stockLedger.allocate(so.confirm(CurrentUser.id()));
```

"어느 상태가 할당을 붙잡고 있나"(`SalesOrderStatus.holdsAllocation()`)와 "얼마를 풀어야 하나"가 상태 전이와 같은 클래스에 있다.
Service마다 "취소면 할당을 풀어야지"를 기억하게 하면, 언젠가 새 전이(예: 반품)를 추가하는 사람이 잊는다.

## `StockLedger`가 할당도 맡는다

3단계에서 정한 "재고를 바꾸는 입구는 하나"를 할당에도 그대로 적용했다.

- `allocate(List<StockAllocation>)`: 3단계와 같은 순서(품목 id, 창고 id)로 현재고 행을 잠근다. 늘릴 때는 잠근 뒤 가용을 확인한다.
  부족한 줄이 여럿이면 처음 하나에서 멈추지 않고 모아서 한 번에 알린다:
  `본사 자재창고의 가용 재고가 부족합니다. BOLT-M10(육각볼트) 가용 20, 요청 30, PLATE-T3(철판) 가용 30, 요청 40`
- `post()`: 거래 종류가 출고(`SHIPMENT.consumesAllocation()`)면 현재고와 함께 할당도 줄인다. 출고 Service가 할당을 따로 풀지 않는다.
- **잠금 순서는 항상 문서 → 현재고.** 입고는 발주 행 → 현재고, 확정·출고·취소·마감은 수주 행 → 현재고, 조정은 현재고만.
  거꾸로 잠그는 길이 없어서 문서와 재고 사이의 교착 상태가 생기지 않는다.

## 일부러 걸지 않은 DB 제약

`allocated_quantity <= quantity`를 걸고 싶어진다. 걸지 않았다.

볼트 80개를 약속해 둔 상태에서 실사로 50개 분실이 확인됐다. 현재고는 100 → 50이 되어야 한다.
제약이 있으면 **현실을 장부에 적는 일(실사 조정) 자체가 막힌다.** 창고에는 50개뿐인데 장부는 100개라고 우기게 된다.

| 불변식 | 누가 지키나 | 이유 |
|---|---|---|
| 현재고 ≥ 0 (`ck_stock_quantity`) | DB가 강제 | 실물이 음수일 수는 없다 |
| 할당 ≥ 0 (`ck_stock_allocated`) | DB가 강제 | 음수 할당은 버그다 |
| 할당 ≤ 현재고 | 강제하지 않고 **드러낸다** | 분실·파손은 일어난다. 가용이 음수(-30)로 보이고 화면이 빨갛게 표시한다 |

그 뒤 60개 출고는 원장 기록(`Stock.apply`)에서 `INSUFFICIENT_STOCK`으로 막힌다. 장부상 할당이 있어도 창고에 없는 물건은 내보낼 수 없다.
담당자는 있는 50개를 내보내고 잔량을 마감하면 할당까지 0으로 정리된다(`SalesApiTest`의 실사 분실 시나리오).

## 할당은 다시 계산할 수 있어야 한다

할당은 원장처럼 사건으로 쌓지 않는다. 대신 진행 중인 수주 줄에서 언제든 다시 계산할 수 있다.

```sql
-- 할당량 = 확정·부분 출고 상태인 수주 줄의 (수량 - 출고 수량) 합. 차이 나는 행이 없어야 한다
select count(*) from stock s
left join (select l.item_id, o.warehouse_id, sum(l.quantity - l.shipped_quantity) as remaining
           from sales_order_line l join sales_order o on o.id = l.sales_order_id
           where o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
           group by l.item_id, o.warehouse_id) r
  on r.item_id = s.item_id and r.warehouse_id = s.warehouse_id
where s.allocated_quantity <> coalesce(r.remaining, 0);
```

`현재고 = 원장 합계`와 함께 이 쿼리가 6단계 정합성 검증 배치의 재료다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/sales-orders?keyword=&status=&customerId=&fromDate=&toDate=` | 수주 목록 (상태는 여러 개 가능) |
| GET | `/api/sales-orders/{id}` | 수주 상세 (줄, 이력) |
| POST | `/api/sales-orders` | 작성. 고객 역할이 없으면 409 `NOT_A_CUSTOMER` |
| PUT | `/api/sales-orders/{id}` | 수정 (작성 중일 때만) |
| POST | `/api/sales-orders/{id}/confirm` | 확정 + 할당. 모자라면 409 `INSUFFICIENT_STOCK` |
| POST | `/api/sales-orders/{id}/cancel` | 취소 (출고가 있으면 409, 확정 상태면 할당 해제) |
| POST | `/api/sales-orders/{id}/close` | 잔량 마감 (남은 할당 해제) |
| POST | `/api/sales-orders/{id}/shipments` | 출고 (부분 출고, 원장 SHIPMENT) |
| GET | `/api/shipments?keyword=&salesOrderId=&fromDate=&toDate=` | 출고 목록 |
| GET | `/api/stocks` | 응답에 `allocatedQuantity`, `availableQuantity` 추가 |

기준정보 가드도 늘었다. 진행 중인 수주가 있는 고객은 사용 중지할 수 없고(`OpenSalesOrderPartnerGuard`),
현재고가 0이어도 할당이 남은 창고는 사용 중지할 수 없다(`StockRepository.hasStockOrAllocation`).

## 트러블슈팅

### 1. 같은 수주를 동시에 확정한 9건이 500으로 끝났다

`SalesConfirmRaceTest`에서 같은 수주를 같은 버전으로 10번 동시에 확정했다. 기대는 `{200=1, 409 CONCURRENT_MODIFICATION=9}`.

| 실행 | 응답 | 확정 이력 | 할당 |
|---|---|---|---|
| 처음 | `{200=1, 500 INTERNAL_SERVER_ERROR=9}` | 1 | 30 |
| 수정 후 | `{200=1, 409 CONCURRENT_MODIFICATION=9}` | 1 | 30 |

데이터는 처음부터 맞았다(할당은 한 번만 됐다). 틀린 건 응답 코드였다. 로그는 이랬다.

```
ERROR GlobalExceptionHandler : unexpected error
jakarta.persistence.OptimisticLockException: Unexpected row count (expected row count 1 but was 0)
  [update sales_order set version=? where id=? and version=?]
    at org.hibernate.internal.SessionImpl.lock(...)
```

- 10건 모두 버전 0을 읽고 `checkVersion(0)`을 통과했다. 그다음 `entityManager.lock(so, PESSIMISTIC_FORCE_INCREMENT)`가
  `update ... set version = 1 where version = 0`을 실행한다. 첫 건이 커밋한 뒤 나머지 9건은 0건이 바뀌어 낙관적 락 예외가 났다. 여기까지는 의도대로다.
- 문제는 예외의 **종류**였다. `GlobalExceptionHandler`는 Spring 예외(`ObjectOptimisticLockingFailureException`)만 409로 바꾸고 있었다.
  Spring이 JPA 예외를 자기 예외로 바꿔 주는 것(예외 변환)은 **Repository 프록시를 거칠 때뿐**이다. Service에서 `EntityManager`를 직접 부르면
  JPA 원본 예외(`jakarta.persistence.OptimisticLockException`)가 그대로 올라와 "예상하지 못한 예외" 500이 된다.
- 2단계 `PurchaseOrderService.update`도 같은 `entityManager.lock()`을 쓴다. 지금까지 동시 수정 테스트가 그 사이를 노리지 않아서 드러나지 않았던 같은 버그다.

고친 내용: 처리기가 JPA 원본 예외도 받는다.

```java
@ExceptionHandler({ ObjectOptimisticLockingFailureException.class, OptimisticLockException.class })      // → 409 CONCURRENT_MODIFICATION
@ExceptionHandler({ PessimisticLockingFailureException.class, PessimisticLockException.class,
        LockTimeoutException.class })                                                                 // → 409 LOCK_CONFLICT
```

교훈: "Spring이 예외를 바꿔 준다"는 경로에 따라 다르다. 동시성 테스트는 데이터만이 아니라 **실패한 쪽이 받는 응답**까지 확인해야 이런 구멍이 보인다.

### 2. 다른 수주의 동시 확정: 약속 초과가 생기지 않는가

볼트 100개, 수주 10건(각 30개)을 동시에 확정했다. 3번 실행 모두 같은 결과였다.

| 결과 | 값 |
|---|---|
| 응답 | `{200=3, 409 INSUFFICIENT_STOCK=7}` |
| 확정된 수주 | 3 |
| 할당 | 90 |

가용 확인과 할당 증가가 현재고 행 잠금 하나 안에 있어서, 뒤의 요청은 앞 요청이 커밋한 할당을 보고 판단한다.
잠금을 빼면 여러 건이 같은 "가용 100"을 보고 함께 통과한다. 그 재현과 락 방식 비교는 5단계에서 한다.

## 화면

- **수주** (`/sales/orders`): 진행 중 / 전체 / 상태별 조회. 신규 수주는 고객(고객 역할만), 출고 창고, 줄을 입력한다.
  작성 중에는 줄마다 고른 창고의 **가용** 수량을 보여 주고, 수량이 가용보다 크면 빨갛게 표시한다(확정하면 거절된다는 예고).
  가용 값은 참고용이고 판단은 확정할 때 서버가 잠근 뒤 다시 한다.
- 버튼: 작성 중 → 저장, 확정(재고 할당), 수주 취소. 확정 → 출고 처리, 수주 취소. 부분 출고 → 출고 처리, 잔량 마감.
- **출고** (`/sales/shipments`): 출고 대기 수주(확정, 부분 출고)를 고르면 줄마다 남은 수량(= 할당)이 채워진다.
- **현재고**: 할당, 가용 열 추가. 가용이 음수면(할당 부족) 빨갛게 표시한다. 수불 이력의 종류 필터에 출고가 추가됐다.

## 테스트

| 테스트 | 검증 |
|---|---|
| `SalesApiTest` (16개) | 작성은 재고와 무관, 고객 역할, 확정 → 할당·가용, 가용 부족 시 모자란 품목 전부 알림 + 전부 롤백, 부분·전량 출고 → 현재고·할당·원장, 남은 수량 초과, 미확정 출고 금지, 확정 취소·작성 중 취소, 출고 뒤 취소 금지와 잔량 마감, 확정 뒤 수정 금지, 옛 버전 확정 409, 실사 분실 뒤 가용 음수·출고 차단, 고객·창고 사용 중지 가드, 할당 = 진행 중 수주 줄 합 |
| `SalesConfirmRaceTest` (2개) | 다른 수주 10건 동시 확정 → 3건만, 같은 수주 10번 동시 확정 → 1건만 |

1~3단계 테스트까지 합쳐 106개가 모두 통과한다. 기존 테스트는 초기화 문장(`PurchaseFixtures.reset`)에 판매 테이블을 더한 것 외에는 바꾸지 않았다.

화면은 헤드리스 Chrome(puppeteer-core)으로 개발 DB에서 확인했다(9/9). 볼트 가용 113개에서 시작:

1. 신규 수주에 볼트 150개를 넣으면 가용 칸 113이 빨갛게 표시된다. 등록하면 SO-000001.
2. 확정하면 `INSUFFICIENT_STOCK ... BOLT-M10(육각볼트 M10) 가용 113, 요청 150`.
3. 100개로 고쳐 저장하고 확정하면 상태가 확정이 된다. 현재고 화면은 현재고 113, 할당 100, 가용 13.
4. 창고 담당(wh01)이 40개를 출고하면 SH-000001, 현재고 73, 할당 60, 가용 13. 수불 이력에 출고 -40.
5. 잔량 마감하면 할당이 풀려 현재고 73, 할당 -, 가용 73.
