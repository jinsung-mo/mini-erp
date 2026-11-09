package com.minierp.purchase.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.minierp.TestcontainersConfiguration;
import com.minierp.purchase.PurchaseFixtures;

/**
 * 입고 API: 부분 입고 → 입고 완료, 남은 수량 초과, 승인 전 입고, 입고 후 취소 불가·마감, 원자성.
 * 발주 1: 줄 1 볼트 100개(EA), 줄 2 철판 25.5kg(KG). 발주 줄 id도 1, 2다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoodsReceiptApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() throws Exception {
		PurchaseFixtures.reset(jdbcTemplate);
		perform(post("/api/purchase-orders"), "buyer01", PurchaseFixtures.orderJson()).andExpect(status().isCreated());
	}

	@Test
	void 일부만_받으면_부분_입고가_되고_남은_수량이_줄어든다() throws Exception {
		approve();

		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 60}]}
				""")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/goods-receipts/1"))
				.andExpect(jsonPath("$.receiptNo").value("GR-000001"))
				.andExpect(jsonPath("$.purchaseOrder.orderNo").value("PO-000001"))
				.andExpect(jsonPath("$.warehouse.code").value("WH-MAIN"))
				.andExpect(jsonPath("$.lines[0].item.code").value("BOLT-M10"))
				.andExpect(jsonPath("$.createdBy").value("wh01"));

		mockMvc.perform(get("/api/purchase-orders/1"))
				.andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
				.andExpect(jsonPath("$.lines[0].receivedQuantity").value(60))
				.andExpect(jsonPath("$.lines[0].remainingQuantity").value(40))
				.andExpect(jsonPath("$.lines[1].receivedQuantity").value(0))
				.andExpect(jsonPath("$.events[3].type").value("RECEIVED"))
				.andExpect(jsonPath("$.events[3].reason").value("GR-000001"));
	}

	@Test
	void 나머지를_모두_받으면_입고_완료가_된다() throws Exception {
		approve();
		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 60}]}
				""").andExpect(status().isCreated());

		receive("""
				{"receiptDate": "2026-11-13", "lines": [{"purchaseOrderLineId": 1, "quantity": 40},
				                                        {"purchaseOrderLineId": 2, "quantity": 25.5}]}
				""").andExpect(status().isCreated());

		mockMvc.perform(get("/api/purchase-orders/1"))
				.andExpect(jsonPath("$.status").value("RECEIVED"))
				.andExpect(jsonPath("$.lines[0].remainingQuantity").value(0))
				.andExpect(jsonPath("$.lines[1].remainingQuantity").value(0));
		mockMvc.perform(get("/api/goods-receipts").param("purchaseOrderId", "1"))
				.andExpect(jsonPath("$[*].receiptNo", contains("GR-000002", "GR-000001")));
		assertReceivedMatchesReceipts();
	}

	@Test
	void 남은_수량보다_많이_받으면_409이고_앞_줄도_반영되지_않는다() throws Exception {
		approve();

		// 1번 줄은 정상, 2번 줄이 남은 수량(25.5)을 넘는다
		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 50},
				                                        {"purchaseOrderLineId": 2, "quantity": 30}]}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXCEEDS_REMAINING_QUANTITY"))
				.andExpect(jsonPath("$.detail", containsString("25.5")));

		mockMvc.perform(get("/api/purchase-orders/1"))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.lines[0].receivedQuantity").value(0));
		assertThat(jdbcTemplate.queryForObject("select count(*) from goods_receipt", Long.class)).isZero();
	}

	@Test
	void 승인되지_않은_발주는_입고할_수_없다() throws Exception {
		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 10}]}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
				.andExpect(jsonPath("$.detail", containsString("작성 중")));
	}

	@Test
	void 개수_단위는_소수로_입고할_수_없다() throws Exception {
		approve();

		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 0.5}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUANTITY_FOR_UNIT"));
	}

	@Test
	void 다른_발주의_줄이나_같은_줄_두_번은_받을_수_없다() throws Exception {
		approve();

		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 99, "quantity": 1}]}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 60},
				                                        {"purchaseOrderLineId": 1, "quantity": 60}]}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_LINE_ITEM"));
	}

	@Test
	void 입고일이_발주일보다_빠르면_400() throws Exception {
		approve();

		receive("""
				{"receiptDate": "2026-11-01", "lines": [{"purchaseOrderLineId": 1, "quantity": 10}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("입고일")));
	}

	@Test
	void 입고가_있으면_취소할_수_없고_마감하면_더_받을_수_없다() throws Exception {
		approve();
		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 60}]}
				""");
		long version = currentVersion();

		reason("cancel", version, "필요 없어짐")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

		reason("close", version, "철판 공급처 단종")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.lines[0].remainingQuantity").value(40));

		receive("""
				{"receiptDate": "2026-11-13", "lines": [{"purchaseOrderLineId": 1, "quantity": 10}]}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void 화면을_연_뒤_입고가_들어왔으면_옛_화면의_마감은_막힌다() throws Exception {
		approve();
		receive("""
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 60}]}
				""");
		long seenByManager = currentVersion();

		// 팀장이 화면을 보는 사이 창고에서 10개를 더 받았다 (상태는 부분 입고 그대로)
		receive("""
				{"receiptDate": "2026-11-13", "lines": [{"purchaseOrderLineId": 1, "quantity": 10}]}
				""").andExpect(status().isCreated());

		reason("close", seenByManager, "남은 수량 불필요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	void 입고_목록은_입고_번호와_발주_번호로_찾는다() throws Exception {
		approve();
		receive("""
				{"receiptDate": "2026-11-12", "memo": "1차분", "lines": [{"purchaseOrderLineId": 1, "quantity": 60}]}
				""");

		mockMvc.perform(get("/api/goods-receipts").param("keyword", "po-000001"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].memo").value("1차분"))
				.andExpect(jsonPath("$[0].supplier.name").value("가나철강"));
		mockMvc.perform(get("/api/goods-receipts").param("keyword", "GR-000002"))
				.andExpect(jsonPath("$", hasSize(0)));
	}

	/** 정합성: 발주 줄의 받은 수량 = 그 줄을 가리키는 입고 줄 수량의 합 */
	private void assertReceivedMatchesReceipts() {
		var rows = jdbcTemplate.queryForList("""
				select l.id, l.received_quantity, coalesce(sum(g.quantity), 0) as receipt_sum
				from purchase_order_line l left join goods_receipt_line g on g.purchase_order_line_id = l.id
				group by l.id, l.received_quantity
				""");
		assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat((BigDecimal) row.get("received_quantity"))
				.isEqualByComparingTo((BigDecimal) row.get("receipt_sum")));
	}

	private void approve() throws Exception {
		perform(post("/api/purchase-orders/1/submit"), "buyer01", "{\"version\": 0}").andExpect(status().isOk());
		perform(post("/api/purchase-orders/1/approve"), "manager01", "{\"version\": 1}").andExpect(status().isOk());
	}

	private long currentVersion() throws Exception {
		String body = mockMvc.perform(get("/api/purchase-orders/1")).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.version")).longValue();
	}

	private ResultActions receive(String body) throws Exception {
		return perform(post("/api/purchase-orders/1/receipts"), "wh01", body);
	}

	private ResultActions reason(String action, long version, String reason) throws Exception {
		return perform(post("/api/purchase-orders/1/" + action), "manager01",
				"{\"version\": %d, \"reason\": \"%s\"}".formatted(version, reason));
	}

	private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String user, String body) throws Exception {
		return mockMvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
