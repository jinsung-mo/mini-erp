package com.minierp.sales.order;

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
 * 수주 API의 요청·응답 형식. 발주와 같은 규칙으로, 상태를 바꾸는 요청(수정, 확정, 취소, 마감)은 화면이 본 version을 함께 보낸다.
 * 참조 형식(PartnerRef 등)은 발주 쪽 것을 가져다 쓰지 않고 따로 둔다. 판매가 구매 패키지에 기대지 않게.
 */
public final class SalesOrderDtos {

	private SalesOrderDtos() {
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
			@NotNull(message = "고객을 선택하세요.")
			Long customerId,

			@NotNull(message = "출고 창고를 선택하세요.")
			Long warehouseId,

			@NotNull(message = "수주일을 입력하세요.")
			LocalDate orderDate,

			@Nullable LocalDate dueDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "수주 품목을 한 줄 이상 입력하세요.")
			@Size(max = 100, message = "수주 품목은 100줄 이하여야 합니다.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	public record UpdateRequest(
			@NotNull(message = "버전이 없습니다. 화면을 다시 조회하세요.")
			Long version,

			@NotNull(message = "고객을 선택하세요.")
			Long customerId,

			@NotNull(message = "출고 창고를 선택하세요.")
			Long warehouseId,

			@NotNull(message = "수주일을 입력하세요.")
			LocalDate orderDate,

			@Nullable LocalDate dueDate,

			@Size(max = 500, message = "메모는 500자 이하여야 합니다.")
			@Nullable String memo,

			@NotEmpty(message = "수주 품목을 한 줄 이상 입력하세요.")
			@Size(max = 100, message = "수주 품목은 100줄 이하여야 합니다.")
			List<@NotNull @Valid LineRequest> lines) {
	}

	/** 확정 */
	public record ActionRequest(
			@NotNull(message = "버전이 없습니다. 화면을 다시 조회하세요.")
			Long version) {
	}

	/** 취소·마감: 왜 그랬는지 남겨야 하는 행위 */
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
			SalesOrderStatus status,
			PartnerRef customer,
			WarehouseRef warehouse,
			LocalDate orderDate,
			@Nullable LocalDate dueDate,
			int lineCount,
			BigDecimal totalAmount,
			long version,
			Instant updatedAt,
			String updatedBy) {

		static Summary from(SalesOrder so) {
			return new Summary(so.getId(), so.getOrderNo(), so.getStatus(), PartnerRef.from(so.getCustomer()),
					WarehouseRef.from(so.getWarehouse()), so.getOrderDate(), so.getDueDate(), so.getLines().size(),
					so.totalAmount(), so.getVersion(), so.getUpdatedAt(), so.getUpdatedBy());
		}
	}

	public record LineResponse(
			Long id,
			int lineNo,
			ItemRef item,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal amount,
			BigDecimal shippedQuantity,
			BigDecimal remainingQuantity) {

		static LineResponse from(SalesOrderLine line) {
			return new LineResponse(line.getId(), line.getLineNo(), ItemRef.from(line.getItem()), line.getQuantity(),
					line.getUnitPrice(), line.amount(), line.getShippedQuantity(), line.remainingQuantity());
		}
	}

	public record EventResponse(
			SalesOrderEvent.Type type,
			@Nullable SalesOrderStatus fromStatus,
			SalesOrderStatus toStatus,
			@Nullable String reason,
			Instant occurredAt,
			String actor) {

		static EventResponse from(SalesOrderEvent event) {
			return new EventResponse(event.getType(), event.getFromStatus(), event.getToStatus(), event.getReason(),
					event.getOccurredAt(), event.getActor());
		}
	}

	/** 상세: 줄과 상태 이력까지 */
	public record Response(
			Long id,
			String orderNo,
			SalesOrderStatus status,
			PartnerRef customer,
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

		static Response from(SalesOrder so) {
			return new Response(so.getId(), so.getOrderNo(), so.getStatus(), PartnerRef.from(so.getCustomer()),
					WarehouseRef.from(so.getWarehouse()), so.getOrderDate(), so.getDueDate(), so.getMemo(),
					so.totalAmount(), so.getLines().stream().map(LineResponse::from).toList(),
					so.getEvents().stream().map(EventResponse::from).toList(), so.getVersion(), so.getCreatedAt(),
					so.getCreatedBy(), so.getUpdatedAt(), so.getUpdatedBy());
		}
	}
}
