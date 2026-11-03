package com.minierp.master.item;

import org.jspecify.annotations.Nullable;

import com.minierp.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 품목 마스터.
 *
 * setter를 두지 않는다. 값은 의도가 드러나는 메서드(changeInfo, deactivate)로만 바뀐다.
 * - 코드는 업무 키라서 등록 후 바꿀 수 없다 (엑셀·바코드·다른 시스템이 코드로 이 품목을 부른다)
 * - 삭제 대신 사용 중지한다 (과거 거래가 이 품목을 참조하고 있다)
 */
@Entity
public class Item extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 100)
	private @Nullable String spec;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Unit unit;

	@Column(nullable = false)
	private boolean active;

	/** JPA가 조회 결과로 객체를 만들 때만 쓴다 */
	protected Item() {
	}

	public Item(String code, String name, @Nullable String spec, Unit unit) {
		this.code = code;
		this.name = name.strip();
		this.spec = normalize(spec);
		this.unit = unit;
		this.active = true;
	}

	public void changeInfo(String name, @Nullable String spec, Unit unit) {
		this.name = name.strip();
		this.spec = normalize(spec);
		this.unit = unit;
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

	public @Nullable String getSpec() {
		return spec;
	}

	public Unit getUnit() {
		return unit;
	}

	public boolean isActive() {
		return active;
	}
}
