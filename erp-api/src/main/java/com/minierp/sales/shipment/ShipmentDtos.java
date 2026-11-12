package com.minierp.sales.shipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.minierp.sales.order.SalesOrderDtos.ItemRef;
import com.minierp.sales.order.SalesOrderDtos.PartnerRef;
import com.minierp.sales.order.SalesOrderDtos.WarehouseRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 출고 API의 요청·응답 형식. 입고처럼 version이 없다. 남은 수량 안에서만 내보내면 되고, 서버가 수주 행을 잠그고 다시 확인한다.
 */
public final class ShipmentDtos {

	private ShipmentDtos() {
	}

	public record LineRequest(
			@NotNull(message = "수주 줄이 없습니다.")
			Long salesOrderLineId,

			@NotNull(message = "출고 수량을 입력하세요.")
			@Positive(message = "출고 수량은 0보다 커야 합니다.")
			@Digits(integer = 12, fraction = 3, message = "수량은 정수 12자리, 소수 3자리까지입니다.")
			BigDecimal quantity) {
	}

	public record CreateRequest(
			@NotNull(message = "출고일을 입력하세요.")
			LocalDate shipDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "출고할 줄을 하나 이상 입력하세요.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	public record OrderRef(Long id, String orderNo) {
	}

	public record LineResponse(Long id, Long salesOrderLineId, int lineNo, ItemRef item, BigDecimal quantity) {

		static LineResponse from(ShipmentLine line) {
			return new LineResponse(line.getId(), line.getSalesOrderLine().getId(),
					line.getSalesOrderLine().getLineNo(), ItemRef.from(line.getItem()), line.getQuantity());
		}
	}

	public record Response(
			Long id,
			String shipmentNo,
			OrderRef salesOrder,
			PartnerRef customer,
			WarehouseRef warehouse,
			LocalDate shipDate,
			@Nullable String memo,
			List<LineResponse> lines,
			Instant createdAt,
			String createdBy) {

		static Response from(Shipment shipment) {
			var so = shipment.getSalesOrder();
			return new Response(shipment.getId(), shipment.getShipmentNo(), new OrderRef(so.getId(), so.getOrderNo()),
					PartnerRef.from(so.getCustomer()), WarehouseRef.from(shipment.getWarehouse()),
					shipment.getShipDate(), shipment.getMemo(),
					shipment.getLines().stream().map(LineResponse::from).toList(), shipment.getCreatedAt(),
					shipment.getCreatedBy());
		}
	}
}
