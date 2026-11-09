package com.minierp.master.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
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
 * 거래처 API를 HTTP 요청부터 실제 PostgreSQL까지 통과시켜 검증한다.
 * 품목과 같은 부분(감사 컬럼, 코드 중복, 사용 중지)은 ItemApiTest가 보고, 여기서는 거래처에만 있는 규칙을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PartnerApiTest {

	// 검증번호 공식으로 계산한 테스트용 사업자등록번호
	static final String BIZ_NO_A = "123-45-67891";
	static final String BIZ_NO_B = "113-81-00019";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanUp() {
		jdbcTemplate.execute("truncate table partner restart identity cascade");
	}

	@Test
	void 등록하면_사업자번호는_숫자_10자리로_저장하고_역할을_돌려준다() throws Exception {
		create("P0001", "가나철강", BIZ_NO_A, "SUPPLIER")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/partners/1"))
				.andExpect(jsonPath("$.businessNo").value("1234567891"))
				.andExpect(jsonPath("$.roles", contains("SUPPLIER")))
				.andExpect(jsonPath("$.createdBy").value("admin"));

		assertThat(jdbcTemplate.queryForObject("select business_no from partner where code = 'P0001'", String.class))
				.isEqualTo("1234567891");
	}

	@Test
	void 공급처이면서_고객인_거래처는_한_번만_등록하고_역할을_둘_다_가진다() throws Exception {
		create("P0001", "마바상사", BIZ_NO_A, "CUSTOMER", "SUPPLIER")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roles", contains("SUPPLIER", "CUSTOMER")));
	}

	@Test
	void 검증번호가_틀리면_400과_사업자번호_필드_오류() throws Exception {
		create("P0001", "가나철강", "123-45-67890", "SUPPLIER")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[0].field").value("businessNo"))
				.andExpect(jsonPath("$.errors[0].message", containsString("검증번호")));
	}

	@Test
	void 구분을_하나도_고르지_않으면_400과_roles_필드_오류() throws Exception {
		create("P0001", "가나철강", BIZ_NO_A)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("roles"));
	}

	@Test
	void 하이픈_유무가_달라도_같은_사업자번호면_409_DUPLICATE_BUSINESS_NO() throws Exception {
		create("P0001", "가나철강", "123-45-67891", "SUPPLIER").andExpect(status().isCreated());

		create("P0002", "가나철강(주)", "1234567891", "CUSTOMER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NO"))
				// 어느 거래처와 겹치는지 알려 준다: 새로 등록하지 말고 P0001에 고객 역할을 추가하면 된다
				.andExpect(jsonPath("$.detail", containsString("P0001")));
	}

	@Test
	void 사업자번호가_없는_거래처는_여러_개_등록할_수_있다() throws Exception {
		// unique 제약은 NULL끼리를 같은 값으로 보지 않는다
		create("P0001", "해외 공급처", null, "SUPPLIER").andExpect(status().isCreated());
		create("P0002", "개인 고객", null, "CUSTOMER").andExpect(status().isCreated());
	}

	@Test
	void 수정할_때_자기_사업자번호는_중복이_아니다() throws Exception {
		create("P0001", "가나철강", BIZ_NO_A, "SUPPLIER").andExpect(status().isCreated());

		update(1, "가나철강(주)", BIZ_NO_A, "SUPPLIER", "CUSTOMER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("가나철강(주)"))
				.andExpect(jsonPath("$.roles", contains("SUPPLIER", "CUSTOMER")))
				.andExpect(jsonPath("$.updatedBy").value("buyer01"));
	}

	@Test
	void 수정해서_다른_거래처의_사업자번호와_겹치면_409_DUPLICATE_BUSINESS_NO() throws Exception {
		create("P0001", "가나철강", BIZ_NO_A, "SUPPLIER").andExpect(status().isCreated());
		create("P0002", "다라볼트", BIZ_NO_B, "SUPPLIER").andExpect(status().isCreated());

		update(2, "다라볼트", BIZ_NO_A, "SUPPLIER")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NO"));
	}

	@Test
	void 역할로_조회하면_그_역할이_있는_거래처만_나온다() throws Exception {
		create("P0001", "공급 전용", null, "SUPPLIER");
		create("P0002", "고객 전용", null, "CUSTOMER");
		create("P0003", "공급 겸 고객", null, "SUPPLIER", "CUSTOMER");

		mockMvc.perform(get("/api/partners").param("role", "SUPPLIER"))
				.andExpect(jsonPath("$[*].code", contains("P0001", "P0003")));
		mockMvc.perform(get("/api/partners").param("role", "CUSTOMER"))
				.andExpect(jsonPath("$[*].code", contains("P0002", "P0003")));
		mockMvc.perform(get("/api/partners"))
				.andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void 숫자로_된_검색어는_사업자번호에서도_찾는다() throws Exception {
		create("P0001", "가나철강", BIZ_NO_A, "SUPPLIER");
		create("P0002", "다라볼트", BIZ_NO_B, "SUPPLIER");

		mockMvc.perform(get("/api/partners").param("keyword", "123-45"))
				.andExpect(jsonPath("$[*].code", contains("P0001")));
		// 숫자가 없는 검색어로 사업자번호 조건을 만들면 "%%"가 되어 모든 거래처가 걸린다. 그러지 않는지 확인
		mockMvc.perform(get("/api/partners").param("keyword", "볼트"))
				.andExpect(jsonPath("$[*].code", contains("P0002")));
	}

	@Test
	void 역할_값이_틀리면_400_BAD_REQUEST() throws Exception {
		mockMvc.perform(get("/api/partners").param("role", "VENDOR"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	private ResultActions create(String code, String name, @Nullable String businessNo, String... roles)
			throws Exception {
		return mockMvc.perform(post("/api/partners").header("X-User", "admin")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code": "%s", "name": "%s", "businessNo": %s, "roles": %s}
						""".formatted(code, name, jsonString(businessNo), jsonArray(roles))));
	}

	private ResultActions update(long id, String name, @Nullable String businessNo, String... roles)
			throws Exception {
		return mockMvc.perform(put("/api/partners/" + id).header("X-User", "buyer01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "businessNo": %s, "roles": %s}
						""".formatted(name, jsonString(businessNo), jsonArray(roles))));
	}

	private static String jsonString(@Nullable String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static String jsonArray(String... values) {
		return Arrays.stream(values).map(PartnerApiTest::jsonString).collect(Collectors.joining(", ", "[", "]"));
	}
}
