package com.minierp.common.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DB 없이 웹 계층(MVC)만 띄워서 에러 응답 형식을 검증한다.
 * 테스트 전용 컨트롤러가 일부러 예외를 던지고, 응답이 ProblemDetail + code 형식인지 확인한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 업무_예외는_ErrorCode의_상태와_code로_응답한다() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("품목 ITEM-001을 찾을 수 없습니다."));
	}

	@Test
	void 검증_실패는_400과_필드별_오류를_준다() throws Exception {
		mockMvc.perform(post("/test/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "quantity": -1}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors", hasSize(2)))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "quantity")));
	}

	@Test
	void JSON_형식이_깨지면_400_BAD_REQUEST() throws Exception {
		mockMvc.perform(post("/test/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void 없는_URL은_404_NOT_FOUND() throws Exception {
		mockMvc.perform(get("/test/no-such-path"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 지원하지_않는_HTTP_메서드는_405_METHOD_NOT_ALLOWED() throws Exception {
		mockMvc.perform(delete("/test/business"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 예상하지_못한_예외는_500이고_내부_메시지를_숨긴다() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
	}

	@Test
	void DB_unique_제약_위반은_409_DUPLICATE_KEY() throws Exception {
		mockMvc.perform(get("/test/unique-violation"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
	}

	@Test
	void 그_밖의_DB_제약_위반은_버그이므로_500() throws Exception {
		mockMvc.perform(get("/test/not-null-violation"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
	}

	@RestController
	@RequestMapping("/test")
	static class TestController {

		@GetMapping("/unique-violation")
		void uniqueViolation() {
			throw new DataIntegrityViolationException("duplicate",
					new SQLException("duplicate key value violates unique constraint \"uk_item_code\"", "23505"));
		}

		@GetMapping("/not-null-violation")
		void notNullViolation() {
			throw new DataIntegrityViolationException("not null",
					new SQLException("null value in column \"name\" violates not-null constraint", "23502"));
		}

		@GetMapping("/business")
		void business() {
			throw new BusinessException(ErrorCode.NOT_FOUND, "품목 ITEM-001을 찾을 수 없습니다.");
		}

		@PostMapping("/validate")
		void validate(@Valid @RequestBody CreateRequest request) {
		}

		@GetMapping("/unexpected")
		void unexpected() {
			// 이런 내부 정보가 응답으로 새어 나가면 안 된다
			throw new IllegalStateException("connection refused: db-host=10.0.0.5 password=secret");
		}
	}

	record CreateRequest(@NotBlank String name, @NotNull @Positive Integer quantity) {
	}
}
