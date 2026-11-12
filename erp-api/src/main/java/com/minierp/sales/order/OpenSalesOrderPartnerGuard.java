package com.minierp.sales.order;

import org.springframework.stereotype.Component;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.master.partner.Partner;
import com.minierp.master.partner.PartnerDeactivationGuard;

/**
 * 진행 중인 수주(작성 중 ~ 부분 출고)가 있는 고객은 사용 중지할 수 없다 (발주의 OpenOrderPartnerGuard와 같은 규칙).
 * 거래처 하나가 공급처이면서 고객일 수 있으므로, 사용 중지할 때는 두 가드가 모두 통과해야 한다.
 */
@Component
class OpenSalesOrderPartnerGuard implements PartnerDeactivationGuard {

	private final SalesOrderRepository salesOrderRepository;

	OpenSalesOrderPartnerGuard(SalesOrderRepository salesOrderRepository) {
		this.salesOrderRepository = salesOrderRepository;
	}

	@Override
	public void checkDeactivatable(Partner partner) {
		long open = salesOrderRepository.countByCustomerIdAndStatusIn(partner.getId(), SalesOrderStatus.open());
		if (open > 0) {
			throw new BusinessException(ErrorCode.PARTNER_HAS_OPEN_ORDERS,
					"%s %s에 진행 중인 수주가 %d건 있어서 사용 중지할 수 없습니다. 수주를 마감하거나 취소한 뒤 시도하세요."
							.formatted(partner.getCode(), partner.getName(), open));
		}
	}
}
