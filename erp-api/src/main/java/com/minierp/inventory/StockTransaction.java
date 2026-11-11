package com.minierp.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.minierp.master.item.Item;
import com.minierp.master.warehouse.Warehouse;

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
 * 수불 원장 한 줄: 어느 품목이 어느 창고에 몇 개 들어오거나 나갔나.
 *
 * 모든 컬럼이 updatable = false다. 원장은 쌓기만 하고 고치지 않는다. 잘못 기록했으면 반대 부호의 거래(조정)를 하나 더 쌓는다.
 * 그래야 "언제 누가 왜 재고를 바꿨나"가 그대로 남고, 어느 시점의 재고든 원장을 그때까지 더해서 다시 계산할 수 있다.
 */
@Entity
public class StockTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false, updatable = false)
	private Item item;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
	private Warehouse warehouse;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private StockTransactionType type;

	/** 들어오면 +, 나가면 - */
	@Column(nullable = false, updatable = false, precision = 15, scale = 3)
	private BigDecimal quantity;

	/** 이 거래 직후의 현재고 */
	@Column(nullable = false, updatable = false, precision = 15, scale = 3)
	private BigDecimal balanceAfter;

	@Column(nullable = false, updatable = false)
	private LocalDate txnDate;

	@Column(nullable = false, updatable = false, length = 20)
	private String sourceNo;

	@Column(updatable = false)
	private @Nullable Long sourceLineId;

	@Column(updatable = false, length = 500)
	private @Nullable String reason;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false, length = 50)
	private String actor;

	protected StockTransaction() {
	}

	StockTransaction(StockMovement movement, BigDecimal balanceAfter, String actor) {
		this.item = movement.item();
		this.warehouse = movement.warehouse();
		this.type = movement.type();
		this.quantity = movement.quantity();
		this.balanceAfter = balanceAfter;
		this.txnDate = movement.txnDate();
		this.sourceNo = movement.sourceNo();
		this.sourceLineId = movement.sourceLineId();
		this.reason = movement.reason();
		this.occurredAt = Instant.now();
		this.actor = actor;
	}

	public Long getId() {
		return id;
	}

	public Item getItem() {
		return item;
	}

	public Warehouse getWarehouse() {
		return warehouse;
	}

	public StockTransactionType getType() {
		return type;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public LocalDate getTxnDate() {
		return txnDate;
	}

	public String getSourceNo() {
		return sourceNo;
	}

	public @Nullable Long getSourceLineId() {
		return sourceLineId;
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
