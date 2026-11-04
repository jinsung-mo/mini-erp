package com.minierp.master.warehouse;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.minierp.master.MasterCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 창고 API의 요청·응답 형식 */
public final class WarehouseDtos {

	private WarehouseDtos() {
	}

	public record CreateRequest(
			@NotNull(message = "창고 코드를 입력하세요.")
			@Pattern(regexp = MasterCode.PATTERN, message = MasterCode.MESSAGE)
			String code,

			@NotBlank(message = "창고명을 입력하세요.")
			@Size(max = 100, message = "창고명은 100자 이하여야 합니다.")
			String name,

			@Size(max = 200, message = "위치는 200자 이하여야 합니다.")
			@Nullable String location) {
	}

	/** 코드는 바꿀 수 없으므로 수정 요청에는 없다 */
	public record UpdateRequest(
			@NotBlank(message = "창고명을 입력하세요.")
			@Size(max = 100, message = "창고명은 100자 이하여야 합니다.")
			String name,

			@Size(max = 200, message = "위치는 200자 이하여야 합니다.")
			@Nullable String location) {
	}

	public record Response(
			Long id,
			String code,
			String name,
			@Nullable String location,
			boolean active,
			Instant createdAt,
			String createdBy,
			Instant updatedAt,
			String updatedBy) {

		static Response from(Warehouse warehouse) {
			return new Response(warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.getLocation(),
					warehouse.isActive(), warehouse.getCreatedAt(), warehouse.getCreatedBy(), warehouse.getUpdatedAt(),
					warehouse.getUpdatedBy());
		}
	}
}
