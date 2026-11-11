package com.minierp.master.warehouse;

import org.jspecify.annotations.Nullable;

import com.minierp.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 창고 마스터. 재고가 놓이는 장소다.
 *
 * 재고는 "품목 × 창고" 단위로 쌓인다. 같은 볼트라도 본사 창고의 30개와 외부 창고의 70개는 다른 재고다.
 * "재고가 남은 창고는 사용 중지할 수 없다"는 재고 모듈이 WarehouseDeactivationGuard로 검사한다.
 */
@Entity
public class Warehouse extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 200)
	private @Nullable String location;

	@Column(nullable = false)
	private boolean active;

	/** JPA가 조회 결과로 객체를 만들 때만 쓴다 */
	protected Warehouse() {
	}

	public Warehouse(String code, String name, @Nullable String location) {
		this.code = code;
		this.name = name.strip();
		this.location = normalize(location);
		this.active = true;
	}

	public void changeInfo(String name, @Nullable String location) {
		this.name = name.strip();
		this.location = normalize(location);
	}

	public void deactivate() {
		this.active = false;
	}

	public void activate() {
		this.active = true;
	}

	private static @Nullable String normalize(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public @Nullable String getLocation() {
		return location;
	}

	public boolean isActive() {
		return active;
	}
}
