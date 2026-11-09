package com.minierp.purchase.order;

import org.springframework.stereotype.Component;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.master.partner.Partner;
import com.minierp.master.partner.PartnerDeactivationGuard;

/**
 * 진행 중인 발주(작성 중 ~ 부분 입고)가 있는 공급처는 사용 중지할 수 없다.
 * 사용 중지하면 그 발주는 승인 요청·승인 단계에서 "사용 중지된 거래처"로 막혀 오도 가도 못 한다. 먼저 발주를 끝내거나 취소해야 한다.
 */
@Component
class OpenOrderPartnerGuard implements PartnerDeactivationGuard {

	private final PurchaseOrderRepository purchaseOrderRepository;

	OpenOrderPartnerGuard(PurchaseOrderRepository purchaseOrderRepository) {
		this.purchaseOrderRepository = purchaseOrderRepository;
	}

	@Override
	public void checkDeactivatable(Partner partner) {
		long open = purchaseOrderRepository.countBySupplierIdAndStatusIn(partner.getId(), PurchaseOrderStatus.open());
		if (open > 0) {
			throw new BusinessException(ErrorCode.PARTNER_HAS_OPEN_ORDERS,
					"%s %s에 진행 중인 발주가 %d건 있어서 사용 중지할 수 없습니다. 발주를 마감하거나 취소한 뒤 시도하세요."
							.formatted(partner.getCode(), partner.getName(), open));
		}
	}
}
