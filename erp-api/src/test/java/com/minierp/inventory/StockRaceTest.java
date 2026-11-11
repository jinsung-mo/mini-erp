package com.minierp.inventory;

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
 * 트러블슈팅 재현: "서로 다른 발주의 입고가 동시에 같은 품목의 재고를 바꾼다"
 *
 * 2단계의 발주 행 잠금은 같은 발주의 입고만 줄 세운다. 발주가 다르면 잠금도 달라서 함께 지나가고, 둘 다 같은 현재고 줄을 바꾼다.
 * 발주 10건(각각 볼트 100개 + 철판 25.5kg)을 승인해 두고 10건을 동시에 전량 입고한다. 절반은 줄 순서를 뒤집어 보낸다
 * (철판 → 볼트). 정답은 10건 모두 성공, 볼트 1,000개, 철판 255kg, 원장 합계 = 현재고, 직후 재고 값이 겹치지 않음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class StockRaceTest {

	private static final int ORDERS = 10;

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\":\"([A-Z_]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@BeforeEach
	void setUp() throws Exception {
		PurchaseFixtures.reset(jdbcTemplate);
		for (int po = 1; po <= ORDERS; po++) {
			send("/api/purchase-orders", "buyer01", PurchaseFixtures.orderJson());
			send("/api/purchase-orders/%d/submit".formatted(po), "buyer01", "{\"version\": 0}");
			send("/api/purchase-orders/%d/approve".formatted(po), "manager01", "{\"version\": 1}");
		}
	}

	@Test
	void 다른_발주의_입고_10건이_동시에_같은_품목을_받아도_재고가_맞는다() throws Exception {
		List<HttpRequest> receipts = new ArrayList<>();
		for (int po = 1; po <= ORDERS; po++) {
			long bolt = po * 2L - 1;
			long plate = po * 2L;
			String first = "{\"purchaseOrderLineId\": %d, \"quantity\": 100}".formatted(bolt);
			String second = "{\"purchaseOrderLineId\": %d, \"quantity\": 25.5}".formatted(plate);
			String lines = po % 2 == 0 ? second + ", " + first : first + ", " + second;
			receipts.add(request("/api/purchase-orders/%d/receipts".formatted(po), "wh01",
					"{\"receiptDate\": \"2026-11-12\", \"lines\": [%s]}".formatted(lines)));
		}

		CountDownLatch ready = new CountDownLatch(ORDERS);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(ORDERS);
		List<Future<HttpResponse<String>>> futures = new ArrayList<>();
		try {
			for (HttpRequest receipt : receipts) {
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
				outcome.merge(describe(future.get(60, TimeUnit.SECONDS)), 1, Integer::sum);
			}
			BigDecimal boltStock = stock(1);
			BigDecimal plateStock = stock(2);
			BigDecimal boltLedger = jdbcTemplate.queryForObject(
					"select coalesce(sum(quantity), 0) from stock_transaction where item_id = 1", BigDecimal.class);
			List<BigDecimal> boltBalances = jdbcTemplate.queryForList(
					"select balance_after from stock_transaction where item_id = 1 order by id", BigDecimal.class);
			System.out.printf("[동시 입고 재고] %s, 볼트 현재고=%s 원장합=%s, 철판 현재고=%s, 볼트 직후재고=%s%n", outcome,
					boltStock, boltLedger, plateStock, boltBalances);

			assertThat(outcome).containsExactly(Map.entry("201", ORDERS));
			assertThat(boltStock).isEqualByComparingTo("1000");
			assertThat(plateStock).isEqualByComparingTo("255");
			assertThat(boltLedger).isEqualByComparingTo(boltStock);
			// 줄을 세워 기록했다면 직후 재고는 100, 200, ..., 1000이 한 번씩 나온다
			assertThat(boltBalances).map(BigDecimal::intValue)
					.containsExactly(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000);
		} finally {
			pool.shutdownNow();
		}
	}

	private BigDecimal stock(long itemId) {
		return jdbcTemplate.queryForObject("select quantity from stock where item_id = ? and warehouse_id = 1",
				BigDecimal.class, itemId);
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
