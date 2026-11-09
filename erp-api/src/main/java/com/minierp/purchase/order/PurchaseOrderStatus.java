package com.minierp.purchase.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * 발주 상태와 전이 규칙.
 *
 * <pre>
 *            submit            approve            receive(일부)              receive(나머지)
 *   DRAFT ──────────▶ SUBMITTED ──────────▶ APPROVED ──────────▶ PARTIALLY_RECEIVED ──────────▶ RECEIVED
 *     ▲                   │                     │                       │
 *     └───── reject ──────┘                     │                       └── close (잔량 마감) ──▶ CLOSED
 *                                               │
 *   DRAFT · SUBMITTED · APPROVED ── cancel ──▶ CANCELED   (입고가 한 번이라도 있으면 취소 대신 마감)
 * </pre>
 *
 * 전이 가능한 상태를 여기 한곳에 모은다. 화면 버튼, Service의 if문, 테스트가 각자 규칙을 들고 있으면 언젠가 서로 어긋난다.
 * 값을 추가하면 DB의 CHECK 제약(ck_purchase_order_status)도 새 마이그레이션으로 함께 바꿔야 한다.
 */
public enum PurchaseOrderStatus {

	/** 작성 중: 구매 담당자가 자유롭게 고칠 수 있다 */
	DRAFT,
	/** 승인 요청: 내용이 고정되고 승인권자를 기다린다 */
	SUBMITTED,
	/** 승인: 공급처에 보낼 수 있는 확정 발주. 이제부터 입고를 받는다 */
	APPROVED,
	/** 부분 입고: 일부 줄 또는 일부 수량만 들어왔다 */
	PARTIALLY_RECEIVED,
	/** 입고 완료: 모든 줄을 발주 수량만큼 받았다 (종료) */
	RECEIVED,
	/** 마감: 덜 받았지만 더 받지 않기로 했다 (종료, 예: 공급처 단종) */
	CLOSED,
	/** 취소: 입고 없이 없던 일로 했다 (종료) */
	CANCELED;

	public Set<PurchaseOrderStatus> next() {
		return switch (this) {
			case DRAFT -> EnumSet.of(SUBMITTED, CANCELED);
			case SUBMITTED -> EnumSet.of(APPROVED, DRAFT, CANCELED);
			case APPROVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CANCELED);
			case PARTIALLY_RECEIVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CLOSED);
			case RECEIVED, CLOSED, CANCELED -> EnumSet.noneOf(PurchaseOrderStatus.class);
		};
	}

	public boolean canTransitionTo(PurchaseOrderStatus target) {
		return next().contains(target);
	}

	/** 내용(거래처·창고·줄)을 고칠 수 있는 상태 */
	public boolean isEditable() {
		return this == DRAFT;
	}

	/** 입고를 받을 수 있는 상태 */
	public boolean isReceivable() {
		return this == APPROVED || this == PARTIALLY_RECEIVED;
	}

	/** 아직 끝나지 않은 발주. 이런 발주가 있는 거래처는 사용 중지할 수 없다 */
	public static Set<PurchaseOrderStatus> open() {
		return EnumSet.of(DRAFT, SUBMITTED, APPROVED, PARTIALLY_RECEIVED);
	}

	public String label() {
		return switch (this) {
			case DRAFT -> "작성 중";
			case SUBMITTED -> "승인 요청";
			case APPROVED -> "승인";
			case PARTIALLY_RECEIVED -> "부분 입고";
			case RECEIVED -> "입고 완료";
			case CLOSED -> "마감";
			case CANCELED -> "취소";
		};
	}
}
