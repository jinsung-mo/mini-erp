package com.minierp.master.item;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.minierp.master.MasterCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 품목 API의 요청·응답 형식.
 * 엔티티를 그대로 응답하지 않는다: 테이블 구조가 API 계약에 묶이고, 지연 로딩 필드가 트랜잭션 밖에서 터질 수 있다.
 */
public final class ItemDtos {

	private ItemDtos() {
	}

	public record CreateRequest(
			@NotNull(message = "품목 코드를 입력하세요.")
			@Pattern(regexp = MasterCode.PATTERN, message = MasterCode.MESSAGE)
			String code,

			@NotBlank(message = "품목명을 입력하세요.")
			@Size(max = 100, message = "품목명은 100자 이하여야 합니다.")
			String name,

			@Size(max = 100, message = "규격은 100자 이하여야 합니다.")
			@Nullable String spec,

			@NotNull(message = "단위를 선택하세요.")
			Unit unit) {
	}

	/** 코드는 바꿀 수 없으므로 수정 요청에는 없다 */
	public record UpdateRequest(
			@NotBlank(message = "품목명을 입력하세요.")
			@Size(max = 100, message = "품목명은 100자 이하여야 합니다.")
			String name,

			@Size(max = 100, message = "규격은 100자 이하여야 합니다.")
			@Nullable String spec,

			@NotNull(message = "단위를 선택하세요.")
			Unit unit) {
	}

	public record Response(
			Long id,
			String code,
			String name,
			@Nullable String spec,
			Unit unit,
			boolean active,
			Instant createdAt,
			String createdBy,
			Instant updatedAt,
			String updatedBy) {

		static Response from(Item item) {
			return new Response(item.getId(), item.getCode(), item.getName(), item.getSpec(), item.getUnit(),
					item.isActive(), item.getCreatedAt(), item.getCreatedBy(), item.getUpdatedAt(),
					item.getUpdatedBy());
		}
	}
}
