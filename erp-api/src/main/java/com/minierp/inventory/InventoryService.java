package com.minierp.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.inventory.InventoryDtos.AdjustmentRequest;
import com.minierp.inventory.InventoryDtos.StockResponse;
import com.minierp.inventory.InventoryDtos.TransactionResponse;
import com.minierp.master.item.Item;
import com.minierp.master.item.ItemRepository;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.master.warehouse.WarehouseRepository;

/** 현재고·수불부 조회와 실사 조정 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

	private final StockRepository stockRepository;
	private final StockTransactionRepository transactionRepository;
	private final StockLedger stockLedger;
	private final ItemRepository itemRepository;
	private final WarehouseRepository warehouseRepository;

	public InventoryService(StockRepository stockRepository, StockTransactionRepository transactionRepository,
			StockLedger stockLedger, ItemRepository itemRepository, WarehouseRepository warehouseRepository) {
		this.stockRepository = stockRepository;
		this.transactionRepository = transactionRepository;
		this.stockLedger = stockLedger;
		this.itemRepository = itemRepository;
		this.warehouseRepository = warehouseRepository;
	}

	public List<StockResponse> searchStocks(@Nullable String keyword, @Nullable Long warehouseId, boolean includeZero) {
		return stockRepository.search(LikePatterns.contains(keyword), warehouseId, includeZero).stream()
				.map(StockResponse::from).toList();
	}

	public List<TransactionResponse> searchTransactions(@Nullable String keyword, @Nullable Long itemId,
			@Nullable Long warehouseId, @Nullable StockTransactionType type, @Nullable LocalDate fromDate,
			@Nullable LocalDate toDate) {
		return transactionRepository
				.search(LikePatterns.contains(keyword), itemId, warehouseId, type, fromDate, toDate).stream()
				.map(TransactionResponse::from).toList();
	}

	/**
	 * 실사 결과를 장부에 반영한다. 차이만큼 원장에 조정 한 줄을 쌓는다 (현재고를 실사 수량으로 덮어쓰지 않는다).
	 *
	 * 사용 중지된 품목도 조정할 수 있다. 남은 물건을 폐기하는 것도 조정이고, 실사는 기준정보가 아니라 실제 물건이 기준이다.
	 * 사용 중지된 창고는 조정할 수 없다. 재고가 남은 창고는 사용 중지되지 않으므로, 거기서 물건이 나왔다면 먼저 창고를 다시 써야 한다.
	 */
	@Transactional
	public TransactionResponse adjust(AdjustmentRequest request) {
		Item item = itemRepository.findById(request.itemId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"품목을 찾을 수 없습니다. (id=" + request.itemId() + ")"));
		Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"창고를 찾을 수 없습니다. (id=" + request.warehouseId() + ")"));
		if (!warehouse.isActive()) {
			throw new BusinessException(ErrorCode.INACTIVE_MASTER,
					"사용 중지된 창고입니다: %s %s".formatted(warehouse.getCode(), warehouse.getName()));
		}
		if (!item.getUnit().allows(request.countedQuantity())) {
			throw new BusinessException(ErrorCode.INVALID_QUANTITY_FOR_UNIT,
					"%s(%s)은(는) %s 단위라서 실사 수량은 정수여야 합니다. (입력 %s)".formatted(item.getCode(), item.getName(),
							item.getUnit(), request.countedQuantity().toPlainString()));
		}
		BigDecimal difference = request.countedQuantity().subtract(request.bookQuantity());
		if (difference.signum() == 0) {
			throw new BusinessException(ErrorCode.NO_STOCK_DIFFERENCE);
		}

		String adjustmentNo = "ADJ-%06d".formatted(transactionRepository.nextAdjustmentNo());
		StockMovement movement = new StockMovement(StockTransactionType.ADJUSTMENT, item, warehouse, difference,
				request.adjustDate(), adjustmentNo, null, request.reason().strip(), request.bookQuantity());
		return TransactionResponse.from(stockLedger.post(List.of(movement)).get(0));
	}
}
