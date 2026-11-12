package com.minierp.inventory;

/** 수불 원장의 거래 종류 */
public enum StockTransactionType {

	GOODS_RECEIPT("입고"),
	ADJUSTMENT("재고 조정"),
	/** 출고: 확정한 수주의 할당분을 내보낸다. 현재고와 할당량이 같은 수량만큼 함께 준다 */
	SHIPMENT("출고");

	private final String label;

	StockTransactionType(String label) {
		this.label = label;
	}

	/** 이 거래가 할당된 재고를 쓰는가. 출고만 그렇다 (조정은 할당과 관계없이 실물을 맞춘다) */
	public boolean consumesAllocation() {
		return this == SHIPMENT;
	}

	public String label() {
		return label;
	}
}
