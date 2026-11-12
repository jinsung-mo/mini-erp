package com.minierp.sales.order;

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
 * 수주 상태 변경 이력 한 줄. 한 번 쌓이면 고치거나 지우지 않는다 (발주 이력과 같은 구조).
 * 누가 언제 확정했는지(= 재고를 약속했는지), 출고마다 어느 출고 번호였는지, 취소·마감 사유가 무엇인지 남는다.
 */
@Entity
public class SalesOrderEvent {

	public enum Type {
		CREATED, CONFIRMED, SHIPPED, CLOSED, CANCELED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_order_id", nullable = false, updatable = false)
	private SalesOrder salesOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private @Nullable SalesOrderStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SalesOrderStatus toStatus;

	@Column(updatable = false, length = 500)
	private @Nullable String reason;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false, length = 50)
	private String actor;

	protected SalesOrderEvent() {
	}

	SalesOrderEvent(SalesOrder salesOrder, Type type, @Nullable SalesOrderStatus fromStatus,
			SalesOrderStatus toStatus, @Nullable String reason, String actor, Instant occurredAt) {
		this.salesOrder = salesOrder;
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

	public @Nullable SalesOrderStatus getFromStatus() {
		return fromStatus;
	}

	public SalesOrderStatus getToStatus() {
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
