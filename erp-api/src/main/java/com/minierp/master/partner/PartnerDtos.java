package com.minierp.master.partner;

import java.time.Instant;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.minierp.master.MasterCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 거래처 API의 요청·응답 형식.
 * DB는 역할을 boolean 컬럼 두 개(supplier, customer)로 저장하지만, API는 역할 목록(roles)으로 주고받는다.
 * 테이블 모양과 API 모양이 달라도 되는 이유가 DTO다.
 */
public final class PartnerDtos {

	private PartnerDtos() {
	}

	public record CreateRequest(
			@NotNull(message = "거래처 코드를 입력하세요.")
			@Pattern(regexp = MasterCode.PATTERN, message = MasterCode.MESSAGE)
			String code,

			@NotBlank(message = "거래처명을 입력하세요.")
			@Size(max = 100, message = "거래처명은 100자 이하여야 합니다.")
			String name,

			// 하이픈이 있어도 된다. 저장할 때 숫자 10자리로 바꾼다
			@ValidBusinessNumber
			@Nullable String businessNo,

			@NotEmpty(message = "거래처 구분(공급처·고객)을 하나 이상 선택하세요.")
			Set<@NotNull PartnerRole> roles) {
	}

	/** 코드는 바꿀 수 없어서 없다. 사업자등록번호는 오타를 고칠 수 있어야 하므로 수정할 수 있다 */
	public record UpdateRequest(
			@NotBlank(message = "거래처명을 입력하세요.")
			@Size(max = 100, message = "거래처명은 100자 이하여야 합니다.")
			String name,

			@ValidBusinessNumber
			@Nullable String businessNo,

			@NotEmpty(message = "거래처 구분(공급처·고객)을 하나 이상 선택하세요.")
			Set<@NotNull PartnerRole> roles) {
	}

	public record Response(
			Long id,
			String code,
			String name,
			// 숫자 10자리. 하이픈은 화면이 붙여서 보여 준다
			@Nullable String businessNo,
			Set<PartnerRole> roles,
			boolean active,
			Instant createdAt,
			String createdBy,
			Instant updatedAt,
			String updatedBy) {

		static Response from(Partner partner) {
			return new Response(partner.getId(), partner.getCode(), partner.getName(), partner.getBusinessNo(),
					partner.getRoles(), partner.isActive(), partner.getCreatedAt(), partner.getCreatedBy(),
					partner.getUpdatedAt(), partner.getUpdatedBy());
		}
	}
}
