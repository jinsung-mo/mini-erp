package com.minierp.sales.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.master.item.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 수주 줄: 어떤 품목을, 몇 개, 얼마에 팔기로 했나. 지금까지 몇 개 내보냈는지(shippedQuantity)도 누적한다.
 * 확정 뒤에는 남은 수량(quantity - shippedQuantity)이 곧 이 줄이 붙잡고 있는 할당량이다.
 */
@Entity
public class SalesOrderLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_order_id", nullable = false, updatable = false)
	private SalesOrder salesOrder;

	@Column(nullable = false)
	private int lineNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal quantity;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal shippedQuantity;

	protected SalesOrderLine() {
	}

	SalesOrderLine(SalesOrder salesOrder, int lineNo, Item item, BigDecimal quantity, BigDecimal unitPrice) {
		this.salesOrder = salesOrder;
		this.lineNo = lineNo;
		this.shippedQuantity = BigDecimal.ZERO;
		change(item, quantity, unitPrice);
	}

	/** 작성 중(DRAFT)에만 불린다. 그때는 출고 수량이 항상 0이다 */
	void change(Item item, BigDecimal quantity, BigDecimal unitPrice) {
		if (quantity.signum() <= 0 || unitPrice.signum() < 0) {
			throw new IllegalArgumentException("수량은 0보다 크고 단가는 0 이상이어야 합니다.");
		}
		this.item = item;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	void ship(BigDecimal amount) {
		if (amount.compareTo(remainingQuantity()) > 0) {
			throw new BusinessException(ErrorCode.EXCEEDS_REMAINING_QUANTITY,
					"%d번 줄(%s)은 %s만 남았는데 %s을(를) 출고하려고 했습니다."
							.formatted(lineNo, item.getCode(), plain(remainingQuantity()), plain(amount)));
		}
		this.shippedQuantity = shippedQuantity.add(amount);
	}

	public BigDecimal amount() {
		return quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
	}

	public BigDecimal remainingQuantity() {
		return quantity.subtract(shippedQuantity);
	}

	public boolean isFullyShipped() {
		return remainingQuantity().signum() == 0;
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	public Long getId() {
		return id;
	}

	public SalesOrder getSalesOrder() {
		return salesOrder;
	}

	public int getLineNo() {
		return lineNo;
	}

	public Item getItem() {
		return item;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getShippedQuantity() {
		return shippedQuantity;
	}
}
