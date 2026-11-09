package com.minierp.master.warehouse;

import static org.hamcrest.Matchers.contains;
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

/** 창고 API. 품목과 같은 패턴이라 핵심 흐름만 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WarehouseApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanUp() {
		jdbcTemplate.execute("truncate table warehouse restart identity cascade");
	}

	@Test
	void 등록하면_201과_Location을_돌려준다() throws Exception {
		create("WH-MAIN", "본사 자재창고", "본사 1동")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/warehouses/1"))
				.andExpect(jsonPath("$.location").value("본사 1동"))
				.andExpect(jsonPath("$.createdBy").value("wh01"));
	}

	@Test
	void 같은_코드로_다시_등록하면_409_DUPLICATE_CODE() throws Exception {
		create("WH-MAIN", "본사 자재창고", null).andExpect(status().isCreated());

		create("WH-MAIN", "다른 창고", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
	}

	@Test
	void 수정하면_창고명과_위치가_바뀌고_빈_위치는_null로_저장한다() throws Exception {
		create("WH-MAIN", "본사 자재창고", "본사 1동").andExpect(status().isCreated());

		mockMvc.perform(put("/api/warehouses/1").header("X-User", "admin")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "본사 원자재창고", "location": "  "}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("본사 원자재창고"))
				.andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.createdBy").value("wh01"))
				.andExpect(jsonPath("$.updatedBy").value("admin"));
	}

	@Test
	void 사용_중지한_창고는_사용_필터에서_빠진다() throws Exception {
		create("WH-MAIN", "본사 자재창고", null);
		create("WH-EXT", "외부 임차창고", null);

		mockMvc.perform(post("/api/warehouses/2/deactivate").header("X-User", "admin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/warehouses").param("active", "true"))
				.andExpect(jsonPath("$[*].code", contains("WH-MAIN")));
		mockMvc.perform(get("/api/warehouses"))
				.andExpect(jsonPath("$[*].code", contains("WH-EXT", "WH-MAIN")));
	}

	private ResultActions create(String code, String name, String location) throws Exception {
		String locationJson = location == null ? "null" : "\"" + location + "\"";
		return mockMvc.perform(post("/api/warehouses").header("X-User", "wh01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code": "%s", "name": "%s", "location": %s}
						""".formatted(code, name, locationJson)));
	}
}
