package com.minierp.master.partner;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 사업자등록번호 (예: 123-45-67891).
 *
 * 앞 3자리는 세무서, 가운데 2자리는 사업자 구분(개인·법인 등), 뒤 5자리는 일련번호이고 그중 마지막 1자리가 검증번호다.
 * <ul>
 * <li>저장은 숫자 10자리 한 가지 형식으로만 한다. "123-45-67891"과 "1234567891"이 섞여 저장되면
 * 같은 회사인데도 unique 제약이 서로 다른 값으로 보고 중복 등록을 막지 못한다.</li>
 * <li>검증번호로 오타를 입구에서 거른다. 숫자 하나를 잘못 치면 검증번호가 맞지 않는다.</li>
 * </ul>
 */
public final class BusinessNumber {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

	private static final Pattern TEN_DIGITS = Pattern.compile("[0-9]{10}");

	private static final int[] WEIGHTS = { 1, 3, 7, 1, 3, 7, 1, 3, 5 };

	private BusinessNumber() {
	}

	/** 하이픈·공백을 지운다. 비어 있으면 null (사업자등록번호가 없는 거래처: 개인 고객, 해외 거래처) */
	public static @Nullable String normalize(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return SEPARATORS.matcher(raw).replaceAll("");
	}

	public static boolean isTenDigits(String digits) {
		return TEN_DIGITS.matcher(digits).matches();
	}

	/**
	 * 검증번호가 맞는지 본다.
	 * 앞 9자리에 가중치 1,3,7,1,3,7,1,3,5를 곱해 더하고, (9번째 자리 × 5)의 십의 자리를 한 번 더 더한다.
	 * 10에서 그 합의 일의 자리를 뺀 값(10이면 0)이 마지막 자리와 같아야 한다.
	 */
	public static boolean hasValidCheckDigit(String digits) {
		if (!isTenDigits(digits)) {
			return false;
		}
		int sum = 0;
		for (int i = 0; i < WEIGHTS.length; i++) {
			sum += digitAt(digits, i) * WEIGHTS[i];
		}
		sum += digitAt(digits, 8) * 5 / 10;
		return (10 - sum % 10) % 10 == digitAt(digits, 9);
	}

	/** 1234567891 → 123-45-67891 (메시지 표시용) */
	public static String format(String digits) {
		if (!isTenDigits(digits)) {
			return digits;
		}
		return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
	}

	private static int digitAt(String digits, int index) {
		return digits.charAt(index) - '0';
	}

	/** {@link ValidBusinessNumber}의 검사 로직. 형식 오류와 검증번호 오류를 다른 메시지로 알려 준다 */
	public static class Validator implements ConstraintValidator<ValidBusinessNumber, String> {

		@Override
		public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
			String digits = normalize(value);
			if (digits == null) {
				return true; // 선택 입력
			}
			if (!isTenDigits(digits)) {
				return reject(context, "사업자등록번호는 숫자 10자리여야 합니다. (하이픈은 있어도 됩니다)");
			}
			if (!hasValidCheckDigit(digits)) {
				return reject(context, "사업자등록번호가 올바르지 않습니다. 마지막 자리(검증번호)가 맞지 않으니 번호를 다시 확인하세요.");
			}
			return true;
		}

		private static boolean reject(ConstraintValidatorContext context, String message) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
			return false;
		}
	}
}
