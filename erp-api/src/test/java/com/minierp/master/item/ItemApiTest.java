package com.minierp.master.item;

import static org.hamcrest.Matchers.contains;
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

/**
 * 품목 API를 HTTP 요청부터 실제 PostgreSQL까지 통과시켜 검증한다.
 *
 * 테스트 메서드에 @Transactional(끝나면 롤백)을 붙이지 않았다. 붙이면 요청 전체가 테스트 트랜잭션 하나로 묶여서
 * 커밋·flush 시점에 생기는 문제(감사 컬럼, 지연 로딩)를 테스트가 가려버린다. 대신 매번 테이블을 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ItemApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanUp() {
		jdbcTemplate.execute("truncate table item restart identity cascade");
	}

	@Test
	void 등록하면_201과_작업자가_채워진_감사정보를_돌려준다() throws Exception {
		create("buyer01", "BOLT-M10", "육각볼트 M10", "M10x30", "EA")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/items/1"))
				.andExpect(jsonPath("$.code").value("BOLT-M10"))
				.andExpect(jsonPath("$.spec").value("M10x30"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.createdBy").value("buyer01"))
				.andExpect(jsonPath("$.updatedBy").value("buyer01"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void 작업자_헤더가_없으면_anonymous로_남는다() throws Exception {
		mockMvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("""
				{"code": "NUT-M10", "name": "육각너트 M10", "unit": "EA"}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdBy").value("anonymous"));
	}

	@Test
	void 같은_코드로_다시_등록하면_409_DUPLICATE_CODE() throws Exception {
		create("buyer01", "BOLT-M10", "육각볼트 M10", null, "EA").andExpect(status().isCreated());

		create("buyer01", "BOLT-M10", "다른 이름", null, "BOX")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
	}

	@Test
	void 코드_형식이_틀리면_400과_어느_필드인지_알려준다() throws Exception {
		create("buyer01", "bolt m10", "육각볼트 M10", null, "EA")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[0].field").value("code"));
	}

	@Test
	void 수정하면_수정자는_바뀌고_등록자는_그대로이며_응답에_바로_반영된다() throws Exception {
		create("buyer01", "BOLT-M10", "육각볼트 M10", "M10x30", "EA").andExpect(status().isCreated());

		mockMvc.perform(put("/api/items/1").header("X-User", "admin")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "육각볼트 M10 (아연도금)", "spec": "M10x30", "unit": "BOX"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("육각볼트 M10 (아연도금)"))
				.andExpect(jsonPath("$.unit").value("BOX"))
				.andExpect(jsonPath("$.createdBy").value("buyer01"))
				// flush 없이 DTO를 만들면 여기가 buyer01로 나온다 (ItemService.flushAndRespond 참고)
				.andExpect(jsonPath("$.updatedBy").value("admin"));
	}

	@Test
	void 사용_중지한_품목은_사용_필터에서_빠지고_전체_조회에는_남는다() throws Exception {
		create("admin", "BOLT-M10", "육각볼트 M10", null, "EA");
		create("admin", "NUT-M10", "육각너트 M10", null, "EA");

		mockMvc.perform(post("/api/items/2/deactivate").header("X-User", "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/items").param("active", "true"))
				.andExpect(jsonPath("$[*].code", contains("BOLT-M10")));
		mockMvc.perform(get("/api/items").param("active", "false"))
				.andExpect(jsonPath("$[*].code", contains("NUT-M10")));
		mockMvc.perform(get("/api/items"))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void 검색어는_코드와_품목명에서_대소문자_구분_없이_찾는다() throws Exception {
		create("admin", "BOLT-M10", "육각볼트 M10", null, "EA");
		create("admin", "NUT-M10", "육각너트 M10", null, "EA");
		create("admin", "WASHER-10", "평와셔 10mm", null, "BOX");

		mockMvc.perform(get("/api/items").param("keyword", "볼트"))
				.andExpect(jsonPath("$[*].code", contains("BOLT-M10")));
		mockMvc.perform(get("/api/items").param("keyword", "m10"))
				.andExpect(jsonPath("$[*].code", contains("BOLT-M10", "NUT-M10")));
	}

	@Test
	void 없는_품목은_404_NOT_FOUND() throws Exception {
		mockMvc.perform(get("/api/items/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private ResultActions create(String user, String code, String name, String spec, String unit) throws Exception {
		String specJson = spec == null ? "null" : "\"" + spec + "\"";
		return mockMvc.perform(post("/api/items").header("X-User", user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code": "%s", "name": "%s", "spec": %s, "unit": "%s"}
						""".formatted(code, name, specJson, unit)));
	}
}
