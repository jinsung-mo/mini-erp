package com.minierp.purchase.order;

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
 * 발주 줄: 어떤 품목을, 몇 개, 얼마에. 지금까지 몇 개 받았는지(receivedQuantity)도 여기에 누적한다.
 *
 * 줄은 머리(PurchaseOrder)를 통해서만 만들고 바꾼다. 줄을 따로 저장하는 Repository가 없다.
 * 줄 하나만 바꾸면 "발주 전체가 입고 완료인가" 같은 머리의 상태가 어긋날 수 있기 때문이다 (집합 루트).
 */
@Entity
public class PurchaseOrderLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
	private PurchaseOrder purchaseOrder;

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
	private BigDecimal receivedQuantity;

	protected PurchaseOrderLine() {
	}

	PurchaseOrderLine(PurchaseOrder purchaseOrder, int lineNo, Item item, BigDecimal quantity, BigDecimal unitPrice) {
		this.purchaseOrder = purchaseOrder;
		this.lineNo = lineNo;
		this.receivedQuantity = BigDecimal.ZERO;
		change(item, quantity, unitPrice);
	}

	/** 작성 중(DRAFT)에만 불린다. 그때는 받은 수량이 항상 0이다 */
	void change(Item item, BigDecimal quantity, BigDecimal unitPrice) {
		if (quantity.signum() <= 0 || unitPrice.signum() < 0) {
			throw new IllegalArgumentException("수량은 0보다 크고 단가는 0 이상이어야 합니다.");
		}
		this.item = item;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	void receive(BigDecimal amount) {
		if (amount.compareTo(remainingQuantity()) > 0) {
			throw new BusinessException(ErrorCode.EXCEEDS_REMAINING_QUANTITY,
					"%d번 줄(%s)은 %s만 남았는데 %s을(를) 입고하려고 했습니다."
							.formatted(lineNo, item.getCode(), plain(remainingQuantity()), plain(amount)));
		}
		this.receivedQuantity = receivedQuantity.add(amount);
	}

	/** 금액 = 수량 × 단가. 원 단위 미만은 반올림한다 (부가세는 다루지 않는다) */
	public BigDecimal amount() {
		return quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
	}

	public BigDecimal remainingQuantity() {
		return quantity.subtract(receivedQuantity);
	}

	public boolean isFullyReceived() {
		return remainingQuantity().signum() == 0;
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	public Long getId() {
		return id;
	}

	public PurchaseOrder getPurchaseOrder() {
		return purchaseOrder;
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

	public BigDecimal getReceivedQuantity() {
		return receivedQuantity;
	}
}
