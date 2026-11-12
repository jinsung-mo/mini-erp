package com.minierp.sales.shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.common.web.CurrentUser;
import com.minierp.inventory.StockLedger;
import com.minierp.inventory.StockMovement;
import com.minierp.master.item.Item;
import com.minierp.sales.order.SalesOrder;
import com.minierp.sales.order.SalesOrderRepository;
import com.minierp.sales.shipment.ShipmentDtos.CreateRequest;
import com.minierp.sales.shipment.ShipmentDtos.LineRequest;
import com.minierp.sales.shipment.ShipmentDtos.Response;

/**
 * 출고 처리. 입고(GoodsReceiptService)를 뒤집은 흐름이고, 한 트랜잭션 안에서 네 가지를 함께 바꾼다.
 * <ol>
 * <li>출고 문서(shipment, shipment_line) 생성</li>
 * <li>수주 줄의 출고 수량(shipped_quantity) 누적</li>
 * <li>수주 상태 전이(부분 출고 / 출고 완료)와 이력</li>
 * <li>재고 원장에 출고(-) 기록, 현재고와 할당량을 같은 수량만큼 감소 ({@link StockLedger})</li>
 * </ol>
 * 확정 때 할당해 두었으므로 보통은 재고가 모자랄 수 없다. 그래도 실사에서 분실이 확인돼 현재고가 할당보다 작아졌다면
 * 원장 기록에서 INSUFFICIENT_STOCK으로 막히고 넷 다 롤백된다. 장부상 있어도 창고에 없는 물건은 내보낼 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

	private final ShipmentRepository shipmentRepository;
	private final SalesOrderRepository salesOrderRepository;
	private final StockLedger stockLedger;

	public ShipmentService(ShipmentRepository shipmentRepository, SalesOrderRepository salesOrderRepository,
			StockLedger stockLedger) {
		this.shipmentRepository = shipmentRepository;
		this.salesOrderRepository = salesOrderRepository;
		this.stockLedger = stockLedger;
	}

	public List<Response> search(@Nullable String keyword, @Nullable Long salesOrderId, @Nullable LocalDate fromDate,
			@Nullable LocalDate toDate) {
		return shipmentRepository.search(LikePatterns.contains(keyword), salesOrderId, fromDate, toDate)
				.stream().map(Response::from).toList();
	}

	public Response get(Long id) {
		return Response.from(shipmentRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "출고를 찾을 수 없습니다. (id=" + id + ")")));
	}

	@Transactional
	public Response ship(Long salesOrderId, CreateRequest request) {
		// 수주 행을 잠근다 (잠금 순서: 수주 → 현재고). 같은 수주의 출고가 동시에 오면 뒤의 요청은 앞 요청의 커밋을 기다린다
		SalesOrder so = salesOrderRepository.findByIdForUpdate(salesOrderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"수주를 찾을 수 없습니다. (id=" + salesOrderId + ")"));
		if (request.shipDate().isBefore(so.getOrderDate())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"출고일(%s)은 수주일(%s)보다 빠를 수 없습니다.".formatted(request.shipDate(), so.getOrderDate()));
		}
		Map<Long, BigDecimal> quantities = quantities(so, request.lines());
		String shipmentNo = "SH-%06d".formatted(shipmentRepository.nextShipmentNo());

		so.ship(quantities, shipmentNo, CurrentUser.id());
		Shipment shipment = shipmentRepository
				.save(new Shipment(shipmentNo, so, request.shipDate(), request.memo(), quantities));
		stockLedger.post(shipment.getLines().stream()
				.map(line -> StockMovement.shipment(line.getItem(), shipment.getWarehouse(), line.getQuantity(),
						shipment.getShipDate(), shipmentNo, line.getId()))
				.toList());
		return Response.from(shipment);
	}

	/** 요청 줄 → (수주 줄 id → 수량). 같은 수주 줄을 두 번 적지 못하게 막는다 (입고와 같은 이유) */
	private static Map<Long, BigDecimal> quantities(SalesOrder so, List<LineRequest> requests) {
		Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
		for (LineRequest request : requests) {
			Item item = so.line(request.salesOrderLineId()).getItem();
			if (!item.getUnit().allows(request.quantity())) {
				throw new BusinessException(ErrorCode.INVALID_QUANTITY_FOR_UNIT,
						"%s(%s)은(는) %s 단위라서 정수 수량만 출고할 수 있습니다. (입력 %s)".formatted(item.getCode(), item.getName(),
								item.getUnit(), request.quantity().toPlainString()));
			}
			if (quantities.putIfAbsent(request.salesOrderLineId(), request.quantity()) != null) {
				throw new BusinessException(ErrorCode.DUPLICATE_LINE_ITEM,
						"같은 수주 줄을 두 번 출고할 수 없습니다. 한 줄에 수량을 합쳐 입력하세요. (%s)".formatted(item.getCode()));
			}
		}
		return quantities;
	}
}
