package com.minierp.sales;

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

/**
 * 동시 확정: 가용 재고 검사와 할당이 같은 잠금 안에 있어야 하는 이유.
 *
 * ① 서로 다른 수주 10건(각 볼트 30개)을 볼트 100개로 동시에 확정한다. 정답은 3건 성공, 7건 INSUFFICIENT_STOCK, 할당 90.
 *    잠그지 않고 "가용 확인 → 할당"을 하면 여러 건이 같은 가용 100을 보고 함께 통과해 100개보다 많이 약속한다 (over-promise).
 * ② 같은 수주 하나를 같은 버전으로 10번 동시에 확정한다. 정답은 1건 성공, 할당 30 (두 번 할당되지 않는다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SalesConfirmRaceTest {

	private static final int REQUESTS = 10;

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\":\"([A-Z_]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@BeforeEach
	void setUp() throws Exception {
		SalesFixtures.reset(jdbcTemplate);
		SalesFixtures.stock(jdbcTemplate, 1, "100");
		for (int i = 1; i <= REQUESTS; i++) {
			send("/api/sales-orders", SalesFixtures.orderJson("30", "0"));
		}
	}

	@Test
	void 다른_수주_10건을_동시에_확정해도_가용_재고보다_많이_약속하지_않는다() throws Exception {
		List<HttpRequest> confirms = new ArrayList<>();
		for (int so = 1; so <= REQUESTS; so++) {
			confirms.add(request("/api/sales-orders/%d/confirm".formatted(so), "{\"version\": 0}"));
		}

		Map<String, Integer> outcome = race(confirms);
		BigDecimal allocated = allocated();
		long confirmed = jdbcTemplate.queryForObject(
				"select count(*) from sales_order where status = 'CONFIRMED'", Long.class);
		System.out.printf("[동시 확정, 다른 수주] %s, 확정=%d, 할당=%s%n", outcome, confirmed, allocated);

		assertThat(outcome).containsExactly(Map.entry("200", 3), Map.entry("409 INSUFFICIENT_STOCK", 7));
		assertThat(confirmed).isEqualTo(3);
		assertThat(allocated).isEqualByComparingTo("90");
	}

	@Test
	void 같은_수주를_동시에_10번_확정해도_할당은_한_번이다() throws Exception {
		List<HttpRequest> confirms = new ArrayList<>();
		for (int i = 0; i < REQUESTS; i++) {
			confirms.add(request("/api/sales-orders/1/confirm", "{\"version\": 0}"));
		}

		Map<String, Integer> outcome = race(confirms);
		BigDecimal allocated = allocated();
		long events = jdbcTemplate.queryForObject(
				"select count(*) from sales_order_event where sales_order_id = 1 and type = 'CONFIRMED'", Long.class);
		System.out.printf("[동시 확정, 같은 수주] %s, 확정 이력=%d, 할당=%s%n", outcome, events, allocated);

		assertThat(outcome).containsExactly(Map.entry("200", 1), Map.entry("409 CONCURRENT_MODIFICATION", 9));
		assertThat(events).isEqualTo(1);
		assertThat(allocated).isEqualByComparingTo("30");
	}

	private Map<String, Integer> race(List<HttpRequest> requests) throws Exception {
		CountDownLatch ready = new CountDownLatch(requests.size());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(requests.size());
		List<Future<HttpResponse<String>>> futures = new ArrayList<>();
		try {
			for (HttpRequest request : requests) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return client.send(request, HttpResponse.BodyHandlers.ofString());
				}));
			}
			ready.await(5, TimeUnit.SECONDS);
			start.countDown();
			Map<String, Integer> outcome = new TreeMap<>();
			for (Future<HttpResponse<String>> future : futures) {
				outcome.merge(describe(future.get(60, TimeUnit.SECONDS)), 1, Integer::sum);
			}
			return outcome;
		} finally {
			pool.shutdownNow();
		}
	}

	private BigDecimal allocated() {
		return jdbcTemplate.queryForObject("select allocated_quantity from stock where item_id = 1 and warehouse_id = 1",
				BigDecimal.class);
	}

	private void send(String path, String body) throws Exception {
		HttpResponse<String> response = client.send(request(path, body), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
	}

	private HttpRequest request(String path, String body) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("X-User", "sales01")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
	}

	private static String describe(HttpResponse<String> response) {
		if (response.statusCode() == 200) {
			return "200";
		}
		Matcher matcher = ERROR_CODE.matcher(response.body());
		return response.statusCode() + " " + (matcher.find() ? matcher.group(1) : "?");
	}
}
