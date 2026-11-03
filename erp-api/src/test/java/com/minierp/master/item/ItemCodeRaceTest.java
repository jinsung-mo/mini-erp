package com.minierp.master.item;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 트러블슈팅 재현: "중복 검사를 했는데도 중복 에러(500)가 난다"
 *
 * 같은 코드로 동시에 여러 요청이 들어오면, 여러 요청이 existsByCode 검사를 동시에 "없음"으로 통과할 수 있다.
 * 그중 하나만 INSERT에 성공하고, 나머지는 DB unique 제약(uk_item_code)에 걸린다.
 * 이 예외를 처리하지 않으면 500이 나간다. GlobalExceptionHandler가 409 DUPLICATE_KEY로 바꾼다.
 *
 * MockMvc가 아니라 실제 서버(RANDOM_PORT)를 띄우고, 진짜 HTTP 요청을 여러 스레드에서 동시에 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ItemCodeRaceTest {

	private static final int REQUESTS = 10;

	private static final Pattern ERROR_CODE = Pattern.compile("\"code\":\"([A-Z_]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanUp() {
		jdbcTemplate.execute("truncate table item restart identity");
	}

	@Test
	void 같은_코드를_동시에_10번_등록해도_1건만_저장되고_500은_나가지_않는다() throws Exception {
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/items"))
				.header("Content-Type", "application/json")
				.header("X-User", "buyer01")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"code": "RACE-001", "name": "동시 등록 테스트", "unit": "EA"}
						"""))
				.build();

		// 모든 스레드가 준비될 때까지 기다렸다가 한꺼번에 출발시킨다
		CountDownLatch ready = new CountDownLatch(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
		List<Future<HttpResponse<String>>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < REQUESTS; i++) {
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
				HttpResponse<String> response = future.get(30, TimeUnit.SECONDS);
				outcome.merge(describe(response), 1, Integer::sum);
			}
			System.out.println("[동시 등록 결과] " + outcome);

			assertThat(outcome).containsEntry("201", 1);
			assertThat(outcome.keySet()).allSatisfy(key -> assertThat(key).matches("201|409 (DUPLICATE_CODE|DUPLICATE_KEY)"));
			assertThat(jdbcTemplate.queryForObject("select count(*) from item where code = 'RACE-001'", Long.class))
					.isEqualTo(1L);
		} finally {
			pool.shutdownNow();
		}
	}

	/** 201은 상태코드만, 실패는 "상태코드 + 에러 code"로 묶어서 센다 */
	private static String describe(HttpResponse<String> response) {
		if (response.statusCode() == 201) {
			return "201";
		}
		Matcher matcher = ERROR_CODE.matcher(response.body());
		return response.statusCode() + " " + (matcher.find() ? matcher.group(1) : "?");
	}
}
