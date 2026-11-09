package com.minierp.purchase;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 구매 테스트가 쓰는 기준정보. HTTP를 거치지 않고 SQL로 넣는다 (기준정보 API는 각자의 테스트가 검증한다).
 *
 * <pre>
 * 거래처 1 P0001 가나철강   공급처
 * 거래처 2 P0002 다라유통   고객만 (발주 불가)
 * 창고   1 WH-MAIN 본사 자재창고
 * 품목   1 BOLT-M10 육각볼트 EA
 * 품목   2 PLATE-T3 철판 KG (소수 수량 가능)
 * </pre>
 */
public final class PurchaseFixtures {

	private PurchaseFixtures() {
	}

	/** 구매·기준정보 테이블을 모두 비운다. 발주가 거래처·품목·창고를 FK로 참조하므로 한 문장으로 함께 비운다 */
	public static void reset(JdbcTemplate jdbc) {
		jdbc.execute("""
				truncate table goods_receipt_line, goods_receipt, purchase_order_event, purchase_order_line,
				               purchase_order, partner, warehouse, item restart identity
				""");
		jdbc.execute("alter sequence purchase_order_no_seq restart");
		jdbc.execute("alter sequence goods_receipt_no_seq restart");
		jdbc.update("""
				insert into partner (code, name, business_no, supplier, customer, active, created_at, created_by, updated_at, updated_by)
				values ('P0001', '가나철강', null, true, false, true, now(), 'system', now(), 'system'),
				       ('P0002', '다라유통', null, false, true, true, now(), 'system', now(), 'system')
				""");
		jdbc.update("""
				insert into warehouse (code, name, location, active, created_at, created_by, updated_at, updated_by)
				values ('WH-MAIN', '본사 자재창고', null, true, now(), 'system', now(), 'system')
				""");
		jdbc.update("""
				insert into item (code, name, spec, unit, active, created_at, created_by, updated_at, updated_by)
				values ('BOLT-M10', '육각볼트', 'M10x30', 'EA', true, now(), 'system', now(), 'system'),
				       ('PLATE-T3', '철판', 't3', 'KG', true, now(), 'system', now(), 'system')
				""");
	}

	/** 볼트 100개 × 120원, 철판 25.5kg × 3,000원 */
	public static String orderJson() {
		return """
				{"supplierId": 1, "warehouseId": 1, "orderDate": "2026-11-09", "dueDate": "2026-11-20",
				 "memo": "11월 정기 발주",
				 "lines": [{"itemId": 1, "quantity": 100, "unitPrice": 120},
				           {"itemId": 2, "quantity": 25.5, "unitPrice": 3000}]}
				""";
	}
}
