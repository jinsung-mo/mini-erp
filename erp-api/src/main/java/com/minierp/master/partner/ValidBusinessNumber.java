package com.minierp.master.partner;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 사업자등록번호 형식 검증 (Bean Validation 커스텀 제약). 비어 있으면 통과한다: 사업자등록번호는 선택 입력이다.
 *
 * DTO 필드에 붙이면 @NotBlank 같은 기본 제약과 똑같이 400 응답의 errors에 필드 오류로 나간다.
 * 형식 검증은 이렇게 입구(DTO)에서 하고, DB를 봐야 하는 검증(중복)은 Service에서 한다.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BusinessNumber.Validator.class)
public @interface ValidBusinessNumber {

	String message() default "사업자등록번호가 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
