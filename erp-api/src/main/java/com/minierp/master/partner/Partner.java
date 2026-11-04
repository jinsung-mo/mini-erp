package com.minierp.master.partner;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.minierp.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 거래처 마스터.
 *
 * 한 회사는 거래처 하나다. 공급처(매입)·고객(매출)은 레코드를 따로 만들지 않고 역할로 붙인다.
 * 같은 회사를 두 번 등록하면 시스템은 둘이 같은 회사인지 모르고, 거래 실적과 채권·채무가 둘로 쪼개진다.
 * 역할은 DB에 boolean 컬럼 두 개로 저장하고, 밖으로는 Set&lt;PartnerRole&gt;로 보여 준다.
 */
@Entity
public class Partner extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	/** 숫자 10자리로만 저장한다. 없으면 null (개인 고객, 해외 거래처) */
	@Column(length = 10)
	private @Nullable String businessNo;

	@Column(nullable = false)
	private boolean supplier;

	@Column(nullable = false)
	private boolean customer;

	@Column(nullable = false)
	private boolean active;

	/** JPA가 조회 결과로 객체를 만들 때만 쓴다 */
	protected Partner() {
	}

	public Partner(String code, String name, @Nullable String businessNo, Set<PartnerRole> roles) {
		this.code = code;
		this.active = true;
		apply(name, businessNo, roles);
	}

	public void changeInfo(String name, @Nullable String businessNo, Set<PartnerRole> roles) {
		apply(name, businessNo, roles);
	}

	/** 입력 검증(DTO)을 거쳐 왔더라도 엔티티는 자기 규칙을 한 번 더 지킨다. 여기서 실패하면 버그다 */
	private void apply(String name, @Nullable String businessNo, Set<PartnerRole> roles) {
		String normalized = BusinessNumber.normalize(businessNo);
		if (normalized != null && !BusinessNumber.hasValidCheckDigit(normalized)) {
			throw new IllegalArgumentException("사업자등록번호가 올바르지 않습니다: " + businessNo);
		}
		if (roles.isEmpty()) {
			throw new IllegalArgumentException("거래처 역할이 하나 이상 있어야 합니다.");
		}
		this.name = name.strip();
		this.businessNo = normalized;
		this.supplier = roles.contains(PartnerRole.SUPPLIER);
		this.customer = roles.contains(PartnerRole.CUSTOMER);
	}

	public void deactivate() {
		this.active = false;
	}

	public void activate() {
		this.active = true;
	}

	/** 2단계 발주는 hasRole(SUPPLIER), 4단계 수주는 hasRole(CUSTOMER)인 거래처만 받는다 */
	public boolean hasRole(PartnerRole role) {
		return switch (role) {
			case SUPPLIER -> supplier;
			case CUSTOMER -> customer;
		};
	}

	public Set<PartnerRole> getRoles() {
		Set<PartnerRole> roles = EnumSet.noneOf(PartnerRole.class);
		for (PartnerRole role : PartnerRole.values()) {
			if (hasRole(role)) {
				roles.add(role);
			}
		}
		return roles;
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

	public @Nullable String getBusinessNo() {
		return businessNo;
	}

	public boolean isActive() {
		return active;
	}
}
