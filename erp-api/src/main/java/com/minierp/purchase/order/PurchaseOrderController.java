package com.minierp.purchase.order;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minierp.purchase.order.PurchaseOrderDtos.ActionRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.CreateRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.ReasonRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.Response;
import com.minierp.purchase.order.PurchaseOrderDtos.Summary;
import com.minierp.purchase.order.PurchaseOrderDtos.UpdateRequest;

import jakarta.validation.Valid;

/**
 * 발주 API.
 *
 * 상태 변경은 PUT으로 status 필드를 바꾸는 대신 업무 행위마다 URL을 둔다 (POST /{id}/approve).
 * "status를 APPROVED로 바꿔 줘"가 아니라 "승인한다"를 요청해야 서버가 누가, 어떤 규칙으로 승인하는지 판단할 수 있다.
 * DELETE는 없다. 잘못 만든 발주는 취소(cancel)하고 기록은 남긴다.
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

	private final PurchaseOrderService purchaseOrderService;

	public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
		this.purchaseOrderService = purchaseOrderService;
	}

	/** 상태는 여러 개를 줄 수 있다: ?status=APPROVED&status=PARTIALLY_RECEIVED (입고 대기 목록) */
	@GetMapping
	public List<Summary> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(name = "status", required = false) @Nullable List<PurchaseOrderStatus> statuses,
			@RequestParam(required = false) @Nullable Long supplierId,
			@RequestParam(required = false) @Nullable LocalDate fromDate,
			@RequestParam(required = false) @Nullable LocalDate toDate) {
		return purchaseOrderService.search(keyword, statuses, supplierId, fromDate, toDate);
	}

	@GetMapping("/{id}")
	public Response get(@PathVariable Long id) {
		return purchaseOrderService.get(id);
	}

	@PostMapping
	public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
		Response created = purchaseOrderService.create(request);
		return ResponseEntity.created(URI.create("/api/purchase-orders/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		return purchaseOrderService.update(id, request);
	}

	@PostMapping("/{id}/submit")
	public Response submit(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
		return purchaseOrderService.submit(id, request);
	}

	@PostMapping("/{id}/approve")
	public Response approve(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
		return purchaseOrderService.approve(id, request);
	}

	@PostMapping("/{id}/reject")
	public Response reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return purchaseOrderService.reject(id, request);
	}

	@PostMapping("/{id}/cancel")
	public Response cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return purchaseOrderService.cancel(id, request);
	}

	@PostMapping("/{id}/close")
	public Response close(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return purchaseOrderService.close(id, request);
	}
}
