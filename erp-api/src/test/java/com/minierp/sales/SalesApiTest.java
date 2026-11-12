package com.minierp.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.minierp.TestcontainersConfiguration;

/**
 * 판매 API: 수주 → 확정(할당) → 출고, 취소·마감의 할당 해제, 실사 분실 뒤의 출고.
 * 기초 재고: WH-MAIN에 볼트 100개, 철판 30kg.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SalesApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		SalesFixtures.reset(jdbcTemplate);
		SalesFixtures.stock(jdbcTemplate, 1, "100");
		SalesFixtures.stock(jdbcTemplate, 2, "30");
	}

	@Test
	void 수주를_만들면_작성_중이고_재고는_그대로다() throws Exception {
		createOrder("80", "10.5")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderNo").value("SO-000001"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.customer.code").value("P0002"))
				.andExpect(jsonPath("$.totalAmount").value(82000))
				.andExpect(jsonPath("$.lines[0].shippedQuantity").value(0))
				.andExpect(jsonPath("$.lines[0].remainingQuantity").value(80))
				.andExpect(jsonPath("$.events[0].type").value("CREATED"))
				.andExpect(jsonPath("$.events[0].actor").value("sales01"));

		assertThat(allocated(1)).isEqualByComparingTo("0");
	}

	@Test
	void 고객_역할이_없는_거래처에는_수주할_수_없다() throws Exception {
		perform(post("/api/sales-orders"), "sales01", SalesFixtures.orderJson("10", "0").replace("\"customerId\": 2", "\"customerId\": 1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_A_CUSTOMER"));
	}

	@Test
	void 확정하면_줄_수량만큼_할당되고_가용_재고가_준다() throws Exception {
		createOrder("80", "10.5");

		confirm(1, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.events[1].type").value("CONFIRMED"));

		mockMvc.perform(get("/api/stocks"))
				.andExpect(jsonPath("$[0].item.code").value("BOLT-M10"))
				.andExpect(jsonPath("$[0].quantity").value(100))
				.andExpect(jsonPath("$[0].allocatedQuantity").value(80))
				.andExpect(jsonPath("$[0].availableQuantity").value(20))
				.andExpect(jsonPath("$[1].allocatedQuantity").value(10.5))
				.andExpect(jsonPath("$[1].availableQuantity").value(19.5));
		// 할당은 약속이지 실물 이동이 아니라서 원장에는 아무것도 쌓이지 않는다
		assertThat(count("stock_transaction where type <> 'ADJUSTMENT'")).isZero();
	}

	@Test
	void 가용_재고가_모자라면_확정은_409이고_모자란_품목을_모두_알려준다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0).andExpect(status().isOk());

		// 현재고는 100으로 충분해 보이지만, 80은 이미 약속했다. 가용은 20
		createOrder("30", "40");
		confirm(2, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
				.andExpect(jsonPath("$.detail").value(allOf(
						containsString("BOLT-M10(육각볼트) 가용 20, 요청 30"),
						containsString("PLATE-T3(철판) 가용 30, 요청 40"))));

		// 확정·이력·할당이 모두 롤백된다
		mockMvc.perform(get("/api/sales-orders/2"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.events", hasSize(1)))
				.andExpect(jsonPath("$.version").value(0));
		assertThat(allocated(1)).isEqualByComparingTo("80");
		assertThat(allocated(2)).isEqualByComparingTo("0");
	}

	@Test
	void 출고하면_현재고와_할당이_함께_줄고_원장에_출고가_쌓인다() throws Exception {
		createOrder("80", "10.5");
		confirm(1, 0);

		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 30}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shipmentNo").value("SH-000001"))
				.andExpect(jsonPath("$.salesOrder.orderNo").value("SO-000001"))
				.andExpect(jsonPath("$.customer.code").value("P0002"))
				.andExpect(jsonPath("$.lines[0].quantity").value(30));

		mockMvc.perform(get("/api/sales-orders/1"))
				.andExpect(jsonPath("$.status").value("PARTIALLY_SHIPPED"))
				.andExpect(jsonPath("$.lines[0].shippedQuantity").value(30))
				.andExpect(jsonPath("$.lines[0].remainingQuantity").value(50))
				.andExpect(jsonPath("$.events[2].type").value("SHIPPED"))
				.andExpect(jsonPath("$.events[2].reason").value("SH-000001"));
		// 현재고 100 → 70, 할당 80 → 50. 가용은 20 그대로 (약속한 몫이 나갔을 뿐)
		mockMvc.perform(get("/api/stocks").param("keyword", "bolt"))
				.andExpect(jsonPath("$[0].quantity").value(70))
				.andExpect(jsonPath("$[0].allocatedQuantity").value(50))
				.andExpect(jsonPath("$[0].availableQuantity").value(20));
		mockMvc.perform(get("/api/stock-transactions").param("type", "SHIPMENT"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].typeLabel").value("출고"))
				.andExpect(jsonPath("$[0].quantity").value(-30))
				.andExpect(jsonPath("$[0].balanceAfter").value(70))
				.andExpect(jsonPath("$[0].sourceNo").value("SH-000001"))
				.andExpect(jsonPath("$[0].actor").value("wh01"));

		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 50}, {\"salesOrderLineId\": 2, \"quantity\": 10.5}")
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/sales-orders/1")).andExpect(jsonPath("$.status").value("SHIPPED"));
		assertThat(allocated(1)).isEqualByComparingTo("0");
		assertThat(allocated(2)).isEqualByComparingTo("0");
		assertThat(quantity(1)).isEqualByComparingTo("20");
		assertLedgerMatchesStock();
	}

	@Test
	void 남은_수량보다_많이_출고하면_409이고_아무것도_바뀌지_않는다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0);

		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 81}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXCEEDS_REMAINING_QUANTITY"));

		assertThat(quantity(1)).isEqualByComparingTo("100");
		assertThat(allocated(1)).isEqualByComparingTo("80");
		assertThat(count("shipment")).isZero();
	}

	@Test
	void 확정하지_않은_수주는_출고할_수_없다() throws Exception {
		createOrder("10", "0");

		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 10}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void 확정한_수주를_취소하면_할당이_전부_풀린다() throws Exception {
		createOrder("80", "10.5");
		confirm(1, 0);

		reason("cancel", 1, "고객 주문 철회")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELED"))
				.andExpect(jsonPath("$.events[2].reason").value("고객 주문 철회"));

		assertThat(allocated(1)).isEqualByComparingTo("0");
		assertThat(allocated(2)).isEqualByComparingTo("0");
	}

	@Test
	void 작성_중인_수주의_취소는_재고와_관계없다() throws Exception {
		createOrder("80", "0");

		reason("cancel", 1, "중복 입력").andExpect(status().isOk());

		assertThat(allocated(1)).isEqualByComparingTo("0");
	}

	@Test
	void 출고가_있으면_취소_대신_마감하고_남은_할당만_풀린다() throws Exception {
		createOrder("80", "10.5");
		confirm(1, 0);
		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 30}");

		reason("cancel", 1, "고객 요청")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

		reason("close", 1, "고객이 잔량 취소")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		// 볼트: 현재고 70, 할당 50 → 0. 철판: 할당 10.5 → 0
		assertThat(quantity(1)).isEqualByComparingTo("70");
		assertThat(allocated(1)).isEqualByComparingTo("0");
		assertThat(allocated(2)).isEqualByComparingTo("0");
	}

	@Test
	void 확정한_수주는_고칠_수_없다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0);

		perform(put("/api/sales-orders/1"), "sales01",
				SalesFixtures.orderJson("90", "0").replace("{\"customerId\"", "{\"version\": %d, \"customerId\"".formatted(version(1))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void 화면을_연_뒤_다른_사람이_확정했으면_두_번째_확정은_409다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0).andExpect(status().isOk());

		confirm(1, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

		assertThat(allocated(1)).isEqualByComparingTo("80");
	}

	/**
	 * 할당량에 DB 제약(allocated <= quantity)을 걸지 않은 이유.
	 * 볼트 80개를 약속해 둔 상태에서 실사로 50개 분실이 확인됐다. 조정은 막히지 않고, 가용 재고가 -30으로 드러난다.
	 * 장부상 할당은 80이지만 창고에는 50뿐이라서 60개 출고는 원장 기록에서 막힌다.
	 */
	@Test
	void 실사_분실로_현재고가_할당보다_작아지면_가용이_음수로_보이고_없는_만큼은_출고할_수_없다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0);

		perform(post("/api/stock-adjustments"), "wh01", """
				{"itemId": 1, "warehouseId": 1, "bookQuantity": 100, "countedQuantity": 50,
				 "adjustDate": "2026-11-12", "reason": "분실"}
				""").andExpect(status().isCreated());

		mockMvc.perform(get("/api/stocks").param("keyword", "bolt"))
				.andExpect(jsonPath("$[0].quantity").value(50))
				.andExpect(jsonPath("$[0].allocatedQuantity").value(80))
				.andExpect(jsonPath("$[0].availableQuantity").value(-30));

		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 60}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		assertThat(count("shipment")).isZero();

		// 있는 만큼 내보내고 나머지는 마감하면 할당도 0으로 정리된다
		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 50}").andExpect(status().isCreated());
		reason("close", 1, "재고 분실로 잔량 마감").andExpect(status().isOk());
		assertThat(quantity(1)).isEqualByComparingTo("0");
		assertThat(allocated(1)).isEqualByComparingTo("0");
	}

	@Test
	void 진행_중인_수주가_있는_고객은_사용_중지할_수_없다() throws Exception {
		createOrder("10", "0");

		perform(post("/api/partners/2/deactivate"), "admin", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PARTNER_HAS_OPEN_ORDERS"))
				.andExpect(jsonPath("$.detail").value(containsString("진행 중인 수주가 1건")));
	}

	@Test
	void 할당이_남은_창고는_현재고가_0이어도_사용_중지할_수_없다() throws Exception {
		createOrder("80", "0");
		confirm(1, 0);
		perform(post("/api/stock-adjustments"), "wh01", """
				{"itemId": 1, "warehouseId": 1, "bookQuantity": 100, "countedQuantity": 0,
				 "adjustDate": "2026-11-12", "reason": "전량 분실"}
				""");
		perform(post("/api/stock-adjustments"), "wh01", """
				{"itemId": 2, "warehouseId": 1, "bookQuantity": 30, "countedQuantity": 0,
				 "adjustDate": "2026-11-12", "reason": "전량 분실"}
				""");

		perform(post("/api/warehouses/1/deactivate"), "admin", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAREHOUSE_HAS_STOCK"));
	}

	@Test
	void 할당량은_진행_중인_수주_줄의_남은_수량_합과_같다() throws Exception {
		createOrder("30", "5");
		createOrder("20", "0");
		createOrder("40", "0");
		confirm(1, 0);
		confirm(2, 0);
		confirm(3, 0);
		ship(1, "{\"salesOrderLineId\": 1, \"quantity\": 10}");
		reason("cancel", 2, "철회");

		assertThat(allocated(1)).isEqualByComparingTo("60");
		// 6단계 정합성 배치가 쓸 검증 쿼리: 차이가 나는 품목 × 창고가 없어야 한다
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from stock s
				left join (select l.item_id, o.warehouse_id, sum(l.quantity - l.shipped_quantity) as remaining
				           from sales_order_line l join sales_order o on o.id = l.sales_order_id
				           where o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
				           group by l.item_id, o.warehouse_id) r
				  on r.item_id = s.item_id and r.warehouse_id = s.warehouse_id
				where s.allocated_quantity <> coalesce(r.remaining, 0)
				""", Long.class)).isZero();
	}

	private ResultActions createOrder(String boltQty, String plateQty) throws Exception {
		return perform(post("/api/sales-orders"), "sales01", SalesFixtures.orderJson(boltQty, plateQty));
	}

	private ResultActions confirm(long id, long version) throws Exception {
		return perform(post("/api/sales-orders/%d/confirm".formatted(id)), "sales01",
				"{\"version\": %d}".formatted(version));
	}

	/** 취소·마감은 지금 버전으로 보낸다 (화면을 새로 연 상태) */
	private ResultActions reason(String action, long id, String reason) throws Exception {
		return perform(post("/api/sales-orders/%d/%s".formatted(id, action)), "sales01",
				"{\"version\": %d, \"reason\": \"%s\"}".formatted(version(id), reason));
	}

	private long version(long id) {
		return jdbcTemplate.queryForObject("select version from sales_order where id = ?", Long.class, id);
	}

	private ResultActions ship(long id, String lines) throws Exception {
		return perform(post("/api/sales-orders/%d/shipments".formatted(id)), "wh01",
				"{\"shipDate\": \"2026-11-12\", \"lines\": [%s]}".formatted(lines));
	}

	private BigDecimal allocated(long itemId) {
		return jdbcTemplate.queryForObject(
				"select coalesce(sum(allocated_quantity), 0) from stock where item_id = ?", BigDecimal.class, itemId);
	}

	private BigDecimal quantity(long itemId) {
		return jdbcTemplate.queryForObject("select quantity from stock where item_id = ?", BigDecimal.class, itemId);
	}

	private long count(String tableAndWhere) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableAndWhere, Long.class);
	}

	private void assertLedgerMatchesStock() {
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from stock s
				where s.quantity <> (select sum(t.quantity) from stock_transaction t
				                     where t.item_id = s.item_id and t.warehouse_id = s.warehouse_id)
				""", Long.class)).isZero();
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String user, String body) throws Exception {
		return mockMvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
