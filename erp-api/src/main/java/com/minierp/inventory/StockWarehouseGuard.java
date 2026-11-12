package com.minierp.inventory;

import org.springframework.stereotype.Component;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.master.warehouse.WarehouseDeactivationGuard;

/**
 * 재고가 남은 창고는 사용 중지할 수 없다. 사용 중지된 창고의 재고는 장부에는 있는데 아무도 옮기거나 팔 수 없는 물건이 된다.
 * 먼저 다른 창고로 옮기거나(이동은 이후 단계) 실사 조정으로 0을 만든 뒤 사용 중지한다.
 */
@Component
class StockWarehouseGuard implements WarehouseDeactivationGuard {

	private final StockRepository stockRepository;

	StockWarehouseGuard(StockRepository stockRepository) {
		this.stockRepository = stockRepository;
	}

	@Override
	public void checkDeactivatable(Warehouse warehouse) {
		if (stockRepository.hasStockOrAllocation(warehouse.getId())) {
			throw new BusinessException(ErrorCode.WAREHOUSE_HAS_STOCK,
					"%s %s에 재고나 수주 할당이 남아 있어서 사용 중지할 수 없습니다. 재고를 0으로 만들고 수주를 끝낸 뒤 시도하세요."
							.formatted(warehouse.getCode(), warehouse.getName()));
		}
	}
}
