package com.minierp.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.minierp.master.item.Item;
import com.minierp.master.item.Unit;
import com.minierp.master.warehouse.Warehouse;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 재고 API의 요청·응답 형식 */
public final class InventoryDtos {

	private InventoryDtos() {
	}

	public record ItemRef(Long id, String code, String name, @Nullable String spec, Unit unit) {
		static ItemRef from(Item item) {
			return new ItemRef(item.getId(), item.getCode(), item.getName(), item.getSpec(), item.getUnit());
		}
	}

	public record WarehouseRef(Long id, String code, String name) {
		static WarehouseRef from(Warehouse warehouse) {
			return new WarehouseRef(warehouse.getId(), warehouse.getCode(), warehouse.getName());
		}
	}

	/** 현재고 한 줄 */
	public record StockResponse(Long id, ItemRef item, WarehouseRef warehouse, BigDecimal quantity, Instant updatedAt,
			String updatedBy) {

		static StockResponse from(Stock stock) {
			return new StockResponse(stock.getId(), ItemRef.from(stock.getItem()),
					WarehouseRef.from(stock.getWarehouse()), stock.getQuantity(), stock.getUpdatedAt(),
					stock.getUpdatedBy());
		}
	}

	/** 원장 한 줄 */
	public record TransactionResponse(
			Long id,
			StockTransactionType type,
			String typeLabel,
			ItemRef item,
			WarehouseRef warehouse,
			BigDecimal quantity,
			BigDecimal balanceAfter,
			LocalDate txnDate,
			String sourceNo,
			@Nullable String reason,
			Instant occurredAt,
			String actor) {

		static TransactionResponse from(StockTransaction t) {
			return new TransactionResponse(t.getId(), t.getType(), t.getType().label(), ItemRef.from(t.getItem()),
					WarehouseRef.from(t.getWarehouse()), t.getQuantity(), t.getBalanceAfter(), t.getTxnDate(),
					t.getSourceNo(), t.getReason(), t.getOccurredAt(), t.getActor());
		}
	}

	/**
	 * 실사 조정: 창고에서 실제로 센 수량을 장부에 맞춘다.
	 *
	 * 차이(+2, -3)가 아니라 장부 수량과 실사 수량을 받는다. 담당자가 아는 것은 "센 결과"이고, 차이는 서버가 계산한다.
	 * bookQuantity는 화면을 열 때 본 장부 수량이다. 그사이 입고가 있었다면 차이가 달라지므로 서버가 잠근 뒤 다시 비교한다.
	 */
	public record AdjustmentRequest(
			@NotNull(message = "품목을 선택하세요.")
			Long itemId,

			@NotNull(message = "창고를 선택하세요.")
			Long warehouseId,

			@NotNull(message = "장부 수량이 없습니다. 다시 조회하세요.")
			@PositiveOrZero(message = "장부 수량은 0 이상이어야 합니다.")
			@Digits(integer = 12, fraction = 3, message = "수량은 정수 12자리, 소수 3자리까지입니다.")
			BigDecimal bookQuantity,

			@NotNull(message = "실사 수량을 입력하세요.")
			@PositiveOrZero(message = "실사 수량은 0 이상이어야 합니다.")
			@Digits(integer = 12, fraction = 3, message = "수량은 정수 12자리, 소수 3자리까지입니다.")
			BigDecimal countedQuantity,

			@NotNull(message = "조정일을 입력하세요.")
			LocalDate adjustDate,

			@NotBlank(message = "조정 사유를 입력하세요.")
			@Size(max = 500, message = "사유는 500자 이하여야 합니다.")
			String reason) {
	}
}
