package com.minierp.purchase.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.minierp.purchase.order.PurchaseOrderDtos.ItemRef;
import com.minierp.purchase.order.PurchaseOrderDtos.PartnerRef;
import com.minierp.purchase.order.PurchaseOrderDtos.WarehouseRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 입고 API의 요청·응답 형식.
 * 입고 요청에는 version이 없다. 입고는 "남은 수량 안에서 더하는" 일이라, 화면이 본 뒤 다른 입고가 있었더라도
 * 남은 수량만 넘지 않으면 받아도 된다. 대신 발주 행을 잠그고 남은 수량을 다시 확인한다 (GoodsReceiptService).
 */
public final class GoodsReceiptDtos {

	private GoodsReceiptDtos() {
	}

	public record LineRequest(
			@NotNull(message = "발주 줄이 없습니다.")
			Long purchaseOrderLineId,

			@NotNull(message = "입고 수량을 입력하세요.")
			@Positive(message = "입고 수량은 0보다 커야 합니다.")
			@Digits(integer = 12, fraction = 3, message = "수량은 정수 12자리, 소수 3자리까지입니다.")
			BigDecimal quantity) {
	}

	public record CreateRequest(
			@NotNull(message = "입고일을 입력하세요.")
			LocalDate receiptDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "입고할 줄을 하나 이상 입력하세요.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	public record OrderRef(Long id, String orderNo) {
	}

	public record LineResponse(Long id, Long purchaseOrderLineId, int lineNo, ItemRef item, BigDecimal quantity) {

		static LineResponse from(GoodsReceiptLine line) {
			return new LineResponse(line.getId(), line.getPurchaseOrderLine().getId(),
					line.getPurchaseOrderLine().getLineNo(), ItemRef.from(line.getItem()), line.getQuantity());
		}
	}

	public record Response(
			Long id,
			String receiptNo,
			OrderRef purchaseOrder,
			PartnerRef supplier,
			WarehouseRef warehouse,
			LocalDate receiptDate,
			@Nullable String memo,
			List<LineResponse> lines,
			Instant createdAt,
			String createdBy) {

		static Response from(GoodsReceipt receipt) {
			var po = receipt.getPurchaseOrder();
			return new Response(receipt.getId(), receipt.getReceiptNo(), new OrderRef(po.getId(), po.getOrderNo()),
					PartnerRef.from(po.getSupplier()), WarehouseRef.from(receipt.getWarehouse()),
					receipt.getReceiptDate(), receipt.getMemo(),
					receipt.getLines().stream().map(LineResponse::from).toList(), receipt.getCreatedAt(),
					receipt.getCreatedBy());
		}
	}
}
