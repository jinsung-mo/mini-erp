package com.minierp.master.warehouse;

/**
 * 창고를 사용 중지해도 되는지 다른 모듈에 묻는다. 안 되면 BusinessException을 던진다.
 * 거래처의 PartnerDeactivationGuard와 같은 이유로 둔다: 기준정보는 재고 모듈을 모르고, 규칙의 구현은 재고 모듈이 한다.
 */
public interface WarehouseDeactivationGuard {

	void checkDeactivatable(Warehouse warehouse);
}
