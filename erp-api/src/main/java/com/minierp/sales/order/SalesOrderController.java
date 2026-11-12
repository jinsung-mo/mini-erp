package com.minierp.sales.order;

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

import com.minierp.sales.order.SalesOrderDtos.ActionRequest;
import com.minierp.sales.order.SalesOrderDtos.CreateRequest;
import com.minierp.sales.order.SalesOrderDtos.ReasonRequest;
import com.minierp.sales.order.SalesOrderDtos.Response;
import com.minierp.sales.order.SalesOrderDtos.Summary;
import com.minierp.sales.order.SalesOrderDtos.UpdateRequest;

import jakarta.validation.Valid;

/**
 * 수주 API. 발주 API와 같은 모양이다: 상태 변경은 업무 행위마다 URL을 두고(POST /{id}/confirm), DELETE는 없다.
 * 확정(confirm)이 실패하는 가장 흔한 이유는 409 INSUFFICIENT_STOCK(가용 재고 부족)이다.
 */
@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

	private final SalesOrderService salesOrderService;

	public SalesOrderController(SalesOrderService salesOrderService) {
		this.salesOrderService = salesOrderService;
	}

	/** 상태는 여러 개를 줄 수 있다: ?status=CONFIRMED&status=PARTIALLY_SHIPPED (출고 대기 목록) */
	@GetMapping
	public List<Summary> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(name = "status", required = false) @Nullable List<SalesOrderStatus> statuses,
			@RequestParam(required = false) @Nullable Long customerId,
			@RequestParam(required = false) @Nullable LocalDate fromDate,
			@RequestParam(required = false) @Nullable LocalDate toDate) {
		return salesOrderService.search(keyword, statuses, customerId, fromDate, toDate);
	}

	@GetMapping("/{id}")
	public Response get(@PathVariable Long id) {
		return salesOrderService.get(id);
	}

	@PostMapping
	public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
		Response created = salesOrderService.create(request);
		return ResponseEntity.created(URI.create("/api/sales-orders/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		return salesOrderService.update(id, request);
	}

	@PostMapping("/{id}/confirm")
	public Response confirm(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
		return salesOrderService.confirm(id, request);
	}

	@PostMapping("/{id}/cancel")
	public Response cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return salesOrderService.cancel(id, request);
	}

	@PostMapping("/{id}/close")
	public Response close(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return salesOrderService.close(id, request);
	}
}
