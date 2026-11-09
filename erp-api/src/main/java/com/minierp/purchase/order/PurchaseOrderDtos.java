package com.minierp.purchase.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.minierp.master.item.Item;
import com.minierp.master.item.Unit;
import com.minierp.master.partner.Partner;
import com.minierp.master.warehouse.Warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 발주 API의 요청·응답 형식.
 *
 * 상태를 바꾸는 요청(수정, 승인 요청, 승인, 반려, 취소, 마감)은 모두 화면이 본 version을 함께 보낸다.
 * 화면을 연 뒤 다른 사람이 이 발주를 바꿨다면 409 CONCURRENT_MODIFICATION으로 막는다.
 */
public final class PurchaseOrderDtos {

	private PurchaseOrderDtos() {
	}

	public record LineRequest(
			@NotNull(message = "품목을 선택하세요.")
			Long itemId,

			@NotNull(message = "수량을 입력하세요.")
			@Positive(message = "수량은 0보다 커야 합니다.")
			@Digits(integer = 12, fraction = 3, message = "수량은 정수 12자리, 소수 3자리까지입니다.")
			BigDecimal quantity,

			@NotNull(message = "단가를 입력하세요.")
			@PositiveOrZero(message = "단가는 0 이상이어야 합니다.")
			@Digits(integer = 13, fraction = 2, message = "단가는 정수 13자리, 소수 2자리까지입니다.")
			BigDecimal unitPrice) {
	}

	public record CreateRequest(
			@NotNull(message = "공급처를 선택하세요.")
			Long supplierId,

			@NotNull(message = "입고 창고를 선택하세요.")
			Long warehouseId,

			@NotNull(message = "발주일을 입력하세요.")
			LocalDate orderDate,

			@Nullable LocalDate dueDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "발주 품목을 한 줄 이상 입력하세요.")
			@Size(max = 100, message = "발주 품목은 100줄 이하여야 합니다.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	public record UpdateRequest(
			@NotNull(message = "버전이 없습니다. 화면을 다시 조회하세요.")
			Long version,

			@NotNull(message = "공급처를 선택하세요.")
			Long supplierId,

			@NotNull(message = "입고 창고를 선택하세요.")
			Long warehouseId,

			@NotNull(message = "발주일을 입력하세요.")
			LocalDate orderDate,

			@Nullable LocalDate dueDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "발주 품목을 한 줄 이상 입력하세요.")
			@Size(max = 100, message = "발주 품목은 100줄 이하여야 합니다.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	/** 승인 요청·승인 */
	public record ActionRequest(
			@NotNull(message = "버전이 없습니다. 화면을 다시 조회하세요.")
			Long version) {
	}

	/** 반려·취소·마감: 왜 그랬는지 남겨야 하는 행위 */
	public record ReasonRequest(
			@NotNull(message = "버전이 없습니다. 화면을 다시 조회하세요.")
			Long version,

			@NotBlank(message = "사유를 입력하세요.")
			@Size(max = 500, message = "사유는 500자 이하여야 합니다.")
			String reason) {
	}

	public record PartnerRef(Long id, String code, String name) {
		public static PartnerRef from(Partner partner) {
			return new PartnerRef(partner.getId(), partner.getCode(), partner.getName());
		}
	}

	public record WarehouseRef(Long id, String code, String name) {
		public static WarehouseRef from(Warehouse warehouse) {
			return new WarehouseRef(warehouse.getId(), warehouse.getCode(), warehouse.getName());
		}
	}

	public record ItemRef(Long id, String code, String name, @Nullable String spec, Unit unit) {
		public static ItemRef from(Item item) {
			return new ItemRef(item.getId(), item.getCode(), item.getName(), item.getSpec(), item.getUnit());
		}
	}

	/** 목록 한 줄 */
	public record Summary(
			Long id,
			String orderNo,
			PurchaseOrderStatus status,
			PartnerRef supplier,
			WarehouseRef warehouse,
			LocalDate orderDate,
			@Nullable LocalDate dueDate,
			int lineCount,
			BigDecimal totalAmount,
			long version,
			Instant updatedAt,
			String updatedBy) {

		static Summary from(PurchaseOrder po) {
			return new Summary(po.getId(), po.getOrderNo(), po.getStatus(), PartnerRef.from(po.getSupplier()),
					WarehouseRef.from(po.getWarehouse()), po.getOrderDate(), po.getDueDate(), po.getLines().size(),
					po.totalAmount(), po.getVersion(), po.getUpdatedAt(), po.getUpdatedBy());
		}
	}

	public record LineResponse(
			Long id,
			int lineNo,
			ItemRef item,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal amount,
			BigDecimal receivedQuantity,
			BigDecimal remainingQuantity) {

		static LineResponse from(PurchaseOrderLine line) {
			return new LineResponse(line.getId(), line.getLineNo(), ItemRef.from(line.getItem()), line.getQuantity(),
					line.getUnitPrice(), line.amount(), line.getReceivedQuantity(), line.remainingQuantity());
		}
	}

	public record EventResponse(
			PurchaseOrderEvent.Type type,
			@Nullable PurchaseOrderStatus fromStatus,
			PurchaseOrderStatus toStatus,
			@Nullable String reason,
			Instant occurredAt,
			String actor) {

		static EventResponse from(PurchaseOrderEvent event) {
			return new EventResponse(event.getType(), event.getFromStatus(), event.getToStatus(), event.getReason(),
					event.getOccurredAt(), event.getActor());
		}
	}

	/** 상세: 줄과 상태 이력까지 */
	public record Response(
			Long id,
			String orderNo,
			PurchaseOrderStatus status,
			PartnerRef supplier,
			WarehouseRef warehouse,
			LocalDate orderDate,
			@Nullable LocalDate dueDate,
			@Nullable String memo,
			BigDecimal totalAmount,
			List<LineResponse> lines,
			List<EventResponse> events,
			long version,
			Instant createdAt,
			String createdBy,
			Instant updatedAt,
			String updatedBy) {

		static Response from(PurchaseOrder po) {
			return new Response(po.getId(), po.getOrderNo(), po.getStatus(), PartnerRef.from(po.getSupplier()),
					WarehouseRef.from(po.getWarehouse()), po.getOrderDate(), po.getDueDate(), po.getMemo(),
					po.totalAmount(), po.getLines().stream().map(LineResponse::from).toList(),
					po.getEvents().stream().map(EventResponse::from).toList(), po.getVersion(), po.getCreatedAt(),
					po.getCreatedBy(), po.getUpdatedAt(), po.getUpdatedBy());
		}
	}
}
