package com.minierp.master.partner;

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

import com.minierp.master.partner.PartnerDtos.CreateRequest;
import com.minierp.master.partner.PartnerDtos.Response;
import com.minierp.master.partner.PartnerDtos.UpdateRequest;

import jakarta.validation.Valid;

/**
 * 거래처 API. 품목처럼 DELETE가 없고 사용 중지/재개만 있다.
 * 2단계 발주 화면은 role=SUPPLIER, 4단계 수주 화면은 role=CUSTOMER로 조회해서 고른다.
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {

	private final PartnerService partnerService;

	public PartnerController(PartnerService partnerService) {
		this.partnerService = partnerService;
	}

	@GetMapping
	public List<Response> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable PartnerRole role,
			@RequestParam(required = false) @Nullable Boolean active) {
		return partnerService.search(keyword, role, active);
	}

	@GetMapping("/{id}")
	public Response get(@PathVariable Long id) {
		return partnerService.get(id);
	}

	@PostMapping
	public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
		Response created = partnerService.create(request);
		return ResponseEntity.created(URI.create("/api/partners/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		return partnerService.update(id, request);
	}

	@PostMapping("/{id}/deactivate")
	public Response deactivate(@PathVariable Long id) {
		return partnerService.deactivate(id);
	}

	@PostMapping("/{id}/activate")
	public Response activate(@PathVariable Long id) {
		return partnerService.activate(id);
	}
}
