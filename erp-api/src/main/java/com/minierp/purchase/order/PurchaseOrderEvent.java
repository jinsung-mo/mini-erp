package com.minierp.purchase.order;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 발주 상태 변경 이력 한 줄. 한 번 쌓이면 고치거나 지우지 않는다 (그래서 수정자 컬럼이 없고 BaseEntity를 상속하지 않는다).
 *
 * 발주 머리의 감사 컬럼(updated_by)은 "마지막으로 바꾼 사람" 한 명만 남는다. 누가 승인 요청하고, 누가 반려하고, 누가 승인했는지는
 * 덮어써지면 안 되는 기록이라 따로 쌓는다. 직무 분리 검사(본인 요청 승인 금지)도 이 이력을 본다.
 */
@Entity
public class PurchaseOrderEvent {

	public enum Type {
		CREATED, SUBMITTED, APPROVED, REJECTED, RECEIVED, CLOSED, CANCELED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
	private PurchaseOrder purchaseOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private @Nullable PurchaseOrderStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private PurchaseOrderStatus toStatus;

	@Column(updatable = false, length = 500)
	private @Nullable String reason;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false, length = 50)
	private String actor;

	protected PurchaseOrderEvent() {
	}

	PurchaseOrderEvent(PurchaseOrder purchaseOrder, Type type, @Nullable PurchaseOrderStatus fromStatus,
			PurchaseOrderStatus toStatus, @Nullable String reason, String actor, Instant occurredAt) {
		this.purchaseOrder = purchaseOrder;
		this.type = type;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason;
		this.actor = actor;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public Type getType() {
		return type;
	}

	public @Nullable PurchaseOrderStatus getFromStatus() {
		return fromStatus;
	}

	public PurchaseOrderStatus getToStatus() {
		return toStatus;
	}

	public @Nullable String getReason() {
		return reason;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getActor() {
		return actor;
	}
}
