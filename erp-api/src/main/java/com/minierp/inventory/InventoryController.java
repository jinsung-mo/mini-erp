package com.minierp.inventory;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minierp.inventory.InventoryDtos.AdjustmentRequest;
import com.minierp.inventory.InventoryDtos.StockResponse;
import com.minierp.inventory.InventoryDtos.TransactionResponse;

import jakarta.validation.Valid;

/**
 * 재고 API. 현재고와 원장은 조회만 있다. 재고를 바꾸는 길은 입고(구매)와 실사 조정뿐이고, 둘 다 원장에 한 줄을 남긴다.
 * "현재고를 N으로 수정" 같은 API는 일부러 두지 않는다. 그런 API가 있으면 원장 합계와 현재고가 어긋나는 순간 이유를 찾을 수 없다.
 */
@RestController
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping("/api/stocks")
	public List<StockResponse> stocks(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Long warehouseId,
			@RequestParam(defaultValue = "false") boolean includeZero) {
		return inventoryService.searchStocks(keyword, warehouseId, includeZero);
	}

	@GetMapping("/api/stock-transactions")
	public List<TransactionResponse> transactions(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Long itemId,
			@RequestParam(required = false) @Nullable Long warehouseId,
			@RequestParam(required = false) @Nullable StockTransactionType type,
			@RequestParam(required = false) @Nullable LocalDate fromDate,
			@RequestParam(required = false) @Nullable LocalDate toDate) {
		return inventoryService.searchTransactions(keyword, itemId, warehouseId, type, fromDate, toDate);
	}

	@PostMapping("/api/stock-adjustments")
	public ResponseEntity<TransactionResponse> adjust(@Valid @RequestBody AdjustmentRequest request) {
		TransactionResponse created = inventoryService.adjust(request);
		return ResponseEntity.created(URI.create("/api/stock-transactions?keyword=" + created.sourceNo()))
				.body(created);
	}
}
