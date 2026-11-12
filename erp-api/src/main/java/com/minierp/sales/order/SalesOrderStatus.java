package com.minierp.sales.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * 수주 상태와 전이 규칙.
 *
 * <pre>
 *            confirm               ship(일부)                  ship(나머지)
 *   DRAFT ───────────▶ CONFIRMED ───────────▶ PARTIALLY_SHIPPED ───────────▶ SHIPPED
 *     │    (할당 +)         │      (현재고·할당 -)        │
 *     │                     │                            └── close (잔량 마감, 남은 할당 -) ──▶ CLOSED
 *     └──── cancel ─────────┴── cancel (할당 전부 -) ──▶ CANCELED   (출고가 한 번이라도 있으면 취소 대신 마감)
 * </pre>
 *
 * 발주와 달리 승인 단계가 없다. 수주는 고객이 보낸 주문이라 내부 결재보다 "약속할 물건이 있는가"가 핵심이고,
 * 그 확인이 확정(confirm) 때의 가용 재고 검사와 할당이다. 할당이 걸려 있는 상태는 CONFIRMED와 PARTIALLY_SHIPPED 둘뿐이다.
 * 값을 추가하면 DB의 CHECK 제약(ck_sales_order_status)도 새 마이그레이션으로 함께 바꿔야 한다.
 */
public enum SalesOrderStatus {

	/** 작성 중: 영업 담당자가 자유롭게 고칠 수 있다. 재고와는 아직 관계없다 */
	DRAFT,
	/** 확정: 가용 재고를 확인하고 할당했다. 고객에게 납기를 약속할 수 있다 */
	CONFIRMED,
	/** 부분 출고: 일부 줄 또는 일부 수량만 내보냈다. 남은 수량은 계속 할당돼 있다 */
	PARTIALLY_SHIPPED,
	/** 출고 완료 (종료) */
	SHIPPED,
	/** 마감: 덜 내보냈지만 더 내보내지 않기로 했다. 남은 할당은 풀린다 (종료) */
	CLOSED,
	/** 취소: 출고 없이 없던 일로 했다. 할당은 풀린다 (종료) */
	CANCELED;

	public Set<SalesOrderStatus> next() {
		return switch (this) {
			case DRAFT -> EnumSet.of(CONFIRMED, CANCELED);
			case CONFIRMED -> EnumSet.of(PARTIALLY_SHIPPED, SHIPPED, CANCELED);
			case PARTIALLY_SHIPPED -> EnumSet.of(PARTIALLY_SHIPPED, SHIPPED, CLOSED);
			case SHIPPED, CLOSED, CANCELED -> EnumSet.noneOf(SalesOrderStatus.class);
		};
	}

	public boolean canTransitionTo(SalesOrderStatus target) {
		return next().contains(target);
	}

	public boolean isEditable() {
		return this == DRAFT;
	}

	public boolean isShippable() {
		return this == CONFIRMED || this == PARTIALLY_SHIPPED;
	}

	/** 재고가 할당돼 있는 상태. 할당량 = 이 상태인 수주 줄의 남은 수량 합 */
	public boolean holdsAllocation() {
		return isShippable();
	}

	/** 아직 끝나지 않은 수주. 이런 수주가 있는 고객은 사용 중지할 수 없다 */
	public static Set<SalesOrderStatus> open() {
		return EnumSet.of(DRAFT, CONFIRMED, PARTIALLY_SHIPPED);
	}

	public String label() {
		return switch (this) {
			case DRAFT -> "작성 중";
			case CONFIRMED -> "확정";
			case PARTIALLY_SHIPPED -> "부분 출고";
			case SHIPPED -> "출고 완료";
			case CLOSED -> "마감";
			case CANCELED -> "취소";
		};
	}
}
