package com.minierp.purchase.order;

import static com.minierp.purchase.order.PurchaseOrderStatus.APPROVED;
import static com.minierp.purchase.order.PurchaseOrderStatus.CANCELED;
import static com.minierp.purchase.order.PurchaseOrderStatus.CLOSED;
import static com.minierp.purchase.order.PurchaseOrderStatus.DRAFT;
import static com.minierp.purchase.order.PurchaseOrderStatus.PARTIALLY_RECEIVED;
import static com.minierp.purchase.order.PurchaseOrderStatus.RECEIVED;
import static com.minierp.purchase.order.PurchaseOrderStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 전이 규칙표 자체를 검사한다. 규칙을 바꾸면 여기서 먼저 깨진다 */
class PurchaseOrderStatusTest {

	@Test
	void 승인을_건너뛰고_입고할_수_없다() {
		assertThat(DRAFT.canTransitionTo(RECEIVED)).isFalse();
		assertThat(SUBMITTED.canTransitionTo(PARTIALLY_RECEIVED)).isFalse();
		assertThat(APPROVED.canTransitionTo(PARTIALLY_RECEIVED)).isTrue();
	}

	@Test
	void 반려는_승인_요청에서_작성_중으로만_간다() {
		assertThat(SUBMITTED.canTransitionTo(DRAFT)).isTrue();
		assertThat(APPROVED.canTransitionTo(DRAFT)).isFalse();
	}

	@Test
	void 입고가_시작되면_취소_대신_마감만_된다() {
		assertThat(APPROVED.canTransitionTo(CANCELED)).isTrue();
		assertThat(PARTIALLY_RECEIVED.canTransitionTo(CANCELED)).isFalse();
		assertThat(PARTIALLY_RECEIVED.canTransitionTo(CLOSED)).isTrue();
		assertThat(APPROVED.canTransitionTo(CLOSED)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(names = { "RECEIVED", "CLOSED", "CANCELED" })
	void 끝난_상태에서는_어디로도_갈_수_없다(PurchaseOrderStatus status) {
		assertThat(status.next()).isEmpty();
		assertThat(PurchaseOrderStatus.open()).doesNotContain(status);
	}

	@Test
	void 고칠_수_있는_것은_작성_중뿐이고_입고는_승인_뒤에만_받는다() {
		assertThat(PurchaseOrderStatus.values()).filteredOn(PurchaseOrderStatus::isEditable).containsExactly(DRAFT);
		assertThat(PurchaseOrderStatus.values()).filteredOn(PurchaseOrderStatus::isReceivable)
				.containsExactly(APPROVED, PARTIALLY_RECEIVED);
	}
}
