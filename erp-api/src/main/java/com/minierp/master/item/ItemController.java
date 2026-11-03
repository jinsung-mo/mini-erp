package com.minierp.master.item;

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

import com.minierp.master.item.ItemDtos.CreateRequest;
import com.minierp.master.item.ItemDtos.Response;
import com.minierp.master.item.ItemDtos.UpdateRequest;

import jakarta.validation.Valid;

/**
 * 품목 API. DELETE는 없다: 마스터는 지우지 않고 사용 중지한다.
 * 사용 중지/재개는 필드 수정이 아니라 업무 행위라서 별도 URL(POST .../deactivate)로 드러낸다.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

	private final ItemService itemService;

	public ItemController(ItemService itemService) {
		this.itemService = itemService;
	}

	@GetMapping
	public List<Response> search(@RequestParam(required = false) @Nullable String keyword,
			@RequestParam(required = false) @Nullable Boolean active) {
		return itemService.search(keyword, active);
	}

	@GetMapping("/{id}")
	public Response get(@PathVariable Long id) {
		return itemService.get(id);
	}

	@PostMapping
	public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
		Response created = itemService.create(request);
		return ResponseEntity.created(URI.create("/api/items/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public Response update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
		return itemService.update(id, request);
	}

	@PostMapping("/{id}/deactivate")
	public Response deactivate(@PathVariable Long id) {
		return itemService.deactivate(id);
	}

	@PostMapping("/{id}/activate")
	public Response activate(@PathVariable Long id) {
		return itemService.activate(id);
	}
}
