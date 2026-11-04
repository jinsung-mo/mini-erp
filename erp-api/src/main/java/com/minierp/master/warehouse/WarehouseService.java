package com.minierp.master.warehouse;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.master.warehouse.WarehouseDtos.CreateRequest;
import com.minierp.master.warehouse.WarehouseDtos.Response;
import com.minierp.master.warehouse.WarehouseDtos.UpdateRequest;

/**
 * 창고 업무 규칙과 트랜잭션 경계. 지금은 품목과 같은 모양이지만 공통 클래스로 묶지 않았다.
 * 3단계에서 "재고가 남은 창고는 사용 중지할 수 없다"가 deactivate()에 들어오면서 규칙이 갈라진다.
 */
@Service
@Transactional(readOnly = true)
public class WarehouseService {

	private final WarehouseRepository warehouseRepository;

	public WarehouseService(WarehouseRepository warehouseRepository) {
		this.warehouseRepository = warehouseRepository;
	}

	public List<Response> search(@Nullable String keyword, @Nullable Boolean active) {
		return warehouseRepository.search(LikePatterns.contains(keyword), active).stream().map(Response::from).toList();
	}

	public Response get(Long id) {
		return Response.from(find(id));
	}

	@Transactional
	public Response create(CreateRequest request) {
		if (warehouseRepository.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.DUPLICATE_CODE, "이미 사용 중인 창고 코드입니다: " + request.code());
		}
		Warehouse warehouse = warehouseRepository.save(new Warehouse(request.code(), request.name(), request.location()));
		return Response.from(warehouse);
	}

	@Transactional
	public Response update(Long id, UpdateRequest request) {
		Warehouse warehouse = find(id);
		warehouse.changeInfo(request.name(), request.location());
		return flushAndRespond(warehouse);
	}

	@Transactional
	public Response deactivate(Long id) {
		Warehouse warehouse = find(id);
		warehouse.deactivate();
		return flushAndRespond(warehouse);
	}

	@Transactional
	public Response activate(Long id) {
		Warehouse warehouse = find(id);
		warehouse.activate();
		return flushAndRespond(warehouse);
	}

	/** 수정자·수정일시는 flush(UPDATE 실행) 때 채워지므로, 응답을 만들기 전에 flush한다 (ItemService와 같은 이유) */
	private Response flushAndRespond(Warehouse warehouse) {
		warehouseRepository.flush();
		return Response.from(warehouse);
	}

	private Warehouse find(Long id) {
		return warehouseRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고를 찾을 수 없습니다. (id=" + id + ")"));
	}
}
