package com.minierp.sales.shipment;

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

import com.minierp.sales.shipment.ShipmentDtos.CreateRequest;
import com.minierp.sales.shipment.ShipmentDtos.Response;

import jakarta.validation.Valid;

/**
 * 출고 API. 출고는 항상 확정된 수주에서 시작하므로 만드는 주소는 수주 아래에 둔다 (POST /api/sales-orders/{id}/shipments).
 * 수정·삭제 API는 없다. 출고는 이미 일어난 사건이다.
 */
@RestController
public class ShipmentController {

	private final ShipmentService shipmentService;

	public ShipmentController(ShipmentService shipmentService) {
		this.shipmentService = shipmentService;
	}

	@PostMapping("/api/sales-orders/{salesOrderId}/shipments")
	public ResponseEntity<Response> ship(@PathVariable Long salesOrderId,
			@Valid @RequestBody CreateRequest request) {
		Response created = shipmentService.ship(salesOrderId, request);
		return ResponseEntity.created(URI.create("/api/shipments/" + created.id())).body(created);
	}

	@GetMapping("/api/shipments")
	public List<Response> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Long salesOrderId,
			@RequestParam(required = false) @Nullable LocalDate fromDate,
			@RequestParam(required = false) @Nullable LocalDate toDate) {
		return shipmentService.search(keyword, salesOrderId, fromDate, toDate);
	}

	@GetMapping("/api/shipments/{id}")
	public Response get(@PathVariable Long id) {
		return shipmentService.get(id);
	}
}
