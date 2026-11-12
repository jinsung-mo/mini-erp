package com.minierp.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.minierp.master.item.Item;
import com.minierp.master.warehouse.Warehouse;

/**
 * 원장에 올릴 재고 이동 하나. 다른 모듈(구매, 판매)은 이것을 만들어 {@link StockLedger#post}에 넘긴다.
 *
 * @param quantity 들어오면 +, 나가면 -
 * @param expectedBalance 화면이 본 장부 수량. 값이 있으면 잠근 뒤의 현재고와 같은지 확인한다 (실사 조정).
 *                        입고처럼 "있는 재고에 더하기만 하는" 이동은 null이다.
 */
public record StockMovement(
		StockTransactionType type,
		Item item,
		Warehouse warehouse,
		BigDecimal quantity,
		LocalDate txnDate,
		String sourceNo,
		@Nullable Long sourceLineId,
		@Nullable String reason,
		@Nullable BigDecimal expectedBalance) {

	public static StockMovement goodsReceipt(Item item, Warehouse warehouse, BigDecimal quantity, LocalDate date,
			String receiptNo, Long receiptLineId) {
		return new StockMovement(StockTransactionType.GOODS_RECEIPT, item, warehouse, quantity, date, receiptNo,
				receiptLineId, null, null);
	}

	/** 출고: 원장에는 음수로 쌓는다 */
	public static StockMovement shipment(Item item, Warehouse warehouse, BigDecimal quantity, LocalDate date,
			String shipmentNo, Long shipmentLineId) {
		return new StockMovement(StockTransactionType.SHIPMENT, item, warehouse, quantity.negate(), date, shipmentNo,
				shipmentLineId, null, null);
	}
}
