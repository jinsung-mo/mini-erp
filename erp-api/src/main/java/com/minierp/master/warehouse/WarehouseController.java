package com.minierp.master.warehouse;

import java.net.URI;
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

import com.minierp.master.warehouse.WarehouseDtos.CreateRequest;
import com.minierp.master.warehouse.WarehouseDtos.Response;
import com.minierp.master.warehouse.WarehouseDtos.UpdateRequest;

import jakarta.validation.Valid;

/** 창고 API. 품목처럼 DELETE가 없고 사용 중지/재개만 있다 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

	private final WarehouseService warehouseService;

	public WarehouseController(WarehouseService warehouseService) {
		this.warehouseService = warehouseService;
	}

	@GetMapping
	public List<Response> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Boolean active) {
		return warehouseService.search(keyword, active);
	}

	@GetMapping("/{id}")
	public Response get(@PathVariable Long id) {
		return warehouseService.get(id);
	}

	@PostMapping
	public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
		Response created = warehouseService.create(request);
		return ResponseEntity.created(URI.create("/api/warehouses/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		return warehouseService.update(id, request);
	}

	@PostMapping("/{id}/deactivate")
	public Response deactivate(@PathVariable Long id) {
		return warehouseService.deactivate(id);
	}

	@PostMapping("/{id}/activate")
	public Response activate(@PathVariable Long id) {
		return warehouseService.activate(id);
	}
}
