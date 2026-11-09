package com.minierp.purchase.order;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.minierp.TestcontainersConfiguration;
import com.minierp.purchase.PurchaseFixtures;

/**
 * 발주 API: 작성 → 승인 요청 → 승인/반려 → 취소, 직무 분리, 낙관적 락, 기준정보 사용 여부.
 * 작업자: buyer01(구매 담당)이 만들고 manager01(구매 팀장)이 승인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PurchaseOrderApiTest {

	static final String BUYER = "buyer01";
	static final String MANAGER = "manager01";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		PurchaseFixtures.reset(jdbcTemplate);
	}

	@Test
	void 등록하면_작성_중_상태로_번호를_매기고_금액을_계산한다() throws Exception {
		create()
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/purchase-orders/1"))
				.andExpect(jsonPath("$.orderNo").value("PO-000001"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.supplier.code").value("P0001"))
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].lineNo").value(1))
				.andExpect(jsonPath("$.lines[0].amount").value(12000))
				// 25.5kg × 3,000원 = 76,500원. double로 계산해도 이 값은 맞지만, 금액은 항상 BigDecimal로 계산한다
				.andExpect(jsonPath("$.lines[1].amount").value(76500))
				.andExpect(jsonPath("$.totalAmount").value(88500))
				.andExpect(jsonPath("$.lines[1].remainingQuantity").value(25.5))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.events[*].type", contains("CREATED")))
				.andExpect(jsonPath("$.createdBy").value(BUYER));
	}

	@Test
	void 공급처가_아닌_거래처에는_발주할_수_없다() throws Exception {
		perform(post("/api/purchase-orders"), BUYER, PurchaseFixtures.orderJson().replace("\"supplierId\": 1", "\"supplierId\": 2"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_A_SUPPLIER"))
				.andExpect(jsonPath("$.detail", containsString("P0002")));
	}

	@Test
	void 개수_단위_품목에_소수_수량을_넣으면_400() throws Exception {
		perform(post("/api/purchase-orders"), BUYER, """
				{"supplierId": 1, "warehouseId": 1, "orderDate": "2026-11-09",
				 "lines": [{"itemId": 1, "quantity": 2.5, "unitPrice": 120}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUANTITY_FOR_UNIT"));
	}

	@Test
	void 같은_품목을_두_줄로_넣으면_400() throws Exception {
		perform(post("/api/purchase-orders"), BUYER, """
				{"supplierId": 1, "warehouseId": 1, "orderDate": "2026-11-09",
				 "lines": [{"itemId": 1, "quantity": 10, "unitPrice": 120},
				           {"itemId": 1, "quantity": 5, "unitPrice": 120}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DUPLICATE_LINE_ITEM"));
	}

	@Test
	void 줄의_입력_오류는_몇_번째_줄의_어느_항목인지_알려_준다() throws Exception {
		perform(post("/api/purchase-orders"), BUYER, """
				{"supplierId": 1, "warehouseId": 1, "orderDate": "2026-11-09",
				 "lines": [{"itemId": 1, "quantity": 10, "unitPrice": 120},
				           {"itemId": 2, "quantity": 0, "unitPrice": 3000}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[0].field").value("lines[1].quantity"));
	}

	@Test
	void 납기_요청일이_발주일보다_빠르면_400() throws Exception {
		perform(post("/api/purchase-orders"), BUYER, PurchaseFixtures.orderJson().replace("2026-11-20", "2026-11-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.detail", containsString("납기 요청일")));
	}

	@Test
	void 작성_중에는_줄을_바꿔_저장할_수_있고_버전이_오른다() throws Exception {
		create().andExpect(status().isCreated());

		// 2줄 → 1줄, 1번 줄의 품목을 볼트에서 철판으로 바꾼다
		perform(put("/api/purchase-orders/1"), BUYER, """
				{"version": 0, "supplierId": 1, "warehouseId": 1, "orderDate": "2026-11-09",
				 "lines": [{"itemId": 2, "quantity": 10, "unitPrice": 2900}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines", hasSize(1)))
				.andExpect(jsonPath("$.lines[0].lineNo").value(1))
				.andExpect(jsonPath("$.lines[0].item.code").value("PLATE-T3"))
				.andExpect(jsonPath("$.totalAmount").value(29000))
				.andExpect(jsonPath("$.memo").doesNotExist())
				.andExpect(jsonPath("$.version").value(greaterThan(0)));
	}

	@Test
	void 줄만_바꿔도_버전이_올라서_옛_화면의_저장을_막는다() throws Exception {
		create().andExpect(status().isCreated());

		// 머리 값은 그대로 두고 수량만 바꾼다
		String linesOnly = PurchaseFixtures.orderJson().replace("{\"supplierId\"", "{\"version\": 0, \"supplierId\"")
				.replace("\"quantity\": 100", "\"quantity\": 200");
		perform(put("/api/purchase-orders/1"), BUYER, linesOnly)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].quantity").value(200))
				.andExpect(jsonPath("$.version").value(greaterThan(0)));

		// 버전 0을 보고 있던 다른 화면이 저장하면 막힌다
		perform(put("/api/purchase-orders/1"), MANAGER, linesOnly)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	void 작성자는_승인할_수_없고_다른_사람이_승인한다() throws Exception {
		create();
		action("submit", BUYER, 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));

		action("approve", BUYER, 1)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));

		action("approve", MANAGER, 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.events[*].type", contains("CREATED", "SUBMITTED", "APPROVED")))
				.andExpect(jsonPath("$.events[2].actor").value(MANAGER))
				.andExpect(jsonPath("$.createdBy").value(BUYER))
				.andExpect(jsonPath("$.updatedBy").value(MANAGER));
	}

	@Test
	void 남이_만든_발주라도_본인이_승인_요청했으면_승인할_수_없다() throws Exception {
		create();
		action("submit", MANAGER, 0).andExpect(status().isOk());

		action("approve", MANAGER, 1)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));
	}

	@Test
	void 반려하면_사유를_남기고_작성_중으로_돌아가_다시_고칠_수_있다() throws Exception {
		create();
		action("submit", BUYER, 0);

		reason("reject", MANAGER, 1, " ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("reason"));

		reason("reject", MANAGER, 1, "단가가 지난달보다 높습니다")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.events[2].type").value("REJECTED"))
				.andExpect(jsonPath("$.events[2].reason").value("단가가 지난달보다 높습니다"));

		perform(put("/api/purchase-orders/1"), BUYER, PurchaseFixtures.orderJson()
				.replace("{\"supplierId\"", "{\"version\": 2, \"supplierId\"").replace("\"unitPrice\": 120", "\"unitPrice\": 110"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].unitPrice").value(110));
	}

	@Test
	void 승인_요청한_발주는_고칠_수_없다() throws Exception {
		create();
		action("submit", BUYER, 0);

		perform(put("/api/purchase-orders/1"), BUYER, PurchaseFixtures.orderJson()
				.replace("{\"supplierId\"", "{\"version\": 1, \"supplierId\""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
	}

	@Test
	void 취소한_발주는_다시_승인_요청할_수_없다() throws Exception {
		create();
		reason("cancel", BUYER, 0, "공급처 변경").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));

		action("submit", BUYER, 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
				.andExpect(jsonPath("$.detail", containsString("취소")));
	}

	@Test
	void 승인_전에_품목이_사용_중지되면_승인_요청에서_막는다() throws Exception {
		create();
		jdbcTemplate.update("update item set active = false where code = 'PLATE-T3'");

		action("submit", BUYER, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INACTIVE_MASTER"))
				.andExpect(jsonPath("$.detail", containsString("PLATE-T3")));
	}

	@Test
	void 진행_중인_발주가_있는_공급처는_사용_중지할_수_없다() throws Exception {
		create();

		mockMvc.perform(post("/api/partners/1/deactivate").header("X-User", "admin"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PARTNER_HAS_OPEN_ORDERS"));

		reason("cancel", BUYER, 0, "발주 철회");
		mockMvc.perform(post("/api/partners/1/deactivate").header("X-User", "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void 상태를_여러_개_골라_조회한다() throws Exception {
		create();
		create();
		create();
		action("submit", BUYER, 0, 2);
		action("approve", MANAGER, 1, 2);
		reason("cancel", BUYER, 0, 3, "중복 작성");

		mockMvc.perform(get("/api/purchase-orders").param("status", "DRAFT", "APPROVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].orderNo", contains("PO-000002", "PO-000001")))
				.andExpect(jsonPath("$[0].totalAmount").value(88500))
				.andExpect(jsonPath("$[0].lineCount").value(2));

		mockMvc.perform(get("/api/purchase-orders").param("keyword", "가나"))
				.andExpect(jsonPath("$", hasSize(3)));
	}

	private ResultActions create() throws Exception {
		return perform(post("/api/purchase-orders"), BUYER, PurchaseFixtures.orderJson());
	}

	private ResultActions action(String action, String user, long version) throws Exception {
		return action(action, user, version, 1);
	}

	private ResultActions action(String action, String user, long version, long id) throws Exception {
		return perform(post("/api/purchase-orders/" + id + "/" + action), user, "{\"version\": " + version + "}");
	}

	private ResultActions reason(String action, String user, long version, String reason) throws Exception {
		return reason(action, user, version, 1, reason);
	}

	private ResultActions reason(String action, String user, long version, long id, String reason) throws Exception {
		return perform(post("/api/purchase-orders/" + id + "/" + action), user,
				"{\"version\": %d, \"reason\": \"%s\"}".formatted(version, reason));
	}

	private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String user, String body) throws Exception {
		return mockMvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
