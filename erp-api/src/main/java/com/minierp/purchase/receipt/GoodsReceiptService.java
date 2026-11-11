package com.minierp.purchase.receipt;

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
import com.minierp.purchase.order.PurchaseOrder;
import com.minierp.purchase.order.PurchaseOrderRepository;
import com.minierp.purchase.receipt.GoodsReceiptDtos.CreateRequest;
import com.minierp.purchase.receipt.GoodsReceiptDtos.LineRequest;
import com.minierp.purchase.receipt.GoodsReceiptDtos.Response;

/**
 * 입고 처리. 입고 한 건은 한 트랜잭션 안에서 네 가지를 함께 바꾼다.
 * <ol>
 * <li>입고 문서(goods_receipt, goods_receipt_line) 생성</li>
 * <li>발주 줄의 받은 수량(received_quantity) 누적</li>
 * <li>발주 상태 전이(부분 입고 / 입고 완료)와 이력</li>
 * <li>재고 원장 기록과 현재고 증가 (3단계, {@link StockLedger})</li>
 * </ol>
 * 하나라도 실패하면 넷 다 없던 일이 된다. "입고 문서는 있는데 재고는 그대로" 같은 어긋남이 ERP가 막아야 하는 일이다.
 */
@Service
@Transactional(readOnly = true)
public class GoodsReceiptService {

	private final GoodsReceiptRepository goodsReceiptRepository;
	private final PurchaseOrderRepository purchaseOrderRepository;
	private final StockLedger stockLedger;

	public GoodsReceiptService(GoodsReceiptRepository goodsReceiptRepository,
			PurchaseOrderRepository purchaseOrderRepository, StockLedger stockLedger) {
		this.goodsReceiptRepository = goodsReceiptRepository;
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.stockLedger = stockLedger;
	}

	public List<Response> search(@Nullable String keyword, @Nullable Long purchaseOrderId, @Nullable LocalDate fromDate,
			@Nullable LocalDate toDate) {
		return goodsReceiptRepository.search(LikePatterns.contains(keyword), purchaseOrderId, fromDate, toDate)
				.stream().map(Response::from).toList();
	}

	public Response get(Long id) {
		return Response.from(goodsReceiptRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "입고를 찾을 수 없습니다. (id=" + id + ")")));
	}

	@Transactional
	public Response receive(Long purchaseOrderId, CreateRequest request) {
		// 발주 행을 잠근다. 같은 발주의 입고가 동시에 들어오면 뒤의 요청은 여기서 앞 요청의 커밋을 기다린다
		PurchaseOrder po = purchaseOrderRepository.findByIdForReceipt(purchaseOrderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"발주를 찾을 수 없습니다. (id=" + purchaseOrderId + ")"));
		if (request.receiptDate().isBefore(po.getOrderDate())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"입고일(%s)은 발주일(%s)보다 빠를 수 없습니다.".formatted(request.receiptDate(), po.getOrderDate()));
		}
		Map<Long, BigDecimal> quantities = quantities(po, request.lines());
		String receiptNo = "GR-%06d".formatted(goodsReceiptRepository.nextReceiptNo());

		po.receive(quantities, receiptNo, CurrentUser.id());
		GoodsReceipt receipt = goodsReceiptRepository
				.save(new GoodsReceipt(receiptNo, po, request.receiptDate(), request.memo(), quantities));
		// 입고 줄 id가 원장의 근거가 된다. IDENTITY라서 save() 때 INSERT가 나가 id가 이미 있다
		stockLedger.post(receipt.getLines().stream()
				.map(line -> StockMovement.goodsReceipt(line.getItem(), receipt.getWarehouse(), line.getQuantity(),
						receipt.getReceiptDate(), receiptNo, line.getId()))
				.toList());
		return Response.from(receipt);
	}

	/** 요청 줄 → (발주 줄 id → 수량). 같은 발주 줄을 두 번 적으면 남은 수량 검사가 줄마다 따로 돌아 합계가 넘칠 수 있어서 막는다 */
	private static Map<Long, BigDecimal> quantities(PurchaseOrder po, List<LineRequest> requests) {
		Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
		for (LineRequest request : requests) {
			Item item = po.line(request.purchaseOrderLineId()).getItem();
			if (!item.getUnit().allows(request.quantity())) {
				throw new BusinessException(ErrorCode.INVALID_QUANTITY_FOR_UNIT,
						"%s(%s)은(는) %s 단위라서 정수 수량만 입고할 수 있습니다. (입력 %s)".formatted(item.getCode(), item.getName(),
								item.getUnit(), request.quantity().toPlainString()));
			}
			if (quantities.putIfAbsent(request.purchaseOrderLineId(), request.quantity()) != null) {
				throw new BusinessException(ErrorCode.DUPLICATE_LINE_ITEM,
						"같은 발주 줄을 두 번 입고할 수 없습니다. 한 줄에 수량을 합쳐 입력하세요. (%s)".formatted(item.getCode()));
			}
		}
		return quantities;
	}
}
