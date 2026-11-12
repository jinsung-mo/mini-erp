package com.minierp.sales;

import org.springframework.jdbc.core.JdbcTemplate;

import com.minierp.purchase.PurchaseFixtures;

/**
 * 판매 테스트가 쓰는 기준정보와 재고. 기준정보는 구매와 같고({@link PurchaseFixtures}), 재고는 원장을 거쳐 넣는다.
 *
 * <pre>
 * 거래처 2 P0002 다라유통  고객 (거래처 1 가나철강은 공급처만이라 수주 불가)
 * 창고   1 WH-MAIN
 * 품목   1 BOLT-M10 EA, 품목 2 PLATE-T3 KG
 * </pre>
 */
public final class SalesFixtures {

	private SalesFixtures() {
	}

	public static void reset(JdbcTemplate jdbc) {
		PurchaseFixtures.reset(jdbc);
	}

	/**
	 * 현재고를 넣는다. 실사 조정 API 대신 SQL로 원장 한 줄과 현재고를 함께 넣는다 (원장 합계 = 현재고가 유지되게).
	 * 판매 테스트는 "재고가 이미 있다"에서 출발하므로, 재고를 만드는 경로는 재고 테스트가 검증한다.
	 */
	public static void stock(JdbcTemplate jdbc, long itemId, String quantity) {
		jdbc.update("""
				insert into stock_transaction (item_id, warehouse_id, type, quantity, balance_after, txn_date, source_no,
				                               reason, occurred_at, actor)
				values (?, 1, 'ADJUSTMENT', ?::numeric, ?::numeric, date '2026-11-10', 'ADJ-TEST', '기초 재고', now(), 'system')
				""", itemId, quantity, quantity);
		jdbc.update("""
				insert into stock (item_id, warehouse_id, quantity, created_at, created_by, updated_at, updated_by)
				values (?, 1, ?::numeric, now(), 'system', now(), 'system')
				""", itemId, quantity);
	}

	/** 다라유통에 볼트 boltQty개 × 500원, 철판 plateQty kg × 4,000원 (0이면 그 줄은 뺀다) */
	public static String orderJson(String boltQty, String plateQty) {
		StringBuilder lines = new StringBuilder();
		if (!"0".equals(boltQty)) {
			lines.append("{\"itemId\": 1, \"quantity\": %s, \"unitPrice\": 500}".formatted(boltQty));
		}
		if (!"0".equals(plateQty)) {
			if (!lines.isEmpty()) {
				lines.append(", ");
			}
			lines.append("{\"itemId\": 2, \"quantity\": %s, \"unitPrice\": 4000}".formatted(plateQty));
		}
		return """
				{"customerId": 2, "warehouseId": 1, "orderDate": "2026-11-11", "dueDate": "2026-11-18",
				 "memo": "11월 납품", "lines": [%s]}
				""".formatted(lines);
	}
}
