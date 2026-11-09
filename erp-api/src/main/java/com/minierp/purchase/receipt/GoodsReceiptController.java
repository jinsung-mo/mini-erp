package com.minierp.purchase.receipt;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minierp.purchase.receipt.GoodsReceiptDtos.CreateRequest;
import com.minierp.purchase.receipt.GoodsReceiptDtos.Response;

import jakarta.validation.Valid;

/**
 * 입고 API. 입고는 항상 발주에서 시작하므로 만드는 주소는 발주 아래에 둔다 (POST /api/purchase-orders/{id}/receipts).
 * 수정·삭제 API는 없다. 입고는 이미 일어난 사건이다.
 */
@RestController
public class GoodsReceiptController {

	private final GoodsReceiptService goodsReceiptService;

	public GoodsReceiptController(GoodsReceiptService goodsReceiptService) {
		this.goodsReceiptService = goodsReceiptService;
	}

	@PostMapping("/api/purchase-orders/{purchaseOrderId}/receipts")
	public ResponseEntity<Response> receive(@PathVariable Long purchaseOrderId,
			@Valid @RequestBody CreateRequest request) {
		Response created = goodsReceiptService.receive(purchaseOrderId, request);
		return ResponseEntity.created(URI.create("/api/goods-receipts/" + created.id())).body(created);
	}

	@GetMapping("/api/goods-receipts")
	public List<Response> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Long purchaseOrderId,
			@RequestParam(required = false) @Nullable LocalDate fromDate,
			@RequestParam(required = false) @Nullable LocalDate toDate) {
		return goodsReceiptService.search(keyword, purchaseOrderId, fromDate, toDate);
	}

	@GetMapping("/api/goods-receipts/{id}")
	public Response get(@PathVariable Long id) {
		return goodsReceiptService.get(id);
	}
}
