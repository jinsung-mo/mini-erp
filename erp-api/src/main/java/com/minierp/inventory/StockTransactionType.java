package com.minierp.inventory;

/** 수불 원장의 거래 종류. 4단계 판매가 출고(SHIPMENT)를 더한다 */
public enum StockTransactionType {

	GOODS_RECEIPT("입고"),
	ADJUSTMENT("재고 조정");

	private final String label;

	StockTransactionType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
