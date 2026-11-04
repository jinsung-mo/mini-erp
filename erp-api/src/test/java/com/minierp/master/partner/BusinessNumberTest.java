package com.minierp.master.partner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 사업자등록번호 정규화·검증번호 규칙. DB도 Spring도 필요 없는 순수 단위 테스트다 */
class BusinessNumberTest {

	@Test
	void 하이픈과_공백을_지워_숫자만_남긴다() {
		assertThat(BusinessNumber.normalize("123-45-67891")).isEqualTo("1234567891");
		assertThat(BusinessNumber.normalize(" 123 45 67891 ")).isEqualTo("1234567891");
		assertThat(BusinessNumber.normalize("1234567891")).isEqualTo("1234567891");
	}

	@Test
	void 비어_있으면_null이다() {
		assertThat(BusinessNumber.normalize(null)).isNull();
		assertThat(BusinessNumber.normalize("")).isNull();
		assertThat(BusinessNumber.normalize("   ")).isNull();
	}

	// 검증번호 공식으로 계산한 테스트용 번호
	@ParameterizedTest
	@ValueSource(strings = { "1234567891", "1138100019", "2148600020" })
	void 검증번호가_맞으면_통과한다(String digits) {
		assertThat(BusinessNumber.hasValidCheckDigit(digits)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"1234567890", // 마지막 자리(검증번호)만 틀림
			"1234567981", // 8·9번째 자리 순서가 바뀜 (흔한 오타)
			"123456789", // 9자리
			"12345678AB" // 숫자가 아님
	})
	void 검증번호가_틀리거나_숫자_10자리가_아니면_실패한다(String digits) {
		assertThat(BusinessNumber.hasValidCheckDigit(digits)).isFalse();
	}

	@Test
	void 표시용으로_하이픈을_붙인다() {
		assertThat(BusinessNumber.format("1234567891")).isEqualTo("123-45-67891");
	}
}
