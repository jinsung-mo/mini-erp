package com.minierp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
import org.springframework.transaction.IllegalTransactionStateException;

import com.minierp.TestcontainersConfiguration;
import com.minierp.master.item.ItemRepository;
import com.minierp.master.warehouse.WarehouseRepository;
import com.minierp.purchase.PurchaseFixtures;

/**
 * 재고 API: 입고 → 원장·현재고, 실사 조정, 창고 사용 중지 규칙.
 * 발주 1: 줄 1 볼트 100개(EA), 줄 2 철판 25.5kg(KG), 창고 1 WH-MAIN. 승인까지 해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	StockLedger stockLedger;

	@Autowired
	ItemRepository itemRepository;

	@Autowired
	WarehouseRepository warehouseRepository;

	@BeforeEach
	void setUp() throws Exception {
		PurchaseFixtures.reset(jdbcTemplate);
		perform(post("/api/purchase-orders"), "buyer01", PurchaseFixtures.orderJson()).andExpect(status().isCreated());
		perform(post("/api/purchase-orders/1/submit"), "buyer01", "{\"version\": 0}").andExpect(status().isOk());
		perform(post("/api/purchase-orders/1/approve"), "manager01", "{\"version\": 1}").andExpect(status().isOk());
	}

	@Test
	void 입고하면_원장에_한_줄씩_쌓이고_현재고가_함께_늘어난다() throws Exception {
		receive(60, 10.5).andExpect(status().isCreated());
		receive(40, null).andExpect(status().isCreated());

		mockMvc.perform(get("/api/stocks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].item.code").value("BOLT-M10"))
				.andExpect(jsonPath("$[0].warehouse.code").value("WH-MAIN"))
				.andExpect(jsonPath("$[0].quantity").value(100))
				.andExpect(jsonPath("$[1].item.code").value("PLATE-T3"))
				.andExpect(jsonPath("$[1].quantity").value(10.5));

		// 최근 기록이 위. 직후 재고(balanceAfter)는 같은 품목 × 창고의 누적
		mockMvc.perform(get("/api/stock-transactions").param("itemId", "1"))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].type").value("GOODS_RECEIPT"))
				.andExpect(jsonPath("$[0].typeLabel").value("입고"))
				.andExpect(jsonPath("$[0].sourceNo").value("GR-000002"))
				.andExpect(jsonPath("$[0].quantity").value(40))
				.andExpect(jsonPath("$[0].balanceAfter").value(100))
				.andExpect(jsonPath("$[0].txnDate").value("2026-11-12"))
				.andExpect(jsonPath("$[0].actor").value("wh01"))
				.andExpect(jsonPath("$[1].sourceNo").value("GR-000001"))
				.andExpect(jsonPath("$[1].balanceAfter").value(60));

		// 원장의 근거는 입고 줄이다
		assertThat(jdbcTemplate.queryForList("select source_line_id from stock_transaction order by id", Long.class))
				.containsExactly(1L, 2L, 3L);
	}

	@Test
	void 입고가_거절되면_원장과_현재고도_바뀌지_않는다() throws Exception {
		receive(60, 30.0).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXCEEDS_REMAINING_QUANTITY"));

		assertThat(count("stock_transaction")).isZero();
		assertThat(count("stock")).isZero();
	}

	@Test
	void 원장은_입고_트랜잭션_밖에서_혼자_기록할_수_없다() {
		var movement = StockMovement.goodsReceipt(itemRepository.findById(1L).orElseThrow(),
				warehouseRepository.findById(1L).orElseThrow(), BigDecimal.TEN, LocalDate.of(2026, 11, 12),
				"GR-999999", 999L);

		assertThatThrownBy(() -> stockLedger.post(List.of(movement)))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(count("stock_transaction")).isZero();
	}

	@Test
	void 실사_수량을_넣으면_차이만큼_조정_한_줄이_쌓인다() throws Exception {
		receive(60, null).andExpect(status().isCreated());

		adjust(1, 1, "60", "58", "파손 2개 폐기")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("ADJUSTMENT"))
				.andExpect(jsonPath("$.sourceNo").value("ADJ-000001"))
				.andExpect(jsonPath("$.quantity").value(-2))
				.andExpect(jsonPath("$.balanceAfter").value(58))
				.andExpect(jsonPath("$.reason").value("파손 2개 폐기"))
				.andExpect(jsonPath("$.actor").value("wh01"));

		mockMvc.perform(get("/api/stocks").param("keyword", "bolt"))
				.andExpect(jsonPath("$[0].quantity").value(58));
		assertThat(jdbcTemplate.queryForObject("select sum(quantity) from stock_transaction where item_id = 1",
				BigDecimal.class)).isEqualByComparingTo("58");
	}

	@Test
	void 화면을_연_뒤_입고가_있었으면_조정은_409로_거절된다() throws Exception {
		receive(60, null).andExpect(status().isCreated());
		// 담당자는 장부 60을 보고 58개를 셌다. 그사이 40개가 더 들어왔다
		receive(40, null).andExpect(status().isCreated());

		adjust(1, 1, "60", "58", "파손")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
				.andExpect(jsonPath("$.detail", containsString("화면 60, 지금 100")));
		assertThat(count("stock_transaction where type = 'ADJUSTMENT'")).isZero();
	}

	@Test
	void 실사_수량이_장부와_같으면_조정하지_않는다() throws Exception {
		receive(60, null).andExpect(status().isCreated());

		adjust(1, 1, "60", "60.000", "정기 실사")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_STOCK_DIFFERENCE"));
	}

	@Test
	void 조정_사유가_없거나_수량이_음수면_400() throws Exception {
		adjust(1, 1, "0", "-1", " ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors", hasSize(2)));
	}

	@Test
	void 낱개_단위_품목의_실사_수량은_정수여야_한다() throws Exception {
		adjust(1, 1, "0", "2.5", "실사")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUANTITY_FOR_UNIT"));
	}

	@Test
	void 장부에_없던_품목이_실사에서_나오면_현재고_줄이_새로_생긴다() throws Exception {
		adjust(2, 1, "0", "3.25", "기초 재고 등록")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.quantity").value(3.25))
				.andExpect(jsonPath("$.balanceAfter").value(3.25));

		mockMvc.perform(get("/api/stocks"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].item.code").value("PLATE-T3"))
				.andExpect(jsonPath("$[0].updatedBy").value("wh01"));
	}

	@Test
	void 재고가_남은_창고는_사용_중지할_수_없고_0으로_맞추면_할_수_있다() throws Exception {
		receive(5, null).andExpect(status().isCreated());

		perform(post("/api/warehouses/1/deactivate"), "admin", "")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAREHOUSE_HAS_STOCK"));

		adjust(1, 1, "5", "0", "전량 타 창고 이관").andExpect(status().isCreated());

		// 0이 된 줄은 기본 조회에서 숨기고, includeZero로 볼 수 있다
		mockMvc.perform(get("/api/stocks")).andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/stocks").param("includeZero", "true"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].quantity").value(0));

		perform(post("/api/warehouses/1/deactivate"), "admin", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		// 사용 중지된 창고에는 조정도 할 수 없다
		adjust(1, 1, "0", "1", "실사").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INACTIVE_MASTER"));
	}

	@Test
	void 원장은_종류와_문서_번호와_기간으로_찾는다() throws Exception {
		receive(60, 10.5).andExpect(status().isCreated());
		adjust(1, 1, "60", "59", "분실").andExpect(status().isCreated());

		mockMvc.perform(get("/api/stock-transactions").param("type", "ADJUSTMENT"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].sourceNo").value("ADJ-000001"));
		mockMvc.perform(get("/api/stock-transactions").param("keyword", "gr-000001"))
				.andExpect(jsonPath("$", hasSize(2)));
		mockMvc.perform(get("/api/stock-transactions").param("fromDate", "2026-11-13"))
				.andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(get("/api/stock-transactions").param("toDate", "2026-11-12"))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	/** 볼트(발주 줄 1)와 철판(발주 줄 2)을 2026-11-12에 받는다. null이면 그 줄은 받지 않는다 */
	private ResultActions receive(int bolt, Double plate) throws Exception {
		String lines = "{\"purchaseOrderLineId\": 1, \"quantity\": %d}".formatted(bolt)
				+ (plate == null ? "" : ", {\"purchaseOrderLineId\": 2, \"quantity\": %s}".formatted(plate));
		return perform(post("/api/purchase-orders/1/receipts"), "wh01",
				"{\"receiptDate\": \"2026-11-12\", \"lines\": [%s]}".formatted(lines));
	}

	/** 조정일은 2026-11-13 */
	private ResultActions adjust(long itemId, long warehouseId, String book, String counted, String reason)
			throws Exception {
		return perform(post("/api/stock-adjustments"), "wh01", """
				{"itemId": %d, "warehouseId": %d, "bookQuantity": %s, "countedQuantity": %s,
				 "adjustDate": "2026-11-13", "reason": "%s"}
				""".formatted(itemId, warehouseId, book, counted, reason));
	}

	private long count(String tableAndCondition) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableAndCondition, Long.class);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String user, String body) throws Exception {
		return mockMvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
