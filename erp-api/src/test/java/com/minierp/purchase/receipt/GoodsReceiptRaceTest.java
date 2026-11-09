package com.minierp.purchase.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.minierp.TestcontainersConfiguration;
import com.minierp.purchase.PurchaseFixtures;

/**
 * 트러블슈팅 재현: "같은 발주에 입고가 동시에 들어오면 발주보다 많이 받는다"
 *
 * 볼트 100개 발주에 20개짜리 입고 10건을 동시에 보낸다. 정답은 5건 성공, 5건 실패(남은 수량 초과)이고,
 * 발주 줄의 받은 수량(100)과 입고 줄 수량의 합(100)이 같아야 한다.
 * 잠금 없이 "조회 → 남은 수량 검사 → 더하기"를 하면 여러 요청이 같은 "받은 수량"을 읽고 각자 더해서 덮어쓴다 (lost update).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GoodsReceiptRaceTest {

	private static final int REQUESTS = 10;

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\":\"([A-Z_]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@BeforeEach
	void setUp() throws Exception {
		PurchaseFixtures.reset(jdbcTemplate);
		send("/api/purchase-orders", "buyer01", PurchaseFixtures.orderJson());
		send("/api/purchase-orders/1/submit", "buyer01", "{\"version\": 0}");
		send("/api/purchase-orders/1/approve", "manager01", "{\"version\": 1}");
	}

	@Test
	void 같은_발주에_입고_10건이_동시에_들어와도_발주_수량을_넘지_않는다() throws Exception {
		HttpRequest receipt = request("/api/purchase-orders/1/receipts", "wh01", """
				{"receiptDate": "2026-11-12", "lines": [{"purchaseOrderLineId": 1, "quantity": 20}]}
				""");

		CountDownLatch ready = new CountDownLatch(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
		List<Future<HttpResponse<String>>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < REQUESTS; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return client.send(receipt, HttpResponse.BodyHandlers.ofString());
				}));
			}
			ready.await(5, TimeUnit.SECONDS);
			start.countDown();

			Map<String, Integer> outcome = new TreeMap<>();
			for (Future<HttpResponse<String>> future : futures) {
				outcome.merge(describe(future.get(30, TimeUnit.SECONDS)), 1, Integer::sum);
			}
			BigDecimal received = jdbcTemplate.queryForObject(
					"select received_quantity from purchase_order_line where id = 1", BigDecimal.class);
			BigDecimal receiptSum = jdbcTemplate.queryForObject(
					"select coalesce(sum(quantity), 0) from goods_receipt_line where purchase_order_line_id = 1",
					BigDecimal.class);
			System.out.printf("[동시 입고 결과] %s, 받은 수량=%s, 입고 줄 합계=%s%n", outcome, received, receiptSum);

			assertThat(outcome).containsEntry("201", 5).containsEntry("409 EXCEEDS_REMAINING_QUANTITY", 5);
			assertThat(received).isEqualByComparingTo("100");
			assertThat(receiptSum).isEqualByComparingTo("100");
		} finally {
			pool.shutdownNow();
		}
	}

	private void send(String path, String user, String body) throws Exception {
		HttpResponse<String> response = client.send(request(path, user, body), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
	}

	private HttpRequest request(String path, String user, String body) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("X-User", user)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
	}

	private static String describe(HttpResponse<String> response) {
		if (response.statusCode() == 201) {
			return "201";
		}
		Matcher matcher = ERROR_CODE.matcher(response.body());
		return response.statusCode() + " " + (matcher.find() ? matcher.group(1) : "?");
	}
}
